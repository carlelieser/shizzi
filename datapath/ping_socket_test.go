//go:build linux

package datapath

import (
	"bytes"
	"errors"
	"strings"
	"syscall"
	"testing"
	"time"

	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/tcpip"
)

// TestEchoOverPingSocket exercises the real unprivileged ping socket against
// loopback. Skips where the kernel's ping_group_range excludes this process
// (Android opens it to every UID; most CI containers do too).
func TestEchoOverPingSocket(t *testing.T) {
	binding := &networkBinding{}
	payload := []byte("shizzi-echo")

	reply, err := binding.echo(echoRequest{
		destination: tcpip.AddrFrom4([4]byte{127, 0, 0, 1}),
		seq:         42,
		payload:     payload,
	}, 2*time.Second)
	if errors.Is(err, syscall.EACCES) || errors.Is(err, syscall.EPERM) {
		t.Skipf("ping sockets not permitted here: %v", err)
	}
	if err != nil {
		t.Fatalf("echo: %v", err)
	}
	if !bytes.Equal(reply, payload) {
		t.Fatalf("reply payload %q, want %q", reply, payload)
	}
}

// TestEchoWaitEndsAtDeadline: a reply that does not match must not restart the
// wait, or a late stray reply stretches an echo past its deadline.
func TestEchoWaitEndsAtDeadline(t *testing.T) {
	binding := &networkBinding{}
	loopback := tcpip.AddrFrom4([4]byte{127, 0, 0, 1})
	socket, err := binding.openPingSocket(echoRequest{destination: loopback})
	if errors.Is(err, syscall.EACCES) || errors.Is(err, syscall.EPERM) {
		t.Skipf("ping sockets not permitted here: %v", err)
	}
	if err != nil {
		t.Fatalf("openPingSocket: %v", err)
	}
	defer unix.Close(socket.fd)

	const wait = 300 * time.Millisecond
	time.AfterFunc(wait-50*time.Millisecond, func() {
		_ = socket.send(echoRequest{destination: loopback, seq: 1})
	})

	result := make(chan error, 1)
	start := time.Now()
	go func() {
		_, err := socket.awaitReply(99, start.Add(wait))
		result <- err
	}()

	select {
	case err := <-result:
		if !errors.Is(err, errEchoTimedOut) {
			t.Fatalf("awaitReply error = %v, want %v", err, errEchoTimedOut)
		}
		if elapsed := time.Since(start); elapsed > wait+150*time.Millisecond {
			t.Fatalf("awaitReply returned after %v, want about %v", elapsed, wait)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("awaitReply kept waiting past its deadline")
	}
}

func TestEchoErrorNamesDestination(t *testing.T) {
	binding := &networkBinding{}
	destination := tcpip.AddrFrom16([16]byte{15: 1})

	_, err := binding.echo(echoRequest{destination: destination}, time.Second)
	if err == nil || !strings.Contains(err.Error(), destination.String()) {
		t.Fatalf("echo error = %v, want it to name %v", err, destination)
	}
}
