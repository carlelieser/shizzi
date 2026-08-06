<p align="center">
  <img src="docs/assets/icon.png" width="96" alt="">
</p>

<h1 align="center">Shizzi</h1>

<p align="center">
  Wi-Fi tethering over a Shizuku-privileged test network.
</p>

Creates a test TUN interface, sets it as the preferred tethering upstream, and
forwards hotspot traffic through a Go datapath.

## Requirements

- Android 10 (API 29+), arm64
- [Shizuku](https://shizuku.rikka.app/) 13.6.0+

## Install

Download the APK from the
[latest release](https://github.com/carlelieser/shizzi/releases/latest).

## Build

Needs the Android SDK with NDK, Go, and gomobile:

```
go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
./gradlew assembleDebug
```

## Known limitations

- IPv6 is not suppressed on the downstream; v6 traffic may bypass the tunnel.
