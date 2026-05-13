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
