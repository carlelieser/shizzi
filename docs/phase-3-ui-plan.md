# Phase 3 — UI

Turns the spike's single screen into the shipping app: three screens, a real
design system, a real log, and names that describe the product rather than the
experiment.

Scope boundary: this phase does not change the datapath, the fail-closed
teardown, or the foreground service. It changes what the user sees and one
backend addition — the log — that exists only to be seen.

---

## 1. Design tokens

### Color

Turquoise is the only color in the app. Everything else is neutral. A state
worth acting on is turquoise; a state that is merely true is not.

Concretely: the connect button is turquoise, and the status icon is turquoise
when connected. Ready, loading, and error all render neutral — an error is
communicated by its toast, which carries text the user can act on, not by a red
icon that carries only alarm.

| Token | Light | Dark |
|---|---|---|
| `primary` | `#14B8A6` | `#2DD4BF` |
| `onPrimary` | `#000000` | `#000000` |
| `background` | `#FAFAF9` | `#0C0A09` |
| `surface` | `#FFFFFF` | `#1C1917` |
| `onSurface` | `#0C0A09` | `#FAFAF9` |
| `onSurfaceMuted` | `#57534E` | `#A8A29E` |
| `border` | `#000000` | `#FFFFFF` |
| `borderPrimary` | `#000000` | `#2DD4BF` |
| `shadow` | `#000000` | `#FFFFFF` |

Dark mode inverts the structural colors rather than recoloring them: a black
offset shadow is invisible on a near-black background, so borders and shadows
go white for neutral elements and turquoise for primary ones.

`onPrimary` is black in both themes — turquoise is a light color, and white
text on it fails contrast.

### Shape and elevation — neobrutalism

Three rules, applied consistently:

1. **Sharp corners.** Radius 0 everywhere. No exceptions, including the
   progress indicator and toasts.
2. **Hard borders.** 2dp solid. Neutral elements use `border`; primary
   elements use `borderPrimary`.
3. **Flat offset shadows.** 4dp down-right, zero blur, drawn as a solid
   offset rectangle rather than a Material elevation shadow.

Interactive elements shift into their shadow on press: the element translates
+4dp x and +4dp y while the shadow is suppressed, so the button visually
depresses to meet the surface. Released, it springs back. This is the only
motion in the design system besides the loading indicator.

Compose has no built-in flat-offset shadow, so this is a custom
`Modifier.brutalSurface(isPrimary, isPressed)` that draws the offset rect,
the border, and the fill in one place. Every bordered element uses it, which
is what keeps the look consistent.

### Typography

Two bundled families, split by role rather than by screen:

- **JetBrains Mono** — display, titles, labels, badges, the log. Weights 400,
  500, 700.
- **Inter** — body text only. Weight 400.

The line is structure versus prose. Anything that names a thing — a screen
title, a settings item, a status badge, a log line — is mono, which is the
typographic register neobrutalism wants and what makes uppercase tracked text
read as deliberate. Anything the user reads as a sentence is Inter.

That keeps mono off the only strings long enough for its cost to matter:
uniform advance widths flatten word shapes, which slows continuous reading.
Irrelevant for a two-word label, real for a paragraph.

| Token | Font | Size | Weight | Notes |
|---|---|---|---|---|
| `display` | Mono | 28sp | 700 | |
| `title` | Mono | 18sp | 700 | screen headers |
| `label` | Mono | 13sp | 500 | settings item titles |
| `caption` | Mono | 11sp | 500 | uppercase, +0.08em tracking |
| `log` | Mono | 13sp | 400 | log body and gutter |
| `body` | Inter | 14sp | 400 | descriptions, toast messages |

Mono sizes account for its extra width: the Shizuku badge reads
`PERMISSION NEEDED` rather than `PERMISSION REQUIRED`, and any mono string
that overflows loses a size step before it loses meaning.

Settings items pair a mono `label` with an Inter `body` description directly
beneath it. The contrast is the point — the label is a name, the description
is a sentence.

Session detail on Home is `caption`, uppercase — the tracking is what makes
11sp read as deliberate rather than merely small.

In the log, gutter and body are the same face and size; the gutter is
distinguished by `onSurfaceMuted` and the divider rule alone.

