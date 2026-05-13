# TimerWithAuto Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Android app described in `docs/superpowers/specs/2026-05-13-timer-with-auto-design.md` — a single-timer phone app with an Android Auto cancel surface.

**Architecture:** Single Gradle module. `TimerRepository` (DataStore-backed) is the single source of truth, observed by Compose UI and `CarAppService`. `AlarmManager` schedules an exact alarm; `AlarmReceiver` starts a short-lived foreground service (`AlarmService`) on expiry. `BootReceiver` restores state across reboots.

**Tech Stack:** Kotlin 2.0+, Jetpack Compose, Coroutines + Flow, AndroidX DataStore Preferences, AndroidX Car App Library, JUnit4 + Turbine for tests, Android instrumentation tests via AndroidX Test.

---

## Conventions used throughout the plan

- **Package**: `net.kimptoc.timerwithauto`
- **Application ID**: `net.kimptoc.timerwithauto`
- **Git remote / branch**: working directly on `main` (single-dev project per the spec)
- **Test runner**: `./gradlew :app:testDebugUnitTest` for JVM tests, `./gradlew :app:connectedDebugAndroidTest` for instrumented (requires an emulator or device on `adb`)
- **Commit style**: short imperative `<type>: <subject>` (e.g. `feat: add TimerRepository`, `test: cover RecentDurations`)
- **Each task ends with a commit step**. If a step says "Run tests, expect PASS" and they don't pass, stop and fix before committing.

## Conscious deviation from the spec

Spec §11 lists three Android **instrumentation** tests (`TimerSchedulerTest`, `BootReceiverTest`, `AlarmServiceTest`). This plan defers them and relies on the manual smoke checklist (Task 19) for v1, because:
- The behaviours they cover (alarm firing, boot-restore, foreground-service start) are observable manually with high signal.
- Instrumentation tests require a running emulator and tend to be flaky for `AlarmManager` + foreground-service flows across API levels.
- The JVM tests in Tasks 4, 5, and 8 still cover the bulk of the business logic.

If the smoke checklist surfaces regressions in these paths during execution, add the corresponding instrumentation test before fixing the bug (test-then-fix).

---

## Task 1: Create the Android Studio project scaffold

**Files (created by Android Studio):**
- `settings.gradle.kts`, `build.gradle.kts` (root + `:app`)
- `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`
- `gradlew`, `gradlew.bat`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/net/kimptoc/timerwithauto/MainActivity.kt` (boilerplate)
- `app/src/main/res/...` (themes, strings, launcher icons)
- `.gitignore`

- [ ] **Step 1: In Android Studio, File → New → New Project**

Use these exact settings:
- Template: **Empty Activity** (the Compose-based one — confirm "Build configuration language: Kotlin DSL" is selected)
- Name: `TimerWithAuto`
- Package name: `net.kimptoc.timerwithauto`
- Save location: `/Users/kimptoc/AndroidStudioProjects/TimerWithAuto` (use the existing folder; Studio will populate it)
- Language: Kotlin
- Minimum SDK: **API 29 ("Android 10")**

Click Finish. Wait for Gradle sync.

- [ ] **Step 2: Verify it builds**

From the project root:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Resolve any sync issues before proceeding.

- [ ] **Step 3: Commit the scaffold**

```bash
git add -A
git status   # sanity check — should NOT include /app/build, /.gradle, /.idea (gitignore handles these)
git commit -m "chore: scaffold Android Studio project (Empty Compose Activity, minSdk 29)"
```

---

## Task 2: Add project dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version entries to `gradle/libs.versions.toml`**

Open `gradle/libs.versions.toml`. Under the `[versions]` block, **add** these entries (preserve anything Studio already put there):

```toml
datastore = "1.1.1"
carApp = "1.7.0"
lifecycleRuntimeCompose = "2.8.7"
kotlinxCoroutines = "1.9.0"
turbine = "1.2.0"
robolectric = "4.13"
```

Under `[libraries]`, **add**:

```toml
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-car-app = { group = "androidx.car.app", name = "app", version.ref = "carApp" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycleRuntimeCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleRuntimeCompose" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
```

- [ ] **Step 2: Add the dependencies to `app/build.gradle.kts`**

Inside the `dependencies { ... }` block, **add** (preserve existing entries):

```kotlin
implementation(libs.androidx.datastore.preferences)
implementation(libs.androidx.car.app)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)

testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.turbine)
testImplementation(libs.robolectric)
```

In the `android { ... }` block, ensure `testOptions` includes Robolectric:

```kotlin
android {
    // ...existing config...
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}
```

- [ ] **Step 3: Verify it still builds**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add DataStore, Car App, lifecycle-compose, test deps"
```

---

## Task 3: Configure manifest permissions, services, and receivers

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/xml/automotive_app_desc.xml`
- Modify: `app/src/main/res/values/strings.xml` (add app name strings if missing)

- [ ] **Step 1: Replace `app/src/main/AndroidManifest.xml`**

Overwrite the file with this (keep Studio's `android:icon`, `android:roundIcon`, and `android:theme` references — copy them from the existing file into the placeholders below):

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:name=".TimerApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.TimerWithAuto">

        <meta-data
            android:name="com.google.android.gms.car.application"
            android:resource="@xml/automotive_app_desc" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.TimerWithAuto">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".alarm.AlarmService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <service
            android:name=".car.TimerCarAppService"
            android:exported="true"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.car.app.CarAppService" />
                <category android:name="androidx.car.app.category.IOT" />
            </intent-filter>
            <meta-data
                android:name="androidx.car.app.minCarApiLevel"
                android:value="1" />
        </service>

        <receiver
            android:name=".alarm.AlarmReceiver"
            android:exported="false" />

        <receiver
            android:name=".alarm.BootReceiver"
            android:exported="true"
            android:directBootAware="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.LOCKED_BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>

</manifest>
```

- [ ] **Step 2: Create `app/src/main/res/xml/automotive_app_desc.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<automotiveApp>
    <uses name="template" />
</automotiveApp>
```

- [ ] **Step 3: Confirm `app/src/main/res/values/strings.xml` has the app name**

Open the file. If `<string name="app_name">` is not present, add:

```xml
<string name="app_name">Timer With Auto</string>
```

