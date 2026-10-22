# Privilege backends

Shizzi needs a helper running with the Android `shell` identity to reach the
protected test-network and tethering APIs. App-facing session code depends on
`PrivilegedClient`, not on one transport.

## Shizuku

Shizuku remains supported and uses a UserService. Existing users can keep the
same workflow.

## Wireless Debugging / Local ADB

Android 11+ can use the phone's own Wireless Debugging daemon:

1. Shizzi starts a short-lived foreground pairing-discovery service.
2. The user opens **Wireless debugging → Pair device with pairing code**.
3. Shizzi discovers `_adb-tls-pairing._tcp` over mDNS.
4. The foreground notification exposes Android's inline `RemoteInput` for the
   six-digit pairing code plus Cancel.
5. Shizzi pairs using a standard RSA PKCS#8 private key and X.509 certificate
   stored in app-private no-backup storage.
6. Reconnects discover only `_adb-tls-connect._tcp` and use the Android
   platform TLS 1.3 provider.
7. ADB is used only to launch `LocalAdbHelperMain` as `shell`.
8. Normal session operations use an authenticated loopback RPC socket rather
   than executing one ADB shell command per operation.

The previous Android-Keystore Local-ADB identity is versioned out by
0.5.0-rc.1. Existing Local ADB users pair once again after updating.

## TUN / datapath ownership

`TestNetworkManager` keeps its original `ParcelFileDescriptor`. Before
attaching gVisor, Shizzi duplicates that TUN descriptor and keeps the duplicate
open for the entire datapath lifetime. gVisor never shares ownership of the
framework descriptor.

## Failure handling

- Pairing discovery is cancelable and bounded.
- TLS/RSA handshake failures invalidate the stale ADB identity and explain how
  to re-pair.
- Compatibility checking exposes Troubleshooting after three seconds and hard
  times out after twelve seconds.
- Local ADB and Shizuku report their own provider-specific setup failures while
  sharing the same session implementation.

A future root backend can implement the same `PrivilegedClient` contract
without changing tethering/session logic.