Bundle cost is roughly 250–300 KB: three mono weights plus one Inter weight.

### Spacing

4dp base. Named scale, no arbitrary values:

`xs` 4 · `sm` 8 · `md` 12 · `lg` 16 · `xl` 24 · `xxl` 32 · `xxxl` 48

Screen edge padding is `lg` (16dp). Header height is 56dp. Touch targets are
never below 48dp regardless of visual size.

### Files

```
ui/theme/Color.kt      — the table above, as a ShizziColors class
ui/theme/Type.kt       — both families and the type scale
ui/theme/Spacing.kt    — the spacing scale
ui/theme/Shape.kt      — border width, shadow offset, brutalSurface modifier
ui/theme/Theme.kt      — ShizziTheme, resolves light/dark from the setting
res/font/              — JetBrains Mono 400/500/700, Inter 400
```

Tokens are exposed via `CompositionLocal` so composables read
`ShizziTheme.colors.primary` rather than importing a global.

---

## 2. Navigation

Three screens, no overlays: Home, Log, Settings.

No navigation library. A sealed `Screen` class held in a `rememberSaveable`,
with `BackHandler` on the two child screens. `navigation-compose` would add a
dependency and a nav graph to express a linear two-level back stack.

The state survives configuration change and process death via `Saveable`,
which matters because the log is a scroll position users will not want to lose
on rotation.

---

## 3. Home

```
┌─────────────────────────────────────┐
│ [Shizuku: Ready]        [Log] [Gear]│  header, 56dp
├─────────────────────────────────────┤
│                                     │
│                                     │
│              ╔═══════╗              │
│              ║       ║              │  status icon, 96dp
│              ║   ⬢   ║              │  turquoise when connected,
│              ║       ║              │  neutral otherwise
│              ╚═══════╝              │
│                                     │
│           ┌─────────────┐           │
│           │    START    │           │  button, 200x56
│           └─────────────┘           │
│                                     │
│                                     │
│         VIA TESTTUN51               │  caption, uppercase, bottom edge
└─────────────────────────────────────┘
```

**Shizuku badge** — display only. Bordered, `caption`, uppercase. Reads
`READY` / `PERMISSION NEEDED` / `NOT RUNNING` / `NOT INSTALLED` /
`UNSUPPORTED`. Not tappable; when permission is required an actionable toast
handles the request.

Strings are kept short deliberately: monospace at 11sp runs wide, and the
badge shares the header with two icon buttons.

**Status icon** — 96dp, four states, no text label. The button underneath
says what state you are in, so a label would repeat it. Turquoise fill only
when connected; neutral otherwise.

**Connect button** — 200x56, always primary turquoise, never changes color.
Label is `START` when idle and `STOP` when connected. Disabled when Shizuku is
not ready.

While loading, the button is replaced by a progress indicator, and a **Cancel**
button appears underneath — but only during *start*. There is no cancel during
teardown: aborting a fail-closed teardown is exactly the failure the teardown
prevents.

Cancel is implemented as abandon-and-stop. `ITetherService.start()` is a
single blocking binder call with no interruption point, so cancel issues
`stop()`, which tears down whatever the partial start created. The existing
teardown path already handles partial state — that is what a failed start does
today. No AIDL change.

**Session detail** — bottom edge, centered, `caption`, uppercase. Shows the
interface carrying traffic (`VIA TESTTUN51`) when connected; empty otherwise.
This is the proof the tunnel is real, which is why it stays on screen rather
than moving to the log.

---

## 4. Toasts

Custom, not `android.widget.Toast` — the platform toast cannot be styled,
cannot persist, and cannot stack.

A `ToastHost` overlay at the app level, above all three screens. Toasts stack
bottom-up, newest at the bottom, each a bordered surface with the flat shadow.

**Keyed replacement.** Every toast has a key. Posting a toast with an existing
key replaces it in place rather than adding to the stack. Session-state toasts
share one key, so there is never more than one on screen — the sequence
"session died unconfirmed → user taps Start → start fails" produces one current
message, not two with a stale one on top.