- [ ] **Step 4: Verify the project still builds (it will fail to find the `.TimerApp` class — that's expected for now; we want to confirm the manifest parses)**

```bash
./gradlew :app:assembleDebug
```

Expected: either `BUILD SUCCESSFUL`, or a **class-not-found** error mentioning `TimerApp`/`AlarmService`/`AlarmReceiver`/`BootReceiver`/`TimerCarAppService`. Both are acceptable at this point — manifest parsing has succeeded. A **manifest merger error** is NOT acceptable; fix the XML.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/xml/automotive_app_desc.xml app/src/main/res/values/strings.xml
git commit -m "feat: manifest permissions, services, receivers for timer + Auto"
```

---

## Task 4: Pure formatting helpers + Clock interface (TDD)

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/timer/Clock.kt`
- Create: `app/src/main/java/net/kimptoc/timerwithauto/ui/Format.kt`
- Test: `app/src/test/java/net/kimptoc/timerwithauto/ui/FormatTest.kt`

- [ ] **Step 1: Write the failing tests** at `app/src/test/java/net/kimptoc/timerwithauto/ui/FormatTest.kt`

```kotlin
package net.kimptoc.timerwithauto.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test fun `remainingMillis returns deadline minus now when positive`() {
        assertEquals(10_000L, remainingMillis(deadlineEpochMs = 100_000L, nowEpochMs = 90_000L))
    }

    @Test fun `remainingMillis clamps negative values to zero`() {
        assertEquals(0L, remainingMillis(deadlineEpochMs = 50_000L, nowEpochMs = 90_000L))
    }

    @Test fun `formatRemaining renders MM_SS under one hour`() {
        assertEquals("00:00", formatRemaining(0L))
        assertEquals("00:01", formatRemaining(1_000L))
        assertEquals("00:59", formatRemaining(59_000L))
        assertEquals("01:00", formatRemaining(60_000L))
        assertEquals("12:34", formatRemaining(12 * 60_000L + 34_000L))
        assertEquals("59:59", formatRemaining(59 * 60_000L + 59_000L))
    }

    @Test fun `formatRemaining renders H_MM_SS at or above one hour`() {
        assertEquals("1:00:00", formatRemaining(60 * 60_000L))
        assertEquals("1:23:45", formatRemaining(1 * 3_600_000L + 23 * 60_000L + 45_000L))
        assertEquals("3:00:00", formatRemaining(3 * 3_600_000L))
    }

    @Test fun `formatRemaining rounds up sub-second remainder to avoid showing zero early`() {
        // 12.4 seconds remaining should display 00:13, not 00:12
        assertEquals("00:13", formatRemaining(12_400L))
        assertEquals("00:01", formatRemaining(1L))
    }
}
```

- [ ] **Step 2: Run tests, expect FAIL (unresolved references)**

```bash
./gradlew :app:testDebugUnitTest --tests "net.kimptoc.timerwithauto.ui.FormatTest"
```

Expected: compilation error — `remainingMillis` / `formatRemaining` not found.

- [ ] **Step 3: Implement `app/src/main/java/net/kimptoc/timerwithauto/ui/Format.kt`**

```kotlin
package net.kimptoc.timerwithauto.ui

import kotlin.math.max

/**
 * Returns ms remaining until deadline, clamped at zero.
 */
fun remainingMillis(deadlineEpochMs: Long, nowEpochMs: Long): Long =
    max(0L, deadlineEpochMs - nowEpochMs)

/**
 * Formats remaining time. Rounds up to the next whole second so 12.4s reads "00:13"
 * (avoids showing 00:00 while the alarm hasn't fired yet).
 *
 * Returns `MM:SS` under one hour, `H:MM:SS` at or above one hour.
 */
fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
```

- [ ] **Step 4: Create `app/src/main/java/net/kimptoc/timerwithauto/timer/Clock.kt`**

```kotlin
package net.kimptoc.timerwithauto.timer

/**
 * Indirection over wall-clock time so tests can substitute a fake.
 * Always returns System.currentTimeMillis() in production.
 */
fun interface Clock {
    fun nowEpochMs(): Long

    companion object {
        val System: Clock = Clock { java.lang.System.currentTimeMillis() }
    }
}
```

- [ ] **Step 5: Run tests, expect PASS**

```bash
./gradlew :app:testDebugUnitTest --tests "net.kimptoc.timerwithauto.ui.FormatTest"
```

Expected: `BUILD SUCCESSFUL`, 5 tests passed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/ui/Format.kt \
        app/src/main/java/net/kimptoc/timerwithauto/timer/Clock.kt \
        app/src/test/java/net/kimptoc/timerwithauto/ui/FormatTest.kt
git commit -m "feat: time formatting helpers and Clock seam"
```

---

## Task 5: RecentDurations MRU helper (TDD)

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/timer/RecentDurations.kt`
- Test: `app/src/test/java/net/kimptoc/timerwithauto/timer/RecentDurationsTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package net.kimptoc.timerwithauto.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentDurationsTest {

    @Test fun `update prepends new value`() {
        assertEquals(listOf(7, 5, 12, 60), RecentDurations.update(listOf(5, 12, 60), 7))
    }

    @Test fun `update moves existing value to front (dedupe)`() {
        assertEquals(listOf(12, 5, 60), RecentDurations.update(listOf(5, 12, 60), 12))
    }

    @Test fun `update caps at five entries`() {
        assertEquals(
            listOf(99, 1, 2, 3, 4),
            RecentDurations.update(listOf(1, 2, 3, 4, 5), 99)
        )
    }

    @Test fun `update with value already at head returns equal list`() {
        assertEquals(listOf(5, 12, 60), RecentDurations.update(listOf(5, 12, 60), 5))
    }

    @Test fun `update on empty list returns single-element list`() {
        assertEquals(listOf(10), RecentDurations.update(emptyList(), 10))
    }

    @Test fun `seed returns the documented initial list`() {
        assertEquals(listOf(5, 12, 60), RecentDurations.SEED)
    }

    @Test fun `parse handles empty string as empty list`() {
        assertEquals(emptyList<Int>(), RecentDurations.parse(""))
    }

    @Test fun `parse and format are inverses`() {
        val list = listOf(7, 5, 12, 60)
        assertEquals(list, RecentDurations.parse(RecentDurations.format(list)))
    }

    @Test fun `parse drops malformed tokens`() {
        assertEquals(listOf(5, 12), RecentDurations.parse("5,nope,12,"))
    }
}
```

- [ ] **Step 2: Run tests, expect FAIL**

```bash
./gradlew :app:testDebugUnitTest --tests "net.kimptoc.timerwithauto.timer.RecentDurationsTest"
```

Expected: unresolved reference `RecentDurations`.

- [ ] **Step 3: Implement `app/src/main/java/net/kimptoc/timerwithauto/timer/RecentDurations.kt`**

```kotlin
package net.kimptoc.timerwithauto.timer

object RecentDurations {

    /** First-launch seed values (minutes). */
    val SEED: List<Int> = listOf(5, 12, 60)

    /** Maximum entries kept in the recents list. */
    const val MAX_SIZE: Int = 5

    /**
     * MRU + dedupe: removes [value] from [list] if present, prepends it, caps at [MAX_SIZE].
     */
    fun update(list: List<Int>, value: Int): List<Int> =
        (listOf(value) + list.filter { it != value }).take(MAX_SIZE)

    /** Joins values with commas. Used to persist into a single Preferences string. */
    fun format(list: List<Int>): String = list.joinToString(",")

    /** Parses the comma-joined form, silently dropping malformed tokens. */
    fun parse(text: String): List<Int> =
        if (text.isEmpty()) emptyList()
        else text.split(",").mapNotNull { it.toIntOrNull() }
}
```

- [ ] **Step 4: Run tests, expect PASS (9 tests)**

```bash
./gradlew :app:testDebugUnitTest --tests "net.kimptoc.timerwithauto.timer.RecentDurationsTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/timer/RecentDurations.kt \
        app/src/test/java/net/kimptoc/timerwithauto/timer/RecentDurationsTest.kt
git commit -m "feat: RecentDurations MRU helper with parse/format codec"
```

---

## Task 6: TimerState sealed interface

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/timer/TimerState.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kimptoc.timerwithauto.timer

/**
 * Public state of the timer subsystem. UI and Auto observe this via TimerRepository.state.
 */
sealed interface TimerState {
    /** No timer scheduled, alarm not ringing. */
    data object Idle : TimerState

    /** A future deadline is scheduled. */
    data class Running(val deadlineEpochMs: Long, val durationMinutes: Int) : TimerState

    /** AlarmService is currently playing the alarm. */
    data class Ringing(val durationMinutes: Int) : TimerState
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` (or fails only on references in not-yet-written files — those are OK).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/timer/TimerState.kt
git commit -m "feat: TimerState sealed interface (Idle / Running / Ringing)"
```

---

## Task 7: TimerRepository interface

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/timer/TimerRepository.kt`

This task introduces the interface only. The concrete DataStore-backed implementation is Task 8 (tests-first).

- [ ] **Step 1: Create the file with interface + a small data holder for persisted snapshot**

```kotlin
package net.kimptoc.timerwithauto.timer

import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the running timer.
 *
 * Internally combines:
 *  - Persisted snapshot {deadlineEpochMs, durationMinutes} from DataStore
 *  - In-memory isRinging flag set by AlarmService
 * ... into a single [TimerState] StateFlow observed by UI and Auto.
 */
interface TimerRepository {

    val state: StateFlow<TimerState>

    val recents: StateFlow<List<Int>>

    /** Schedule a new timer. No-op if currently ringing. */
    suspend fun startTimer(durationMinutes: Int)

    /** Clears persisted deadline and cancels the scheduled alarm. */
    suspend fun cancelTimer()

    /**
     * Called by AlarmService when it begins playing / stops playing.
     * On start (isRinging=true), the persisted deadline is also cleared (alarm consumed it).
     */
    suspend fun setRinging(isRinging: Boolean, durationMinutes: Int)

    /**
     * Read the current persisted snapshot synchronously (suspending).
     * Used by AlarmReceiver and BootReceiver which run outside the long-lived flow.
     */
    suspend fun readSnapshot(): PersistedSnapshot

    data class PersistedSnapshot(
        val deadlineEpochMs: Long?,
        val durationMinutes: Int?,
    )
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` (modulo missing impls — fine).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/timer/TimerRepository.kt
git commit -m "feat: TimerRepository interface"
```

---

## Task 8: DataStore-backed TimerRepository implementation (TDD with Robolectric)

**Files:**
- Modify: `app/src/main/java/net/kimptoc/timerwithauto/timer/TimerRepository.kt` (add impl)
- Test: `app/src/test/java/net/kimptoc/timerwithauto/timer/TimerRepositoryTest.kt`

DataStore needs a `Context`. We use Robolectric in JVM tests to provide one, and a unique file name per test to keep them isolated.

- [ ] **Step 1: Write the failing test**

```kotlin
package net.kimptoc.timerwithauto.timer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TimerRepositoryTest {

    private lateinit var context: Context
    private lateinit var fakeScheduler: FakeScheduler
    private lateinit var fakeClock: FakeClock

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeScheduler = FakeScheduler()
        fakeClock = FakeClock(initial = 1_000_000L)
    }

    @After fun tearDown() {
        // Wipe DataStore files between tests
        File(context.filesDir, "datastore").deleteRecursively()
    }

    private fun newRepo(testScope: TestScope): TimerRepositoryImpl =
        TimerRepositoryImpl(
            context = context,
            scheduler = fakeScheduler,
            clock = fakeClock,
            scope = testScope,
            dataStoreName = "test_${System.nanoTime()}",
        )

    @Test fun `state starts Idle and recents seeded`() = runTest(StandardTestDispatcher()) {
        val repo = newRepo(this)
        advanceUntilIdle()
        assertEquals(TimerState.Idle, repo.state.value)
        assertEquals(listOf(5, 12, 60), repo.recents.value)
    }

    @Test fun `startTimer schedules and transitions to Running`() = runTest(StandardTestDispatcher()) {
        val repo = newRepo(this)
        repo.state.test {
            assertEquals(TimerState.Idle, awaitItem())
            repo.startTimer(durationMinutes = 10)
            val running = awaitItem() as TimerState.Running
            assertEquals(1_000_000L + 10 * 60_000L, running.deadlineEpochMs)
            assertEquals(10, running.durationMinutes)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fakeScheduler.scheduleCalls.size)
        assertEquals(1_000_000L + 10 * 60_000L, fakeScheduler.scheduleCalls.first())
    }

    @Test fun `startTimer updates recents MRU`() = runTest(StandardTestDispatcher()) {
        val repo = newRepo(this)
        advanceUntilIdle()
        repo.startTimer(7)
        advanceUntilIdle()
        assertEquals(listOf(7, 5, 12, 60), repo.recents.value)
        repo.startTimer(12)
        advanceUntilIdle()
        assertEquals(listOf(12, 7, 5, 60), repo.recents.value)
    }

    @Test fun `cancelTimer clears deadline and calls scheduler`() = runTest(StandardTestDispatcher()) {
        val repo = newRepo(this)
        repo.startTimer(5)
        advanceUntilIdle()
        repo.cancelTimer()
        advanceUntilIdle()
        assertEquals(TimerState.Idle, repo.state.value)
        assertEquals(1, fakeScheduler.cancelCalls)
        val snap = repo.readSnapshot()
        assertNull(snap.deadlineEpochMs)
        assertNull(snap.durationMinutes)
    }

    @Test fun `setRinging(true) clears persisted deadline and emits Ringing`() = runTest(StandardTestDispatcher()) {
        val repo = newRepo(this)
        repo.startTimer(3)
        advanceUntilIdle()
        repo.setRinging(isRinging = true, durationMinutes = 3)
        advanceUntilIdle()
        assertEquals(TimerState.Ringing(3), repo.state.value)
        assertNull(repo.readSnapshot().deadlineEpochMs)
    }

    @Test fun `setRinging(false) returns to Idle`() = runTest(StandardTestDispatcher()) {
        val repo = newRepo(this)
        repo.setRinging(true, 3)
        advanceUntilIdle()
        repo.setRinging(false, 3)
        advanceUntilIdle()
        assertEquals(TimerState.Idle, repo.state.value)
    }

    @Test fun `startTimer is no-op while Ringing`() = runTest(StandardTestDispatcher()) {
        val repo = newRepo(this)
        repo.setRinging(true, 3)
        advanceUntilIdle()
        repo.startTimer(10)
        advanceUntilIdle()
        // Still ringing, scheduler not called
        assertEquals(TimerState.Ringing(3), repo.state.value)
        assertTrue(fakeScheduler.scheduleCalls.isEmpty())
    }

    // ---------- Fakes ----------

    private class FakeScheduler : TimerScheduler {
        val scheduleCalls = mutableListOf<Long>()
        var cancelCalls = 0
        override fun schedule(deadlineEpochMs: Long) { scheduleCalls += deadlineEpochMs }
        override fun cancel() { cancelCalls++ }
    }

    private class FakeClock(initial: Long) : Clock {
        var nowMs = initial
        override fun nowEpochMs(): Long = nowMs
    }
}
```

- [ ] **Step 2: Run tests, expect FAIL (TimerRepositoryImpl + TimerScheduler not defined)**

```bash
./gradlew :app:testDebugUnitTest --tests "net.kimptoc.timerwithauto.timer.TimerRepositoryTest"
```

Expected: compile error.

- [ ] **Step 3: Add a placeholder `TimerScheduler` interface so the test compiles** in `app/src/main/java/net/kimptoc/timerwithauto/timer/TimerScheduler.kt`

```kotlin
package net.kimptoc.timerwithauto.timer

interface TimerScheduler {
    fun schedule(deadlineEpochMs: Long)
    fun cancel()
}
```

(The AlarmManager-backed implementation comes in Task 10. This interface alone is enough to test the repository.)

- [ ] **Step 4: Replace `TimerRepository.kt` entirely with the interface + implementation**

```kotlin
package net.kimptoc.timerwithauto.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for the running timer.
 *
 * Internally combines:
 *  - Persisted snapshot {deadlineEpochMs, durationMinutes} from DataStore
 *  - In-memory isRinging flag set by AlarmService
 * ... into a single [TimerState] StateFlow observed by UI and Auto.
 */
interface TimerRepository {

    val state: StateFlow<TimerState>

    val recents: StateFlow<List<Int>>

    /** Schedule a new timer. No-op if currently ringing. */
    suspend fun startTimer(durationMinutes: Int)

    /** Clears persisted deadline and cancels the scheduled alarm. */
    suspend fun cancelTimer()

    /**
     * Called by AlarmService when it begins playing / stops playing.
     * On start (isRinging=true), the persisted deadline is also cleared (alarm consumed it).
     */
    suspend fun setRinging(isRinging: Boolean, durationMinutes: Int)

    /**
     * Read the current persisted snapshot synchronously (suspending).
     * Used by AlarmReceiver and BootReceiver which run outside the long-lived flow.
     */
    suspend fun readSnapshot(): PersistedSnapshot

    data class PersistedSnapshot(
        val deadlineEpochMs: Long?,
        val durationMinutes: Int?,
    )
}

class TimerRepositoryImpl(
    context: Context,
    private val scheduler: TimerScheduler,
    private val clock: Clock,
    scope: CoroutineScope,
    dataStoreName: String = "timer_with_auto",
) : TimerRepository {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(dataStoreName) },
    )

    private val isRingingFlow = MutableStateFlow(false)
    private val ringingDurationFlow = MutableStateFlow(0)

    private val persistedFlow = dataStore.data.map { prefs ->
        TimerRepository.PersistedSnapshot(
            deadlineEpochMs = prefs[KEY_DEADLINE],
            durationMinutes = prefs[KEY_DURATION],
        )
    }

    override val state: StateFlow<TimerState> = combine(
        persistedFlow,
        isRingingFlow,
        ringingDurationFlow,
    ) { snap, ringing, ringingMinutes ->
        when {
            ringing -> TimerState.Ringing(ringingMinutes)
            snap.deadlineEpochMs != null && snap.durationMinutes != null ->
                TimerState.Running(snap.deadlineEpochMs, snap.durationMinutes)
            else -> TimerState.Idle
        }
    }.stateIn(scope, SharingStarted.Eagerly, TimerState.Idle)

    override val recents: StateFlow<List<Int>> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_RECENTS]
            if (raw == null) RecentDurations.SEED else RecentDurations.parse(raw)
        }
        .stateIn(scope, SharingStarted.Eagerly, RecentDurations.SEED)

    override suspend fun startTimer(durationMinutes: Int) {
        if (isRingingFlow.value) return
        val deadline = clock.nowEpochMs() + durationMinutes * 60_000L
        dataStore.edit { prefs ->
            prefs[KEY_DEADLINE] = deadline
            prefs[KEY_DURATION] = durationMinutes
            val current = prefs[KEY_RECENTS]?.let { RecentDurations.parse(it) } ?: RecentDurations.SEED
            prefs[KEY_RECENTS] = RecentDurations.format(RecentDurations.update(current, durationMinutes))
        }
        scheduler.schedule(deadline)
    }

    override suspend fun cancelTimer() {
        scheduler.cancel()
        dataStore.edit { prefs ->
            prefs.remove(KEY_DEADLINE)
            prefs.remove(KEY_DURATION)
        }
    }

    override suspend fun setRinging(isRinging: Boolean, durationMinutes: Int) {
        if (isRinging) {
            // Alarm consumed the deadline.
            dataStore.edit { prefs ->
                prefs.remove(KEY_DEADLINE)
                prefs.remove(KEY_DURATION)
            }
            ringingDurationFlow.value = durationMinutes
            isRingingFlow.value = true
        } else {
            isRingingFlow.value = false
        }
    }

    override suspend fun readSnapshot(): TimerRepository.PersistedSnapshot =
        persistedFlow.first()

    companion object {
        private val KEY_DEADLINE = longPreferencesKey("timer_deadline_epoch_ms")
        private val KEY_DURATION = intPreferencesKey("timer_duration_minutes")
        private val KEY_RECENTS = stringPreferencesKey("recent_durations_minutes")
    }
}
```

- [ ] **Step 5: Run tests, expect PASS (7 tests)**

```bash
./gradlew :app:testDebugUnitTest --tests "net.kimptoc.timerwithauto.timer.TimerRepositoryTest"
```

If a test fails on first run because of DataStore file collisions, ensure `tearDown` is wiping the test datastore directory.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/timer/TimerRepository.kt \
        app/src/main/java/net/kimptoc/timerwithauto/timer/TimerScheduler.kt \
        app/src/test/java/net/kimptoc/timerwithauto/timer/TimerRepositoryTest.kt
git commit -m "feat: DataStore-backed TimerRepository with MRU recents"
```

---

## Task 9: AlarmReceiver

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/alarm/AlarmReceiver.kt`

`AlarmReceiver` is thin: it forwards to `AlarmService`. We test it by integration (Task 13). Here we just create the class so subsequent tasks can refer to it.

- [ ] **Step 1: Create the file**

```kotlin
package net.kimptoc.timerwithauto.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking
import net.kimptoc.timerwithauto.TimerApp

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TimerApp
        val snapshot = runBlocking { app.container.timerRepository.readSnapshot() }
        val duration = snapshot.durationMinutes ?: return  // race with cancel — drop
        val svc = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_DURATION_MINUTES, duration)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
```

(`TimerApp` and `AppContainer` are written in Task 16. This won't compile yet — that's expected. We'll fix the build in Task 16.)

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/alarm/AlarmReceiver.kt
git commit -m "feat: AlarmReceiver forwards to AlarmService"
```

---

## Task 10: AlarmManager-backed TimerScheduler

**Files:**
- Modify: `app/src/main/java/net/kimptoc/timerwithauto/timer/TimerScheduler.kt`

- [ ] **Step 1: Replace `TimerScheduler.kt` entirely with the interface + AlarmManager implementation**

```kotlin
package net.kimptoc.timerwithauto.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import net.kimptoc.timerwithauto.alarm.AlarmReceiver

interface TimerScheduler {
    fun schedule(deadlineEpochMs: Long)
    fun cancel()
}

class AlarmManagerScheduler(private val context: Context) : TimerScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val pendingIntent: PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun schedule(deadlineEpochMs: Long) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineEpochMs,
                pendingIntent,
            )
        } catch (e: SecurityException) {
            // USE_EXACT_ALARM is install-granted for alarm-category apps; if the OEM disables it,
            // fall back to an inexact alarm. Better late than silent.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineEpochMs,
                pendingIntent,
            )
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: should succeed (now that `AlarmReceiver` exists from Task 9).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/timer/TimerScheduler.kt
git commit -m "feat: AlarmManager-backed TimerScheduler"
```

---

## Task 11: Notifier (notification channel + builders)

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/alarm/Notifier.kt`
- Modify: `app/src/main/res/values/strings.xml` (add notification channel name + content text)

- [ ] **Step 1: Add strings**

Append to `app/src/main/res/values/strings.xml`:

```xml
<string name="notif_channel_ringing">Timer ringing</string>
<string name="notif_channel_ringing_desc">Notifications shown when a timer expires</string>
<string name="notif_title">Timer finished</string>
<string name="notif_action_stop">Stop</string>
```

- [ ] **Step 2: Create the Notifier**

```kotlin
package net.kimptoc.timerwithauto.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import net.kimptoc.timerwithauto.MainActivity
import net.kimptoc.timerwithauto.R

object Notifier {

    const val CHANNEL_RINGING = "alarm_ringing"
    const val NOTIF_ID_RINGING = 1

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_RINGING)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.notif_channel_ringing),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_ringing_desc)
            setSound(null, null) // we play audio ourselves
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    fun buildRingingNotification(context: Context, stopIntent: PendingIntent): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_channel_ringing))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(contentIntent, true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause,
                       context.getString(R.string.notif_action_stop),
                       stopIntent)
            .build()
    }
}
```

- [ ] **Step 3: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: success.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/alarm/Notifier.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat: Notifier with high-importance ringing channel"
```

---

## Task 12: AudioPlayer + VibratorWrapper

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/alarm/AudioPlayer.kt`
- Create: `app/src/main/java/net/kimptoc/timerwithauto/alarm/VibratorWrapper.kt`
- Add: `app/src/main/res/raw/alarm.ogg` (bundled alarm sound — short loopable file ≤ 30s)

- [ ] **Step 1: Add the alarm sound file**

Place a short loopable `.ogg` file at `app/src/main/res/raw/alarm.ogg`.

If you don't have a file handy, generate a placeholder beep with `ffmpeg`:

```bash
ffmpeg -f lavfi -i "sine=frequency=880:duration=2" -c:a libvorbis app/src/main/res/raw/alarm.ogg
```

(2-second 880Hz sine tone. Replace with a nicer asset later.)

- [ ] **Step 2: Create AudioPlayer**

```kotlin
package net.kimptoc.timerwithauto.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import net.kimptoc.timerwithauto.R

interface AudioPlayer {
    /** Starts looping the alarm sound. Safe to call again — re-starts cleanly. */
    fun startLoop()
    fun stop()
}

class MediaPlayerAudioPlayer(private val context: Context) : AudioPlayer {

    private var player: MediaPlayer? = null

    override fun startLoop() {
        stop()
        player = MediaPlayer.create(context, R.raw.alarm)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            start()
        }
    }

    override fun stop() {
        player?.apply {
            try { if (isPlaying) stop() } catch (_: IllegalStateException) {}
            release()
        }
        player = null
    }
}
```

- [ ] **Step 3: Create VibratorWrapper**

```kotlin
package net.kimptoc.timerwithauto.alarm

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

interface VibratorWrapper {
    fun startLoop()
    fun stop()
}

class SystemVibratorWrapper(context: Context) : VibratorWrapper {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun startLoop() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0L, 500L, 500L)  // 500ms on, 500ms off, repeating
        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    override fun stop() {
        vibrator?.cancel()
    }
}
```

- [ ] **Step 4: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: success.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/alarm/AudioPlayer.kt \
        app/src/main/java/net/kimptoc/timerwithauto/alarm/VibratorWrapper.kt \
        app/src/main/res/raw/alarm.ogg
git commit -m "feat: AudioPlayer + VibratorWrapper for alarm playback"
```

---

## Task 13: AlarmService

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/alarm/AlarmService.kt`

- [ ] **Step 1: Create the service**

```kotlin
package net.kimptoc.timerwithauto.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.TimerApp

class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var isRinging = false

    private lateinit var audio: AudioPlayer
    private lateinit var vibrator: VibratorWrapper

    override fun onCreate() {
        super.onCreate()
        val container = (application as TimerApp).container
        audio = container.audioPlayer
        vibrator = container.vibratorWrapper
        Notifier.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRinging(); return START_NOT_STICKY }
        }

        if (isRinging) return START_NOT_STICKY

        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 0) ?: 0
        isRinging = true

        val stopPi = PendingIntent.getService(
            this,
            STOP_REQ_CODE,
            Intent(this, AlarmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = Notifier.buildRingingNotification(this, stopPi)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifier.NOTIF_ID_RINGING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(Notifier.NOTIF_ID_RINGING, notif)
        }

        scope.launch {
            (application as TimerApp).container.timerRepository
                .setRinging(isRinging = true, durationMinutes = durationMinutes)
        }

        audio.startLoop()
        vibrator.startLoop()

        stopRunnable = Runnable { stopRinging() }
        handler.postDelayed(stopRunnable!!, AUTO_STOP_MS)

        return START_NOT_STICKY
    }

    private fun stopRinging() {
        if (!isRinging) return
        isRinging = false
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        audio.stop()
        vibrator.stop()
        scope.launch {
            (application as TimerApp).container.timerRepository
                .setRinging(isRinging = false, durationMinutes = 0)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val ACTION_STOP = "net.kimptoc.timerwithauto.action.STOP_ALARM"
        const val AUTO_STOP_MS = 2L * 60L * 1000L  // 2 minutes
        private const val STOP_REQ_CODE = 2001
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/alarm/AlarmService.kt
git commit -m "feat: AlarmService foreground service with auto-stop"
```

---

## Task 14: BootReceiver

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/alarm/BootReceiver.kt`

- [ ] **Step 1: Create the receiver**

```kotlin
package net.kimptoc.timerwithauto.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking
import net.kimptoc.timerwithauto.TimerApp

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val container = (context.applicationContext as TimerApp).container
        val snap = runBlocking { container.timerRepository.readSnapshot() }
        val deadline = snap.deadlineEpochMs ?: return
        val duration = snap.durationMinutes ?: return
        val now = container.clock.nowEpochMs()

        when {
            deadline > now -> {
                container.scheduler.schedule(deadline)
            }
            now - deadline <= MISSED_GRACE_MS -> {
                // Recently missed — clear and ring immediately.
                runBlocking { container.timerRepository.cancelTimer() }
                val svc = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_DURATION_MINUTES, duration)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
            }
            else -> {
                // Old enough that the user has moved on.
                runBlocking { container.timerRepository.cancelTimer() }
            }
        }
    }

    companion object {
        private const val MISSED_GRACE_MS = 60L * 60L * 1000L  // 1 hour
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

Expected: success.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/alarm/BootReceiver.kt
git commit -m "feat: BootReceiver restores or fires missed timer after reboot"
```

---

## Task 15: TimerViewModel

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/ui/TimerViewModel.kt`

- [ ] **Step 1: Create the ViewModel**

```kotlin
package net.kimptoc.timerwithauto.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.TimerApp
import net.kimptoc.timerwithauto.alarm.AlarmService
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerState

class TimerViewModel(
    application: Application,
    private val repo: TimerRepository,
) : AndroidViewModel(application) {

    val state: StateFlow<TimerState> = repo.state
    val recents: StateFlow<List<Int>> = repo.recents

    fun start(durationMinutes: Int) {
        viewModelScope.launch { repo.startTimer(durationMinutes) }
    }

    fun cancel() {
        viewModelScope.launch { repo.cancelTimer() }
    }

    fun stopAlarm() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        ctx.startService(intent)
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = (app as TimerApp).container.timerRepository
            return TimerViewModel(app, repo) as T
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :app:compileDebugKotlin
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/ui/TimerViewModel.kt
git commit -m "feat: TimerViewModel exposing repo state and intents"
```

---

## Task 16: TimerApp Application + AppContainer (manual DI)

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/TimerApp.kt`
- Create: `app/src/main/java/net/kimptoc/timerwithauto/di/AppContainer.kt`

- [ ] **Step 1: Create AppContainer**

```kotlin
package net.kimptoc.timerwithauto.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kimptoc.timerwithauto.alarm.AudioPlayer
import net.kimptoc.timerwithauto.alarm.MediaPlayerAudioPlayer
import net.kimptoc.timerwithauto.alarm.SystemVibratorWrapper
import net.kimptoc.timerwithauto.alarm.VibratorWrapper
import net.kimptoc.timerwithauto.timer.AlarmManagerScheduler
import net.kimptoc.timerwithauto.timer.Clock
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerRepositoryImpl
import net.kimptoc.timerwithauto.timer.TimerScheduler

class AppContainer(context: Context) {
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.System
    val scheduler: TimerScheduler = AlarmManagerScheduler(context.applicationContext)
    val timerRepository: TimerRepository = TimerRepositoryImpl(
        context = context.applicationContext,
        scheduler = scheduler,
        clock = clock,
        scope = applicationScope,
    )
    val audioPlayer: AudioPlayer = MediaPlayerAudioPlayer(context.applicationContext)
    val vibratorWrapper: VibratorWrapper = SystemVibratorWrapper(context.applicationContext)
}
```

- [ ] **Step 2: Create TimerApp**

```kotlin
package net.kimptoc.timerwithauto

import android.app.Application
import net.kimptoc.timerwithauto.di.AppContainer

class TimerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

- [ ] **Step 3: Build the whole app**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. This is the first task that should produce a fully linked app — every reference to `TimerApp`, `AppContainer`, `AlarmService`, etc. is now satisfied.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/TimerApp.kt \
        app/src/main/java/net/kimptoc/timerwithauto/di/AppContainer.kt
git commit -m "feat: AppContainer manual DI and TimerApp Application class"
```

---

## Task 17: TimerScreen Compose UI

**Files:**
- Modify: `app/src/main/java/net/kimptoc/timerwithauto/MainActivity.kt`
- Create: `app/src/main/java/net/kimptoc/timerwithauto/ui/TimerScreen.kt`

- [ ] **Step 1: Replace `MainActivity.kt`**

```kotlin
package net.kimptoc.timerwithauto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import net.kimptoc.timerwithauto.ui.TimerScreen
import net.kimptoc.timerwithauto.ui.TimerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TimerViewModel by viewModels {
        TimerViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimerScreen(viewModel = viewModel)
        }
    }
}
```

- [ ] **Step 2: Create `TimerScreen.kt`**

```kotlin
package net.kimptoc.timerwithauto.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import net.kimptoc.timerwithauto.timer.TimerState

