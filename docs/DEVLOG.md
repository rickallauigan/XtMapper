# Development log

## 2026-10-07 — GameSir G8 Galileo / S10e MLBB

Continued `feature/gamesir-g8-support` from `6d9ec8f` (merged KBM PR #7).
No root AGENTS.md existed at inspection. Preserved the KBM commits, measured
profile, documentation, Device Mapping Manager and release-app data.

**DETECTED:** wireless ADB discovered SM-G970F / beyond0lte. Current G8 mode
reports `Sony Interactive Entertainment GameSir-G8`, USB VID/PID 054c:0ce6.
Capability inspection confirmed all supplied BTN mappings, both sticks,
analog/digital triggers, hats, and separate touchpad/motion-sensor event
interfaces. Android merges these into one external logical GAMEPAD. RX/RY
range 0..255 with centered reading 128. Persisted identities use descriptors,
not the transient device/event IDs. Native left-stick movement was already
physically reported smooth by the user before this implementation.

**IMPLEMENTED:** controller code/label layer, editor button/hat capture,
fixed BTN mappings, profile parser/validation, Parcelable/scaling and normal
ZIP import/export compatibility. `STICK_AIM` reuses `AimKey` and the existing
multi-touch injector, with direct absolute-vector normalization, radial dead
zone, magnitude, sensitivity, Y inversion and independent held pointers.
Digital LT/RT activate mappings; analog trigger values do not double-fire.
Left stick is never synthesized. Capability-based discovery excludes sensors
and touchpad, discovers current nodes and resets on disconnect/replacement,
SYN_DROPPED, stop/pause or reload. D-pad transitions release old directions
before pressing replacements. Existing keyboard/mouse runtime remains intact.

Created `mlbb-gamesir-g8-v0.1.txt` using a generator that reads the validated
KBM profile. Attack/skills/spell/Regen/Recall share its coordinates. No second
MLBB coordinate parser or event-handler magic numbers were introduced.
Equipment, turret/minion target controls, Shop, target lock, scoreboard and
cancel have no measured anchors in that source; they remain configurable and
unbound. Start native chat, Mode system behavior and experimental M touchpad
behavior are not mapped by default. Right-stick camera refinement is deferred.

**BUILT:** `./gradlew testDebugUnitTest assembleDebug` successful; 69 tests,
zero failures/errors. Real app ZIP export/import, controller editor markers,
labels, hats, vectors, range extrema, dead zone, Y inversion, independent
pointers, digital trigger de-duplication, displaced/centered start/release,
node removal/reconnect and InputService lifecycle regression checks pass.
Native controller capability-probe and existing mouse-reader tests pass.
Generated GameSir profile passes its shared-anchor consistency check.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

**INSTALLED:** final debug APK installed with ADB `install -r`, retaining debug
preferences and unrelated profiles. Original debug shared_prefs/files backed
up to `/tmp/mlbb-gamesir-g8-v01/debug-original.tar`; original KBM session
backup remains `/tmp/mlbb-s10e-kbm-v01/debug-original.tar`.

**AIM DIAGNOSIS:** Initial RB aiming responded and cast, but the user reported
snapping. Raw stick capture showed continuous analog values. Injection tracing
showed periodic pauses during repeated capability probes. Discovery now caches
capabilities by node device/inode identity, uses stat-only removal polling, and
reprobes replaced nodes or SYN_DROPPED. Regression tests cover cached rejected
interfaces and first-button discovery. The rebuilt APK is installed; smoothness
was reported “smoother now” by the user after installation; accurate aiming
and then confirmed accurate smooth aiming and directional cast on RB release. With XtMapper stopped, RB/right stick caused no
native skill, camera or hero response in Training. Temporary aim tracing was
removed from source before this build.

**PHYSICALLY TESTED:** A Basic Attack and clean release passed human Hero
Training verification. RB Skill 1 smooth right-stick aiming and directional
release cast also passed after the discovery fix, as did RT Skill 2 and LB
Ultimate. LT Revitalize activation and clean release passed. The user reaffirmed
LT for Battle Spell and Y reserved for active equipment. B Regen and X Recall
activation and clean release passed. After restarting the service, native
left-stick movement alongside RB aiming/casting and A attack passed with clean
release. G8 disconnect/reconnect recovered automatically with the same stable
descriptor, and movement/RB/A smoke plus physical touchscreen camera dragging
passed. The debug service remains active in Training. The previous native-left-stick and KBM gameplay
results must not be interpreted as validation of the new controller runtime.
Further acceptance should cover rapid multi-skill transitions, all analog
magnitudes and centered/displaced trigger edge cases, directional spells,
and additional heroes. These have unit coverage where applicable, but are not
all physically validated. Pending utility anchors remain unvalidated.

**CONFIGURED:** normal app ZIP import and Device Mapping Manager UI saved
GameSir G8 Setup (Active, 1/1 logical Android interface), bound to MLBB GameSir
G8 S10e v0.1. Auto profiling enabled; foreground debug service active in Hero
Training. Root runtime capability probe selects the gamepad event interface,
not its sensor/touchpad. Installed profile matches repository exactly; every
pre-existing debug profile and the KBM group/binding remain unchanged.

### Follow-up: controller camera and held A

Added STICK_CAMERA using the existing KBM camera anchor and generic Camera
model. Analog velocity panning releases on center; skills take priority and
camera resumes after release. Independent pointer 58 and cancellation on
stop/reload/disconnect are tested. A's optional fixed-binding repeat interval
is 150 ms; duplicate DOWN is ignored and both pulse phases cancel on release.
Legacy four-field bindings and mouse CAMERA retain their behavior.

BUILT: 74 unit tests and debug build pass. INSTALLED: updated debug APK/profile
via normal ZIP import. Human camera pan/center-release acceptance passed after service restart.
Held-A repeat/release and camera → RB aim/cast → camera priority also passed
human Hero Training checks. Combined native movement/camera/repeated-attack smoke and clean release passed.
The tested debug service/profile remain active and configured.
The pre-follow-up debug backup is debug-before-camera.tar in the session /tmp
folder. All changes continue on the existing branch and PR #8.

## 2026-10-10 — MLBB GameSir v0.2 (#14)

Generated `mlbb-gamesir-g8-v0.2.txt` from existing S10e KBM anchors: LT is
Basic Attack with 150 ms repeat; A is Battle Spell with the existing STICK_AIM
parameters. Every other generated binding, v0.1 and KBM are preserved. The
version selector defaults to v0.2; `--version 0.1 --check` verifies rollback.
Updated the real InputService stop/pause/reload fixture to cover LT repeat,
A spell, shoulder skill and mouse aim concurrently. Import/install/rollback
and new physical acceptance steps are in the hardware profile documentation.
Physical v0.2 gameplay testing remains required; historical v0.1 passes do not
validate the new layout. No auto-profile or unrelated UI behavior changed.

## 2026-10-10 — Reusable controller leveling chords and D-pad routing (#9)

Added validated `CHORD modifier trigger x y offset` fixed touches, with
modifier-first precedence, full-press base suppression, duplicate filtering,
independent pointers and disconnect/SYN loss/node replacement/stop cleanup.
Persistence, scaling, editor labels/movable markers and ZIP round trips are
covered. Extended InputService stop/pause/reload coverage with a chord while
LT attack, A spell, shoulder aim and mouse aim are active.

D-pad investigation: current G8 profile has no utility targets. A hat event
before hotplug polling also failed to discover a reconnected controller; hats
now trigger discovery, and BTN_DPAD aliases normalize for runtime/capture.
This routing change still needs physical confirmation on the S10e.

The generator accepts optional physically recorded extra anchors for v0.2,
including SELECT+RB/RT/LB leveling. No actual leveling, Shop, Quick Buy,
Turret, Minion or equipment targets were available, so all remain unbound.
Calibration instructions and physical chord/D-pad acceptance are documented.

Validation of the combined change: `./gradlew testDebugUnitTest assembleDebug`
passed (79 unit tests, debug APK); both native reader/probe scripts passed;
two Python generator tests, v0.2/v0.1 consistency and `git diff --check` passed.
Build used temporary Android SDK API 37.0 and the existing JDK 21. Toolchain
setup did not change project build configuration. Installation and human
Hero Training acceptance have not been performed for these changes.

## 2026-10-14 — Phase 1 gameplay stability (#29, #26, #14)

Branched from current origin/dev 3fc89c6, preserving untracked prior Codex
work. Fixed held MOVE pressure, async injection-mode/display separation,
MotionEvent recycling and shared Input pointer-buffer synchronization. Added
real MotionEvent overlap/lifecycle/concurrent-producer regression tests and
opt-in debug traces. Audited v0.2 LT/A swap and added separately generated,
measured-only rear-leveling support with duplicate/inconsistent-target guards.
No Skill 1/2 level-up measurements or committed v0.3 were available.

VALIDATED: 161 Android tests/build, 4 Python tests, both generator versions
and both native suites pass. No phone commands, installs, service changes or
profile/configuration changes were performed. Gameplay acceptance remains
pending; all three issues remain open. See phase1-gameplay-stability.md and
phase1-profile-mappings.md for controlled diagnostics, checklist and rollback.

## 2026-10-14 — Phase 1 post-merge anchor follow-up

Confirmed merged PR #30 baseline 7a69ce8a and successful post-merge APK CI.
Read-only USB inspection identifies SM-G970F/beyond0lte, active s10etest
package/service and one saved v0.3. A new /tmp snapshot and installed APK copy
preserve that state without overwriting the original nine-profile backup.
No G8 is currently connected; no gameplay test/install/service change occurred.

Reproduced fixed-key editor save drift: center coordinates gain marker pivot
offset. Loaded markers now preserve effective anchors and serialize canonical
centers; legacy offsets scale with effective targets. Saved v0.3 attack and rear
entries contain 94.5 offsets; their actual target displacement is evidence, not
a claimed diagnosis of MLBB native-input suppression or certified (+) positions.

165 Android tests/build pass; 4 Python tests, both rollback checks and native
controller/mouse suites pass. Added no-op/drag/scale editor regressions and
LT-displaced-stick/reconnect MotionEvent regression. See phase1-anchor-followup.md
for pending physical acceptance, protected installed state and staging/rollback.

## 2026-10-14 — Phase 1 isolated gameplay follow-up

Recovered historical v0.3 (+) anchors from matching approved exports; preserved
v0.1/v0.2 and recovered v0.3 references. A separate 80-pixel controller-aim
profile prevents the user-reproduced RT-to-Ultimate unintended cast. Human
confirmed LT + native movement + RB/RT/LB aim/cast in staging, both LT/skill
press orders, directional A/Flicker and distinct rear upgrades. Raw captures
proved both rear buttons initially emitted L3; explicitly authorized R4→R3
programming restored separate upgrades. Original app APK/profile untouched.
Added safe asynchronous cursor migration with two regression tests.
167 Android tests, 9 Python tests, four fixtures and native suites PASS.
See phase1-anchor-followup.md for authorization, evidence and causal limits.

## 2026-10-14 — Phase 2 coordinate capture and controller candidates

Continued merged Phase 1 dev `1aa2f00` on a new Phase 2 branch. Reused the visual
editor plane/pause lease and chord runtime. Added explicit coordinate capture,
visible crosshair/optional bounded trace, precision nudges, named target storage,
clipboard snippets and confirmed saved-target binding. Added visual chord editing
and independently justified legacy chord effective-anchor scaling/marker fixes.
Separate Phase 2 candidates preserve tuned v0.3 or explicitly opt into SELECT
chords. D-pad Up means Quick Buy, but no unmeasured target was added. Read-only
S10e checks verified the existing debug mapper active; no APK installation,
service/profile/device-association or firmware change was performed. Phase 2
physical acceptance remains pending. See coordinate-capture.md.

Phase 2 validation: 187 Android tests (zero failures/errors/skips), 12 Python
tests, six profile/reference generator checks, native controller/mouse suites,
`git diff --check`, and debug assembly PASS. APK SHA-256:
`298e1025fecb9a6447dff16973845863ce983e732df416e9dfbb9b1850eb17c5`.
Landscape capture UI was rendered/inspected locally; device acceptance pending.

## Phase 3 quality baseline (2026-10-14)

Prepared blocking Android lint, runtime Maven inventory/OSV audit, basic redacted source-secret guards, artifact checksums, and manual-only release controls. Removed production signing secret use from debug CI without changing app identities. Fixed platform constant and API-level lint errors; documented conservative pre-29 hardware discovery and individually justified privileged manifest declarations. Existing warnings remain visible. Added contributor/security/release guidance and review checklist. No device access, deployment, release dispatch, or signing-identity change occurred. Privileged shell helper's legacy global changes remain an explicitly documented security limitation requiring a separate platform-reviewed fix.