**Duration is per-toast, and may be indefinite.** Transient messages
(`Copied`, `Diagnostics complete`) auto-dismiss after ~3s. The
unconfirmed-teardown message is indefinite: it tells the user to go turn off
their hotspot manually, and a message that disappears before it is read fails
at the one job it has. Indefinite toasts carry a dismiss affordance.

**Actionable toasts** carry an optional action. Shizuku permission required →
toast with a `GRANT` action. This is why the badge does not need to be
tappable.

---

## 5. Log

Nothing in the app produces a log today. `TetherService` writes one JSON
snapshot that is overwritten on each call, and `Log.i` goes to logcat, which
the app process cannot read without a privileged permission.

The events worth logging — session start, upstream drift, watchdog strikes,
teardown — happen in the **shell process**, not the app.

### Transport

Append-only file at `/data/local/tmp/shizzi.log`, written by the shell process
and read by the app. The shell process already writes to that directory (the
probe report lives there), so this reuses a proven path rather than adding a
binder polling loop. uid 2000 can write there and the app can read it.

The app appends its own entries to the same file, so one file is the whole
history in order.

### Format

One entry per line, parsed into a timestamp, a level, and a message:

```
2026-08-05T14:22:31.442 INFO  session start requested
2026-08-05T14:22:33.118 INFO  tun up: testtun51
2026-08-05T14:22:41.902 WARN  upstream drift: expected testtun51, saw wlan0
2026-08-05T14:22:42.330 ERROR teardown: downstream still tethered: [wlan1]
```

### What gets logged

Session lifecycle only, not verbose datapath tracing:

- start requested / succeeded / failed
- TUN created, interface name
- upstream selection outcome
- watchdog strikes and drift-triggered teardown
- stop requested / completed, and whether the downstream was confirmed down
- shell process death and orphan recovery
- Shizuku binder lost

### Retention

Truncate at 1 MB, keeping the newest half. Simple, bounded, no rotation
scheme. Not user-configurable — you asked to leave verbosity and retention
out of settings, and this default needs no tuning.

### Screen

```
┌─────────────────────────────────────┐
│ [←]  Log                     [Copy] │  header, 56dp
├─────────────────────────────────────┤
│  1 │ 14:22:31 INFO  session start   │
│    │ requested                      │  wrapped continuation, blank gutter
│  2 │ 14:22:33 INFO  tun up:         │
│    │ testtun51                      │
│  3 │ 14:22:41 WARN  upstream drift: │
│    │ expected testtun51, saw wlan0  │
│  4 │ 14:22:42 ERROR teardown:       │
│    │ downstream still tethered      │
└─────────────────────────────────────┘
```

**Line numbers** in a fixed-width gutter, right-aligned, muted, separated by a
1dp rule. They are a reading aid, not a citation mechanism: lines wrap, and the
gutter is the only thing distinguishing a wrapped continuation from a new
entry. **Never included in copied text.**

**Wrapping**, not horizontal scroll. Log entries carry exception messages; a
screen you have to drag sideways to read is not readable.

**Line-granularity selection.** Tap a row to select, tap again to deselect,
selected rows get a turquoise-tinted background and a turquoise left edge. No
character-level drag selection — that is a large amount of machinery and
awkward on touch.

**Copy button** in the header, right edge. Reads `COPY ALL` with nothing
selected, `COPY 3` with three lines selected. Copies text only.

The `log` token, which is the same face as the rest of the app — the gutter
aligns because everything is mono, not because this screen is special.

---

## 6. Settings

```
┌─────────────────────────────────────┐
│ [←]  Settings                       │
├─────────────────────────────────────┤
│  SHIZUKU                            │  section label
│  ┌───────────────────────────────┐  │
│  │ Status          Ready         │  │
│  │ UID             2000 (shell)  │  │
│  │ Version         13.6.0        │  │
│  └───────────────────────────────┘  │
│                                     │
│  APPEARANCE                         │
│  Theme                              │
│  ○ System   ● Light   ○ Dark        │
│                                     │
│  DIAGNOSTICS                        │
│  Debug logging              [ ⬛ ]  │
│  Write session reports to disk      │
│                                     │
│  Run diagnostics                 →  │
│  Full probe sequence                │
│                                     │
│  ABOUT                              │
│  Source                          ↗  │
│  Report a bug                    ↗  │
│  Author                          ↗  │
└─────────────────────────────────────┘
```

