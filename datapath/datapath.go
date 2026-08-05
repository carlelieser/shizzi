// Package datapath terminates tethered client traffic in userspace.
//
// The tethering stack routes hotspot clients into a TUN owned by the shell
// process. Nothing in the kernel forwards those packets onward, so this package
// reads raw IP frames off the TUN fd, terminates TCP and UDP in a gVisor
// netstack instance, and proxies each flow onto an ordinary socket.
package datapath

import (
	"fmt"
	"net"

	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/link/fdbased"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

// nicID identifies the single TUN-backed interface in the stack.
const nicID tcpip.NICID = 1

// Session owns a running netstack bound to one TUN file descriptor.
//
// gomobile can only expose a narrow set of types across the JNI boundary, so
// this deliberately exposes no gVisor types: the Kotlin side sees a handle it
// can start and stop, nothing more.
type Session struct {
	stack *stack.Stack
}

// Start builds a netstack over tunFD and attaches it to the TUN.
//
// The fd must already be open and owned by the caller; ownership does not
// transfer, because SessionResources on the Kotlin side holds the TUN, the
// framework interface, and the network handle as one atomic group.
//
// mtu is the link MTU of the TUN interface.
func Start(tunFD int, mtu int) (*Session, error) {
	netStack := stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol},
	})

	endpoint, err := fdbased.New(&fdbased.Options{
		FDs: []int{tunFD},
		MTU: uint32(mtu),
	})
	if err != nil {
		netStack.Close()
		return nil, fmt.Errorf("datapath.Start: link endpoint over fd %d: %w", tunFD, err)
	}

	if tcpipErr := netStack.CreateNIC(nicID, endpoint); tcpipErr != nil {
		netStack.Close()
		return nil, fmt.Errorf("datapath.Start: create NIC on fd %d: %v", tunFD, tcpipErr)
	}

	// Accept traffic for every destination: these are forwarded flows, not
	// packets addressed to this host, so the stack must not filter by address.
	netStack.SetPromiscuousMode(nicID, true)
	netStack.SetSpoofing(nicID, true)

	netStack.SetRouteTable([]tcpip.Route{{Destination: header4Subnet(), NIC: nicID}})

	installForwarders(netStack, &net.Dialer{Timeout: dialTimeout})

	return &Session{stack: netStack}, nil
}

// Stop tears the netstack down. It does not close the TUN fd.
func (s *Session) Stop() {
	if s.stack == nil {
		return
	}
	s.stack.Close()
	s.stack = nil
}

// header4Subnet returns the 0.0.0.0/0 subnet used as the default route.
func header4Subnet() tcpip.Subnet {
	subnet, err := tcpip.NewSubnet(
		tcpip.AddrFrom4([4]byte{0, 0, 0, 0}),
		tcpip.MaskFromBytes([]byte{0, 0, 0, 0}),
	)
	if err != nil {
		panic(fmt.Sprintf("datapath: default IPv4 subnet is invalid: %v", err))
	}
	return subnet
}
