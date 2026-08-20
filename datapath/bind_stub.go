//go:build !android

package datapath

import "errors"

// bindToNetwork stands in for bind.go off Android, where libandroid's
// android_setsocknetwork has no equivalent, so the package and its tests build
// on a CI runner.
//
// Errors rather than returning nil. Nothing reaches this in a real session, but
// if that changes, a dial believing it is pinned to a VPN fails here instead of
// silently leaving over the physical network -- the fail-closed answer the real
// implementation gives when a bind fails.
func bindToNetwork(handle uint64, fd uintptr) error {
	return errors.New("bindToNetwork: not supported on this platform")
}
