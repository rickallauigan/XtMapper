# Phase 1 gameplay stability: review and deployment checkpoint

Scope: #29, #26 and #14. Work starts from origin/dev `3fc89c6`.
No ADB commands, installation, service restart, profile changes or other device
operations were performed in the original PR #30 implementation session. The current installed
APK, preferred local GameSir profile and nine-profile backup baseline are not
verified or changed by these repository tests.

## #29: established defects versus outstanding cause

`InputService` previously injected ACTION_MOVE with pressure 0 despite keeping
that finger in its active-touch ledger. A held skill/camera contact must retain
pressure until its actual UP. The real MotionEvent regression asserts pressure 1
for every held pointer, including when an attack pointer lifts alongside it.
This is a reproducible correctness defect; it is not proof of the cause of the
user's LT-correlated MLBB movement suppression.

`Input` also passed displayId as the second injectInputEvent argument. That
argument selects asynchronous/wait-for-result/wait-for-finish delivery, not the
display. Display routing remains on the MotionEvent; injection now always uses
async mode 0. The default S10e display was already 0, so this secondary fix
must not be presented as an explanation of the S10e symptom. Injected events
are recycled after submission, preventing repeated attack from retaining native
MotionEvent allocations indefinitely. Shared pointer arrays/ledger access for
hover checks, mouse clicks and scroll injection now use the same Input monitor
as touch injection, preventing concurrent producers from mutating event
buffers during assembly. A controlled concurrent-producer test checks one
valid serialized touch stream while hover checks run.

No speculative repeat-cadence, chord, input-grab or synthetic-left-joystick
workaround was added. Native ABS_X/Y and analog Z/RZ remain uninjected. Integrated
regressions route those raw events through ControllerDeviceMonitor while LT
repeats and each skill holds; they assert no added touch events. This proves
mapper routing, not native movement inside MLBB. Android/game arbitration
between injected touchscreen input and native gamepad motion remains a physical
blocker until a controlled Training comparison establishes the cause.

## Automated coverage

Phase1GameplayTest captures copies of actual MotionEvents from InputService and
Input through the real repeat scheduler and controller router. It checks LT +
RB/RT/LB/A in both press orders, right-stick MOVE, pulse timing, unique local
IDs, valid action indices/pointer sets, stable gesture downTime, pressure,
digital-only triggers, native-axis routing, ordinary hold versus repeat,
release/disconnect/pause/reload/stop cleanup and asynchronous injection mode.
Existing reconnect, chord, camera, editor/import/export, keyboard/mouse and
native tests remain required. See [profile audit](phase1-profile-mappings.md)
for R4/L4's missing measured anchors.

## Scoped tracing in a separately approved debug session

Debug builds support opt-in `debug.xtmapper.input_trace` (or JVM property
`xtmapper.traceInput=true`). Default off; release builds never trace. The flag
is checked at most once per second. `XtMapperInputTrace` logs selected controller
events, logical ownership and MotionEvent pointer IDs/pressure/action/time;
HUD coordinates and unrelated physical touchscreen events are excluded.

Only after explicit authorization to deploy and change diagnostic state, enable
with `adb -s <verified-device> shell setprop debug.xtmapper.input_trace true` and
capture `adb -s <verified-device> logcat -v monotonic -s XtMapperInputTrace`.
If denied, do not change SELinux; use an authorized debug-launch JVM property.
Disable the same property with `false` after capture. No such command was run
in this mission. Traces are evidence, not physical gameplay acceptance.

## Short S10e Hero Training checklist — physically PENDING

After separate deployment approval, export/retain the preferred profile and
nine-profile backup; record current APK, profile, group, HUD and MLBB version.
Do not replace a local v0.3 with committed v0.2.

1. Keep left stick displaced: compare LT released, single tap, LT hold, then
   LT release without re-centering. Record whether movement freezes/restarts.
2. Compare no target versus an attackable target; ordinary held attack versus
   150 ms repeat only in an approved duplicate diagnostic profile. Compare
   physical Basic Attack touch + native left stick.
3. Test LT + RB/RT/LB, ready skills, both press orders, right-stick aim and
   skill release while LT remains held. Record indicator, cast and attack
   response. Compare manual attack + manual skill to separate game mechanics.
4. Confirm A spell once, B/X utilities, camera recovery and clean release;
   separately approve disconnect/reconnect and service/profile lifecycle tests.
5. Measure actual Skill 1/2 (+) centers and test shared R4/R3 and L4/L3 codes
   individually as described in the profile audit.

If raw events continue and MotionEvents are valid while native movement is
suppressed, preserve that trace and report game/input-source arbitration as
outstanding. Never substitute guessed synthetic movement. All three issues
remain open until their physical criteria pass.

## Rollback

The phone is unchanged; no device rollback is currently needed. Preserve the
preferred profile and nine-profile backup, rather than clearing data/restoring
all profiles. Before later approved installation retain the installed APK and
exact export; rollback uses that APK with its existing package/signature and
restores only intentionally changed profiles/bindings. Committed v0.1/v0.2 are
unchanged options, not replacements for local edits. Revert the Phase 1 source
commit normally after review; never reset/force-push dev.

## Repository validation (2026-10-14)

`./gradlew :app:testDebugUnitTest :app:assembleDebug` PASS: 161 tests, no
failures/errors. Python generator suite PASS: 4 tests. Both v0.1/v0.2
generator `--check` commands and native controller/mouse suites PASS.
`git diff --check` PASS. No assertions were removed or suppressed.
APK: `app/build/outputs/apk/debug/app-debug.apk` (built, NOT installed).
Existing toolchain deprecation/native JNI warnings are outside this patch.
Physical acceptance for the Phase 1 changes is PENDING.

## PR CI follow-up

Gameplay validation runs on pull requests targeting dev and manual dispatch. It
checks both rollback generators, the Python suite, native controller/mouse
suites, and Android unit tests/debug build, uploading test reports even when
validation fails. It uses read-only repository permissions and no signing
secrets, release publication or device access. CI success is independent
automated evidence, not a substitute for the pending physical checkpoints.

Post-merge read-only evidence and the reproduced editor anchor defect are
recorded in [anchor follow-up](phase1-anchor-followup.md).
