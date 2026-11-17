package datapath

import (
	"sync"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/checksum"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/icmp"
)

const (
	// maxInFlightPings bounds concurrent echo requests. Tethered clients ping
	// a handful of hosts every few seconds (a router's internet monitor);
	// this caps sockets and goroutines under a ping flood, not normal use.
	maxInFlightPings = 64

	// echoTimeout is how long one echo waits for its reply. A lost reply is
	// simply never answered, exactly as on a real path.
	echoTimeout = 3 * time.Second

	// echoReplyTTL is the TTL on synthesized replies. The real reply's TTL is
	// not visible through a ping socket, so this is a typical host default.
	echoReplyTTL = 64
)

// echoFunc sends one ICMPv4 echo request to dst carrying seq and payload over
// the upstream network, and returns the reply's payload.
type echoFunc func(dst tcpip.Address, seq uint16, payload []byte, timeout time.Duration) ([]byte, error)

// pingForwarder answers tethered clients' IPv4 pings with real ones.
//
// The netstack accepts every destination (promiscuous NIC), and for those
// temporary addresses gVisor's IPv4 layer deliberately does not synthesize an
// echo reply. Without this, every IPv4 ping from a tethered client goes
// unanswered, and routers that watch the WAN by pinging (UniFi's internet
// monitor among them) mark the uplink as having no internet while TCP and UDP
// work fine.
type pingForwarder struct {
	echo     echoFunc
	write    func(packet []byte) error
	inFlight chan struct{}

	// mu fences writes against close. Writes hold it shared; close takes it
	// exclusively, so once close returns no write is running or can start.
	// That matters because write targets a raw TUN fd number, which the
	// session closes right after stopping and the kernel may hand to another
	// file -- including the next session's TUN -- while an echo is in flight.
	mu     sync.RWMutex
	closed bool
}

func newPingForwarder(echo echoFunc, write func(packet []byte) error) *pingForwarder {
	return &pingForwarder{
		echo:     echo,
		write:    write,
		inFlight: make(chan struct{}, maxInFlightPings),
	}
}

// install makes the forwarder the per-stack default handler for ICMPv4, which
// gVisor consults before its own echo handling; returning true there stops the
// stack from doing anything further with the request.
func (f *pingForwarder) install(netStack *stack.Stack) {
	netStack.SetTransportProtocolHandler(icmp.ProtocolNumber4, f.handle)
}

// handle takes ownership of IPv4 echo requests and leaves every other ICMP
// message to the stack.
func (f *pingForwarder) handle(id stack.TransportEndpointID, pkt *stack.PacketBuffer) bool {
	message := header.ICMPv4(pkt.TransportHeader().Slice())
	if len(message) < header.ICMPv4MinimumSize || message.Type() != header.ICMPv4Echo || message.Code() != 0 {
		return false
	}

	// Copy out everything the reply needs: pkt belongs to the stack and is
	// released once this handler returns.
	view := stack.PayloadSince(pkt.TransportHeader())
	request := header.ICMPv4(append([]byte(nil), view.AsSlice()...))
	view.Release()

	// gVisor hands echo requests to this handler even when their checksum is
	// wrong. A corrupt request is dropped, as a real host would, rather than
	// answered with a valid reply.
	if checksum.Checksum(request, 0) != 0xffff {
		return true
	}
	if f.isClosed() {
		return true
	}

	client := id.RemoteAddress
	destination := id.LocalAddress
	ident, seq := request.Ident(), request.Sequence()
	payload := request.Payload()

	select {
	case f.inFlight <- struct{}{}:
	default:
		// Over the cap: drop, as a congested path would.
		return true
	}

	go func() {
		defer func() { <-f.inFlight }()

		replyPayload, err := f.echo(destination, seq, payload, echoTimeout)
		if err != nil {
			return
		}
		f.deliver(buildEchoReply(destination, client, ident, seq, replyPayload))
	}()
	return true
}

// deliver writes a reply unless the forwarder has been closed.
func (f *pingForwarder) deliver(packet []byte) {
	f.mu.RLock()
	defer f.mu.RUnlock()
	if f.closed {
		return
	}
	_ = f.write(packet)
}

// close stops all further writes. Echoes still in flight finish on their own
// within echoTimeout and are discarded.
func (f *pingForwarder) close() {
	f.mu.Lock()
	f.closed = true
	f.mu.Unlock()
}

func (f *pingForwarder) isClosed() bool {
	f.mu.RLock()
	defer f.mu.RUnlock()
	return f.closed
}

// buildEchoReply renders a complete IPv4 packet carrying an echo reply from
// src to dst, ready to write to the TUN.
func buildEchoReply(src, dst tcpip.Address, ident, seq uint16, payload []byte) []byte {
	total := header.IPv4MinimumSize + header.ICMPv4MinimumSize + len(payload)
	packet := make([]byte, total)

	ip := header.IPv4(packet)
	ip.Encode(&header.IPv4Fields{
		TotalLength: uint16(total),
		TTL:         echoReplyTTL,
		Protocol:    uint8(header.ICMPv4ProtocolNumber),
		SrcAddr:     src,
		DstAddr:     dst,
	})
	ip.SetChecksum(^ip.CalculateChecksum())

	reply := header.ICMPv4(packet[header.IPv4MinimumSize:])
	reply.SetType(header.ICMPv4EchoReply)
	reply.SetCode(0)
	reply.SetIdent(ident)
	reply.SetSequence(seq)
	copy(reply.Payload(), payload)
	reply.SetChecksum(0)
	reply.SetChecksum(^checksum.Checksum(reply, 0))

	return packet
}
