# Shizuku Tether — Prototype Specification

**Status:** Draft v0.1 · Prototype / proof-of-viability only
**Goal:** Route Android Wi-Fi hotspot clients through a userspace tunnel on a non-rooted device, using Shizuku-provided shell privileges and Android's test-network infrastructure.

---

## 1. Scope

### 1.1 In scope

A single-purpose app that makes Android's tethering stack select an app-owned TUN as its upstream, consumes that TUN with a userspace TCP/IP stack, and egresses via a configured SOCKS5 endpoint. Fail-closed at all times.

### 1.2 Explicit non-goals for the prototype

| Excluded | Rationale |
|---|---|
| USB / Bluetooth / Ethernet tethering | Wi-Fi only; other downstreams add lifecycle surface |
| IPv6 forwarding | Must be **blocked**, not passed — see R6.4 |
| Bundled tunnel client | Egress is an external SOCKS5 endpoint |
| Profile switching / hot reconfiguration | Restart the session instead |
| Per-app or process-based routing | Not applicable to tethered clients |
| Hotspot SSID / password / band control | Uses Android's existing persistent hotspot config |
| Recovery from app or Shizuku process death | Prototype may fail closed and stay down |
| Localization, theming, Play Store compliance | — |
| Broad OEM compatibility | Target AOSP/Pixel behavior only |

### 1.3 Platform floor

- **minSdk for the feature: API 33 (Android 13).** API 30–32 can create a shell-owned test TUN but do not expose `TetheringManager.setPreferTestNetworks`. Below 33 the feature is hidden and privileged calls are rejected defensively.
- Shizuku v11+ installed, running, permission granted.
- Reference targets: Pixel-class device on Android 14+, plus an AOSP emulator.

---

## 2. Functional requirements

### R1 — Shizuku binding

- R1.1 Declare `moe.shizuku.manager.permission.API` and register `ShizukuProvider`.
- R1.2 Detect and surface four states: not installed, installed but not running, running without permission, ready.
- R1.3 Request permission on user action. Never auto-request on launch.
- R1.4 Verify `Shizuku.getUid()` returns 2000 (shell) or 0 (root) and record which; the shell path is the one under test.

### R2 — Privileged UserService

- R2.1 All privileged networking runs in a Shizuku `UserService`, not the app process.
- R2.2 AIDL surface is minimal: `start(config)`, `stop()`, `getStatus()`.
- R2.3 Service is not exported. Broadcasts restricted to the app package.
- R2.4 Session token: random per-session value; reject calls carrying a stale or unknown token.
- R2.5 Version the UserService contract so an APK update replaces a long-lived shell process.

### R3 — Test network lifecycle

- R3.1 Obtain the hidden `test_network` service via `TestNetworkManager`.
- R3.2 `createTunInterface()` → assign IPv4 (`192.0.2.2/24`) → `setupTestNetwork()`.
- R3.3 Block until `ConnectivityManager` reports the network available, with a bounded timeout (suggest 10s). Timeout is a hard failure.
- R3.4 Advertise the configured DNS servers on the test network.
- R3.5 Own the TUN fd, framework interface, network handle, and callback registration as one atomic resource group. Teardown releases all or none.

### R4 — Upstream preference and verification

- R4.1 Call `TetheringManager.setPreferTestNetworks(true)` immediately before starting the downstream.
- R4.2 Start Wi-Fi tethering through `TetheringManager`.
- R4.3 **Treat startup as provisional.** After the downstream reports active, verify the real upstream is *only* the owned `testtunN`.
- R4.4 On mismatch: stop the downstream, wait for the framework to settle, retry exactly once. On second failure, leave tethering stopped and report the error.
- R4.5 If tethering was already active before the session started, restart that downstream through the same verification path rather than adopting it blind.
- R4.6 Upstream probing (e.g. `dumpsys tethering`) must be drained concurrently under a short deadline so a stalled OEM service cannot block teardown.

### R5 — Datapath

- R5.1 Userspace TCP/IP stack (hev-socks5-tunnel, tun2socks, or gVisor netstack) reads the test TUN fd.
- R5.2 **Egress mode A (default):** forward to a configured SOCKS5 endpoint (host, port, optional user/pass).
- R5.3 **Egress mode B (debug only):** direct sockets from the shell process. Required so QA can isolate tethering plumbing from tunnel faults. Must be clearly labeled and off by default.
- R5.4 DNS for tethered clients resolves through the datapath, never via the carrier resolver.
- R5.5 Configurable MTU, default 1500 minus tunnel overhead.

### R6 — Fail-closed enforcement

This is the requirement that defines a passing build. Once the user enables protected routing, tethered clients either route through the owned TUN or have no connectivity. There is no third state.

- R6.1 Disabling the session stops the downstream **before** removing the test network.
- R6.2 If Android refuses to stop tethering, retain the test network rather than releasing clients onto a physical upstream.
- R6.3 Loss of the datapath process retains the (now dead) TUN. Clients lose connectivity; they do not fall back.
- R6.4 IPv6 on the downstream must be blocked or unadvertised. An IPv4-only upstream with IPv6 reaching clients is a silent full leak.
- R6.5 Cleanup restores `setPreferTestNetworks(false)`, unregisters callbacks, tears down the test network, closes the fd, and stops the datapath.

