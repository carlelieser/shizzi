package datapath

/*
#cgo LDFLAGS: -landroid
#include <android/multinetwork.h>
*/
import "C"

import "fmt"

// bindToNetwork pins fd to the network identified by handle.
func bindToNetwork(handle uint64, fd uintptr) error {
	rc, errno := C.android_setsocknetwork(C.net_handle_t(handle), C.int(fd))
	if rc != 0 {
		return fmt.Errorf("bindToNetwork: android_setsocknetwork(handle=%d, fd=%d): %w",
			handle, fd, errno)
	}
	return nil
}
