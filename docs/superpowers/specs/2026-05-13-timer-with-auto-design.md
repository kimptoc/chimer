# TimerWithAuto — Design Spec

Date: 2026-05-13
Status: Approved (pending user re-review of written spec)

## 1. Summary

An Android app for setting a single countdown timer from the phone, with an Android Auto surface that shows the running countdown and a large Stop button when the alarm fires. When the timer expires, the app loops an audible alarm and vibration for up to 2 minutes or until the user cancels.

## 2. Goals and non-goals

Goals:
- Set a one-shot countdown timer from the phone with a minute-wheel picker.
- Quick re-use of the last 5 distinct durations (MRU + dedupe; seeded with `[5, 12, 60]` on first launch).
- Looping alarm sound + vibration on expiry, auto-stopping after 2 minutes if the user does not cancel.
- Reliable firing: survive app kill and device reboot.
- Android Auto surface that shows the live countdown while running and a large Stop screen when the alarm fires.

Non-goals (v1):
- Multiple concurrent timers.
- Setting timers from Android Auto.
- User-configurable sound, vibration pattern, or auto-stop duration.
- Stopwatch / interval timers.
- Background analytics, accounts, or cloud sync.

## 3. Platform targets

- Language: Kotlin.
- Phone UI: Jetpack Compose.
- Android Auto: `androidx.car.app` (Car App Library).
- `minSdk = 29` (Android 10). `targetSdk` = current stable at implementation time.
- Single Gradle module (`:app`), default process.

## 4. Architecture

A single phone module exposes two entry surfaces — Compose UI and a `CarAppService` for Android Auto — both reading the same in-process state via `TimerRepository`.

```
┌─────────────────────────┐    ┌──────────────────────────┐
│ MainActivity (Compose)  │    │ TimerCarAppService       │
│  └ TimerScreen          │    │  └ TimerCarScreen        │
└─────────────┬───────────┘    └─────────────┬────────────┘
              │ collects StateFlow           │ collects StateFlow
              ▼                              ▼
                  ┌────────────────────────┐
                  │   TimerRepository      │  ← single source of truth
                  │   (DataStore-backed)   │
                  └─────────┬──────────────┘
                            │ schedule / cancel
                            ▼
                  ┌────────────────────────┐
                  │   TimerScheduler       │  → AlarmManager
                  │   (AlarmManager facade)│
                  └─────────┬──────────────┘
                            │ PendingIntent at deadline
                            ▼
                  ┌────────────────────────┐
                  │   AlarmReceiver        │
                  │   (manifest broadcast) │
                  └─────────┬──────────────┘
                            │ startForegroundService
                            ▼
                  ┌────────────────────────┐
                  │   AlarmService (FGS)   │  plays sound + vibrate + notif
                  │   self-stops on Stop / 2-min cap
                  └────────────────────────┘
```

`BootReceiver` listens for `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` and either re-schedules a future deadline or fires `AlarmService` immediately for a recently-missed deadline.

### 4.1 Why AlarmManager + on-demand foreground service (not a long-lived FGS)

- We only need a live process at the moment of expiry. AlarmManager + `setExactAndAllowWhileIdle` is exact under Doze and survives app kill.
- No persistent countdown notification while the timer is running (user preference).
- The phone UI and Auto screen tick locally from the persisted absolute deadline; no state writes during countdown.
- WorkManager is unsuitable because its timing is best-effort and can be delayed by minutes under Doze.

## 5. Components

### 5.1 `TimerRepository`
Single source of truth.
- Owns persisted state (see §6) via DataStore Preferences.
- Exposes `state: StateFlow<TimerState>`.
- API: `suspend fun startTimer(durationMinutes: Int)`, `suspend fun cancelTimer()`, `fun setRinging(isRinging: Boolean, durationMinutes: Int)`.
- `startTimer` is a no-op if `isRinging` is true.

