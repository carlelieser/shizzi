# Changelog

All notable changes to Shizzi are documented here.

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-08-22

Android 11 and 12 can now run Shizzi. Setup is walked through on first launch,
and the log records what a session did.

### Added

- **Android 11 and 12 (API 30-32).** These releases tether if their tethering
  module is new enough. When the module is all that is missing, the app offers
  to install it: a pinned, checksummed download staged through Shizuku, applied
  on reboot. 0.2.0 refused to install below Android 13.
- **Onboarding on first launch** covering Shizuku and this device's
  compatibility. Repeatable from Developer settings.
- **A compatibility check** that reports each capability's verdict without
  starting a session.
- **Diagnostics**, with progress while running and an exportable report.

### Changed

- **The log records failures, not just successes.** Resource release problems, a
  downstream that would not stop, and shutdown with a session active previously
  reached logcat only.
- **The log moved to settings**, is cleared across both processes that write it,
  and follows the logging setting. Clearing previously emptied the app's file,
  failed silently on the shell's, and reported success.
- **The log screen** was rebuilt around a menu, jump bands, and an empty state.
- Toasts can be swiped away and rank by weight rather than colour.
- Compose moved to the 2025.08.00 BOM, the build to AGP 8.9.2.

### Fixed

- Release builds no longer require a debuggable shell process, which kept the
  privileged side from starting outside a debug build.
- A slow Shizuku start no longer fails the bind.
- The shell context is attributed correctly on API 30.
- A failed context rebase is reported instead of killing the process.
- Timeouts name what timed out rather than a minified class.
- A diagnostics run stops the hotspot it started.
- The datapath builds off Android, so its tests run in CI.

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
