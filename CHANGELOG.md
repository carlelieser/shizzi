# Changelog

This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- A crash report dialog. When the app crashes, the next launch shows the
  captured stack trace with the recent session log tail and a copy action,
  so crashes that happen inside onboarding can be reported without adb.
- The Neobrutalism design language is back and selectable from Settings →
  Appearance → Design. It restores the original treatment: sharp corners,
  a 2dp hard border, a 4dp offset shadow that surfaces slide onto when
  pressed, uppercase labels, mono-and-grotesk type, and mechanically damped
  motion. The design choice again drives shapes, typography, motion, and
  pure black/white accent edges; Material 3 Expressive keeps the softer
  outline colors it has now.
- The onboarding steps swipe horizontally: dragging follows the finger and
  releasing past a quarter of the width flips the step in that direction,
  with an edge step resisting instead of flipping. The footer buttons stay.

### Changed

- The Settings header is now immersive: the list scrolls edge to edge
  beneath it, the header blends into the page background while the list
  rests at the top, and it picks up its surface treatment once content
  scrolls underneath.
- The theme picker options press with a Material 3 state layer on top of
  their existing scale and ripple feedback.
- The README documents both privilege backends (Shizuku and the phone's own
  Wireless Debugging) and links to the privilege backends guide.

### Fixed

- Entering the compatibility step with a failing check crashed the app: the
  step added its own vertical scroll inside the wizard's scrollable column,
  and the nested measurement is disallowed.
- The compatibility check over Wireless Debugging failed with
  "IOException: Stream closed.". libadb-android ends a shell session by
  closing the stream, and `AdbStream.read()` throws
  `IOException("Stream closed.")` instead of returning end-of-stream when
  the daemon's close lands while the reader is blocked. The helper launch
  command runs detached and produces no output, so the compatibility check
  hit that path every time. Shell output is now read in a loop that folds
  that specific message into a normal end of output and still surfaces any
  other failure.
- The Wireless Debugging compatibility check then failed with
  "IllegalStateException: Local ADB helper did not start": the helper poll
  gave up after three seconds while app_process was still paying its
  first-launch dexopt for the APK. The poll now runs for twelve seconds
  (the overall check timeout moved to twenty to match), and a failed start
  carries the helper's own log tail so the underlying reason shows up
  instead of a bare assertion.

## [0.5.0-rc.1] - 2026-09-26

This release collects the full localization and Material 3 work since
the 0.4.0-rc.3 base, and adds a second privilege backend that can operate
without the Shizuku app.

### Added

- **Android resource localization.** English is the default resource language
  and Simplified Chinese lives in `values-zh-rCN`; Compose UI, notifications,
  Quick Settings, onboarding, settings, diagnostics, and accessibility labels
  use Android string resources.
- **Wireless Debugging / Local ADB privilege backend.** Shizzi can pair with the
  device's own Android 11+ Wireless Debugging daemon and launch the same shell
  helper used by the tethering implementation, without requiring the Shizuku
  app.
- **Notification-driven Wireless Debugging pairing.** Pairing discovery stays
  alive while Android settings are open. When the pairing mDNS service appears,
  the foreground notification exposes an inline six-digit pairing-code field
  plus a Cancel action.
- **Pluggable privilege layer.** Session, compatibility, diagnostics, Quick
  Settings, and background service code use a shared `PrivilegedClient`
  interface instead of directly depending on Shizuku.
- **Session overview** on the home screen with privilege state, connected
  clients, traffic, and upstream information.
- **Material 3 Expressive interaction pass:** shape-morphing controls, press
  feedback, smoother transitions, and adaptive motion.
- **Dynamic wallpaper colors** as the default accent on Android 12+, with softer
  Monet-style fallback palettes on older releases.
- **Predictive back** for in-app navigation and edge-to-edge system bars.
- **Tablet and landscape layouts** for the home screen, settings, onboarding,
  and logs.
- **Cancelable diagnostics.**
- **Compatibility troubleshooting.** After three seconds of a pending check a
  troubleshooting panel becomes available; checks now time out with a concrete
  error instead of spinning forever.
- A refreshed low-saturation launcher palette.

