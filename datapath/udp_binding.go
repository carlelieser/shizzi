package datapath

import (
	"errors"
	"fmt"
	"net"
	"net/netip"
	"sync"
	"sync/atomic"
	"time"
)

// firstDatagramWait bounds the inline read of a new flow's first datagram,
// already queued unless its checksum was bad. It runs on the dispatch goroutine
// every client shares, and cannot be zero: gonet rejects an expired deadline.
const firstDatagramWait = 5 * time.Millisecond

// udpBinding is one client source address and port, its upstream socket, and
// the peers it is talking to.
type udpBinding struct {
	mapper   *udpMapper
	client   netip.AddrPort
	handle   uint64 // network the upstream socket was pinned to
	upstream net.PacketConn

	isRetired bool // guarded by mapper.mu

	// mu guards flows. Lock order is mapper.mu, then mu, so the receive path
	// takes only this binding's lock.
	mu    sync.Mutex
	flows map[netip.AddrPort]*udpFlow
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

// sendFirst forwards the datagram that created the flow.
func (b *udpBinding) sendFirst(flow *udpFlow, buffer []byte) error {
	if err := flow.conn.SetReadDeadline(time.Now().Add(firstDatagramWait)); err != nil {
		return fmt.Errorf("udp %v -> %v: set first read deadline: %w", b.client, flow.peer, err)
	}
	read, err := flow.conn.Read(buffer)
	if err != nil {
		return fmt.Errorf("udp %v -> %v: read first datagram: %w", b.client, flow.peer, err)
	}
	if _, err := b.upstream.WriteTo(buffer[:read], net.UDPAddrFromAddrPort(flow.peer)); err != nil {
		return fmt.Errorf("udp %v -> %v: send first datagram: %w", b.client, flow.peer, err)
	}
	return nil
}

// send copies the client's datagrams for one peer out the shared socket until
// the flow has been idle in both directions for the idle timeout.
func (b *udpBinding) send(flow *udpFlow, buffer []byte) {
	defer b.drop(flow)

	destination := net.UDPAddrFromAddrPort(flow.peer)
	for {
		// Measured from traffic in either direction, so replies keep it alive.
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
		address, isUDPAddr := from.(*net.UDPAddr)
		if !isUDPAddr {
			continue
		}
		source := address.AddrPort()
		if b.client.Addr().Is4() {
			// A udp4 socket can report its peer in 4-in-6 form; flows are
			// keyed by the plain IPv4 the client addressed.
			source = netip.AddrPortFrom(source.Addr().Unmap(), source.Port())
		}

		b.mu.Lock()
		flow := b.flows[source]
		b.mu.Unlock()
		if flow == nil {
			continue
		}
		flow.touch()
		if _, err := flow.conn.Write(buffer[:read]); err != nil {
			// The client's endpoint is gone; nothing more reaches it.
			b.drop(flow)
		}
	}
}

// drop ends one flow, and the binding with it once no flow remains.
func (b *udpBinding) drop(flow *udpFlow) {
	flow.conn.Close()

	b.mapper.mu.Lock()
	defer b.mapper.mu.Unlock()
	b.mu.Lock()
	if b.flows[flow.peer] == flow {
		delete(b.flows, flow.peer)
	}
	b.mu.Unlock()
	b.mapper.releaseLocked(b)
}

// closeAll ends every flow after the upstream socket closes or fails.
func (b *udpBinding) closeAll() {
	b.mu.Lock()
	flows := make([]*udpFlow, 0, len(b.flows))
	for _, flow := range b.flows {
		flows = append(flows, flow)
	}
	b.mu.Unlock()

	for _, flow := range flows {
		flow.conn.Close()
	}
}

func isTimeout(err error) bool {
	var netErr net.Error
	return errors.As(err, &netErr) && netErr.Timeout()
}
