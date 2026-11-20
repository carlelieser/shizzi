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
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
	"gvisor.dev/gvisor/pkg/waiter"
)

// udpMapper forwards client UDP with endpoint-independent mapping and
// address-and-port-dependent filtering, as a home router does; see
// https://www.rfc-editor.org/rfc/rfc4787#section-4.1 and section 5. Sockets are
// pinned when they open, so a network change retires every mapping.
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
	client, peer, isAddressable := flowAddresses(request.ID())
	if !isAddressable {
		return false
	}
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

	flow := &udpFlow{peer: peer, conn: gonet.NewUDPConn(&queue, endpoint)}
	flow.touch()
	binding.mu.Lock()
	if previous := binding.flows[peer]; previous != nil {
		previous.conn.Close()
	}
	binding.flows[peer] = flow
	binding.mu.Unlock()
	m.mu.Unlock()

	// Send the first datagram before accepting the flow, so an unroutable
	// destination gets port unreachable (prompt happy-eyeballs fallback).
	// Any other failure loses only that datagram, as on a lossy path.
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
// socket opens.
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

	// Opened outside the lock: pinning is a netd round trip.
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
	// SetNetwork stores the handle before retireAll takes m.mu, so a re-pin
	// that raced the open shows here; its socket may be on the old network.
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

// close retires every binding and refuses new flows, which the link may still
// deliver until its TUN fd closes.
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
	binding.mu.Lock()
	isIdle := len(binding.flows) == 0
	binding.mu.Unlock()
	if isIdle {
		m.retireLocked(binding)
	}
}

// unroutableErrnos are send failures that no retry on this network can fix.
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

// flowAddresses reports the client and peer of a new flow, and false when
// either address is malformed.
func flowAddresses(id stack.TransportEndpointID) (client, peer netip.AddrPort, isAddressable bool) {
	client, isClientValid := addrPortOf(id.RemoteAddress, id.RemotePort)
	peer, isPeerValid := addrPortOf(id.LocalAddress, id.LocalPort)
	return client, peer, isClientValid && isPeerValid
}

func addrPortOf(address tcpip.Address, port uint16) (netip.AddrPort, bool) {
	ip, isValid := netip.AddrFromSlice(address.AsSlice())
	return netip.AddrPortFrom(ip, port), isValid
}