### R7 — User interface

One screen. No navigation.

- R7.1 Shizuku status (R1.2 states) with a request-permission action.
- R7.2 SOCKS5 endpoint fields + debug-egress toggle.
- R7.3 Single master switch: protected tethering on/off.
- R7.4 Live status: session state, active upstream interface name, downstream interface name, client count if cheaply available.
- R7.5 Errors surfaced verbatim from the UserService. No generic "something went wrong."
- R7.6 Switch is disabled while an operation is in flight; toggles serialize.

---

## 3. QA scenarios

A build passes only if **every** scenario below passes. Leak scenarios (L-series) are non-negotiable.

### 3.1 Preconditions and gating

| ID | Scenario | Pass criteria |
|---|---|---|
| P-1 | Shizuku not installed | Feature disabled, install guidance shown, no crash |
| P-2 | Shizuku installed, not running | Correct state shown, request action available |
| P-3 | Permission denied by user | Denial surfaced, no privileged call attempted |
| P-4 | Device on API 32 or lower | Feature hidden; direct privileged call rejected |
| P-5 | Shizuku running as root (Sui) | Either works or fails explicitly; never silently misbehaves |

### 3.2 Happy path

| ID | Scenario | Pass criteria |
|---|---|---|
| H-1 | Enable session → enable hotspot → connect one client | Client gets DHCP lease, resolves DNS, loads HTTPS |
| H-2 | Verify upstream | `dumpsys tethering` shows `testtunN` as the sole upstream |
| H-3 | Sustained transfer | 500 MB sustained through a client with no stall or session drop |
| H-4 | Two clients simultaneously | Both route correctly, no cross-talk |
| H-5 | Debug egress mode (R5.3) | Traffic reaches internet directly; confirms plumbing independent of tunnel |

### 3.3 Leak scenarios — mandatory

Verify each by packet capture at the egress endpoint **and** by checking the tethered client's apparent public IP.

| ID | Scenario | Pass criteria |
|---|---|---|
| L-1 | Hotspot already active before session enabled | Downstream restarted through verification, or left stopped. Never adopted on a physical upstream |
| L-2 | Force-stop the app process mid-transfer | Client connectivity **drops**. No traffic egresses outside the TUN |
| L-3 | Force-stop Shizuku mid-transfer | Same as L-2 |
| L-4 | Phone switches Wi-Fi → cellular mid-session | Either continuity through the TUN, or clean drop. No physical-upstream fallback |
| L-5 | Airplane mode on/off cycle | No fallback; state is either restored or failed closed |
| L-6 | SOCKS5 endpoint becomes unreachable | Client loses connectivity. Datapath does not fall through to direct |
| L-7 | IPv6-capable client on IPv6-capable carrier | Client receives no usable IPv6 route; no IPv6 traffic bypasses the TUN |
| L-8 | DNS leak check | Client DNS queries appear at the tunnel egress, not at the carrier resolver |
| L-9 | Captive-portal / connectivity probe from client | Probes traverse the TUN like any other traffic |

### 3.4 Lifecycle and teardown

| ID | Scenario | Pass criteria |
|---|---|---|
| T-1 | Clean disable | Downstream stops first, then test network; `setPreferTestNetworks(false)` restored |
| T-2 | Post-teardown interface check | No orphaned `testtun` in `ip link`; no leaked fd |
| T-3 | Rapid toggle ×10 | No stuck state, no duplicate TUN, no orphaned UserService |
| T-4 | Enable → reboot → relaunch | No stale session claimed; clean start required |
| T-5 | APK update with session active | Stale shell UserService replaced (R2.5); no version mismatch crash |
| T-6 | Stale/invalid session token replayed | Call rejected |

### 3.5 Egress characteristics

| ID | Scenario | Pass criteria |
|---|---|---|
| E-1 | Hop limit at egress | Packets originating from tethered clients arrive with the phone's TTL, not decremented |
| E-2 | Upstream race (R4.3–4.4) | Across 20 enable cycles, zero cases where a physical interface is accepted as upstream |
| E-3 | Accounting check | Session traffic attributed in `NetworkStats` as expected; document actual observed behavior |

---

## 4. Exit criteria

The prototype is considered viable when:

1. All P, H, L, T, and E scenarios pass on at least one physical device and one AOSP emulator image.
2. E-2 shows zero upstream-selection failures across 20 cycles.
3. No L-series scenario produces a single packet outside the owned TUN.
4. A written record exists of which hidden APIs were touched and which reflection paths were required, for future breakage triage.

---

## 5. Known risks

- `TestNetworkManager` and `setPreferTestNetworks` are hidden/system APIs. Signatures may change between releases, and OEMs may remove them, rename them, or trim shell-UID networking permissions.
- Upstream selection is racy by design; R4.3–4.4 is mitigation, not a fix.
- Shell-UID capabilities vary by Android version and vendor. Probe at runtime; do not assume.
- Carrier entitlement, device policy, or system config can block tethering independently of any of the above.
- Carrier terms of service may treat tethering as a separately metered or restricted feature regardless of technical outcome.
