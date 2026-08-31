# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Permissions.** A permissions step in onboarding and a section in settings,
  each listing what Shizzi needs, why, and whether it is granted.
- **Automation.** Other apps can start, stop, toggle, and query a session
  through intents, for automation from Tasker, MacroDroid, and the like. Off by
  default, and every command carries a token. The token card copies and
  regenerates it, and a setup dialog lists the values an automation app needs
  for each action. See
  [automation](docs/automation.md).

### Fixed

- **Background starts.** Android 12 and up blocked intent-triggered sessions
  from starting the foreground service, so a command was accepted and then did
  nothing. Battery optimization exemption lifts the restriction and is now
  surfaced as a permission, and a start that cannot be delivered reports the
  reason instead of failing silently.

## [0.3.0] - 2026-08-22

Adds support for Android 11 and 12 (API 30-32) by providing a tethering module update if necessary. Also adds an onboarding flow. Minor updates to the UI and better logging.

### Added

- **Android 11 and 12 (API 30-32) Support.** Through tethering module update.
- **Onboarding** With welcome, shizuku setup, and compatibility check.

### Changed

- **Better logging.** Improved logging throughout the codebase.
- **UI** Moved logging into settings, updated setting item labels and descriptions.
- **Log Viewer** was rebuilt around a menu, jump bands, and an empty state.
- Toasts can be swiped away and rank by weight rather than colour.
- Compose moved to the 2025.08.00 BOM, the build to AGP 8.9.2.

### Fixed

- Release builds no longer require a debuggable shell process, which kept the
  privileged side from starting outside a debug build.
- A slow Shizuku start no longer fails to bind.
- The shell context is attributed correctly on API 30.
- A failed context rebase is reported instead of killing the process.
- Better timeout messaging.
- Automatically tear down hotspot after diagnostic run.
- Datapath tests can now run in CI.

## [0.2.0] - 2026-08-19

Tethered devices now get working IPv6, and a session tells you what it is
actually doing — how many devices are connected, how much has gone through,
and whether traffic is leaving over your VPN.

### Added

- **IPv6 for tethered clients.** Connected devices get a real IPv6 address and
  reach the v6 internet. Previously they were handed a v6 route that led
  nowhere, so sites that prefer IPv6 stalled before falling back to IPv4.
- **VPN status on the session.** The app shows whether tethered traffic is
  going out through your VPN, both in the app and on the notification, so you
  no longer have to take it on trust.
- **Device count and data used.** The session notification reports how many
  devices are on the hotspot and how much data the tunnel has carried,
  updated as you watch.

### Changed

- **A dropped VPN now stops the session.** If you started tethering through a
  VPN and it goes away, the hotspot stops instead of quietly continuing over
  your normal connection. Sessions started without a VPN are unaffected.
- **Clearer notification wording.** The notification no longer describes
  tethering as "protected" — it said the same thing whether or not a VPN was
  up. It now states plainly whether devices are going out through one.
- **Minimum Android version is now 13 (API 33), enforced at install.** Older
  devices could install 0.1.0 but never tether with it; the feature it depends
  on does not exist below Android 13. They are now told at install time
  instead of after setup.

### Fixed

- **IPv6 traffic no longer bypasses your VPN.** With a VPN up, IPv6 traffic
  from tethered devices previously escaped the tunnel. This was the known
  limitation noted in 0.1.0 and is now resolved.
  ([#5](https://github.com/carlelieser/shizzi/issues/5),
  [#6](https://github.com/carlelieser/shizzi/issues/6))

## [0.1.0] - 2026-08-06

First public build.

### Added

- Wi-Fi tethering over a Shizuku-privileged test network: creates a test TUN
  interface, sets it as the preferred tethering upstream, and forwards hotspot
  traffic through a Go datapath.
- Requires Android 13 (API 33+) on arm64 and Shizuku 13.6.0+.

### Known limitations

- IPv6 was not suppressed on the downstream; v6 traffic could bypass the
  tunnel. Fixed in 0.2.0.

[0.3.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.3.0
[0.2.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.2.0
[0.1.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.1.0
