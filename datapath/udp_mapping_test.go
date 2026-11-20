package datapath

import (
	"net"
	"net/netip"
	"sync/atomic"
	"syscall"
	"testing"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip/header"
)

// TestUDPMappingSharesSourcePortAcrossPeers is the Teleport regression: one
// client port talking to two peers must leave from one upstream port, or the
// port a STUN server reports is useless for hole punching.
func TestUDPMappingSharesSourcePortAcrossPeers(t *testing.T) {
	link, _ := newUDPTestStack(t, 5*time.Second)
	host := localIPv4(t)
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 58888)

	stun, stunPort := listenPeer(t)
	home, homePort := listenPeer(t)

	inject(link, udpPacket(client, netip.AddrPortFrom(host, stunPort), []byte("binding request")))
	gotSTUN, fromSTUN := receiveFrom(t, stun)
	inject(link, udpPacket(client, netip.AddrPortFrom(host, homePort), []byte("hole punch")))
	gotHome, fromHome := receiveFrom(t, home)

	if gotSTUN != "binding request" || gotHome != "hole punch" {
		t.Fatalf("peers got %q and %q", gotSTUN, gotHome)
	}
	if fromSTUN.Port != fromHome.Port {
		t.Fatalf("client port 58888 left from upstream ports %d and %d; want one shared mapping", fromSTUN.Port, fromHome.Port)
	}

	// Each peer's reply reaches the client, from that peer.
	for _, peer := range []struct {
		conn *net.UDPConn
		port uint16
		to   *net.UDPAddr
	}{{stun, stunPort, fromSTUN}, {home, homePort, fromHome}} {
		if _, err := peer.conn.WriteToUDP([]byte("reply"), peer.to); err != nil {
			t.Fatalf("peer write: %v", err)
		}
		src, payload, ok := clientDatagram(link, 2*time.Second)
		if !ok || payload != "reply" || src != netip.AddrPortFrom(host, peer.port) {
			t.Fatalf("client got %v %q (ok=%v), want reply from %v", src, payload, ok, netip.AddrPortFrom(host, peer.port))
		}
	}
}

// TestUDPMappingFiltersUncontactedPeers keeps the stock NAT's filtering: a
// host the client never sent to cannot reach it through the shared port.
func TestUDPMappingFiltersUncontactedPeers(t *testing.T) {
	link, _ := newUDPTestStack(t, 5*time.Second)
	host := localIPv4(t)
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 40000)

	known, knownPort := listenPeer(t)
	stranger, _ := listenPeer(t)

	inject(link, udpPacket(client, netip.AddrPortFrom(host, knownPort), []byte("hi")))
	_, mapped := receiveFrom(t, known)

	if _, err := stranger.WriteToUDP([]byte("unsolicited"), mapped); err != nil {
		t.Fatalf("stranger write: %v", err)
	}
	if src, payload, ok := clientDatagram(link, 300*time.Millisecond); ok {
		t.Fatalf("client received %q from uncontacted %v", payload, src)
	}
}

// TestUDPMappingIsPerClientPort: different client source ports get different
// mappings, as two sockets behind a NAT would.
func TestUDPMappingIsPerClientPort(t *testing.T) {
	link, _ := newUDPTestStack(t, 5*time.Second)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)
	destination := netip.AddrPortFrom(host, peerPort)

	inject(link, udpPacket(netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 1111), destination, []byte("one")))
	_, first := receiveFrom(t, peer)
	inject(link, udpPacket(netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 2222), destination, []byte("two")))
	_, second := receiveFrom(t, peer)

	if first.Port == second.Port {
		t.Fatalf("client ports 1111 and 2222 share upstream port %d", first.Port)
	}
}

// TestUDPMappingReleasesIdleBindings: the shared socket closes once every flow
// on it has gone idle, so mappings do not accumulate.
func TestUDPMappingReleasesIdleBindings(t *testing.T) {
	link, mapper := newUDPTestStack(t, 150*time.Millisecond)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 3333)

	inject(link, udpPacket(client, netip.AddrPortFrom(host, peerPort), []byte("x")))
	receiveFrom(t, peer)

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		mapper.mu.Lock()
		remaining := len(mapper.bindings)
		mapper.mu.Unlock()
		if remaining == 0 {
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatal("idle binding was never released")
}

// TestUDPMappingKeptAliveByReplies: a peer that keeps sending toward a quiet
// client keeps the mapping, so the client's port does not change under it.
func TestUDPMappingKeptAliveByReplies(t *testing.T) {
	const idle = 300 * time.Millisecond
	link, mapper := newUDPTestStack(t, idle)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 4444)

	inject(link, udpPacket(client, netip.AddrPortFrom(host, peerPort), []byte("open")))
	_, mapped := receiveFrom(t, peer)

	// The peer alone sends for three idle windows; the client stays silent.
	for i := 0; i < 9; i++ {
		time.Sleep(idle / 3)
		if _, err := peer.WriteToUDP([]byte("keepalive"), mapped); err != nil {
			t.Fatalf("peer write: %v", err)
		}
		if _, _, ok := clientDatagram(link, time.Second); !ok {
			t.Fatalf("keepalive %d did not reach the client", i)
		}
	}
	if bindingCount(mapper) != 1 {
		t.Fatal("mapping dropped while the peer was still sending")
	}

	// Once both sides go quiet it is released within about one idle window.
	deadline := time.Now().Add(idle + 500*time.Millisecond)
	for bindingCount(mapper) != 0 {
		if time.Now().After(deadline) {
			t.Fatal("mapping not released after both sides went idle")
		}
		time.Sleep(10 * time.Millisecond)
	}
}

