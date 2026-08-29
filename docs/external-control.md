# External control

Shizzi can be started and stopped by other apps — Tasker, MacroDroid, or
anything else that can send an intent.

## Enabling

Off by default. Turn it on in **Settings › External control**.

Once enabled, any app on the device can control tethering. To restrict that,
set a token in the same section and include it with every intent. Without a
token, no caller is verified.

## Background starts

Android 12 and up block an app in the background from starting a foreground
service, which is what an intent-triggered session needs. Being exempt from
battery optimization lifts that block, so enabling external control adds
Shizzi to the device idle allowlist through Shizuku.

If that fails, **Settings › External control** shows an *Allow background
starts* row that opens the system prompt instead. Until it is granted,
commands are accepted but no session starts, and the log says so.

## Actions

Sent to `dev.shizzi/.ExternalControlReceiver` as a broadcast.

| Action | Effect |
| --- | --- |
| `dev.shizzi.action.START` | Start a session |
| `dev.shizzi.action.STOP` | Stop the session |
| `dev.shizzi.action.TOGGLE` | Stop if running, otherwise start |
| `dev.shizzi.action.QUERY_STATUS` | Report state without changing it |

Pass the token, when set, as the string extra `token`.

## Result

Every command answers with a `dev.shizzi.action.SESSION_RESULT` broadcast.
Because a start runs through Shizuku binding, interface setup, and upstream
verification, this can arrive some seconds after the request — catch it rather
than assuming the command took effect.

| Extra | Type | Meaning |
| --- | --- | --- |
| `command` | string | The command being answered |
| `accepted` | boolean | Whether the command passed the gate |
| `refusal` | string | Why it was refused, when `accepted` is false |
| `status` | string | `READY`, `LOADING`, `CONNECTED`, or `ERROR` |
| `isActive` | boolean | Whether tethering is up |
| `detail` | string | Human-readable state |
| `interface` | string | Upstream interface name |
| `error` | string | Failure reason, when one applies |
| `clientCount` | int | Connected devices |
| `bytesUp` / `bytesDown` | long | Session traffic |

A refused command reports `accepted=false` and changes nothing.

## Tasker

Add a **System › Send Intent** action:

- Action: `dev.shizzi.action.TOGGLE`
- Package: `dev.shizzi`
- Class: `dev.shizzi.ExternalControlReceiver`
- Target: `Broadcast Receiver`
- Extra: `token:your-token` (only if you set one)

To react to the result, add an **Event › System › Intent Received** profile
with the action `dev.shizzi.action.SESSION_RESULT`. The extras above arrive as
variables, so `%isActive` tells you whether tethering came up.

## MacroDroid

Use a **Send Intent** action with target `Broadcast`, the same action and
class, and add `token` as a string extra. Catch the result with an
**Intent Received** trigger on `dev.shizzi.action.SESSION_RESULT`.

## adb

Useful for checking a setup:

```
adb shell am broadcast -a dev.shizzi.action.QUERY_STATUS \
  -n dev.shizzi/.ExternalControlReceiver --es token your-token
```