@Composable
fun TimerScreen(viewModel: TimerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()

    // POST_NOTIFICATIONS runtime permission on Android 13+
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; alarm still plays even if denied */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is TimerState.Idle -> IdleView(recents = recents, onStart = viewModel::start)
            is TimerState.Running -> RunningView(state = s, onCancel = viewModel::cancel)
            is TimerState.Ringing -> RingingView(onStop = viewModel::stopAlarm)
        }
    }
}

@Composable
private fun IdleView(recents: List<Int>, onStart: (Int) -> Unit) {
    var minutes by remember { mutableStateOf(10) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Timer With Auto", style = MaterialTheme.typography.headlineSmall)

        MinutesPicker(value = minutes, onValueChange = { minutes = it })

        Text("Recent:", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recents) { mins ->
                AssistChip(
                    onClick = { minutes = mins },
                    label = { Text("$mins") },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onStart(minutes) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("START", fontSize = 18.sp)
        }
    }
}

@Composable
private fun RunningView(state: TimerState.Running, onCancel: () -> Unit) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.deadlineEpochMs) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val remainingMs = remainingMillis(state.deadlineEpochMs, nowMs)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(formatRemaining(remainingMs), fontSize = 72.sp)
        Text("(set: ${state.durationMinutes} min)")
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text("CANCEL", fontSize = 18.sp)
        }
    }
}

