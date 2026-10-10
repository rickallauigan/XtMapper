# Samsung S10e MLBB GameSir G8 v0.2

This controller profile extends the physically tested S10e KBM setup. It does
not establish a second HUD coordinate system. `generate-gamesir-profile.py`
reads `mlbb-kbm-v0.1.txt` and generates `mlbb-gamesir-g8-v0.2.txt` by default; regenerate
and run `--check` after changing shared action positions. No game coordinates
are embedded in event handlers.

Historical v0.1 tested build: the controller implementation commit in the current GameSir PR;
APK SHA-256 `743a7b971497cec7f14e7eb3b58704869e54d5ddda33c721bd9780a36698ba87`.

## Hardware and evidence

- Samsung Galaxy S10e SM-G970F / beyond0lte, rooted Android 16 / LineageOS.
- GameSir G8 Galileo, USB. In this observed controller mode Android reports
  `Sony Interactive Entertainment GameSir-G8`, VID/PID `054c:0ce6`.
  This is observed device metadata, not a universal G8 identifier requirement.
- Inspection date: 2026-10-07. MLBB Hero Training, current Dyrroth HUD and
  Revitalize Battle Spell from the KBM calibration.
- Full landscape mapping plane: **2280 × 1080**, global origin **0,0**.
  The left cutout does not offset mappings. Insets remain editor chrome only.
- Right stick measured via capability inspection: RX/RY minimum 0, maximum
  255, current centered value 128. Runtime queries actual device ranges.
- Original KBM physical gameplay results remain recorded in [README.md](README.md).
  Those results validate the reused touch anchors, not the new controller path.

## Control layout

| Physical input | Linux input | MLBB action | Default profile |
| --- | --- | --- | --- |
| Left stick | ABS_X / ABS_Y | Native hero movement | Untouched; no synthetic joystick |
| A | BTN_GAMEPAD | Battle Spell + right-stick aim | KBM F anchor |
| B | BTN_EAST | Regen | KBM G anchor |
| X | BTN_WEST | Recall | KBM B anchor |
| Y | BTN_NORTH | Active equipment / Roam active | Configurable; no measured anchor |
| RB | BTN_TR | Skill 1 + right-stick aim | KBM Q center |
| RT | BTN_TR2 | Skill 2 + right-stick aim | KBM E center |
| LB | BTN_TL | Ultimate + right-stick aim | KBM R center |
| LT | BTN_TL2 | Basic Attack; hold to repeat | KBM left-click anchor, 150 ms |
| Right stick | ABS_RX / ABS_RY | Aim held skill/spell; otherwise camera pan | Absolute aim / camera velocity |
| D-pad up | ABS_HAT0Y = -1 → DPAD_UP | Attack Turret | Configurable; no measured anchor |
| D-pad down | ABS_HAT0Y = +1 → DPAD_DOWN | Attack Minion | Configurable; no measured anchor |
| D-pad left | ABS_HAT0X = -1 → DPAD_LEFT | Shop / secondary utility | Configurable; no measured anchor |
| D-pad right | ABS_HAT0X = +1 → DPAD_RIGHT | Utility | Configurable |
| L3 | BTN_THUMBL | Hero target lock / utility | Configurable; no measured anchor |
| R3 | BTN_THUMBR | Cancel skill / utility | Unbound; reliable cancel gesture pending |
| Select | BTN_SELECT | Scoreboard / utility | Configurable; no measured anchor |
| Start | BTN_START | Native MLBB chat | Unbound; preserves reported native behavior |
| Logo / Mode | BTN_MODE | Android/controller behavior | Unbound |
| M-style button | Separate touchpad BTN_TOUCH / BTN_TOOL_FINGER / BTN_MOUSE | Future overlay selector | Experimental; unbound |

LT also produces ABS_Z, and RT produces ABS_RZ. Only their digital buttons
activate actions, so analog trigger changes cannot double-fire. Revitalize is
not a directional spell. A directional spell such as Flicker needs a separate
physical gameplay check; this session does not change spell/account settings.

Hold a shoulder/trigger, aim with the right stick, then release to cast. A
centered quick press uses the skill's normal touch behavior. The profile uses
KBM's accepted 180 px aim radius, sensitivity 1, radial dead zone 0.15, and
non-inverted Y (Linux down is display down). The user physically accepted smooth, accurate right-stick aiming with these
settings after the controller-discovery pause fix. Most recently held skill owns stick
movement; other held skills retain independent, releasable pointers.

## Import and Device Mapping Manager

Use **XtMapper(Debug)**, package `xtr.keymapper.debug`.

