# Chimer

[![CI](https://github.com/kimptoc/chimer/actions/workflows/ci.yml/badge.svg)](https://github.com/kimptoc/chimer/actions/workflows/ci.yml)

A simple Android countdown-timer app — chimes when time's up, with an Android Auto cancel surface.

- Set a timer from the phone (minutes; defaults to your most-recent duration, plus a list of the last five used)
- A four-note chime + vibration loops on expiry, auto-stopping after 2 minutes if you don't cancel (15 seconds with a shorter, distinct chime when connected to Android Auto)
- Survives the app being killed and the device being rebooted
- When connected to Android Auto, the running countdown and a big STOP button show on the head unit
- When the timer expires, the car screen shows a prominent red STOP button (no more "unlock screen" UI)

## Tech stack

- Kotlin, Jetpack Compose, AndroidX Car App Library
- `minSdk = 29` (Android 10), `compileSdk = 35`
- AlarmManager (`USE_EXACT_ALARM`) for scheduling, foreground service (`specialUse`) for the ringing playback
- DataStore Preferences for persistence
- No DI framework — single `AppContainer` for wiring
- 25 JVM unit tests covering the recents helper, time formatting, the repository (DataStore via Robolectric), and the ring-profile / hold-to-repeat logic

## Build

You'll need a JDK 17+ — the build uses Android Studio's bundled JBR by default.

```bash
JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:assembleDebug

JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:testDebugUnitTest    # 25 unit tests

JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home \
  ./gradlew :app:installDebug
```

Open in Android Studio normally; everything is Gradle Kotlin DSL.

## Android Auto: known limitations

This app is **not Play-ready** out of the box:

- The `CarAppService` declares `androidx.car.app.category.POI` (IOT is filtered out of the projected launcher by most real head units; POI is the least-wrong Auto-allowed category for a sideloaded timer). This is for sideloaded/development use only; Android Auto category support is host-controlled and not Play-publishable for this timer app without a different product shape.
- `TimerCarAppService.createHostValidator()` returns `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR`. Useful for development; replace with the strict validator before any production release.

For personal/sideloaded use these are fine. To use it in your own car: enable developer mode in the Android Auto app on your phone, then add this app from "Unknown sources".

## Project docs

- `docs/superpowers/specs/2026-05-13-timer-with-auto-design.md` — design spec
- `docs/superpowers/plans/2026-05-13-timer-with-auto-implementation.md` — full implementation plan (20 tasks)
- `docs/MANUAL_SMOKE_TESTS.md` — manual smoke test checklist for phone + Auto

## How this was built

This repo was developed with [Claude Code](https://claude.com/claude-code) using the brainstorming → spec → plan → subagent-driven implementation workflow from the `superpowers` plugin. The full design spec, implementation plan, and an honest per-commit history (including the field-debug session that fixed five real Android 14/16 bugs after the initial implementation passed unit tests) are checked in.

## License

MIT — see [LICENSE](LICENSE).
