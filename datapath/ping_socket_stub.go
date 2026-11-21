//go:build !linux

package datapath

import (
	"errors"
	"time"
)

// echo stands in for ping_socket.go off Linux so the package and its tests
// build on a CI runner. Sessions only run on Android.
func (b *networkBinding) echo(request echoRequest, timeout time.Duration) ([]byte, error) {
	return nil, errors.New("echo: not supported on this platform")
}
