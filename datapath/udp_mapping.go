package datapath

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
	"gvisor.dev/gvisor/pkg/waiter"
)

// firstDatagramWait bounds the inline read of a new flow's first datagram.
// CreateEndpoint has already queued it, so the read returns at once. The wait
// is only spent when the endpoint dropped it (a bad UDP checksum), and it runs
// on the dispatch goroutine that serves every client, so it stays short. It
// cannot be zero: gonet checks an expired deadline before reading.
const firstDatagramWait = 5 * time.Millisecond

// udpMapper forwards client UDP with endpoint-independent mapping: every
// datagram a client sends from one source address and port leaves through the
// same upstream socket, whatever its destination.
//
// One upstream socket per destination (what a connected-socket proxy gives)
// behaves like a symmetric NAT. The public port a client learns from a STUN
// server is then not the port its packets to anyone else leave from, so NAT
// traversal -- WebRTC, game consoles, and VPN hole punching such as UniFi's
// Teleport and Site Magic -- never completes. Stock tethering keeps one mapping
// per source port, and so does this.
//
// Filtering stays address-and-port dependent: a reply is delivered only from a
// peer the client has already sent to, the same as the kernel NAT it replaces.
//
// Each shared socket is pinned once, when it opens, so it records the network
// it was pinned to. When the binding changes (a VPN adopted or replaced), the
// session retires every mapping and the next datagram opens a fresh, correctly
// pinned socket: a client's mapped port changes with the network, and nothing
// keeps leaving over the network it was pinned to before.
type udpMapper struct {
	listen  func(network string) (net.PacketConn, error)
	network func() uint64
	idle    time.Duration

	mu       sync.Mutex
	bindings map[netip.AddrPort]*udpBinding
	closed   bool // set by close: the session stopped, refuse new flows
}

// udpBinding is one client source address and port, its upstream socket, and
// the peers it is talking to.
type udpBinding struct {
	mapper   *udpMapper
	client   netip.AddrPort
	handle   uint64 // network the upstream socket was pinned to
	upstream net.PacketConn

	// Guarded by mapper.mu.
	flows   map[netip.AddrPort]*udpFlow
	retired bool
}

// udpFlow is one client-to-peer association: the netstack endpoint the client
// talks to, and when traffic last crossed it in either direction.
type udpFlow struct {
	peer       netip.AddrPort
	conn       net.Conn
	lastActive atomic.Int64
}

func (f *udpFlow) touch() { f.lastActive.Store(time.Now().UnixNano()) }

func (f *udpFlow) lastSeen() time.Time { return time.Unix(0, f.lastActive.Load()) }

func newUDPMapper(dialer *net.Dialer, idle time.Duration, network func() uint64) *udpMapper {
	config := net.ListenConfig{Control: dialer.Control}
	return &udpMapper{
		listen: func(network string) (net.PacketConn, error) {
			return config.ListenPacket(context.Background(), network, ":0")
		},
		network:  network,
		idle:     idle,
		bindings: make(map[netip.AddrPort]*udpBinding),
	}
}

// forward attaches one new client flow. Returning false leaves the request
// unhandled, so the stack answers with ICMP port unreachable: the right reply
// when no upstream socket could be opened or the destination has no route.
func (m *udpMapper) forward(request *udp.ForwarderRequest) bool {
	id := request.ID()
	client := addrPortOf(id.RemoteAddress, id.RemotePort)
	peer := addrPortOf(id.LocalAddress, id.LocalPort)

	binding, err := m.bindingFor(client) // returns with m.mu held on success
	if err != nil {
		return false
	}

	var queue waiter.Queue
	endpoint, tcpipErr := request.CreateEndpoint(&queue)
	if tcpipErr != nil {
		m.releaseLocked(binding)
		m.mu.Unlock()
		return false
	}

	if previous := binding.flows[peer]; previous != nil {
		previous.conn.Close()
	}
	flow := &udpFlow{peer: peer, conn: gonet.NewUDPConn(&queue, endpoint)}
	flow.touch()
	binding.flows[peer] = flow
	m.mu.Unlock()

	// Send the first datagram before accepting the flow, so a destination
	// with no route answers the client with port unreachable -- prompt
	// happy-eyeballs fallback for a dual-stack client on a v4-only upstream --
	// instead of silence until its own timeout.
	buffer := make([]byte, maxDatagramSize)
	if err := binding.sendFirst(flow, buffer); isUnroutable(err) {
		binding.drop(flow)
		return false
	}

	go binding.send(flow, buffer)
	return true
}