### Changed

- The home screen is a fixed single-screen layout; Settings is the only primary
  destination exposed from the home screen.
- Settings uses a stable classic top surface and a lazy list to avoid the
  previous scroll/recomposition crash.
- The appearance controls and privilege-provider selector use Material 3
  state/shape transitions.
- The custom-color add button was removed; wallpaper color extraction is the
  default and the built-in palette was softened.
- Wireless Debugging reconnects only through the TLS connect mDNS service,
  avoiding accidental selection of a legacy/plain ADB endpoint.
- Local ADB identity storage now uses a standard PKCS#8 RSA private key and X.509
  certificate in app-private no-backup storage. The previous Android Keystore
  identity is invalidated once and must be paired again.
- The explicit bundled Conscrypt provider was removed for the Android 11+ Local
  ADB path; the platform TLS 1.3 provider is used instead.

### Fixed

- Home-screen content could appear scrollable even though it is a single page.
- The top-right Settings button could be covered by another composable and stop
  receiving taps.
- Scrolling Settings to the bottom and back to the top could crash the app.
- Light-theme Settings headers could flicker while scrolling.
- System status/navigation bars did not fully participate in edge-to-edge.
- Session overview values could be clipped on smaller displays.
- Compatibility checks could show a large indefinite spinner behind the two
  capability cards.
- On Android 16 / some HyperOS builds, compatibility checks could remain pending
  indefinitely; checks now surface a timeout and troubleshooting guidance.
- Local ADB could fail with Conscrypt/OpenSSL RSA internal errors when the TLS
  stack attempted to use an Android Keystore private key.
- Local ADB reconnect could discover the wrong ADB transport.
- Wireless Debugging pairing silently did nothing when the notification
  permission was missing. The open-wireless-debugging action now explains why
  notifications are needed, requests the permission before pairing starts, and
  surfaces the concrete pairing error when a start attempt fails.
- Compatibility checks hid the real failure behind a generic "not reported by
  the privileged process" placeholder. The error reported by the privileged
  process is now surfaced in the capability details, and the shell-context
  attribution falls back to the classic op package name when the modern
  attribution field is unavailable (seen on Android 16).
- The privileged compatibility check itself always failed with a
  `Resources$NotFoundException` when it tried to load detail strings through
  the shell context, whose resources do not contain this app's IDs. The
  capability details are plain literals again, so the check now reports real
  results instead of an error.
- Release/about metadata now describes the localized Material 3 branch instead
  of presenting it as the upstream release.
- The about screen no longer carries the localization fork's own attribution
  and source links; source, issue reporting, and author links all point at the
  upstream project.
- The default and Simplified Chinese string resources each declared
  `action_continue` twice, which failed the resource merge.

## [0.4.0-rc.3] - 2026-09-13

Adds a quick settings tile and an intent API for starting and stopping sessions
from other apps. New permissions screen in onboarding. Adds accent and design
language pickers, and animates screen changes and controls throughout. Adds a
VPN setting, and stops a VPN in another Android user from being mistaken for
this one. Supersedes 0.4.0-rc.1 and 0.4.0-rc.2.

### Added

- **VPN setting.** `Auto` binds to an active VPN if there is one, `Always`
  refuses to start without one, and `Never` leaves the datapath unbound. A
  session that ignores a live VPN says so on the home screen and in the
  notification.
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

- A VPN running in another Android user, such as Samsung's Secure Folder, no
  longer counts as this profile's VPN. It could pin the datapath to a network
  the hotspot never routed over, and end the session when that unrelated VPN
  disconnected.
  ([#32](https://github.com/carlelieser/shizzi/issues/32))
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
- Settings rows that open a picker trailed the same arrow as rows that navigate
  or act in place. They take a chevron now.
- The Neobrutalism bottom sheet stopped above the navigation bar, leaving a band
  of scrim below it. It spans the full display now.

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

[0.4.0-rc.3]: https://github.com/carlelieser/shizzi/releases/tag/v0.4.0-rc.3
[0.3.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.3.0
[0.2.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.2.0
[0.1.0]: https://github.com/carlelieser/shizzi/releases/tag/v0.1.0
