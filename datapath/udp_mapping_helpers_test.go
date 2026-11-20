package datapath

import (
	"net"
	"net/netip"
	"syscall"
	"testing"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

// localIPv4 is a non-loopback address of this host: the netstack, like a real
// one, does not accept loopback destinations arriving on an external NIC.
func localIPv4(t *testing.T) netip.Addr {
	t.Helper()
	addresses, err := net.InterfaceAddrs()
	if err != nil {
		t.Fatalf("InterfaceAddrs: %v", err)
	}
	for _, address := range addresses {
		if prefix, ok := address.(*net.IPNet); ok {
			if ip, ok := netip.AddrFromSlice(prefix.IP.To4()); ok && prefix.IP.To4() != nil && !ip.IsLoopback() {
				return ip
			}
		}
	}
	t.Skip("no non-loopback IPv4 address to stand in for a peer")
	return netip.Addr{}
}

func newUDPTestStack(t *testing.T, idle time.Duration) (*channel.Endpoint, *udpMapper) {
	t.Helper()
	return newUDPTestStackWith(t, &net.Dialer{}, idle, func() uint64 { return unbound })
}

func newUDPTestStackWith(t *testing.T, dialer *net.Dialer, idle time.Duration, network func() uint64) (*channel.Endpoint, *udpMapper) {
	t.Helper()
	netStack, link := newPingTestStack(t)
	mapper := newUDPMapper(dialer, idle, network)
	netStack.SetTransportProtocolHandler(udp.ProtocolNumber, udp.NewForwarder(netStack, mapper.forward).HandlePacket)
	t.Cleanup(mapper.retireAll)
	return link, mapper
}

func udpPacket(src, dst netip.AddrPort, payload []byte) []byte {
	packet := make([]byte, header.IPv4MinimumSize+header.UDPMinimumSize+len(payload))
	ip := header.IPv4(packet)
	ip.Encode(&header.IPv4Fields{
		TotalLength: uint16(len(packet)),
		TTL:         64,
		Protocol:    uint8(header.UDPProtocolNumber),
		SrcAddr:     tcpip.AddrFrom4(src.Addr().As4()),
		DstAddr:     tcpip.AddrFrom4(dst.Addr().As4()),
	})
	ip.SetChecksum(^ip.CalculateChecksum())
	datagram := header.UDP(packet[header.IPv4MinimumSize:])
	datagram.Encode(&header.UDPFields{
		SrcPort: src.Port(),
		DstPort: dst.Port(),
		Length:  uint16(header.UDPMinimumSize + len(payload)),
	})
	copy(datagram.Payload(), payload)
	return packet
}

func listenPeer(t *testing.T) (*net.UDPConn, uint16) {
	t.Helper()
	conn, err := net.ListenUDP("udp4", &net.UDPAddr{})
	if err != nil {
		t.Fatalf("ListenUDP: %v", err)
	}
	t.Cleanup(func() { conn.Close() })
	return conn, uint16(conn.LocalAddr().(*net.UDPAddr).Port)
}

// receiveFrom reads one datagram at a peer and reports where it came from.
func receiveFrom(t *testing.T, peer *net.UDPConn) (string, *net.UDPAddr) {
	t.Helper()
	buffer := make([]byte, 2048)
	peer.SetReadDeadline(time.Now().Add(2 * time.Second))
	read, from, err := peer.ReadFromUDP(buffer)
	if err != nil {
		t.Fatalf("peer read: %v", err)
	}
	return string(buffer[:read]), from
}

// clientDatagram waits for a datagram the stack sends back toward the client.
func clientDatagram(link *channel.Endpoint, within time.Duration) (src netip.AddrPort, payload string, ok bool) {
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
		if !ip.IsValid(len(view.AsSlice())) || ip.TransportProtocol() != header.UDPProtocolNumber {
			view.Release()
			continue
		}
		datagram := header.UDP(ip.Payload())
		src = netip.AddrPortFrom(netip.AddrFrom4(ip.SourceAddress().As4()), datagram.SourcePort())
		payload = string(datagram.Payload())
		view.Release()
		return src, payload, true
	}
	return netip.AddrPort{}, "", false
}

func bindingCount(mapper *udpMapper) int {
	mapper.mu.Lock()
	defer mapper.mu.Unlock()
	return len(mapper.bindings)
}

// unroutableConn is an upstream socket whose sends fail as they do on a
// network with no route to the destination.
type unroutableConn struct{ net.PacketConn }

func (unroutableConn) WriteTo([]byte, net.Addr) (int, error) {
	return 0, &net.OpError{Op: "write", Net: "udp", Err: syscall.ENETUNREACH}
}