// bindingFor returns client's live binding, opening one if needed, and holds
// m.mu on success. A binding pinned to a network other than the current one is
// retired and replaced.
func (m *udpMapper) bindingFor(client netip.AddrPort) (*udpBinding, error) {
	for attempt := 0; attempt < maxRepinAttempts; attempt++ {
		binding, err := m.tryBindingFor(client)
		if !errors.Is(err, errNetworkChanged) {
			return binding, err
		}
	}
	return nil, errNetworkChanged
}

// maxRepinAttempts bounds retries when the network keeps changing while a
// socket opens. Re-pins come from a 5-second watchdog, so one retry is the
// realistic worst case.
const maxRepinAttempts = 3

var errNetworkChanged = errors.New("udp mapper: network changed while opening a socket")

func (m *udpMapper) tryBindingFor(client netip.AddrPort) (*udpBinding, error) {
	handle := m.network()

	m.mu.Lock()
	if m.closed {
		m.mu.Unlock()
		return nil, errMapperClosed
	}
	if binding := m.bindings[client]; binding != nil {
		if binding.handle == handle {
			return binding, nil
		}
		m.retireLocked(binding)
	}
	m.mu.Unlock()

	// Opened outside the lock: pinning is a netd round trip, and the lock
	// also serves every binding's receive path.
	family := "udp6"
	if client.Addr().Is4() {
		family = "udp4"
	}
	upstream, err := m.listen(family)
	if err != nil {
		return nil, err
	}

	m.mu.Lock()
	if m.closed {
		m.mu.Unlock()
		upstream.Close()
		return nil, errMapperClosed
	}
	// SetNetwork stores the new handle before retireAll, and retireAll needs
	// m.mu, so a re-pin that raced the open above is visible here. The socket
	// may be pinned to the old network: discard it rather than insert it
	// after retireAll has already swept.
	if m.network() != handle {
		m.mu.Unlock()
		upstream.Close()
		return nil, errNetworkChanged
	}
	if binding := m.bindings[client]; binding != nil {
		if binding.handle == handle {
			// Another flow from this client opened one meanwhile.
			upstream.Close()
			return binding, nil
		}
		m.retireLocked(binding)
	}
	binding := &udpBinding{
		mapper:   m,
		client:   client,
		handle:   handle,
		upstream: upstream,
		flows:    make(map[netip.AddrPort]*udpFlow),
	}
	m.bindings[client] = binding
	go binding.receive()
	return binding, nil
}

// sendFirst forwards the datagram that created the flow. A nil return also
// covers "nothing queued": the send loop takes over either way.
func (b *udpBinding) sendFirst(flow *udpFlow, buffer []byte) error {
	if err := flow.conn.SetReadDeadline(time.Now().Add(firstDatagramWait)); err != nil {
		return nil
	}
	read, err := flow.conn.Read(buffer)
	if err != nil || read == 0 {
		return nil
	}
	_, err = b.upstream.WriteTo(buffer[:read], net.UDPAddrFromAddrPort(flow.peer))
	return err
}

// send copies the client's datagrams for one peer out the shared socket until
// the flow has been idle in both directions for the idle timeout.
func (b *udpBinding) send(flow *udpFlow, buffer []byte) {
	defer b.drop(flow)

	destination := net.UDPAddrFromAddrPort(flow.peer)
	for {
		// Measured from the last traffic either way, so replies -- a peer
		// that only sends keepalives toward the client -- keep it alive.
		deadline := flow.lastSeen().Add(b.mapper.idle)
		if !time.Now().Before(deadline) {
			return
		}
		if err := flow.conn.SetReadDeadline(deadline); err != nil {
			return
		}
		read, err := flow.conn.Read(buffer)
		if read > 0 {
			flow.touch()
			if _, writeErr := b.upstream.WriteTo(buffer[:read], destination); writeErr != nil {
				return
			}
		}
		if err != nil {
			if isTimeout(err) {
				continue
			}
			return
		}
	}
}

