package datapath

import (
	"bytes"
	"errors"
	"sync"
	"testing"
	"time"

	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/checksum"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/icmp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

var (
	pingClient = tcpip.AddrFrom4([4]byte{192, 0, 2, 2})
	pingTarget = tcpip.AddrFrom4([4]byte{1, 1, 1, 1})
)

// newPingTestStack is Start's stack over a channel endpoint: the same protocol
// set, including ICMP, with the NIC promiscuous and spoofing as in a session.
func newPingTestStack(t *testing.T) (*stack.Stack, *channel.Endpoint) {
	t.Helper()

	netStack := stack.New(stack.Options{
		NetworkProtocols: []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{
			tcp.NewProtocol, udp.NewProtocol, icmp.NewProtocol4, icmp.NewProtocol6,
		},
	})
	t.Cleanup(netStack.Close)

	link := channel.New(testQueueDepth, testMTU, "")
	if err := netStack.CreateNIC(nicID, link); err != nil {
		t.Fatalf("CreateNIC: %v", err)
	}
	netStack.SetPromiscuousMode(nicID, true)
	netStack.SetSpoofing(nicID, true)
	netStack.SetRouteTable([]tcpip.Route{
		{Destination: header.IPv4EmptySubnet, NIC: nicID},
		{Destination: header.IPv6EmptySubnet, NIC: nicID},
	})
	if err := netStack.AddProtocolAddress(nicID, tunAddress4(), stack.AddressProperties{}); err != nil {
		t.Fatalf("AddProtocolAddress: %v", err)
	}
	return netStack, link
}

// echoRequest renders an IPv4 echo request as a tethered client's ping
// arrives on the TUN (source already NATed to the TUN address).
func echoRequest(ident, seq uint16, payload []byte) []byte {
	packet := buildEchoReply(pingClient, pingTarget, ident, seq, payload)
	message := header.ICMPv4(packet[header.IPv4MinimumSize:])
	message.SetType(header.ICMPv4Echo)
	message.SetChecksum(0)
	message.SetChecksum(^checksum.Checksum(message, 0))
	return packet
}

func inject(link *channel.Endpoint, packet []byte) {
	pkt := stack.NewPacketBuffer(stack.PacketBufferOptions{
		Payload: buffer.MakeWithData(packet),
	})
	defer pkt.DecRef()
	link.InjectInbound(header.IPv4ProtocolNumber, pkt)
}

type recordedEcho struct {
	dst     tcpip.Address
	seq     uint16
	payload []byte
}

// fakeUpstream stands in for the ping socket and the TUN write.
type fakeUpstream struct {
	mu      sync.Mutex
	echoes  []recordedEcho
	err     error
	written chan []byte
}

func newFakeUpstream() *fakeUpstream {
	return &fakeUpstream{written: make(chan []byte, 4)}
}

func (u *fakeUpstream) echo(dst tcpip.Address, seq uint16, payload []byte, _ time.Duration) ([]byte, error) {
	u.mu.Lock()
	defer u.mu.Unlock()
	u.echoes = append(u.echoes, recordedEcho{dst, seq, append([]byte(nil), payload...)})
	if u.err != nil {
		return nil, u.err
	}
	return payload, nil
}

func (u *fakeUpstream) write(packet []byte) error {
	u.written <- packet
	return nil
}

func (u *fakeUpstream) echoCount() int {
	u.mu.Lock()
	defer u.mu.Unlock()
	return len(u.echoes)
}

// TestStackLeavesIPv4PingsUnanswered pins down the bug the forwarder fixes:
// with a promiscuous NIC, gVisor never replies to an IPv4 echo addressed to a
// forwarded destination, so a tethered client's ping times out.
func TestStackLeavesIPv4PingsUnanswered(t *testing.T) {
	_, link := newPingTestStack(t)

	inject(link, echoRequest(0x1234, 1, []byte("probe")))

	if pkt := link.Read(); pkt != nil {
		pkt.DecRef()
		t.Fatal("stack answered an IPv4 ping on its own; the forwarder may be redundant")
	}
}

func TestPingForwarderRepliesThroughUpstream(t *testing.T) {
	netStack, link := newPingTestStack(t)
	upstream := newFakeUpstream()
	newPingForwarder(upstream.echo, upstream.write).install(netStack)

	payload := []byte("unifi-internet-monitor")
	inject(link, echoRequest(0x1234, 7, payload))

	var packet []byte
	select {
	case packet = <-upstream.written:
	case <-time.After(2 * time.Second):
		t.Fatal("no echo reply written to the TUN")
	}

	if got := upstream.echoes[0]; got.dst != pingTarget || got.seq != 7 || !bytes.Equal(got.payload, payload) {
		t.Fatalf("upstream echo = %+v, want dst %v seq 7 payload %q", got, pingTarget, payload)
	}

	ip := header.IPv4(packet)
	if !ip.IsValid(len(packet)) || !ip.IsChecksumValid() {
		t.Fatalf("reply IPv4 header invalid: % x", packet[:header.IPv4MinimumSize])
	}
	if ip.SourceAddress() != pingTarget || ip.DestinationAddress() != pingClient {
		t.Fatalf("reply %v -> %v, want %v -> %v", ip.SourceAddress(), ip.DestinationAddress(), pingTarget, pingClient)
	}
	if ip.TransportProtocol() != header.ICMPv4ProtocolNumber {
		t.Fatalf("reply protocol %d, want ICMP", ip.TransportProtocol())
	}

	reply := header.ICMPv4(ip.Payload())
	if reply.Type() != header.ICMPv4EchoReply || reply.Code() != 0 {
		t.Fatalf("reply type/code %d/%d, want echo reply", reply.Type(), reply.Code())
	}
	if reply.Ident() != 0x1234 || reply.Sequence() != 7 {
		t.Fatalf("reply ident/seq %#x/%d, want the client's %#x/7", reply.Ident(), reply.Sequence(), 0x1234)
	}
	if !bytes.Equal(reply.Payload(), payload) {
		t.Fatalf("reply payload %q, want %q", reply.Payload(), payload)
	}
	if checksum.Checksum(reply, 0) != 0xffff {
		t.Fatal("reply ICMP checksum invalid")
	}

	// The stack must not also answer: one reply per request.
	if pkt := link.Read(); pkt != nil {
		pkt.DecRef()
		t.Fatal("stack emitted its own packet alongside the forwarded reply")
	}
}

func TestPingForwarderStaysSilentWhenUpstreamFails(t *testing.T) {
	netStack, link := newPingTestStack(t)
	upstream := newFakeUpstream()
	upstream.err = errors.New("echo: timed out")
	newPingForwarder(upstream.echo, upstream.write).install(netStack)

	inject(link, echoRequest(0x1, 1, []byte("x")))

	select {
	case <-upstream.written:
		t.Fatal("wrote a reply although the upstream ping failed")
	case <-time.After(300 * time.Millisecond):
	}
	if upstream.echoCount() != 1 {
		t.Fatalf("upstream echoes = %d, want 1", upstream.echoCount())
	}
}

func TestPingForwarderIgnoresOtherICMP(t *testing.T) {
	netStack, link := newPingTestStack(t)
	upstream := newFakeUpstream()
	newPingForwarder(upstream.echo, upstream.write).install(netStack)

	// An echo reply arriving from the client is not a request to forward.
	inject(link, buildEchoReply(pingClient, pingTarget, 0x1, 1, []byte("x")))

	select {
	case <-upstream.written:
		t.Fatal("forwarded a non-request ICMP message")
	case <-time.After(300 * time.Millisecond):
	}
	if upstream.echoCount() != 0 {
		t.Fatalf("upstream echoes = %d, want 0", upstream.echoCount())
	}
}

// TestPingForwarderWritesNothingAfterClose: an echo still in flight when the
// session stops must not write to the TUN fd, which is closed (and its number
// possibly reused) as soon as Stop returns.
func TestPingForwarderWritesNothingAfterClose(t *testing.T) {
	netStack, link := newPingTestStack(t)
	release := make(chan struct{})
	written := make(chan []byte, 1)
	forwarder := newPingForwarder(
		func(_ tcpip.Address, _ uint16, payload []byte, _ time.Duration) ([]byte, error) {
			<-release
			return payload, nil
		},
		func(packet []byte) error {
			written <- packet
			return nil
		},
	)
	forwarder.install(netStack)

	inject(link, echoRequest(0x7, 1, []byte("in flight at stop")))
	time.Sleep(50 * time.Millisecond)
	forwarder.close()
	close(release)

	select {
	case <-written:
		t.Fatal("echo reply written after close")
	case <-time.After(300 * time.Millisecond):
	}
}

// TestPingForwarderDropsCorruptRequests: a request with a bad ICMP checksum is
// neither forwarded nor answered.
func TestPingForwarderDropsCorruptRequests(t *testing.T) {
	netStack, link := newPingTestStack(t)
	upstream := newFakeUpstream()
	newPingForwarder(upstream.echo, upstream.write).install(netStack)

	packet := echoRequest(0x1234, 3, []byte("corrupt"))
	message := header.ICMPv4(packet[header.IPv4MinimumSize:])
	message.SetChecksum(message.Checksum() ^ 0x00ff)
	inject(link, packet)

	select {
	case <-upstream.written:
		t.Fatal("answered a request with a bad checksum")
	case <-time.After(300 * time.Millisecond):
	}
	if upstream.echoCount() != 0 {
		t.Fatalf("forwarded %d corrupt requests upstream", upstream.echoCount())
	}
}
