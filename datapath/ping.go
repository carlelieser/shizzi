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

// echoRequest is one IPv4 echo request from a tethered client.
type echoRequest struct {
	client      tcpip.Address
	destination tcpip.Address
	ident       uint16
	seq         uint16
	payload     []byte
}

// echoMessage is one ICMPv4 echo or echo reply inside an IPv4 packet.
type echoMessage struct {
	kind        header.ICMPv4Type
	source      tcpip.Address
	destination tcpip.Address
	ident       uint16
	seq         uint16
	payload     []byte
}

// echoFunc sends request's echo upstream and returns the reply's payload.
type echoFunc func(request echoRequest, timeout time.Duration) ([]byte, error)

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
	mu       sync.RWMutex
	isClosed bool
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
	if !isEchoRequest(header.ICMPv4(pkt.TransportHeader().Slice())) {
		return false
	}
	request, isIntact := readEchoRequest(id, pkt)
	if !isIntact || f.hasClosed() {
		return true
	}
	f.dispatch(request)
	return true
}

func isEchoRequest(message header.ICMPv4) bool {
	return len(message) >= header.ICMPv4MinimumSize && message.Type() == header.ICMPv4Echo && message.Code() == 0
}

// readEchoRequest copies the request out of pkt, which the stack releases once
// the handler returns. It reports false for a corrupt request: gVisor delivers
// echoes with a bad checksum, and a real host drops them.
func readEchoRequest(id stack.TransportEndpointID, pkt *stack.PacketBuffer) (echoRequest, bool) {
	view := stack.PayloadSince(pkt.TransportHeader())
	message := header.ICMPv4(append([]byte(nil), view.AsSlice()...))
	view.Release()

	if checksum.Checksum(message, 0) != 0xffff {
		return echoRequest{}, false
	}
	return echoRequest{
		client:      id.RemoteAddress,
		destination: id.LocalAddress,
		ident:       message.Ident(),
		seq:         message.Sequence(),
		payload:     message.Payload(),
	}, true
}

// dispatch echoes request upstream in the background, or drops it over the
// in-flight cap, as a congested path would.
func (f *pingForwarder) dispatch(request echoRequest) {
	select {
	case f.inFlight <- struct{}{}:
	default:
		return
	}

	go func() {
		defer func() { <-f.inFlight }()

		replyPayload, err := f.echo(request, echoTimeout)
		if err != nil {
			return
		}
		f.deliver(request.reply(replyPayload))
	}()
}

// deliver writes a reply unless the forwarder has been closed.
func (f *pingForwarder) deliver(packet []byte) {
	f.mu.RLock()
	defer f.mu.RUnlock()
	if f.isClosed {
		return
	}
	_ = f.write(packet)
}

// close stops all further writes. Echoes still in flight finish on their own
// within echoTimeout and are discarded.
func (f *pingForwarder) close() {
	f.mu.Lock()
	f.isClosed = true
	f.mu.Unlock()
}

func (f *pingForwarder) hasClosed() bool {
	f.mu.RLock()
	defer f.mu.RUnlock()
	return f.isClosed
}

// reply renders the complete IPv4 echo reply to request, ready for the TUN.
func (r echoRequest) reply(payload []byte) []byte {
	return encodeEcho(echoMessage{
		kind:        header.ICMPv4EchoReply,
		source:      r.destination,
		destination: r.client,
		ident:       r.ident,
		seq:         r.seq,
		payload:     payload,
	})
}

func encodeEcho(message echoMessage) []byte {
	total := header.IPv4MinimumSize + header.ICMPv4MinimumSize + len(message.payload)
	packet := make([]byte, total)

	ip := header.IPv4(packet)
	ip.Encode(&header.IPv4Fields{
		TotalLength: uint16(total),
		TTL:         echoReplyTTL,
		Protocol:    uint8(header.ICMPv4ProtocolNumber),
		SrcAddr:     message.source,
		DstAddr:     message.destination,
	})
	ip.SetChecksum(^ip.CalculateChecksum())

	icmpMessage := header.ICMPv4(packet[header.IPv4MinimumSize:])
	icmpMessage.SetType(message.kind)
	icmpMessage.SetCode(0)
	icmpMessage.SetIdent(message.ident)
	icmpMessage.SetSequence(message.seq)
	copy(icmpMessage.Payload(), message.payload)
	icmpMessage.SetChecksum(0)
	icmpMessage.SetChecksum(^checksum.Checksum(icmpMessage, 0))

	return packet
}