@Composable
private fun RingingView(onStop: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Timer finished", fontSize = 36.sp)
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth().height(72.dp),
        ) {
            Text("STOP", fontSize = 24.sp)
        }
    }
}

@Composable
private fun MinutesPicker(value: Int, onValueChange: (Int) -> Unit) {
    // Simple Compose stand-in for a wheel: + / - buttons around the value.
    // (A polished wheel picker can replace this later without changing the API.)
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { if (value > 1) onValueChange(value - 1) }) { Text("−") }
        Text("$value min", fontSize = 32.sp)
        Button(onClick = { if (value < 180) onValueChange(value + 1) }) { Text("+") }
    }
}
```

- [ ] **Step 3: Build and install on a device or emulator**

```bash
./gradlew :app:installDebug
```

Expected: `BUILD SUCCESSFUL`, app installs.

- [ ] **Step 4: Manual smoke test**

Launch the app on the device. Confirm:
- Idle screen shows the picker, `5`, `12`, `60` chips, and a START button.
- Tap `12` chip → picker shows 12.
- Tap START → screen switches to a countdown that ticks every second.
- Tap CANCEL → returns to idle. Recents now show `12, 5, 60`.
- Tap a chip, START, wait the full timer (use a short value like 1 min) → screen switches to "Timer finished" + red STOP button. Audio + vibration should be playing.
- Tap STOP → returns to idle.

If anything is broken, fix before continuing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/MainActivity.kt \
        app/src/main/java/net/kimptoc/timerwithauto/ui/TimerScreen.kt
git commit -m "feat: Compose phone UI for idle/running/ringing states"
```

