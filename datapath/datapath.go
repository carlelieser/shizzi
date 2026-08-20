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
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/fdbased"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/icmp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
)

// nicID identifies the single TUN-backed interface in the stack.
const nicID tcpip.NICID = 1

// Session owns a running netstack bound to one TUN file descriptor. Exposes no
// gVisor types: gomobile carries only a narrow set across JNI, so the Kotlin
// side sees a handle it can start and stop.
type Session struct {
	stack   *stack.Stack
	binding *networkBinding
}

// Start builds a netstack over tunFD, already open, and attaches it to the TUN.
// Ownership does not transfer -- SessionResources holds the TUN, the framework
// interface, and the network handle as one atomic group. mtu is the link MTU.
func Start(tunFD int, mtu int) (*Session, error) {
	// ICMP is registered for both families so the stack can answer and relay
	// errors. IPv6 in particular cannot fragment in flight, so a client only
	// learns a path MTU from the ICMPv6 Packet Too Big it gets back.
	netStack := stack.New(stack.Options{
		NetworkProtocols: []stack.NetworkProtocolFactory{
			ipv4.NewProtocol,
			ipv6.NewProtocol,
		},
		TransportProtocols: []stack.TransportProtocolFactory{
			tcp.NewProtocol,
			udp.NewProtocol,
			icmp.NewProtocol4,
			icmp.NewProtocol6,
		},
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

	netStack.SetRouteTable([]tcpip.Route{
		{Destination: header.IPv4EmptySubnet, NIC: nicID},
		{Destination: header.IPv6EmptySubnet, NIC: nicID},
	})

	binding := &networkBinding{}
	installForwarders(netStack, &net.Dialer{
		Timeout: dialTimeout,
		Control: binding.control,
	})

	return &Session{stack: netStack, binding: binding}, nil
}

// SetNetwork pins every subsequent dial to a handle from
// Network.getNetworkHandle; 0 unbinds, which is how a session with no VPN runs.
//
// Binding is what makes VPN loss fail rather than fall back: an unbound socket
// follows the default network, so a dropped VPN leaves over the physical one
// and tethered clients keep browsing untunneled with nothing noticing.
//
// int64 because gomobile has no unsigned type. A handle packs a netid above a
// magic word, so it is routinely negative signed; the conversion reinterprets
// the bits without changing them.
func (s *Session) SetNetwork(handle int64) {
	if s.binding == nil {
		return
	}
	s.binding.set(uint64(handle))
}

// Stop tears the netstack down. It does not close the TUN fd.
func (s *Session) Stop() {
	if s.stack == nil {
		return
	}
	s.stack.Close()
	s.stack = nil
}
