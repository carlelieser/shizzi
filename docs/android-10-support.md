# Android 10 support

Shizzi routes a phone's Wi-Fi hotspot traffic through a tunnel the app controls,
instead of letting it go straight out over the phone's own internet connection.
It does this by creating a TUN interface — a virtual network interface the app
can read and write packets on — registering it with Android as a *test network*,
and then getting the tethering stack to use that network as the hotspot's
*upstream*, meaning the connection that carries tethered traffic onward. All of
this needs privileges an ordinary app does not have, which the app obtains
through [Shizuku](https://shizuku.rikka.app/), a service that runs with ADB shell
permissions and lends them to apps.

This answers issue #2: does that work on Android 10 (API 29)?

**No. It does not work on Android 11 or 12 either. `minSdk = 33` stays.**

## The short version

The app needs two separate capabilities from the platform:

1. **Create a test network** — build the TUN and register it with the framework.
   Available from API 29.
2. **Make the tethering stack route hotspot traffic through it** — available from
   API 33.

On Android 10 the app can build the tunnel and register it, but the tethering
stack contains no code that would ever select it. The tunnel would sit there
carrying nothing.

Anyone checking whether Android 10 supports test networks will find that it
does. That is capability 1. It says nothing about capability 2, which is the
one that fails.

## Capability 1: available from API 29

Verified against `android10-release`:

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

`startTetheredHotspot` is the fallback path for starting the hotspot —
`DownstreamControl.startWifiTethering` tries `TetheringManager.startTethering`
first — and `TetheringManager` is also absent on Android 10. So API 29 has no way
to start the hotspot either, independent of everything below.

On Android 10 the app would hold `MANAGE_TEST_NETWORKS` through Shizuku's
shell UID, reach `TestNetworkManager`, create the TUN, and get an available test
network back.

## Capability 2: arrives at API 33

Asking the tethering stack to prefer a test network takes two pieces of
platform. The app calls `setPreferTestNetworks`, which raises a flag; then
`UpstreamNetworkMonitor`, the class that picks the upstream, has to check that
flag.

| Release | API | Ref read | `TetheringManager` | `setPreferTestNetworks` | Test-network path in `UpstreamNetworkMonitor` |
| --- | --- | --- | --- | --- | --- |
| Android 10 | 29 | `android10-release` | absent — `android.net` has only the tethering AIDLs | — | — |
| Android 11 | 30 | `android11-release` | present | absent | — |
| Android 12 | 31 | `android12-d1-release` | present | absent | absent |
| Android 12L | 32 | `android-12.1.0_r10` | present | absent | absent |
| Android 13 | 33 | `android-13.0.0_r43` | present | present, `@hide`, `NETWORK_SETTINGS` | present |
| Android 16 | 36 | `android-16.0.0_r4` | present | present | present |

12L is in the table because it is the last place the mechanism could have
appeared before 33. Android 16, because the app builds against `compileSdk` 35,
so the mechanism has to survive to current releases rather than merely exist at
33. Android 14 and 15 were not checked individually, since 16 having it makes a
gap in between irrelevant.

Below 33 the flag has no reader, and nothing else in the framework selects a test
network as a tethering upstream. From 33 onward both halves are present.

## What would change the answer

Only a different mechanism for winning upstream selection — not a different way
of calling this one. Each of these is new work, not a lowered floor:

- Another hidden switch biasing selection on 30–32. The trace above argues
  against one existing: every return path is accounted for, leaving no exit for a
  renamed mechanism to hide behind.
- Giving the TUN a shape the ordinary rules accept, which means
  `NET_CAPABILITY_INTERNET` on a network `TestNetworkService` refuses to grant it.
- Abandoning tethering-stack upstream selection and forwarding at a lower layer,
  which is a different product.

## Confidence, and what is not covered

These two claims do not have the same strength.

**API 29–32 cannot work — high confidence.** Established by tracing every return
path out of the selection method in shipped AOSP source. The code that would have
to run does not exist.

**Any given API 33+ device will work — not established.** The app is known to
work on at least one Android 16 device, so the mechanism is real and reachable in
practice. That does not generalise: the app reaches `TestNetworkManager` and
`TetheringManager` by reflection into `@hide` and `@TestApi` surfaces, which
vendors are free to trim from production builds, and `HiddenApiCatalog` notes
"Absent on trimmed OEM builds." To find out about a specific handset, run the
app's probe (`ProbeRunner.kt`) on it — it reports which of these APIs that device
actually has.

The API 29–32 finding is source reading alone. Nothing was built or run against
those releases.

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

## Two loose ends found on the way

**A referenced file that does not exist.** `HiddenApi.kt` and
`app/build.gradle.kts` both point at `docs/hidden-api-record.md` as the record of
which hidden APIs the app uses and why. That file is not in the repository. The
catalog in `HiddenApi.kt` is the only such record. This investigation did not
create the missing file.

**A correction to the catalog.** Eight entries in `HiddenApiCatalog` recorded
`since = 30`; the correct value is 29, verified above. Six are the test-network
APIs, the other two are `LinkAddress.<init>` and
`NetworkCapabilities.TRANSPORT_TEST`. Corrected in `HiddenApi.kt`, along with a
header noting that the catalog spans two subsystems with different floors, and a
KDoc fix where `createTunInterface` gave the array overload's floor as 30. This
does not change `minSdk`, which `setPreferTestNetworks` sets alone. It does
suggest the remaining `since` values were written from assumption rather than
verification, and are worth auditing.
