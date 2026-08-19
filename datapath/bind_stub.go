//go:build !android

package datapath

import "errors"

// bindToNetwork is Android-only: android_setsocknetwork comes from libandroid
// and has no equivalent anywhere else, so the real implementation in bind.go
// cannot compile off Android. This stub takes its place there, which is what
// lets the package -- and so its tests -- build on a CI runner.
//
// Returning an error rather than nil is deliberate. Nothing reaches this in a
// real session: control returns early while the binding is unbound, and a
// handle only ever comes from the Android side. If that stops being true, a
// dial that believes it is pinned to a VPN fails here instead of silently
// leaving over the physical network -- the same fail-closed answer the real
// implementation gives when the bind itself fails.
func bindToNetwork(handle uint64, fd uintptr) error {
	return errors.New("bindToNetwork: not supported on this platform")
}
