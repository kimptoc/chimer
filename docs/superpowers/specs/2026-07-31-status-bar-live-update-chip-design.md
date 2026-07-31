# Status-bar "Live Update" chip for running timer (issue #19) — Design Spec

Date: 2026-07-31
Status: Implemented and verified on-device (One UI 8.5 / Android 16)
Issue: https://github.com/kimptoc/chimer/issues/19

## 1. Summary

Issue #19 ("show time left in top left home screen widget") turned out to describe
Android's status-bar chip — the small pill that sits top-left near the clock while an
ongoing activity is live, and expands into a bigger view on tap. This is distinct from
the Samsung One UI "Now Bar" pill the app already drives (`RunningTimerNotifier`,
`TimerMediaSession`), and distinct from a launcher home-screen widget (no such thing
exists in this app today).

Goal: while a timer is `Running`, show a live, ticking "time remaining" chip in the
status bar on devices that support it, without regressing the existing Now Bar support
on devices that don't.

## 2. Background: two different OS mechanisms

- **Samsung Now Bar** (existing, working): debuted in One UI 7 (Android 15). Driven by
  an active `MediaSession` + `MediaStyle` notification (`TimerMediaSession.kt`,
  `RunningTimerNotifier.kt`). Renders a position/duration progress-fill, not literal
  "time remaining" text — the code fakes a countdown by advancing a playback position
  toward a duration.
- **Android "Live Update" / promoted-ongoing notification** (new, not yet implemented):
  introduced in Android 16 ("Baklava", API 36) as `Notification.hasPromotableCharacteristics()`
  / `NotificationCompat.Builder#setRequestPromotedOngoing()`. Confirmed via Google's own
  `platform-samples` repo (`live-updates` sample) and developer docs:
  - Requires manifest permission `android.permission.POST_PROMOTED_NOTIFICATIONS`
    (install-time, no runtime prompt).
  - Requires `NotificationManager.canPostPromotedNotifications()` to be true at runtime
    (user/OEM controlled).
  - Only these notification styles qualify: Standard, `BigTextStyle`, `CallStyle`,
    `ProgressStyle`, `MetricStyle`. **`MediaStyle` is explicitly excluded** — confirmed
    from developer docs and cross-checked against Google's sample code. A notification
    combining `MediaStyle` with `setRequestPromotedOngoing(true)` will simply fail
    `hasPromotableCharacteristics()` and never be promoted.
  - Must be `setOngoing(true)`, have a `contentTitle`, no custom view, not a group
    summary, not colorized, and the channel must not be `IMPORTANCE_MIN`.
  - Supports `setWhen(deadlineEpochMs)` + `setUsesChronometer(true)` +
    `setChronometerCountDown(true)`, which makes the system tick the chip text itself —
    no periodic re-post needed (unlike the Now Bar path, which re-posts every 20s to
    stop One UI from dropping the pill).

The user confirmed their own device (One UI 8.5 / Android 16) already renders Google's
Live Update sports-score pill in Now Bar style — i.e. on a sufficiently new One UI,
*both* mechanisms are live and Now Bar itself has started surfacing promoted-ongoing
notifications too. But One UI 7 (Android 15), still the current shipping version for
many Galaxy devices, predates the promoted-ongoing API entirely and only understands
`MediaStyle`. So `MediaStyle` cannot be dropped without regressing those devices.

## 3. Design

**One notification, one `notify()` call per update, builder chosen dynamically at
runtime** based on device capability — no duplicate/second notification.

The capability check (`isPromotable`) is evaluated once in `start()`'s
`TimerState.Running` branch, not inside `post()`, because it changes the *loop
shape*, not just the builder:

```kotlin
is TimerState.Running -> coroutineScope {
    if (isPromotable(context)) {
        postPromoted(state)          // one shot — system ticks the chronometer itself
    } else {
        while (isActive) {           // existing behaviour, unchanged
            postMediaStyle(state)
            delay(REFRESH_INTERVAL_MS)
        }
    }
}
```

`isPromotable(context)` = `Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
notificationManager.canPostPromotedNotifications()`.

