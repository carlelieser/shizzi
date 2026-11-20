package datapath

import (
	"context"
	"errors"
	"net"
	"net/netip"
	"sync"
	"syscall"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
	"gvisor.dev/gvisor/pkg/waiter"
)

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
	isClosed bool // set by close: the session stopped, refuse new flows
}

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
	binding, err := m.currentLocked(client, handle)
	if err != nil {
		m.mu.Unlock()
		return nil, err
	}
	if binding != nil {
		return binding, nil
	}
	m.mu.Unlock()

	// Opened outside the lock: pinning is a netd round trip, and the lock
	// also serves every binding's receive path.
	upstream, err := m.listen(udpFamily(client))
	if err != nil {
		return nil, err
	}
	return m.installBinding(client, handle, upstream)
}

// currentLocked returns client's binding when it is pinned to handle, retiring
// one pinned elsewhere. Callers hold m.mu.
func (m *udpMapper) currentLocked(client netip.AddrPort, handle uint64) (*udpBinding, error) {
	if m.isClosed {
		return nil, errMapperClosed
	}
	binding := m.bindings[client]
	if binding == nil {
		return nil, nil
	}
	if binding.handle == handle {
		return binding, nil
	}
	m.retireLocked(binding)
	return nil, nil
}

// installBinding makes upstream client's binding and, on success, returns with
// m.mu held. Another flow from this client may have installed one meanwhile.
func (m *udpMapper) installBinding(client netip.AddrPort, handle uint64, upstream net.PacketConn) (*udpBinding, error) {
	m.mu.Lock()
	// SetNetwork stores the new handle before retireAll, and retireAll needs
	// m.mu, so a re-pin that raced the open is visible here. The socket may be
	// pinned to the old network: discard it rather than insert it after
	// retireAll has already swept.
	if m.network() != handle {
		m.mu.Unlock()
		upstream.Close()
		return nil, errNetworkChanged
	}
	binding, err := m.currentLocked(client, handle)
	if err != nil {
		m.mu.Unlock()
		upstream.Close()
		return nil, err
	}
	if binding != nil {
		upstream.Close()
		return binding, nil
	}

	binding = &udpBinding{
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

func udpFamily(client netip.AddrPort) string {
	if client.Addr().Is4() {
		return "udp4"
	}
	return "udp6"
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
	m.isClosed = true
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
	if !binding.isRetired {
		binding.isRetired = true
		binding.upstream.Close()
	}
}

// releaseLocked retires the binding once it carries no flow. Callers hold m.mu.
func (m *udpMapper) releaseLocked(binding *udpBinding) {
	if len(binding.flows) == 0 {
		m.retireLocked(binding)
	}
}

// unroutableErrnos are send failures meaning the destination cannot be reached
// at all from this network, as opposed to a transient failure.
var unroutableErrnos = []syscall.Errno{
	syscall.ENETUNREACH,
	syscall.EHOSTUNREACH,
	syscall.EADDRNOTAVAIL,
	syscall.EACCES,
	syscall.EPERM,
}

func isUnroutable(err error) bool {
	for _, errno := range unroutableErrnos {
		if errors.Is(err, errno) {
			return true
		}
	}
	return false
}

func addrPortOf(address tcpip.Address, port uint16) netip.AddrPort {
	ip, _ := netip.AddrFromSlice(address.AsSlice())
	return netip.AddrPortFrom(ip, port)
}