// receive delivers datagrams arriving on the shared socket to the client, from
// whichever known peer sent them. Others are dropped.
func (b *udpBinding) receive() {
	buffer := make([]byte, maxDatagramSize)
	for {
		read, from, err := b.upstream.ReadFrom(buffer)
		if err != nil {
			b.mapper.mu.Lock()
			b.mapper.retireLocked(b)
			b.mapper.mu.Unlock()
			b.closeAll()
			return
		}
		address, ok := from.(*net.UDPAddr)
		if !ok {
			continue
		}
		source := address.AddrPort()
		if b.client.Addr().Is4() {
			// A udp4 socket can report its peer in 4-in-6 form; flows are
			// keyed by the plain IPv4 the client addressed.
			source = netip.AddrPortFrom(source.Addr().Unmap(), source.Port())
		}

		b.mapper.mu.Lock()
		flow := b.flows[source]
		b.mapper.mu.Unlock()
		if flow == nil {
			continue
		}
		flow.touch()
		_, _ = flow.conn.Write(buffer[:read])
	}
}

// drop ends one flow, and the binding with it once no flow remains.
func (b *udpBinding) drop(flow *udpFlow) {
	flow.conn.Close()

	b.mapper.mu.Lock()
	defer b.mapper.mu.Unlock()
	if b.flows[flow.peer] == flow {
		delete(b.flows, flow.peer)
	}
	b.mapper.releaseLocked(b)
}

// closeAll ends every flow after the upstream socket closes or fails.
func (b *udpBinding) closeAll() {
	b.mapper.mu.Lock()
	flows := make([]*udpFlow, 0, len(b.flows))
	for _, flow := range b.flows {
		flows = append(flows, flow)
	}
	b.mapper.mu.Unlock()

	for _, flow := range flows {
		flow.conn.Close()
	}
}

// retireAll retires every binding because the session's network changed. New
// flows open freshly pinned bindings.
func (m *udpMapper) retireAll() {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, binding := range m.bindings {
		m.retireLocked(binding)
	}
}

// close retires every binding and refuses new flows: the session is stopping,
// and the link may still deliver packets until its TUN fd is closed.
func (m *udpMapper) close() {
	m.mu.Lock()
	m.closed = true
	m.mu.Unlock()
	m.retireAll()
}

var errMapperClosed = errors.New("udp mapper closed")

// retireLocked removes binding from the map and closes its socket; its receive
// loop then ends its flows. Callers hold m.mu.
func (m *udpMapper) retireLocked(binding *udpBinding) {
	if m.bindings[binding.client] == binding {
		delete(m.bindings, binding.client)
	}
	if !binding.retired {
		binding.retired = true
		binding.upstream.Close()
	}
}

// releaseLocked retires the binding once it carries no flow. Callers hold m.mu.
func (m *udpMapper) releaseLocked(binding *udpBinding) {
	if len(binding.flows) == 0 {
		m.retireLocked(binding)
	}
}

func isTimeout(err error) bool {
	var netErr net.Error
	return errors.As(err, &netErr) && netErr.Timeout()
}

// isUnroutable reports a send that failed because the destination cannot be
// reached at all from this network, as opposed to a transient failure.
func isUnroutable(err error) bool {
	return errors.Is(err, syscall.ENETUNREACH) ||
		errors.Is(err, syscall.EHOSTUNREACH) ||
		errors.Is(err, syscall.EADDRNOTAVAIL) ||
		errors.Is(err, syscall.EACCES) ||
		errors.Is(err, syscall.EPERM)
}

func addrPortOf(address tcpip.Address, port uint16) netip.AddrPort {
	ip, _ := netip.AddrFromSlice(address.AsSlice())
	return netip.AddrPortFrom(ip, port)
}