---

## Task 18: Android Auto — TimerCarAppService + TimerCarScreen

**Files:**
- Create: `app/src/main/java/net/kimptoc/timerwithauto/car/TimerCarAppService.kt`
- Create: `app/src/main/java/net/kimptoc/timerwithauto/car/TimerCarScreen.kt`

- [ ] **Step 1: Create TimerCarAppService**

```kotlin
package net.kimptoc.timerwithauto.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.car.app.Screen
import android.content.Intent

class TimerCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session = TimerSession()

    private class TimerSession : Session() {
        override fun onCreateScreen(intent: Intent): Screen = TimerCarScreen(carContext)
    }
}
```

(Note: `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` is convenient for development. For Play release, switch to the strict validator. Out of scope for v1.)

- [ ] **Step 2: Create TimerCarScreen**

```kotlin
package net.kimptoc.timerwithauto.car

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.TimerApp
import net.kimptoc.timerwithauto.alarm.AlarmService
import net.kimptoc.timerwithauto.timer.TimerState
import net.kimptoc.timerwithauto.ui.formatRemaining
import net.kimptoc.timerwithauto.ui.remainingMillis

class TimerCarScreen(carContext: CarContext) : Screen(carContext) {

    private val app = carContext.applicationContext as TimerApp
    private val scope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var collectJob: Job? = null

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1_000L)
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handler.post(tick)
                collectJob = scope.launch {
                    app.container.timerRepository.state.collectLatest { invalidate() }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                handler.removeCallbacks(tick)
                collectJob?.cancel()
                collectJob = null
            }
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val state = app.container.timerRepository.state.value
        return when (state) {
            is TimerState.Idle -> idleTemplate()
            is TimerState.Running -> runningTemplate(state)
            is TimerState.Ringing -> ringingTemplate()
        }
    }

    private fun idleTemplate(): MessageTemplate =
        MessageTemplate.Builder("No timer running.\nStart one from your phone.")
            .setTitle("Timer With Auto")
            .build()

    private fun runningTemplate(state: TimerState.Running): MessageTemplate {
        val remaining = remainingMillis(state.deadlineEpochMs, app.container.clock.nowEpochMs())
        val cancel = Action.Builder()
            .setTitle("Cancel")
            .setOnClickListener {
                scope.launch { app.container.timerRepository.cancelTimer() }
            }
            .build()
        return MessageTemplate.Builder(formatRemaining(remaining))
            .setTitle("Timer running")
            .addAction(cancel)
            .build()
    }

    private fun ringingTemplate(): MessageTemplate {
        val stop = Action.Builder()
            .setTitle("STOP")
            .setBackgroundColor(CarColor.RED)
            .setOnClickListener {
                val intent = Intent(carContext, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_STOP
                }
                carContext.startForegroundService(intent)
            }
            .build()
        return MessageTemplate.Builder("Timer finished")
            .setTitle("Timer With Auto")
            .addAction(stop)
            .build()
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/kimptoc/timerwithauto/car/TimerCarAppService.kt \
        app/src/main/java/net/kimptoc/timerwithauto/car/TimerCarScreen.kt
git commit -m "feat: Android Auto car screen with idle/running/ringing states"
```

