# MLBB KBM S10e v0.1

Calibration target: Samsung Galaxy S10e SM-G970F (`beyond0lte`), Android 16
(LineageOS), rooted; Evision RGB Keyboard and Lenovo Multi-function Mouse M300.
Calibration date: 2026-10-05. HUD: Dyrroth, normal landscape HUD, Revitalize
battle spell, captured first in a bots-only Custom match and then 1v1 Hero Training.

The user physically validated the controls below in **1v1 Hero Training**.
Runtime commit tested: [`23a7f70ec062`](https://github.com/rickallauigan/XtMapper/commit/23a7f70ec062e126a86e948fe9eb66f7a8d0c345).
The subsequent documentation commit changes no runtime code.
Validation uses **XtMapper(Debug)** (`xtr.keymapper.debug`), separate from release data.

The mapping plane is the full **2280 × 1080** display with global origin **0,0**.
The approximately 116-pixel left cutout does not offset mapping coordinates.
Every touch anchor comes from physical touchscreen calibration. The internal
`sec_touchscreen` reported 0..4095 on both axes, with landscape rotation 90°:
`displayX = rawY × 2280 / 4096`, `displayY = (4095 − rawX) × 1080 / 4096`.
Rotation and full display bounds were verified through ADB and HUD screenshots.
The joystick's measured usable drag radius is 91.76 px. Mouse sensitivity 1
and skill aim radius 180 px are tuning values physically accepted with Dyrroth.

| Control | Global anchor (x, y) |
| --- | --- |
| Joystick center | 436.41, 871.70 |
| Skill 1 | 1650.44, 965.04 |
| Skill 2 / RMB | 1775.13, 753.31 |
| Ultimate | 1986.09, 648.63 |
| Battle Spell | 1477.88, 982.97 |
| Recall | 1172.29, 970.84 |
| Regen | 1341.50, 955.02 |
| Basic Attack | 1981.08, 946.58 |
| Camera drag start | 1609.80, 312.19 |

The camera anchor was recaptured from a user-confirmed camera drag; it replaces
the initial battlefield tap that did not reliably pan the camera.

| Input | Action |
| --- | --- |
| W / A / S / D | Movement; diagonal combinations supported |
| Q | Skill 1 (AOE) |
| E | Skill 2 (Mobility) |
| R | Ultimate (Burst) |
| F | Battle Spell (Revitalize in calibration HUD) |
| B | Recall |
| G | Regen |
| Left mouse button | Basic Attack |
| Right mouse button | Skill 2 (explicitly selected by user) |
| Mouse | Camera; held skill takes priority |
| Grave/backtick | Toggle camera mode |

Hold Q/E/R or the right mouse button, move the mouse to aim, and release to cast.
E and right mouse are aliases for one touch while both are held. If multiple
independent skills are held, the most recently held trigger owns mouse motion.
Releasing it returns motion to the previous held skill; releasing the last
skill returns to camera mode. Skill behavior depends on the hero and skill.

While stationary, mouse camera panning stays held for inspection. While a D-pad
direction is held, 250 ms without mouse movement releases the drag and lets MLBB
follow the hero. Camera movement clamps at screen edges without resetting during
continuous motion. Grave/backtick toggles camera mode and cursor movement; fixed mouse-button
bindings remain mapped. A profile/focus change, pause, stop, or mouse disconnect releases
mapped camera/skill/button touches.

Import `mlbb-kbm-v0.1.txt` through the editor's configuration Import/Export dialog
into a profile named **MLBB KBM S10e v0.1**, and save. Use XtMapper(Debug), package
`xtr.keymapper.debug`. In Device & Mapping Manager, create/reuse **My Gaming Setup**
with both logical HID devices, then bind this group and `com.mobile.legends` to
this profile. Enable automatic profiling and start the mapping service normally.
The group uses stable InputDevice descriptors; reconnecting may change runtime IDs.
Do not copy event-device paths or runtime IDs into a persistent configuration.

## Gameplay validation

| Check | Status |
| --- | --- |
| W/A/S/D direction, hold, transitions, release | PASS — human verified |
| W+A, W+D, S+A, S+D | PASS — human verified |
| Q/E/R quick press, hold, mouse aim, release | PASS — human verified |
| F/B/G exact control | PASS — human verified |
| LMB click, repeated clicks, movement, after aim/camera | PASS — human verified |
| RMB Skill 2, down/up, repeats, simultaneous movement | PASS — human verified |
| Mouse horizontal/vertical/diagonal camera and sensitivity | PASS — human verified; no unwanted cursor |
| Camera resumes after skill aim | PASS — Q, E, and R individually verified |
| W+Q, W+D+skill, movement+attack, movement+camera | PASS — human verified |
| Rapid Q→E, mouse+LMB after release | PASS — human verified |
| No stuck touches, service stability, physical touchscreen | PASS — human control tests and final APK smoke; active service |
| Automatic selection, Active group 2/2 + 2/2 | PASS — UI/storage checks plus human gameplay after auto-selection |
| Profile and binding survive restart | PASS — force-stop/reopen and hub reconnect; exact profile match |

## Known limitations

- Coordinates reflect the user's current Dyrroth normal HUD with Revitalize.
  HUD changes or differently placed hero controls require recalibration.
- Camera panning uses MLBB battlefield dragging, not minimap navigation. Reach
  is limited by screen travel, with less vertical than horizontal space.
- Movement returns the view to the hero after a short mouse pause; stationary
  inspection holds a camera touch until movement, skill aiming, or mode changes.
- No dedicated cancel-cast binding is included. Skill aiming clamps to 180 px;
  range and sensitivity may need adjustment for other heroes.
- E and RMB share Skill 2. Other secondary target/attack buttons are not mapped.
- Tests cover this rooted Android 16 setup; controllers, other devices/ROMs,
  Ranked/Classic matches, performance, and latency were not validated.

## Implementation and verification

Generic `AIM_KEY`, fixed physical mouse buttons, and optional automatic camera
mode are documented in [PROFILE-AIM.md](../../../docs/PROFILE-AIM.md). Legacy
profiles without the new elements retain their mode settings. The mouse reader
handles reconnects by checking the live event node, rather than storing runtime
IDs, and terminates on EOF/read errors without replaying stale events.

Final validation: **53 Android unit tests passed**, the debug APK built and
installed, native reader regression tests passed, and `git diff --check` passed.
Post-restart/reconnect smoke covered movement + Q aim/release. The final APK
smoke covered mouse movement + repeated LMB attack with clean release.

Run `./gradlew testDebugUnitTest assembleDebug` and
`app/src/test/native/run-mouse-reader-tests.sh` from the repository root.
Unit tests cover syntax/validation, legacy parsing, scaling, Parcelable and
editor import/export, mouse press/release, held aim, camera priority/inspection,
D-pad coexistence, repeats, pause/stop/reload, and mouse disconnect cleanup.
Gameplay results above are the human's observations, independently of tests/logs.

## GameSir controller profile

[GameSir G8 v0.2](GAMESIR-G8.md) reuses this profile’s measured action anchors
through a generation/check script. Controller implementation/build/installation
status and physical gameplay acceptance are tracked separately from the KBM
results above. Unmeasured utility controls remain configurable.
