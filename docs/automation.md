# Automation

Shizzi can be started and stopped by other apps — Tasker, MacroDroid, or
anything else that can send an intent.

## Enabling

Off by default. Turn it on in **Settings › Automation**.

A token is generated when you turn automation on, and every command must
carry it. Commands without a matching token are refused, so an app that does
not know the token cannot control tethering.

## Background starts

Android 12 and up block an app in the background from starting a foreground
service, which is what an intent-triggered session needs. Exemption from
battery optimization is the documented way out of that restriction, and only
the user can grant it.

Grant **Background activity** in **Settings › Permissions**, or during
onboarding. Until you do, commands are accepted but no session starts, and the
log says so.

## Actions

Sent to `dev.shizzi/.AutomationReceiver` as a broadcast.

| Action | Effect |
| --- | --- |
| `dev.shizzi.action.START` | Start a session |
| `dev.shizzi.action.STOP` | Stop the session |
| `dev.shizzi.action.TOGGLE` | Stop if running, otherwise start |
| `dev.shizzi.action.QUERY_STATUS` | Report state without changing it |

Pass the token as the string extra `token`.

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
- Class: `dev.shizzi.AutomationReceiver`
- Target: `Broadcast Receiver`
- Extra: `token:your-token`

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
  -n dev.shizzi/.AutomationReceiver --es token your-token
```
