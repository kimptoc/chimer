# Status-bar Live Update Chip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** While a timer is running, show a live-ticking "time remaining" chip in the Android status bar (the "Live Update" / promoted-ongoing notification feature added in Android 16 / API 36), on devices that support it, with zero regression to the existing Samsung Now Bar pill on devices that don't.

**Architecture:** `RunningTimerNotifier.start()` branches at runtime on a new `Notifier.canPromote(context)` capability check. The promoted branch posts a new, styleless `NotificationCompat` notification once per `Running` state (system ticks the chip itself via `setChronometerCountDown`). The existing branch (today's `MediaStyle` + `TimerMediaSession`, with its 20s refresh loop) is left behaviorally unchanged. Both branches post to the same `NOTIF_ID_RUNNING` / `CHANNEL_RUNNING`.

**Tech Stack:** Kotlin, `androidx.core:core-ktx` (`NotificationCompat`), Android `NotificationManager` (platform, for the API-36-only `canPostPromotedNotifications()` check).

## Global Constraints

- `minSdk = 29`, `compileSdk = 36`, `targetSdk = 36` (from `app/build.gradle.kts`) — do not change these.
- `androidx.core:core-ktx` must be bumped from `1.13.1` to `1.19.0` (promoted-ongoing APIs need core `1.17.0-alpha01`+).
- Manifest must declare `android.permission.POST_PROMOTED_NOTIFICATIONS`.
- The promoted branch must gate on both `Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA` **and** `NotificationManager.canPostPromotedNotifications()` — never one without the other.
- The existing `MediaStyle`/`TimerMediaSession` code path must not change behavior for devices where the promoted branch doesn't apply.
- No new unit tests — posting real Android notifications isn't unit-testable without Robolectric/instrumentation, consistent with `RunningTimerNotifier`/`Notifier` today (neither has tests). Verification is build-green + manual on-device (Task 5).
- Reuse existing string resources `R.string.notif_running_title` / `R.string.notif_running_text_set` / `R.string.notif_action_cancel` — do not add new strings unless a task below says to.

Reference spec: `docs/superpowers/specs/2026-07-31-status-bar-live-update-chip-design.md`

---

### Task 1: Bump `androidx.core:core-ktx` to 1.19.0

**Files:**
- Modify: `gradle/libs.versions.toml:6`

**Interfaces:**
- Produces: `NotificationCompat.Builder#setRequestPromotedOngoing`, `#setChronometerCountDown` availability for later tasks (already exist for the countdown method at this core version; `setRequestPromotedOngoing` is new as of this bump).

This is a version-floor bump landed in isolation, per the spec, so any breakage here is never tangled with new feature code.

- [ ] **Step 1: Bump the version**

In `gradle/libs.versions.toml`, change line 6:
```toml
coreKtx = "1.19.0"
```

- [ ] **Step 2: Sync and build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. If it fails, read the error — it's most likely an AGP/Kotlin version floor conflict pulled in by the new core version. Do not proceed to Task 2 until this passes.

- [ ] **Step 3: Run the existing unit test suite**

Run: `./gradlew testDebugUnitTest`
Expected: All existing tests pass (`RingProfileTest`, `RecentDurationsTest`, `TimerRepositoryTest`, `FormatTest`, `RepeatAccelerationTest`). This confirms the dependency bump didn't regress anything already covered.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: bump androidx.core to 1.19.0 for Live Update notification support"
```

---

### Task 2: Add the `POST_PROMOTED_NOTIFICATIONS` manifest permission

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: the manifest permission that Task 4's `canPostPromotedNotifications()` call (and the system's promotion mechanism) requires. Without this, promotion silently never happens regardless of code correctness.

- [ ] **Step 1: Add the permission**

In `app/src/main/AndroidManifest.xml`, add this line after the existing `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />` line (line 11):

```xml
    <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
```

- [ ] **Step 2: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "feat: declare POST_PROMOTED_NOTIFICATIONS permission for Live Update chip"
```

---

### Task 3: Add `Notifier.canPromote()` and `Notifier.buildRunningPromotedNotification()`

**Files:**
- Modify: `app/src/main/java/net/kimptoc/timerwithauto/alarm/Notifier.kt`

**Interfaces:**
- Consumes: `Context`, `Context.NOTIFICATION_SERVICE` (existing pattern already used in `ensureChannel`).
- Produces (for Task 4):
  - `fun canPromote(context: Context): Boolean`
  - `fun buildRunningPromotedNotification(context: Context, durationMinutes: Int, deadlineEpochMs: Long, cancelIntent: PendingIntent): Notification`

This task also extracts the duplicated "open app" `PendingIntent` construction (currently inline in `buildRunningNotification`) into a private helper, so both the existing and new builder share it — a pure DRY refactor with no behavior change to the existing notification.

- [ ] **Step 1: Add the imports**

At the top of `Notifier.kt`, add:
```kotlin
import android.os.Build
```
(`android.app.NotificationManager` is already imported.)

- [ ] **Step 2: Extract the shared content `PendingIntent` helper**

Replace the inline `contentIntent` construction inside `buildRunningNotification` (lines 75-82):
```kotlin
        val contentIntent = PendingIntent.getActivity(
            context,
            REQ_RUNNING_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
```
with:
```kotlin
        val contentIntent = runningContentIntent(context)
```

Then add this private function just below `buildRunningNotification` (after its closing brace, before the existing `private const val REQ_RUNNING_CONTENT = 3001` line):
```kotlin
    private fun runningContentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQ_RUNNING_CONTENT,
        Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
```

- [ ] **Step 3: Add `canPromote`**

Add this function to the `Notifier` object, near `ensureChannel`:
```kotlin
    fun canPromote(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.canPostPromotedNotifications()
    }
```

- [ ] **Step 4: Add `buildRunningPromotedNotification`**

Add this function directly after `buildRunningNotification` (after the `private const val REQ_RUNNING_CONTENT = 3001` line):
```kotlin
    fun buildRunningPromotedNotification(
        context: Context,
        durationMinutes: Int,
        deadlineEpochMs: Long,
        cancelIntent: PendingIntent,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_running_title))
            .setContentText(context.getString(R.string.notif_running_text_set, durationMinutes))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(runningContentIntent(context))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notif_action_cancel),
                cancelIntent,
            )
            .setRequestPromotedOngoing(true)
            .setWhen(deadlineEpochMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
    }
```

- [ ] **Step 5: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL. If `setRequestPromotedOngoing` or `setChronometerCountDown` are unresolved, re-check Task 1 landed correctly (`./gradlew :app:dependencies | grep core-ktx` should show `1.19.0`).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/alarm/Notifier.kt
git commit -m "feat: add promoted-ongoing notification builder for Live Update chip"
```

---

### Task 4: Branch `RunningTimerNotifier` on promotion capability

**Files:**
- Modify: `app/src/main/java/net/kimptoc/timerwithauto/alarm/RunningTimerNotifier.kt`

**Interfaces:**
- Consumes: `Notifier.canPromote(context)`, `Notifier.buildRunningPromotedNotification(...)` from Task 3; existing `Notifier.buildRunningNotification(...)`, `Notifier.NOTIF_ID_RUNNING`, `mediaSession.activateRunning(...)`, `mediaSession.deactivate()` (all pre-existing, unchanged signatures).
- Produces: no new public interface — `RunningTimerNotifier.start()` / lifecycle is unchanged from the caller's perspective (`TimerApp` or wherever it's constructed — verify call sites are untouched, see Step 5).

- [ ] **Step 1: Replace the `Running` branch in `start()`**

Replace this block (current lines 42-51):
```kotlin
                    is TimerState.Running -> coroutineScope {
                        // One UI's Now Bar pill drops the entry once the MediaSession's
                        // interpolated position passes duration, and some launchers GC the
                        // ongoing notification if it isn't re-posted. Refresh both
                        // periodically to keep the pills alive for the full countdown.
                        while (isActive) {
                            post(state)
                            delay(REFRESH_INTERVAL_MS)
                        }
                    }
```
with:
```kotlin
                    is TimerState.Running -> coroutineScope {
                        if (Notifier.canPromote(context)) {
                            // Android 16+ Live Update chip: the system ticks the
                            // chronometer itself, so one post is enough.
                            postPromoted(state)
                        } else {
                            // One UI's Now Bar pill drops the entry once the MediaSession's
                            // interpolated position passes duration, and some launchers GC the
                            // ongoing notification if it isn't re-posted. Refresh
                            // periodically to keep the pill alive for the full countdown.
                            while (isActive) {
                                postMediaStyle(state)
                                delay(REFRESH_INTERVAL_MS)
                            }
                        }
                    }
```

- [ ] **Step 2: Rename the existing `post` function to `postMediaStyle`**

Rename `private fun post(state: TimerState.Running) { ... }` (current lines 58-87) to `private fun postMediaStyle(state: TimerState.Running) { ... }`. Body stays byte-for-byte identical.

- [ ] **Step 3: Extract the cancel `PendingIntent` into a private helper**

Replace the inline `cancelPi` construction inside `postMediaStyle` (current lines 68-75):
```kotlin
        val cancelPi = PendingIntent.getBroadcast(
            context,
            REQ_CANCEL,
            Intent(context, CancelTimerReceiver::class.java).apply {
                action = CancelTimerReceiver.ACTION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
```
with:
```kotlin
        val cancelPi = cancelPendingIntent()
```

Then add this private function directly below `postMediaStyle`'s closing brace (before the `private fun cancel()` function):
```kotlin
    private fun cancelPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_CANCEL,
        Intent(context, CancelTimerReceiver::class.java).apply {
            action = CancelTimerReceiver.ACTION
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
```

- [ ] **Step 4: Add `postPromoted`**

Add this function directly after `cancelPendingIntent()`:
```kotlin
    private fun postPromoted(state: TimerState.Running) {
        if (!nm.areNotificationsEnabled()) return
        val notif = Notifier.buildRunningPromotedNotification(
            context = context,
            durationMinutes = state.durationMinutes,
            deadlineEpochMs = state.deadlineEpochMs,
            cancelIntent = cancelPendingIntent(),
        )
        try {
            nm.notify(Notifier.NOTIF_ID_RUNNING, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — accept silently.
        }
    }
```

- [ ] **Step 5: Verify no other file calls `post()` directly**

Run: `grep -rn "\.post(" app/src/main/java/net/kimptoc/timerwithauto/alarm/`
Expected: no matches outside `RunningTimerNotifier.kt` itself (it was already `private`, so this should be a no-op check, but confirm before moving on).

- [ ] **Step 6: Build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/alarm/RunningTimerNotifier.kt
git commit -m "feat: post Live Update chip notification when device supports promotion"
```

---

### Task 5: On-device verification gate (manual — required before this is done)

**Files:** none (verification only; may produce a follow-up task if the fallback triggers — see Step 4).

This is the risk called out in the spec: every reference sample pairs the chronometer countdown with `ProgressStyle`, not a styleless notification. This task confirms whether the styleless approach actually renders a ticking chip, using the user's One UI 8.5 / Android 16 phone (the only device available — no emulator/CI can cover this).

- [ ] **Step 1: Install the debug build on the phone**

With the phone connected (USB debugging or wireless debugging enabled) and `adb devices` showing it:
```bash
./gradlew installDebug
```

- [ ] **Step 2: Confirm the notification qualifies for promotion**

```bash
adb logcat -c
adb shell dumpsys notification --noredact | grep -A5 "net.kimptoc.timerwithauto"
```
Start a timer in the app, then re-run the `dumpsys` command. Look for the running notification entry and confirm it's ongoing / not filtered. (There is no dedicated `hasPromotableCharacteristics()` logcat line without adding temporary logging — if the chip doesn't appear in Step 3, add a temporary `Log.d("LiveUpdate", "promotable=${notification.hasPromotableCharacteristics()}, canPost=${Notifier.canPromote(context)}")` in `postPromoted` before `nm.notify(...)`, rebuild, re-test, then remove it once diagnosed.)

- [ ] **Step 3: Visually confirm the chip**

Start a timer from the app. With the app backgrounded (press home), look at the status bar top-left, next to the clock.

Expected: a pill/chip appears showing a live-ticking countdown (e.g. "9:58", "9:57", ...) that counts down in real time.

- [ ] **Step 4: Decide pass/fail and act**

- **If the chip ticks correctly:** verification passes. No further code changes needed. Proceed to Task 6.
- **If the chip is static, icon-only, or doesn't appear at all:** the styleless-notification approach doesn't work for the chip specifically. Do not guess further — stop and report back what was observed (screenshot if possible) so the plan can be amended with a `ProgressStyle`-based `postPromoted` (mirroring Google's `platform-samples` `live-updates` sample) before Task 6.

- [ ] **Step 5: Confirm cancel and expiry clear the chip**

Tap the notification's cancel action (or let the timer expire). Confirm the chip disappears from the status bar in both cases, and that the existing ringing-alarm notification still works normally.

---

### Task 6: Push branch and open PR

**Files:** none (git/GitHub operations only).

Only do this once Task 5 has passed (or its fallback has been implemented and re-verified).

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/issue-19-live-update-chip
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "Show time left as a status-bar Live Update chip (issue #19)" --body "$(cat <<'EOF'
## Summary
- Issue #19 was titled "show time left in top left home screen widget," but after discussion it turned out to describe Android's status-bar "Live Update" chip (the pill that appears top-left near the clock for ongoing activities on Android 16+), not a launcher home-screen widget or the existing Samsung Now Bar pill.
- Adds a second, styleless promoted-ongoing notification path (`Notifier.buildRunningPromotedNotification`) used only when `Notifier.canPromote(context)` is true (API 36+ and the OS grants promotion). The existing `MediaStyle`/`TimerMediaSession` Now Bar path is unchanged for all other devices (notably One UI 7 / Android 15, which predates this API).
- Bumped `androidx.core:core-ktx` to `1.19.0` for the new `NotificationCompat` APIs, and added the `POST_PROMOTED_NOTIFICATIONS` manifest permission.

See `docs/superpowers/specs/2026-07-31-status-bar-live-update-chip-design.md` for the full design and the reasoning behind the runtime branch.

## Test plan
- [x] Existing unit test suite passes (`./gradlew testDebugUnitTest`)
- [x] Verified on-device on One UI 8.5 / Android 16: chip appears top-left in the status bar, ticks live, clears on cancel and on expiry
- [ ] Regression-check the Now Bar path is unchanged on an Android 15 / pre-API-36 device, if available

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Report the PR URL back to the user**

---

## Self-Review Notes

- **Spec coverage:** §3 runtime branch → Tasks 3-4; §3 verification risk → Task 5; §4 supporting changes (core-ktx bump, manifest permission) → Tasks 1-2; §6 testing plan → Task 5; PR reinterpretation note (advisor feedback) → Task 6.
- **No placeholders:** every step has literal code or literal commands; Task 5's fallback explicitly says "stop and report" rather than guessing at `ProgressStyle` code blind, since that's an on-device-verified decision, not a code-only one.
- **Type consistency:** `buildRunningPromotedNotification(context, durationMinutes, deadlineEpochMs, cancelIntent)` in Task 3 matches the call in Task 4 Step 4 exactly. `canPromote(context)` matches its Task 4 call site.
