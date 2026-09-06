# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0-rc.2] - 2026-09-06

Adds a quick settings tile and an intent API for starting and stopping sessions
from other apps. New permissions screen in onboarding. Adds accent and design
language pickers, and animates screen changes and controls throughout.
Supersedes 0.4.0-rc.1.

### Added

- **Quick settings tile.** Start and stop sharing from the notification shade.
- **Intent API.** Start, stop, toggle, and query a session from another app.
  Off by default, token-authenticated. See [automation](docs/automation.md).
- **Permissions screen** in onboarding, replacing the Shizuku step. Also in
  settings.
- **Accent and design language pickers** in settings.
- **Motion tokens.** Durations, springs, and easing are theme values, so
  Neobrutalism moves mechanically while Material Expressive settles with a
  bounce.
- **Screen transitions.** Navigation slides and fades by screen depth, and
  onboarding fades into the home screen instead of cutting to it.
- **Press feedback in Neobrutalism**, which had none. Surfaces settle onto
  their shadow when pressed, covering every button, card, toggle, and swatch.
- Appearance glyphs spin a full turn on each press.
- **Version tap easter egg.** Three taps on the version label open a
  full-screen tethering icon pattern.

### Changed

- Material Expressive is the new default design language. Neobrutalism is still
  available.
- The connect button, status icon, settings sections, log rows, toasts, accent
  swatches, and the onboarding wizard animate their state changes.
- The tethering glyph accepts a brush, so it can carry a gradient. Icon only
  takes a flat tint.
- Compose moved to a BOM carrying Material3 1.4.0.

### Fixed

- A VPN reconnect or radio handoff left the tethering upstream empty for a few
  seconds, which killed the session. Only real drift onto another interface
  ends it now.
  ([#22](https://github.com/carlelieser/shizzi/issues/22))
- Stopping a session could leave the hotspot on, because stopTethering lands
  asynchronously. It now retries.
  ([#22](https://github.com/carlelieser/shizzi/issues/22))
- Intent-triggered starts silently did nothing on Android 12+, which blocks
  foreground service starts from the background. Battery optimization exemption
  is now requested as a permission, and an undeliverable start says why.
- An automation toggle sent mid-startup tore down the session it meant to leave
  alone, having read the service as running before it had connected.
- The notification permission dialog appeared over the welcome screen on first
  launch. It's asked for in the Permissions step now, and a denial is visible
  instead of silent.
- The onboarding wizard drew the incoming step in both transition layers, so
  the slide animated identical content.

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

[0.4.0-rc.2]: https://github.com/carlelieser/shizzi/releases/tag/v0.4.0-rc.2
[0.3.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.3.0
[0.2.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.2.0
[0.1.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.1.0
