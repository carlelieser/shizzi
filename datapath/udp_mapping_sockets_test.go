package datapath

import (
	"net"
	"net/netip"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
)

// TestUDPMappingReportsUnroutableDestinations: a datagram that cannot be
// routed gets the client an ICMP port unreachable, as the per-flow dial did,
// so dual-stack clients fall back at once instead of timing out.
func TestUDPMappingReportsUnroutableDestinations(t *testing.T) {
	link, mapper := newUDPTestStack(t, 5*time.Second)
	sockets := recordSockets(mapper)
	sockets.writeErr = &net.OpError{Op: "write", Net: "udp", Err: syscall.ENETUNREACH}
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 6666)
	nowhere := netip.AddrPortFrom(netip.AddrFrom4([4]byte{203, 0, 113, 9}), 53)

	inject(link, udpPacket(client, nowhere, []byte("query")))

	if !awaitPortUnreachable(link, 2*time.Second) {
		t.Fatal("client got no port unreachable for an unroutable destination")
	}
	if open := sockets.openCount(); open != 0 {
		t.Fatalf("%d sockets left open for a flow that was refused", open)
	}
}

// awaitPortUnreachable waits for the stack to send the client an ICMP port
// unreachable.
func awaitPortUnreachable(link *channel.Endpoint, within time.Duration) bool {
	deadline := time.Now().Add(within)
	for time.Now().Before(deadline) {
		pkt := link.Read()
		if pkt == nil {
			time.Sleep(5 * time.Millisecond)
			continue
		}
		view := pkt.ToView()
		pkt.DecRef()
		ip := header.IPv4(view.AsSlice())
		isICMP := ip.TransportProtocol() == header.ICMPv4ProtocolNumber
		message := header.ICMPv4(ip.Payload())
		isPortUnreachable := isICMP && message.Type() == header.ICMPv4DstUnreachable &&
			message.Code() == header.ICMPv4PortUnreachable
		view.Release()
		if isPortUnreachable {
			return true
		}
	}
	return false
}

// TestUDPMappingRefusesFlowsAfterClose: once the session stops, packets the
// link still delivers must not open new upstream sockets.
func TestUDPMappingRefusesFlowsAfterClose(t *testing.T) {
	link, mapper := newUDPTestStack(t, 5*time.Second)
	sockets := recordSockets(mapper)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)
	mapper.close()

	inject(link, udpPacket(netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 7777),
		netip.AddrPortFrom(host, peerPort), []byte("late")))

	peer.SetReadDeadline(time.Now().Add(300 * time.Millisecond))
	if _, _, err := peer.ReadFromUDP(make([]byte, 64)); err == nil {
		t.Fatal("forwarded a datagram after close")
	}
	if opened := len(sockets.all()); opened != 0 {
		t.Fatalf("opened %d sockets after close", opened)
	}
}

// TestUDPMappingRepinDuringSocketOpen: a re-pin that lands while a socket is
// being opened (SetNetwork's store and retireAll both inside the window) must
// discard that socket and carry the flow on a fresh one.
func TestUDPMappingRepinDuringSocketOpen(t *testing.T) {
	var current atomic.Uint64
	link, mapper := newUDPTestStackWith(t, &net.Dialer{}, 5*time.Second, current.Load)
	sockets := recordSockets(mapper)
	sockets.onOpen = func(count int) {
		if count == 1 {
			// What SetNetwork does, racing the first open.
			current.Store(7)
			mapper.retireAll()
		}
	}
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)

	inject(link, udpPacket(netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 8888),
		netip.AddrPortFrom(host, peerPort), []byte("during re-pin")))
	_, mapped := receiveFrom(t, peer)

	opened := sockets.all()
	if len(opened) != 2 {
		t.Fatalf("opened %d sockets, want 2 (stale one discarded, fresh one opened)", len(opened))
	}
	if !opened[0].isClosed.Load() {
		t.Fatal("socket opened before the re-pin was kept")
	}
	if fresh := opened[1].LocalAddr().(*net.UDPAddr).Port; mapped.Port != fresh {
		t.Fatalf("flow left from port %d, want the fresh socket's %d", mapped.Port, fresh)
	}
}