---

## Task 19: Manual smoke test checklist

**Files:**
- Create: `docs/MANUAL_SMOKE_TESTS.md`

- [ ] **Step 1: Create the checklist**

```markdown
# Manual smoke tests — TimerWithAuto

Run these on a real device (or emulator + Android Auto Desktop Head Unit / DHU)
before each release.

## Phone — happy paths
- [ ] Launch app from launcher → Idle view shows.
- [ ] Recents on first launch show 5, 12, 60.
- [ ] Tap a chip → picker updates.
- [ ] Pick 1 min, START → Running view, countdown ticks.
- [ ] Wait for expiry → Ringing view, audio loops, phone vibrates, full-screen
      notification visible on lockscreen.
- [ ] Tap STOP → returns to Idle.
- [ ] Start 1 min again → STOP via notification action (not in-app) → Idle.
- [ ] Start 3 min, wait full 2 min after expiry → alarm auto-stops, Idle.

## Phone — recents
- [ ] Start 7 → recents become [7, 5, 12, 60].
- [ ] Start 5 → recents become [5, 7, 12, 60] (5 moved to front).
- [ ] Start 99 → recents become [99, 5, 7, 12, 60].
- [ ] Start 3 → recents become [3, 99, 5, 7, 12] (cap of 5).

## Phone — survival
- [ ] Start 10 min, force-stop app from Settings → wait 10 min → alarm fires.
- [ ] Start 10 min, reboot phone → wait remainder → alarm fires.
- [ ] Start 30 min, leave phone idle overnight (Doze) → alarm fires on time.

## Android Auto — via DHU or real head unit
- [ ] Open Timer With Auto in Auto launcher → Idle template ("Start from phone").
- [ ] Start a 2 min timer from phone → Auto switches to countdown ticking each
      second, Cancel action visible.
- [ ] Tap Cancel on Auto → phone returns to Idle.
- [ ] Start 1 min, wait → Auto switches to "Timer finished" with red STOP.
- [ ] Tap STOP on Auto → audio stops, both surfaces return to Idle.

## Permissions / edge
- [ ] Deny POST_NOTIFICATIONS → start 1 min → audio + vibration still play; no
      visible notification.
- [ ] Toggle airplane mode on, start 1 min, wait → alarm still fires (no network
      dependency).
```

