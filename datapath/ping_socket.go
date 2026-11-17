//go:build linux

package datapath

import (
	"encoding/binary"
	"errors"
	"fmt"
	"time"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/header"
)

// echo sends one ICMPv4 echo through an unprivileged ping socket
// (SOCK_DGRAM/IPPROTO_ICMP), which Android opens to every UID, and waits for
// the matching reply.
//
// The socket is pinned like every TCP and UDP dial: to the bound network when
// there is one, failing rather than leaving over another network if pinning
// fails. The kernel chooses the echo identifier and fills in the checksum; the
// caller restores the client's identifier on the reply.
func (b *networkBinding) echo(dst tcpip.Address, seq uint16, payload []byte, timeout time.Duration) ([]byte, error) {
	if dst.Len() != 4 {
		return nil, errors.New("echo: not an IPv4 address")
	}

	fd, err := unix.Socket(unix.AF_INET, unix.SOCK_DGRAM|unix.SOCK_CLOEXEC, unix.IPPROTO_ICMP)
	if err != nil {
		return nil, fmt.Errorf("echo: ping socket: %w", err)
	}
	defer unix.Close(fd)

	if handle := b.handle.Load(); handle != unbound {
		if err := bindToNetwork(handle, uintptr(fd)); err != nil {
			return nil, err
		}
	}

	deadline := time.Now().Add(timeout)
	timeval := unix.NsecToTimeval(timeout.Nanoseconds())
	if err := unix.SetsockoptTimeval(fd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeval); err != nil {
		return nil, fmt.Errorf("echo: set timeout: %w", err)
	}

	request := make([]byte, header.ICMPv4MinimumSize+len(payload))
	request[0] = byte(header.ICMPv4Echo)
	binary.BigEndian.PutUint16(request[6:], seq)
	copy(request[header.ICMPv4MinimumSize:], payload)

	to := &unix.SockaddrInet4{Addr: dst.As4()}
	if err := unix.Sendto(fd, request, 0, to); err != nil {
		return nil, fmt.Errorf("echo: send: %w", err)
	}

	buffer := make([]byte, 65535)
	for time.Now().Before(deadline) {
		read, from, err := unix.Recvfrom(fd, buffer, 0)
		if err != nil {
			return nil, fmt.Errorf("echo: receive: %w", err)
		}
		source, ok := from.(*unix.SockaddrInet4)
		reply := header.ICMPv4(buffer[:read])
		if !ok || source.Addr != to.Addr || read < header.ICMPv4MinimumSize ||
			reply.Type() != header.ICMPv4EchoReply || reply.Sequence() != seq {
			continue
		}
		return append([]byte(nil), reply.Payload()...), nil
	}
	return nil, errors.New("echo: timed out")
}
