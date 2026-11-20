package datapath

import (
	"testing"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

// TestUDPMappingDeclinesMalformedAddresses: a flow whose address does not
// parse is refused rather than mapped to the zero address.
func TestUDPMappingDeclinesMalformedAddresses(t *testing.T) {
	malformed := stack.TransportEndpointID{
		LocalAddress:  tcpip.AddrFrom4([4]byte{198, 51, 100, 1}),
		LocalPort:     53,
		RemoteAddress: tcpip.Address{},
		RemotePort:    1234,
	}
	if _, _, isAddressable := flowAddresses(malformed); isAddressable {
		t.Fatal("flow with an empty client address was accepted")
	}
}
