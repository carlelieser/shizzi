<p align="center">
  <img src="docs/assets/icon.png" width="96" alt="">
</p>

<h1 align="center">Shizzi</h1>

<p align="center">
  Wi-Fi tethering over a Shizuku-privileged test network.
</p>

Creates a test TUN interface, sets it as the preferred tethering upstream, and
forwards hotspot traffic through a Go datapath. Supports IPv4 and IPv6.

## Requirements

- Android 13 (API 33+), arm64
- [Shizuku](https://shizuku.rikka.app/) 13.6.0+

## Install

Download the APK from the
[latest release](https://github.com/carlelieser/shizzi/releases/latest).

## Features

- 🚀 **Unlimited hotspot.** Sharing draws on your regular data instead of your
  hotspot allowance.
- 🛡️ **VPN compatible.** Stay private on every connected device.
- ⚙️ **Uses your existing hotspot.** No extra configuration required.
- 🙌 **No root.** Shizuku is all it needs.

## Build

Needs the Android SDK with NDK, Go, and gomobile:

```
go install golang.org/x/mobile/cmd/gomobile@latest && gomobile init
./gradlew assembleDebug
```