```kotlin
sealed interface TimerState {
    object Idle : TimerState
    data class Running(val deadlineEpochMs: Long, val durationMinutes: Int) : TimerState
    data class Ringing(val durationMinutes: Int) : TimerState
}
```

### 5.2 `TimerScheduler` (interface, AlarmManager impl)
- `fun schedule(deadlineEpochMs: Long)` → `setExactAndAllowWhileIdle(RTC_WAKEUP, deadline, pendingIntent)`.
- `fun cancel()` → `alarmManager.cancel(pendingIntent)`.
- `PendingIntent` targets `AlarmReceiver` with a constant request code (one timer at a time means no per-timer IDs).

### 5.3 `AlarmReceiver` (manifest `BroadcastReceiver`)
- Internal (no exported intent filter).
- `onReceive`: `context.startForegroundService(Intent(context, AlarmService::class.java).putExtra(EXTRA_DURATION_MINUTES, ...))`.
- Reads `durationMinutes` from persisted state to forward to the service. If persisted state is already cleared (race with cancel), it returns without starting the service.

### 5.4 `AlarmService` (foreground service, `foregroundServiceType="mediaPlayback"`)
- Lifecycle: only runs while the alarm is ringing.
- `onStartCommand`:
  - If already ringing, returns `START_NOT_STICKY` (dedupe).
  - Calls `startForeground(NOTIF_ID, ringingNotification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`.
  - Starts looping `MediaPlayer` on bundled `R.raw.alarm` with `AudioAttributes` `USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`.
  - Starts `Vibrator` with a repeating pattern.
  - Sets `TimerRepository.setRinging(true, durationMinutes)`; clears persisted deadline (alarm consumed it).
  - Posts a delayed stop runnable at 2 minutes via an injected `Handler`.
- Stop paths (Stop intent or 2-min auto-stop):
  - Stop player, cancel vibrator, remove pending runnable, `setRinging(false, ...)`, `stopForeground(true)`, `stopSelf()`.
- Notification: HIGH-importance channel `alarm_ringing`, category `ALARM`, `setFullScreenIntent(...)` to a "show stop UI" deep-link into `MainActivity`. The notification has a Stop action and `setOngoing(true)`.

