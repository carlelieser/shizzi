//go:build linux

package datapath

import (
	"encoding/binary"
	"errors"
	"fmt"
	"time"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/tcpip/header"
)

// pingSocket is an unprivileged ping socket (SOCK_DGRAM/IPPROTO_ICMP), which
// Android opens to every UID, aimed at one IPv4 destination. The kernel
// chooses the echo identifier and fills in the checksum.
type pingSocket struct {
	fd     int
	target unix.SockaddrInet4
}

// echo sends request's echo through a ping socket pinned like every TCP and
// UDP dial, and waits for the matching reply.
func (b *networkBinding) echo(request echoRequest, timeout time.Duration) ([]byte, error) {
	reply, err := b.exchangeEcho(request, time.Now().Add(timeout))
	if err != nil {
		return nil, fmt.Errorf("echo %v: %w", request.destination, err)
	}
	return reply, nil
}

func (b *networkBinding) exchangeEcho(request echoRequest, deadline time.Time) ([]byte, error) {
	if request.destination.Len() != 4 {
		return nil, errors.New("not an IPv4 address")
	}
	socket, err := b.openPingSocket(request)
	if err != nil {
		return nil, err
	}
	defer unix.Close(socket.fd)

	if err := socket.send(request); err != nil {
		return nil, fmt.Errorf("send: %w", err)
	}
	return socket.awaitReply(request.seq, deadline)
}

// openPingSocket opens a ping socket pinned to the bound network when there is
// one, failing rather than leaving over another network if pinning fails.
func (b *networkBinding) openPingSocket(request echoRequest) (pingSocket, error) {
	fd, err := unix.Socket(unix.AF_INET, unix.SOCK_DGRAM|unix.SOCK_CLOEXEC, unix.IPPROTO_ICMP)
	if err != nil {
		return pingSocket{}, fmt.Errorf("ping socket: %w", err)
	}
	if handle := b.handle.Load(); handle != unbound {
		if err := bindToNetwork(handle, uintptr(fd)); err != nil {
			unix.Close(fd)
			return pingSocket{}, err
		}
	}
	return pingSocket{fd: fd, target: unix.SockaddrInet4{Addr: request.destination.As4()}}, nil
}

func (s pingSocket) send(request echoRequest) error {
	message := make([]byte, header.ICMPv4MinimumSize+len(request.payload))
	message[0] = byte(header.ICMPv4Echo)
	binary.BigEndian.PutUint16(message[6:], request.seq)
	copy(message[header.ICMPv4MinimumSize:], request.payload)
	return unix.Sendto(s.fd, message, 0, &s.target)
}

// errEchoTimedOut reports an echo whose reply did not arrive in time.
var errEchoTimedOut = errors.New("timed out")

// awaitReply reads until the reply to seq arrives from the target. The receive
// timeout is renewed to what remains, so replies that do not match cannot push
// the wait past deadline.
func (s pingSocket) awaitReply(seq uint16, deadline time.Time) ([]byte, error) {
	buffer := make([]byte, maxDatagramSize)
	for {
		remaining := time.Until(deadline)
		if remaining <= 0 {
			return nil, errEchoTimedOut
		}
		timeval := unix.NsecToTimeval(remaining.Nanoseconds())
		if err := unix.SetsockoptTimeval(s.fd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeval); err != nil {
			return nil, fmt.Errorf("set timeout: %w", err)
		}
		read, from, err := unix.Recvfrom(s.fd, buffer, 0)
		if errors.Is(err, unix.EAGAIN) {
			return nil, errEchoTimedOut
		}
		if err != nil {
			return nil, fmt.Errorf("receive: %w", err)
		}
		reply := header.ICMPv4(buffer[:read])
		if s.isReplyTo(from, reply, seq) {
			return append([]byte(nil), reply.Payload()...), nil
		}
	}
}

func (s pingSocket) isReplyTo(from unix.Sockaddr, reply header.ICMPv4, seq uint16) bool {
	source, isInet4 := from.(*unix.SockaddrInet4)
	if !isInet4 || source.Addr != s.target.Addr {
		return false
	}
	isComplete := len(reply) >= header.ICMPv4MinimumSize
	return isComplete && reply.Type() == header.ICMPv4EchoReply && reply.Sequence() == seq
}