Every item is label + description + control: a mono `label` naming the
setting, an Inter `body` description beneath it, and the control on the right.
Sections are separated by uppercase mono `caption` headers.

**Expanded Shizuku status** — a bordered card, not a single line. Shows
availability, the UID the service runs as (`ShizukuGate.describeUid` already
produces this), and the installed Shizuku version. When permission is
required, the card carries a `GRANT` button.

The version matters: Shizuku 13.5.4 on Android 16 crashes within minutes, and
this is the surface where a user can see what they are running.

**Theme** — radio group, system / light / dark. Persisted. Makes
`MainActivity`'s `isSystemInDarkTheme()` a fallback for the system option
rather than the source of truth.

**Debug logging** — the existing toggle, now persisted.

**Run diagnostics** — the existing action, unchanged.

**Links** — open in the browser:
- Source → `https://github.com/carlelieser/shizzi`
- Report a bug → `https://github.com/carlelieser/shizzi/issues/new`
- Author → `https://carlelieser.dev`

### Persistence

DataStore Preferences. Two keys today (`theme`, `debugLogging`), read at app
start and exposed as a flow so a theme change recomposes immediately.

Debug logging currently lives in ViewModel memory and resets on every launch —
a setting the user cannot rely on. DataStore fixes that.

---

## 7. Rename

Names describe the product, not the experiment, and not the app either — class
names should not carry the app's brand.

| Now | After |
|---|---|
| `dev.shizzi.spike` | `dev.shizzi` |
| `IProbeService` | `ITetherService` |
| `ProbeService` | `TetherService` |
| `ProbeController` | `TetherClient` |
| `SpikeApplication` | `App` |
| `SpikeUiState` | `SessionUiState` |
| `SpikeViewModel` | `SessionViewModel` |
| `SpikeScreen` | `HomeScreen` |
| `Theme.TetherSpike` | `Theme.Shizzi` |
| label `Tether Spike` | `Shizzi` |
| `rootProject.name` | `Shizzi` |

`ProbeRunner`, `ProbeReport`, and `ProbeOutcome` keep their names — they are
genuinely the diagnostic probe sequence, which survives as a shipped feature.

Two consequences:

- `SessionService.ACTION_STOP` is the literal string
  `"dev.shizzi.spike.STOP_SESSION"` and must move with the package.
- Renaming the AIDL interface changes the daemon contract. Any running daemon
  becomes unreachable, which `CONTRACT_VERSION` already detects and reports.
  One manual stop/start after installing the renamed build.

---

## 8. Commit sequence

One logical change each, on `feat/ui-phase-3`.

| # | Commit | Touches |
|---|---|---|
| 1 | `add design tokens and bundled typeface` | `ui/theme/*`, `res/font/*` |
| 2 | `add persisted settings via DataStore` | `SettingsStore.kt`, gradle |
| 3 | `add append-only session log` | shell + app logging, `SessionLog.kt` |
| 4 | `add screen navigation` | `Screen.kt`, `MainActivity` |
| 5 | `add toast host with keyed replacement` | `ToastHost.kt`, state |
| 6 | `rebuild home screen` | `HomeScreen.kt` |
| 7 | `add log screen` | `LogScreen.kt` |
| 8 | `add settings screen` | `SettingsScreen.kt` |
| 9 | `rename spike identifiers to product names` | everything |

Rename last: doing it first would churn every diff before it, and doing it
mid-sequence splits the churn across two commits.

Commits 1–3 are prerequisites with no visible effect. 4–8 are the screens.
Device verification after 8, before the rename, then again after 9 — the
rename invalidates running daemons and that path deserves a check.

---

## 9. Out of scope

Named so they are decisions rather than omissions:

- **Log verbosity and retention settings** — excluded by request; the defaults
  need no tuning.
- **Character-granularity log selection** — line selection covers the use case.
- **Real start cancellation in the shell service** — abandon-and-stop reuses
  the trusted teardown path.
- **IPv6 suppression** — deferred earlier; still a real bypass path, still
  out of scope.
- **Datapath liveness signal** — deferred from the fail-closed work.