- **Promoted branch** (`postPromoted`): plain `NotificationCompat.Builder`
  (no style — see verification note below) with:
  - `.setOngoing(true)`, `.setRequestPromotedOngoing(true)`
  - `.setContentTitle(...)`, `.setContentText(...)`
  - `.setWhen(state.deadlineEpochMs)`, `.setUsesChronometer(true)`,
    `.setChronometerCountDown(true)` — posted once; no refresh loop, the system
    ticks the chip itself
  - same cancel action and content intent as today
  - reuses `CHANNEL_RUNNING` (already `IMPORTANCE_LOW`, which qualifies)
- **Legacy branch** (`postMediaStyle`): today's `MediaStyle` + `TimerMediaSession`
  path, byte-for-byte unchanged, including the existing 20s refresh loop.

Both branches post to the same `NOTIF_ID_RUNNING` notification ID and the same
`CHANNEL_RUNNING` channel — from the system's perspective it's a single running
notification whose internal construction differs by capability. Cancellation
(`cancel()` on Idle/Ringing) is unchanged — cancelling `NOTIF_ID_RUNNING` and
deactivating the media session covers both branches (the media session is simply
unused/inactive on the promoted branch).

**Verification result:** verified on a Samsung Galaxy S25 Ultra, One UI 8.5,
Android 16 (API 36).
- `dumpsys notification` confirmed the running notification (`NOTIF_ID_RUNNING`,
  channel `timer_running`) carries `flags=...|PROMOTED_ONGOING` when posted via
  `buildRunningPromotedNotification` — the styleless notification (no `ProgressStyle`)
  was accepted for promotion.
- The notification shade's "Live notifications" section showed the chip's chronometer
  text ticking live (e.g. "03:49", counting down), confirming a styleless notification
  does render the ticking countdown text — the `ProgressStyle` fallback described in
  the original open-question was not needed.
- The Cancel action was confirmed to correctly clear the notification (verified via
  `logcat`'s `onNotificationRemoved` event and `dumpsys` showing it moved out of the
  active notification list) on the promoted-ongoing path.
- Additionally, the pre-existing MediaStyle/Now Bar fallback path (taken when
  `canPromote()` is false) was independently re-verified on the same physical device
  after the `androidx.core` version bump (by temporarily forcing `canPromote()` to
  return `false`, confirming the Now Bar pill still renders correctly with the
  progress bar and title/text, and that Cancel correctly clears it too) — closing the
  risk the design doc originally flagged in §4 about the core-ktx bump interacting
  with `androidx.media`/MediaStyle.

## 4. Supporting changes

1. Bump `androidx.core:core-ktx` from `1.13.1` to `1.18.0` in `gradle/libs.versions.toml`
   — the promoted-ongoing APIs need core `1.17.0-alpha01`+; `1.18.0` is current stable.
   Land this as its own commit, build and run the existing unit test suite before
   writing any feature code — it's a six-minor-version jump and could interact with
   `androidx.media` (`MediaStyle`) or AGP/Kotlin version floors; isolate that failure
   mode from the new notification code if it happens.
2. Add to `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
   ```

## 5. Non-goals

- No `ProgressStyle` progress bar / segments / points — on-device verification in §3
  confirmed a styleless notification renders a ticking chip, so the `ProgressStyle`
  fallback was not needed.
- No change to the ringing-alarm notification (`buildRingingNotification`).
- No change to Android Auto (`TimerCarScreen`) — unaffected by this feature.
- No unit tests added — posting real Android notifications isn't unit-testable without
  Robolectric/instrumentation, consistent with `RunningTimerNotifier`/`Notifier` today
  (neither has tests). Verification is manual, on-device.

## 6. Testing / verification plan

Manual, on-device (no emulator has both required OS versions):
- One UI 8.5 / Android 16 phone (user's device): confirm the promoted-ongoing branch
  fires, chip shows ticking time remaining, cancel action works, chip clears on
  cancel/ringing/expiry.
- Regression check on any available Android 15 (or API <36) device, or by forcing the
  `else` branch: confirm Now Bar / MediaStyle path is unchanged from current behavior.
