package datapath

import (
	"sync/atomic"
	"syscall"
)

// unbound is NETWORK_UNSPECIFIED: dial over whatever the process default is.
//
// Not a failure case. A session with no VPN runs here permanently and tethers
// exactly as it did before any of this existed.
const unbound = 0

// networkBinding is the network every new dial is pinned to, swapped live.
//
// Atomic because the writer and the readers are different threads: the VPN
// watchdog calls SetNetwork from its own thread while forwarder goroutines are
// dialling.
type networkBinding struct {
	handle atomic.Uint64
}

// control pins each new socket before it connects.
//
// Dialer.Control runs after the socket exists and before connect(2), which is
// the only window where this takes effect — binding afterwards does nothing to
// a socket that has already chosen its route.
//
// An error here fails the dial, which is the point: a socket that could not be
// pinned to the VPN must not quietly leave over the physical network instead.
func (b *networkBinding) control(network, address string, conn syscall.RawConn) error {
	handle := b.handle.Load()
	if handle == unbound {
		return nil
	}

	var bindErr error
	if err := conn.Control(func(fd uintptr) {
		bindErr = bindToNetwork(handle, fd)
	}); err != nil {
		return err
	}
	return bindErr
}

func (b *networkBinding) set(handle uint64) {
	b.handle.Store(handle)
}
