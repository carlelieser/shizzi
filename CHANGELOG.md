# Changelog

All notable changes to Shizzi are documented here.

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-08-22

Android 11 and 12 devices can now run Shizzi. The capability they were missing
ships as an updatable system module, so the app offers to install it rather than
turning those devices away at the store. First launch also walks you through
setup, and the log now records what a session actually did.

### Added

- **Support for Android 11 and 12 (API 30-32).** These releases can tether if
  their tethering module is new enough. The app checks the device it is on and,
  when the module is the only thing missing, offers to install it -- downloading
  a pinned, checksummed copy, staging it through Shizuku, and telling you a
  reboot is needed to apply it. 0.2.0 refused to install below Android 13.
- **Onboarding on first launch.** A short wizard covers what the app does,
  connecting Shizuku, and checking this device's compatibility, so setup fails
  in a place that explains itself rather than at the first attempt to tether.
  It can be run again from the Developer section in settings.
- **A compatibility check that answers without tethering.** It probes the two
  capabilities the approach needs and reports each one's verdict, instead of
  making you start a session to find out.
- **Diagnostics you can run and export.** A run reports progress while it works
  and produces a report you can share.

### Changed

- **The log records what a session did, not just what went right.** Failures
  that previously only reached logcat -- resource release problems, a downstream
  that would not stop, shutdown with a session still active -- are now in the
  log, which is the point of it existing: it was quietest exactly when you
  opened it to find out what went wrong.
- **The log is reachable from settings**, is cleared across both processes that
  write it, and the logging setting now controls it. Clearing previously
  truncated the app's file, silently failed on the shell's, and reported
  success -- leaving most of the history on disk.
- **The log screen** was rebuilt around a menu, jump bands, and an empty state.
- Toasts can be swiped away and rank themselves by weight rather than colour.
- Compose moved to the 2025.08.00 BOM and the build to AGP 8.9.2.

### Fixed

- **Release builds no longer require a debuggable shell process**, which kept
  the privileged side from starting outside a debug build.
- **A slow Shizuku start no longer fails the bind.** The bind now outlasts
  Shizuku's own start timeout instead of giving up first.
- **The shell context is attributed correctly on API 30.**
- **A failed context rebase is reported** instead of taking the process down.
- **Timeouts name what timed out** rather than a minified class.
- **A diagnostics run stops the hotspot it started.**
- **The datapath builds and tests off Android**, so its tests run in CI --
  `bind.go` is Android-only and now has a stub elsewhere.

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