1. Import the configuration into a new **MLBB GameSir G8 S10e v0.2**, or import a
   normal profile ZIP containing that name and this text.
2. In Device & Mapping Manager, create/reuse **GameSir G8 Setup** and select
   the logical GameSir controller. Android may combine controller, sensor,
   and touchpad interfaces into one InputDevice. Persist its stable descriptor,
   never its runtime ID or event path.
3. Bind this group + `com.mobile.legends` to the controller profile.
4. Leave automatic profiling enabled and start the mapping service.
5. Open MLBB Hero Training. The existing **My Gaming Setup** and KBM binding
   remain available when using the keyboard/mouse hardware instead.

The existing manager already supports GAMEPAD logical devices; no second
controller group store or profile-selection system is introduced.

To add a pending utility later, capture its real HUD center and add a normal
`BTN_NORTH x y 0` or `DPAD_UP x y 0` binding through the editor/configuration.
Do not use placeholder coordinates. R3 cannot reliably cancel merely by
lifting an aimed touch: that normally casts. It stays unbound until a verified
cancel target/gesture is available.

## Historical v0.1 physical acceptance procedure

Use Hero Training only. First press **A** near a training target and verify
Basic Attack, repeated presses, and clean release. Then verify B Regen and X
Recall. For RB, RT, LB, and LT individually: quick tap; hold centered; aim in
four cardinal directions and diagonals; vary magnitude; return to center;
release displaced/centered. Confirm cast behavior and no stuck targeting.
Repeat while moving with the native left stick. Check rapid RB → RT and
movement + aiming + A together. Release all controls, reconnect the controller,
then repeat a short smoke test. Check touchscreen use and service restart.
Pending utilities need calibration before testing their intended actions.

## Historical v0.1 physical results

Human Hero Training checks on the installed debug build:

| Control | Result |
| --- | --- |
| A Basic Attack and release | PASS |
| RB Skill 1, smooth right-stick aim, directional release cast | PASS |
| RT Skill 2, smooth right-stick aim, directional release cast | PASS |
| LB Ultimate, smooth right-stick aim, directional release cast | PASS |
| LT Revitalize and clean release | PASS; non-directional spell |
| B Regen and X Recall, clean release | PASS |
| Native movement + RB aim/cast + A attack, clean release | PASS after service restart |
| Service restart + reconnect recovery, movement/RB/A smoke | PASS |
| Physical touchscreen camera drag with service active | PASS |
| Right-stick camera pan and center release | PASS |
| Hold A repeated attack and release | PASS |
| Camera → RB skill aim/cast → camera priority | PASS |
| Native movement + held-A repeat + camera, clean release | PASS |
| Directional Battle Spell | Not tested |
| Optional unbound utility controls | Not tested |

Initial right-stick snapping failed acceptance. Continuous raw axes and
periodic injected pauses identified repeated capability probes as the cause.
Caching capabilities by node identity and using stat-only hotplug polling
removed those pauses; the user then accepted accurate smooth aiming/casting.

## Historical v0.1 status and limits

- **DETECTED:** S10e and G8 controller/touchpad/sensor capabilities over ADB.
- **IMPLEMENTED:** BTN persistence/editor labels/runtime, controller discovery,
  absolute analog aiming, digital triggers, D-pad state and cleanup.
- **BUILT / INSTALLED / PHYSICALLY TESTED:** see the session entry in
  [DEVLOG](../../../docs/DEVLOG.md); compilation/installation are separate
  from physical gameplay acceptance.
- Native left-stick movement was previously reported physically smooth.
  Native left-stick movement alongside RB aiming/casting and A attack passed
  fresh human acceptance for this APK/profile.
- Y, turret/minion targeting, Shop, L3, Select, and cancel-cast have no
  measured anchors in the tested KBM profile and remain unbound.
- Right-stick camera uses the measured KBM camera anchor. Pan while displaced;
  center to release and restore hero following. Held skills take priority.
  Display edges limit a single drag; center and move again to start a new drag.
  Camera pan/center release, held-A repeat/release, and RB skill priority/return
  to camera passed human acceptance.
- Stock MLBB can still react to native controller buttons/axes. XtMapper does
  not grab the controller or suppress native left-stick/system behavior.
  Physical tests must detect any unintended native button side effects.
- One controller owns mapped input at a time; multi-controller gameplay is
  outside v0.1. Other controllers need their own capability/gameplay checks.
- GameSir G8+ is untested. Its transport, descriptors and controller mode may
  differ; no claims about G8+ support follow from this USB G8 inspection.

## Troubleshooting and building