### 5.5 `BootReceiver` (manifest `BroadcastReceiver`)
- Listens for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` (so it fires before the user unlocks).
- Reads persisted deadline. Behaviour:
  - **Future deadline** → re-schedule via `TimerScheduler` and exit.
  - **Past deadline within 1 hour of now** → clear deadline; `startForegroundService(AlarmService)` immediately so the user still gets notified.
  - **Past deadline older than 1 hour** → clear deadline silently. User has clearly moved on.
  - **Null** → no-op.

### 5.6 Phone UI: `MainActivity` → `TimerScreen` (Compose)
Single screen; two visual states driven by `viewModel.state.collectAsStateWithLifecycle()`.

Idle state: minute wheel picker (NumberPicker-style, 1–180), MRU recents row of up to 5 chips, large Start button.

Running state: large countdown derived from `deadline - now`, "(set: N min)" subtitle, large Cancel button. Display format: `H:MM:SS` if remaining ≥ 1h, otherwise `MM:SS`. A `LaunchedEffect` ticks every 1s to refresh the displayed remaining time (no state writes).

Ringing state: "Timer finished" text, large Stop button. Tapping Stop fires a Stop intent into `AlarmService`.

### 5.7 Android Auto: `TimerCarAppService` → `TimerCarScreen`
- `CarAppService` declared in manifest with category `androidx.car.app.category.IOT`.
- Single `Session` returns a `TimerCarScreen` extending `androidx.car.app.Screen`.
- `onGetTemplate()` switches on the latest `TimerState`:
  - **Idle** → `MessageTemplate` "No timer running. Start one from your phone." No actions.
  - **Running** → `MessageTemplate` with remaining-time headline (same `H:MM:SS` / `MM:SS` rule as phone), "Timer running" subtitle, one Cancel action.
  - **Ringing** → `MessageTemplate` with "Timer finished" headline, one large `STOP` action (high contrast).
- A `Handler` posted in `onStart` invokes `invalidate()` every 1s while attached; cancelled in `onStop`/`onDestroy`. State itself stays in `TimerRepository`; the tick is only a display refresh.
- Cancel/Stop actions delegate into `TimerRepository.cancelTimer()` / `AlarmService` stop intent — identical to the phone paths.

## 6. Persistence (DataStore Preferences)

| Key | Type | Notes |
|---|---|---|
| `timer_deadline_epoch_ms` | `Long?` | null = idle |
| `timer_duration_minutes` | `Int?` | original duration, used for re-use and recents tracking |
| `recent_durations_minutes` | `List<Int>` | MRU, cap 5; seeded `[5, 12, 60]` on first launch |

`recent_durations_minutes` is stored as a comma-separated string in a single Preferences key; parsed and written through a thin codec. (DataStore Preferences does not natively store `List<Int>`.)

Recents update rule (`RecentDurations.update(list, value)`):
1. Remove `value` from `list` if present.
2. Prepend `value`.
3. Truncate to 5.

`isRinging` is **in-memory only** in `TimerRepository`. It is not persisted, because a process death while ringing should resolve to Idle on rebirth (the deadline has already been cleared at ring start).

## 7. Permissions and manifest

Manifest permissions:
- `android.permission.USE_EXACT_ALARM` — granted at install for alarm/timer category apps. Avoids the Android 12+ runtime-permission dance for `SCHEDULE_EXACT_ALARM`. Justified because this app is a user-set countdown timer.
- `android.permission.POST_NOTIFICATIONS` — runtime permission on Android 13+. Requested with a rationale on first launch.
- `android.permission.USE_FULL_SCREEN_INTENT` — restricted to alarm/calling apps on Android 14+. We qualify.
- `android.permission.RECEIVE_BOOT_COMPLETED` — for `BootReceiver`.
- `android.permission.VIBRATE`.
- `android.permission.FOREGROUND_SERVICE`.
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

`AlarmService` declares `android:foregroundServiceType="mediaPlayback"` and passes `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` to `startForeground()` (enforced on Android 14+).

Background-start exemptions used (documented Android behaviour, no special handling needed):
- `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` allow `BootReceiver` to start a foreground service.
- AlarmManager PendingIntent delivery allows `AlarmReceiver` to start a foreground service.

Car App Library manifest:
- `<service android:name=".car.TimerCarAppService">` with `androidx.car.app.CarAppService` intent filter and category `androidx.car.app.category.IOT`.
- `<meta-data android:name="androidx.car.app.minCarApiLevel" android:value="1"/>`.
- `automotive_app_desc.xml` resource declaring `androidx.car.app.appCategory`.

## 8. Data flow

### 8.1 Start
1. User picks `N` minutes on phone, taps Start.
2. `TimerRepository.startTimer(N)`:
   - `deadline = now + N * 60_000`.
   - Atomic DataStore write: `deadline`, `duration`, updated recents (MRU + dedupe, cap 5).
   - `TimerScheduler.schedule(deadline)`.
3. Phone UI and (if attached) Auto re-render to Running.

### 8.2 Tick (display only)
- Phone: `LaunchedEffect(deadline) { while(true) { tick(); delay(1.seconds) } }`. No state writes.
- Auto: `Handler` posts `invalidate()` every 1s while screen attached.

### 8.3 Cancel
1. `TimerRepository.cancelTimer()`:
   - `TimerScheduler.cancel()`.
   - DataStore write: deadline=null, duration=null. (Recents untouched.)
2. UIs re-render to Idle.

### 8.4 Alarm fires
1. `AlarmManager` delivers PendingIntent → `AlarmReceiver.onReceive`.
2. Receiver `startForegroundService(AlarmService)`.
3. `AlarmService.onStartCommand`:
   - `startForeground(...)`.
   - Start looping `MediaPlayer` + `Vibrator`.
   - `TimerRepository.setRinging(true, durationMinutes)`; clear persisted deadline.
   - Schedule 2-minute auto-stop.
4. Stop (user Stop action OR 2-min auto-stop): stop audio, cancel vibrator, `setRinging(false, ...)`, `stopForeground(true)`, `stopSelf()`.

### 8.5 Reboot
1. `BootReceiver.onReceive(BOOT_COMPLETED | LOCKED_BOOT_COMPLETED)`.
2. Read persisted deadline:
   - Future → `TimerScheduler.schedule(deadline)`.
   - Past, ≤ 1h old → clear deadline, `startForegroundService(AlarmService)`.
   - Past, > 1h old → clear deadline silently.
   - Null → no-op.

## 9. Edge cases

- **Start while ringing** — UI hides Start; defensive no-op in `startTimer` if `isRinging`.
- **Cancel/start race with AlarmReceiver** — sub-second window; accepted.
- **AlarmService killed mid-ring** — persisted deadline already cleared at ring start; rebirth resolves to Idle. Accepted.
- **Manual clock change** — `RTC_WAKEUP` is absolute, so backward jumps extend the timer, forward jumps fire it early or immediately. Documented; no special handling.
- **Two AlarmReceiver fires** — `AlarmService` dedupes via `isRinging` check.
- **Auto not installed** — phone-only behaviour unaffected.

## 10. Error handling

| Failure | Handling |
|---|---|
| `setExactAndAllowWhileIdle` throws `SecurityException` | Try/catch; surface a one-time snackbar. No crash. |
| `POST_NOTIFICATIONS` denied | Still schedule. On ring, audio + vibration play; only the heads-up/lockscreen ring screen is suppressed. Banner on idle screen prompts to enable. |
| `MediaPlayer.start()` fails | Log; fall back to vibration-only loop. Notification still posted; Auto Stop screen still shown. |
| `Vibrator` unavailable | No-op silently. |
| BootReceiver fires for a deadline > 1h old | Clear silently; do not ring. |

## 11. Testing strategy

**Unit (JVM, no Robolectric):**
- `TimerRepository`: start updates DataStore + recents; MRU + dedupe semantics with table-driven cases (seed-only list, full-list eviction, duplicate at head); cancel clears deadline only.
- `RecentDurations.update(list, value)`: pure function; exhaustive table.
- Time logic: `remainingMillis(deadline, now)` clamped at zero; `formatRemaining(ms)` (uses `H:MM:SS` ≥ 1h, else `MM:SS`).

**Android instrumentation (`androidTest`):**
- `TimerSchedulerTest`: schedule a 2s timer; assert `AlarmReceiver` runs.
- `BootReceiverTest`: invoke `onReceive` with three persisted states (future, past ≤ 1h, past > 1h, null) and assert correct effect on `TimerScheduler` / `AlarmService` via fake collaborators.
- `AlarmServiceTest`: start → assert Ringing; send Stop intent → assert Idle; assert 2-minute auto-stop runnable fires via injected fake `Handler` / `Clock`.

**Manual smoke (things tests can't verify):**
- Locked phone → alarm fires → full-screen ring screen visible; Stop dismisses.
- Phone connected to Android Auto (or DHU emulator) → countdown ticks → alarm rings → Stop on car screen ends it.
- Reboot with timer running, deadline in future → alarm still fires on time.
- Doze: leave phone idle overnight with a 6h timer → fires on time.

**Dependency seams:** `TimerScheduler`, `Clock`, `Notifier`, `AudioPlayer`, `VibratorWrapper`, and the auto-stop `Handler` are interfaces injected by constructor. No DI framework required.

## 12. Out of scope (future work)

- Multiple concurrent named timers.
- Voice-driven timer start from Android Auto (requires Google Assistant integration).
- User-configurable alarm sound, vibration pattern, and auto-stop duration.
- Repeat/snooze.
- Widgets, tiles, or Wear OS surface.
