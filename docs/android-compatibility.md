# Android compatibility

Shizzi routes a phone's Wi-Fi hotspot traffic through a tunnel the app controls,
instead of letting it go straight out over the phone's own internet connection.
It creates a TUN interface, registers it with Android as a *test network*, and
gets the tethering stack to use that network as the hotspot's upstream. All of
this needs privileges an ordinary app does not have, which the app obtains
through [Shizuku](https://shizuku.rikka.app/).

**`minSdk = 33`.** API 32 may work on an updated tethering module but is
untested on hardware. Nothing below 32 can work.

## What the platform has to provide

Three separate capabilities, with three different floors:

1. **Start the hotspot** — `TetheringManager.startTethering`, falling back to
   `WifiManager.startTetheredHotspot`. From API 30.
2. **Create a test network** — build the TUN and register it via
   `TestNetworkManager`. From API 29.
3. **Route hotspot traffic through it** — `setPreferTestNetworks` raises a flag,
   and `UpstreamNetworkMonitor` has to check it. From API 32 or 33, depending on
   the tethering module.

Capability 3 sets `minSdk`. Capability 2 has the lowest floor, so checking
whether a release "supports test networks" returns yes well below the real
floor and answers nothing.

## Support by release

| API | Release | Start hotspot | Test network | Prefer test network | Net |
| --- | --- | --- | --- | --- | --- |
| 29 | Android 10 | **no** | yes | no | no |
| 30 | Android 11 | yes | yes | **no** | no |
| 31 | Android 12 | yes | yes | **no** | no |
| 32 | Android 12L | yes | yes | *module-dependent* | maybe |
| 33 | Android 13 | yes | yes | yes | **yes** |
| 36 | Android 16 | yes | yes | yes | yes (known-good) |

### API 29

`TetheringManager` does not exist, and `startTetheredHotspot` arrives at API 30.
API 29 cannot start a hotspot, so capability 3 does not matter.

Capability 2 is fully present, verified against `android10-release`:

| API | On Android 10 (29)? |
| --- | --- |
| `TestNetworkManager` (via `getSystemService("test_network")`) | present |
| `createTunInterface(LinkAddress[])` | present (array overload, which `invokeArrayOverload` probes) |
| `setupTestNetwork(String, IBinder)` | present |
| `teardownTestNetwork(Network)` | present |
| `TestNetworkInterface.getFileDescriptor()` / `getInterfaceName()` | present |
| `LinkAddress(InetAddress, int)` | present (package-private, so reached reflectively) |
| `NetworkCapabilities.TRANSPORT_TEST` | present, `= 7` (matches `TRANSPORT_TEST_AOSP_FALLBACK`) |
| `WifiManager.startTetheredHotspot` | **absent** — arrives at API 30 |

### API 30 and 31

The hotspot starts and the TUN registers, but `setPreferTestNetworks` does not
exist, so nothing selects the TUN. Probed at shell UID on both: 
`NoSuchMethodException`. On 31 the tethering jar contains no symbol matching
`TestNetwork`, so there is no renamed method or alternate overload to find.

### API 32

The 12L source tag (`android-12.1.0_r10`) does not have the mechanism. The
emulator image does:

- `framework-tethering.jar` contains `setPreferTestNetworks` and
  `TRANSACTION_setPreferTestNetworks`
- `TetheringGoogle.apk`, which reads the flag, contains it too
- Called at shell UID it returns normally, and the server logs
  `TetheringManager: setPreferTestNetworks caller: android` with no denial

Tethering ships as an updatable Mainline APEX, so the API level does not
determine the module version. An API 32 device that has taken module updates may
have this; one that has not, will not.

This does not lower `minSdk`. Only the call was verified, not tethering:
emulators have no SoftAP, so no hotspot was started and no traffic carried, and
whether `UpstreamNetworkMonitor` selects the TUN on 32 is untested.

### API 33 and up

Both halves are present from 33 and survive to current releases. Checked at 33
(`android-13.0.0_r43`) and 36 (`android-16.0.0_r4`); 34 and 35 were not checked
separately. The app is known to work on an Android 16 device.

A 33+ device is not guaranteed to work. The app reaches `TestNetworkManager` and
`TetheringManager` by reflection into `@hide` and `@TestApi` surfaces, which
vendors can trim. Run `ProbeRunner.kt` to check a specific handset.

## What the shell UID buys

Shizuku runs as shell (UID 2000), which holds the permissions gating these APIs
(`granted=true`, checked on 30, 32 and 33):

| Permission | Held by shell |
| --- | --- |
| `TETHER_PRIVILEGED` | yes |
| `MANAGE_TEST_NETWORKS` | yes |
| `NETWORK_SETTINGS` | yes |
| `NETWORK_STACK` | **no** |

`setPreferTestNetworks` is gated on `TETHER_PRIVILEGED`, not `NETWORK_SETTINGS`
as its `@hide` annotation says. An app without it is refused with
`SecurityException: No android.permission.TETHER_PRIVILEGED or
android.permission.WRITE_SETTINGS permission`.

Shell cannot configure routing directly. On 30, 31, 32 and 33 alike:

- `ip rule add`, `ip route add` → `Cannot talk to rtnetlink: Permission denied`.
  Shell is in `inet` and `net_bw_stats`, not `net_admin`.
- `iptables` → `Permission denied (you must be root)`
- `netd` → `avc: denied { find } ... tcontext=u:object_r:netd_service:s0`.
  SELinux blocks the service lookup before any permission check.

This is why [VPNHotspot](https://github.com/mygod/vpnhotspot), which configures
tethering routes by hand, needs root. It also rules out doing the routing
manually as a way to support older releases. These denials apply on 33 as well,
where the app works, so they are not what separates releases.

## Probing correctly

Two conditions, or the probe reports false negatives:

1. Lift the hidden-API blocklist —
   `settings put global hidden_api_policy 1`
2. Run at shell UID via `app_process`, not as an app

Run as an ordinary app, the probe reports `NoSuchMethodException` on every
release including 33, where the app works.

Source reading has its own blind spot: it cannot see which Mainline module a
device has. Reading `android-12.1.0_r10` says what shipped with 12L, not what is
installed now.

## What would change the answer for 30 and 31

Only a different mechanism for winning upstream selection:

- Another hidden switch biasing selection. The source trace argues against one —
  every return path out of the selection method is accounted for.
- Giving the TUN a shape the ordinary rules accept, which needs
  `NET_CAPABILITY_INTERNET` on a network `TestNetworkService` will not grant it.
- Forwarding at a lower layer instead, which is a different product and needs
  root.

## Sources

- AOSP codenames, tags, and build numbers:
  <https://source.android.com/docs/setup/reference/build-numbers>
- `platform/packages/modules/Connectivity`, tags `android-12.1.0_r10`,
  `android-13.0.0_r43`, `android-16.0.0_r4`; branches `android12-d1-release`,
  `android13-release` (`TetheringManager`, `UpstreamNetworkMonitor`,
  `TestNetworkService`)
- `platform/frameworks/base`, branches `android10-release`, `android11-release`
  (`TestNetworkManager`, `TestNetworkInterface`, `LinkAddress`,
  `NetworkCapabilities`, `ConnectivityManager`, `WifiManager`)
- Emulator runs: `system-images;android-{30,31,32,33};google_apis;arm64-v8a`,
  emulator 36.6.11.0, arm64 host. API 29 was not run.
