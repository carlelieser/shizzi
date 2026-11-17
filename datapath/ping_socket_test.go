//go:build linux

package datapath

import (
	"bytes"
	"errors"
	"syscall"
	"testing"
	"time"

	"gvisor.dev/gvisor/pkg/tcpip"
)

// TestEchoOverPingSocket exercises the real unprivileged ping socket against
// loopback. Skips where the kernel's ping_group_range excludes this process
// (Android opens it to every UID; most CI containers do too).
func TestEchoOverPingSocket(t *testing.T) {
	binding := &networkBinding{}
	payload := []byte("shizzi-echo")

	reply, err := binding.echo(tcpip.AddrFrom4([4]byte{127, 0, 0, 1}), 42, payload, 2*time.Second)
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