`adb devices -l` must show SM-G970F / beyond0lte. Use `getevent -pl` to inspect
current nodes; never save an event number. `logcat` messages `Controller detected`
and `Controller disconnected` show capability selection and reconnect cleanup.
Motion sensor and touchpad interfaces must not be selected as aiming devices.

Run `./gradlew testDebugUnitTest assembleDebug`, both scripts under
`app/src/test/native/`, and
`python3 profiles/mlbb/samsung-s10e/generate-gamesir-profile.py --check`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.
Install with `adb -s <discovered-S10e-serial> install -r <APK>`.
Root/overlay permissions are required for this tested injection architecture.

## v0.2 installation, rollback and acceptance (#14)

v0.2 changes only LT/A inputs, retaining the measured anchors and all other
bindings. v0.1 and the KBM profile remain unchanged. Generate/check v0.2 with
`python3 profiles/mlbb/samsung-s10e/generate-gamesir-profile.py --check`;
use `--version 0.1 --check` to verify the rollback fixture.

Build with `./gradlew testDebugUnitTest assembleDebug`. Export existing debug
profiles before updating; keep the previous debug APK for binary rollback.
Install `app/build/outputs/apk/debug/app-debug.apk` using
`adb -s <S10e-serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
Import v0.2 as a new profile using configuration Import/Export, save it, and
change **GameSir G8 Setup + com.mobile.legends** to that profile in Device &
Mapping Manager. Keep the KBM group association and existing auto-profile
settings. Restart the service. No additional cutout offset is needed.

For rollback, stop the service, reselect the retained v0.1 profile for the
GameSir group, then restart. If necessary import `mlbb-gamesir-g8-v0.1.txt`
under its original name. For APK rollback, reinstall the saved compatible
debug APK; Android may require `adb install -r -d` for a version downgrade.
Do not uninstall or clear app data; export profiles before any binary rollback.

**Physical testing still required for v0.2.** Historical passes above apply
only to v0.1. In Hero Training on the calibrated HUD, test LT tap/150 ms
hold/release, A Revitalize once, LT+A, native movement + LT + each RB/RT/LB
held aim/release, and skill priority followed by camera restoration. Test
rapid presses, pause, app exit, profile switch, service restart and USB
reconnect for stuck touches. Directional Battle Spell aiming (e.g. Flicker)
requires separate testing; Revitalize cannot validate it.

## SELECT skill-leveling and D-pad calibration (#9)

Generic chord support is available. The current calibration has **no measured
skill-level-up (+), Shop, Quick Buy, Turret, Minion or equipment anchors**.
They remain unbound in v0.2. SELECT is reserved for the leveling layer;
START retains native chat. Intended chords are SELECT+RB/RT/LB for Skill 1/2/3.
D-pad Left/Right are reserved for Shop/Quick Buy, Up/Down for Turret/Minion.

After physically capturing each target center on this exact 2280×1080 HUD,
create `mlbb-extra-anchors.json` alongside the generator. Use a `bindings`
array with `code`, `x`, `y`, `measurement` fields for utility entries, and a
`chords` array with `modifier`, `trigger`, `x`, `y`, `measurement` fields for
leveling entries. `measurement` must record physical capture date/HUD/source.
Allowed utility codes are BTN_NORTH and DPAD_UP/DOWN/LEFT/RIGHT. Allowed leveling
pairs are BTN_SELECT with BTN_TR/TR2/TL. No example numeric targets are supplied.
The generator rejects missing measurement records, nonfinite/out-of-screen
positions, duplicate inputs and unsupported pairs. Extra anchors apply only to
v0.2; v0.1 is kept as the rollback fixture. Regenerate, check and import as a
new calibrated profile; do not overwrite the accepted rollback profile.

D-pad investigation found both missing profile targets and a routing gap:
a first hat event before the hotplug scan did not discover a reconnected
controller. That gap and digital BTN_DPAD aliases have automated coverage.
Physical confirmation is still required: inspect `adb shell su -c 'getevent -ql'`,
press every direction, record ABS_HAT0X/Y signed values and neutral/reversal
transitions, and confirm the controller node is detected. Event paths are
transient diagnostic evidence, never persistent profile identity. If events
arrive from a separate interface, capture its capabilities before extending
ownership rules; do not merge arbitrary devices into the controller.

After calibration, physically test SELECT held before each shoulder: only
level-up should fire. Test shoulder-first, SELECT release-first, rapid repeats,
multiple shoulders, movement + LT attack + leveling, camera/aim coexistence,
pause/reload/stop, SYN loss where practical and USB reconnect. Check no base
skill cast or stuck touch occurs. Automated synthetic targets validate the
runtime; they are not S10e HUD measurements or gameplay acceptance.
