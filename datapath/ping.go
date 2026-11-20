package datapath

import (
	"errors"
	"fmt"
	"sync"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/checksum"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/icmp"
)

const (
	// maxInFlightPings caps sockets and goroutines under a ping flood.
	maxInFlightPings = 64

	echoTimeout = 3 * time.Second

	// echoReplyTTL is a typical host default: a ping socket hides the real
	// reply's TTL.
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

// pingForwarder answers tethered clients' IPv4 pings with real ones. gVisor
// does not reply to echoes for the temporary addresses a promiscuous NIC
// accepts; see LocalAddressTemporary in
// https://github.com/google/gvisor/blob/master/pkg/tcpip/network/ipv4/icmp.go
type pingForwarder struct {
	echo     echoFunc
	write    func(packet []byte) error
	inFlight chan struct{}

	// mu fences writes against close: write targets a raw TUN fd number that
	// may be reused once the session stops.
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

// install makes the forwarder the stack's default ICMPv4 handler, which runs
// before gVisor's own echo handling.
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

// readEchoRequest copies the request out of pkt, which the stack releases
// after the handler. It reports false for a bad checksum, which gVisor does not
// check here.
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
		// A failed echo goes unanswered, as a lost reply would on a real path.
		_ = f.relay(request)
	}()
}

// relay echoes request upstream and writes the reply back to the client.
func (f *pingForwarder) relay(request echoRequest) error {
	replyPayload, err := f.echo(request, echoTimeout)
	if err != nil {
		return err
	}
	return f.deliver(request.reply(replyPayload))
}

var errPingForwarderClosed = errors.New("ping forwarder closed")

// deliver writes a reply unless the forwarder has been closed.
func (f *pingForwarder) deliver(packet []byte) error {
	f.mu.RLock()
	defer f.mu.RUnlock()
	if f.isClosed {
		return errPingForwarderClosed
	}
	if err := f.write(packet); err != nil {
		return fmt.Errorf("write echo reply to %v: %w", header.IPv4(packet).DestinationAddress(), err)
	}
	return nil
}

// close stops all further writes. Echoes in flight end within echoTimeout.
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