// TestUDPMappingRepinsOnNetworkChange: after the session's network changes, a
// busy client port must not keep using the socket pinned to the old network,
// not even for new destinations.
func TestUDPMappingRepinsOnNetworkChange(t *testing.T) {
	var controls atomic.Int32
	dialer := &net.Dialer{Control: func(string, string, syscall.RawConn) error {
		controls.Add(1)
		return nil
	}}
	var current atomic.Uint64
	link, mapper := newUDPTestStackWith(t, dialer, 5*time.Second, current.Load)
	host := localIPv4(t)
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 5555)
	first, firstPort := listenPeer(t)
	second, secondPort := listenPeer(t)

	inject(link, udpPacket(client, netip.AddrPortFrom(host, firstPort), []byte("before")))
	_, before := receiveFrom(t, first)

	// A VPN is adopted. Even without the session's retireAll, the next flow
	// from this busy port must open a freshly pinned socket.
	current.Store(42)
	inject(link, udpPacket(client, netip.AddrPortFrom(host, secondPort), []byte("after")))
	_, after := receiveFrom(t, second)

	if got := controls.Load(); got != 2 {
		t.Fatalf("pinning ran %d times, want 2 (once per network)", got)
	}
	if before.Port == after.Port {
		t.Fatalf("port %d survived a network change", after.Port)
	}

	// retireAll, which SetNetwork calls, drops every mapping at once.
	mapper.retireAll()
	if bindingCount(mapper) != 0 {
		t.Fatal("retireAll left mappings in place")
	}
}

// TestUDPMappingReportsUnroutableDestinations: a datagram that cannot be
// routed gets the client an ICMP port unreachable, as the per-flow dial did,
// so dual-stack clients fall back at once instead of timing out.
func TestUDPMappingReportsUnroutableDestinations(t *testing.T) {
	link, mapper := newUDPTestStack(t, 5*time.Second)
	listen := mapper.listen
	mapper.listen = func(network string) (net.PacketConn, error) {
		conn, err := listen(network)
		if err != nil {
			return nil, err
		}
		return unroutableConn{conn}, nil
	}
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 6666)
	nowhere := netip.AddrPortFrom(netip.AddrFrom4([4]byte{203, 0, 113, 9}), 53)

	inject(link, udpPacket(client, nowhere, []byte("query")))

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		pkt := link.Read()
		if pkt == nil {
			time.Sleep(5 * time.Millisecond)
			continue
		}
		view := pkt.ToView()
		pkt.DecRef()
		ip := header.IPv4(view.AsSlice())
		if ip.TransportProtocol() == header.ICMPv4ProtocolNumber {
			message := header.ICMPv4(ip.Payload())
			if message.Type() == header.ICMPv4DstUnreachable && message.Code() == header.ICMPv4PortUnreachable {
				view.Release()
				if bindingCount(mapper) != 0 {
					t.Fatal("mapping kept for a flow that was refused")
				}
				return
			}
		}
		view.Release()
	}
	t.Fatal("client got no port unreachable for an unroutable destination")
}

// TestUDPMappingRefusesFlowsAfterClose: once the session stops, packets the
// link still delivers must not open new upstream sockets.
func TestUDPMappingRefusesFlowsAfterClose(t *testing.T) {
	link, mapper := newUDPTestStack(t, 5*time.Second)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)
	mapper.close()

	inject(link, udpPacket(netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 7777),
		netip.AddrPortFrom(host, peerPort), []byte("late")))

	peer.SetReadDeadline(time.Now().Add(300 * time.Millisecond))
	if _, _, err := peer.ReadFromUDP(make([]byte, 64)); err == nil {
		t.Fatal("forwarded a datagram after close")
	}
	if bindingCount(mapper) != 0 {
		t.Fatal("opened a mapping after close")
	}
}

// TestUDPMappingRepinDuringSocketOpen: a re-pin that lands while a socket is
// being opened (SetNetwork's store and retireAll both inside the window) must
// not leave the old-network socket installed after the sweep.
func TestUDPMappingRepinDuringSocketOpen(t *testing.T) {
	var current atomic.Uint64
	link, mapper := newUDPTestStackWith(t, &net.Dialer{}, 5*time.Second, current.Load)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)

	listen := mapper.listen
	var opens atomic.Int32
	mapper.listen = func(network string) (net.PacketConn, error) {
		conn, err := listen(network)
		if opens.Add(1) == 1 {
			// What SetNetwork does, racing the first open.
			current.Store(7)
			mapper.retireAll()
		}
		return conn, err
	}

	inject(link, udpPacket(netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 8888),
		netip.AddrPortFrom(host, peerPort), []byte("during re-pin")))
	receiveFrom(t, peer)

	if got := opens.Load(); got != 2 {
		t.Fatalf("opened %d sockets, want 2 (stale one discarded, fresh one opened)", got)
	}
	mapper.mu.Lock()
	defer mapper.mu.Unlock()
	for client, binding := range mapper.bindings {
		if binding.handle != current.Load() || binding.isRetired {
			t.Fatalf("binding for %v pinned to handle %d (retired=%v), want live on %d",
				client, binding.handle, binding.isRetired, current.Load())
		}
	}
}
