package datapath

import (
	"net"
	"net/netip"
	"sync/atomic"
	"syscall"
	"testing"
	"time"
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
	link, _ := newUDPTestStack(t, 150*time.Millisecond)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 3333)

	inject(link, udpPacket(client, netip.AddrPortFrom(host, peerPort), []byte("x")))
	_, mapped := receiveFrom(t, peer)

	if !awaitMappingClosed(t, mapped, 2*time.Second) {
		t.Fatalf("idle mapping %v was never released", mapped)
	}
}

// TestUDPMappingKeptAliveByReplies: a peer that keeps sending toward a quiet
// client keeps the mapping, so the client's port does not change under it.
func TestUDPMappingKeptAliveByReplies(t *testing.T) {
	const idle = 600 * time.Millisecond
	link, _ := newUDPTestStack(t, idle)
	host := localIPv4(t)
	peer, peerPort := listenPeer(t)
	client := netip.AddrPortFrom(netip.AddrFrom4([4]byte{192, 0, 2, 2}), 4444)

	inject(link, udpPacket(client, netip.AddrPortFrom(host, peerPort), []byte("open")))
	_, mapped := receiveFrom(t, peer)

	// The peer alone sends for two idle windows; the client stays silent.
	for i := 0; i < 8; i++ {
		time.Sleep(idle / 4)
		if _, err := peer.WriteToUDP([]byte("keepalive"), mapped); err != nil {
			t.Fatalf("peer write: %v", err)
		}
		if _, _, ok := clientDatagram(link, time.Second); !ok {
			t.Fatalf("keepalive %d did not reach the client", i)
		}
	}

	// Once both sides go quiet it is released within about one idle window.
	if !awaitMappingClosed(t, mapped, idle+time.Second) {
		t.Fatal("mapping not released after both sides went idle")
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
	if !awaitMappingClosed(t, after, time.Second) {
		t.Fatal("retireAll left the mapping open")
	}
}