- [ ] **Step 2: Commit**

```bash
git add docs/MANUAL_SMOKE_TESTS.md
git commit -m "docs: manual smoke test checklist for phone + Auto"
```

---

## Task 20 (final): Full-suite test run

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS — `RecentDurationsTest` (9), `FormatTest` (5), `TimerRepositoryTest` (7).

- [ ] **Step 2: Run lint**

```bash
./gradlew :app:lintDebug
```

Expected: no new errors. Warnings about resources/icons from the Studio scaffold are acceptable.

- [ ] **Step 3: Assemble release variant**

```bash
./gradlew :app:assembleRelease
```

Expected: `BUILD SUCCESSFUL`. (If signing config errors, that's expected — we're not configuring release signing in this plan; ignore those for now.)

- [ ] **Step 4: Walk the manual smoke checklist** (`docs/MANUAL_SMOKE_TESTS.md`).

- [ ] **Step 5: Commit (if anything changed)**

If lint or smoke tests surfaced fixes, commit them with descriptive messages.

---

## Out of scope for this plan (per spec §12)

- Multiple concurrent named timers
- Voice-driven start from Auto
- User-configurable alarm sound, vibration pattern, auto-stop duration
- Snooze / repeat
- Widgets / tiles / Wear OS
- Release signing / Play Console upload
