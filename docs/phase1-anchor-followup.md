# Phase 1 post-merge anchor investigation

Baseline: merged PR #30, origin/dev `7a69ce8a317dd7bca68fe7a58be656c4041ec79b`.
Post-merge dev APK CI succeeded; the release job was intentionally skipped.

## Initial read-only S10e evidence (2026-10-14)

USB ADB positively identifies SM-G970F / beyond0lte. Installed gaming package
is `xtr.keymapper.debug.s10etest` (version 2.5.0, updated 2026-10-11), not the
repository's normal `xtr.keymapper.debug`. Its mapper foreground service is
running. No GameSir device is currently present in dumpsys input/getevent.
No APK was installed or replaced, no service stopped/restarted, and no profile,
association, firmware or Android setting was changed during this investigation.

A separate read-only snapshot and installed APK copy are retained under
`/tmp/xtmapper-phase1-completion/`. This snapshot contains ONE saved v0.3 profile;
it does not replace or establish the location/integrity of the user's existing
nine-profile backup. That baseline must remain untouched.

The v0.3 LT line is `BTN_TL2 1981.08 946.58 94.5 150`. Runtime adds offset to
both axes, so it touches **2075.58,1041.08**, rather than the tested KBM attack
anchor **1981.08,946.58**. Rear mappings are distinct in storage:

| Code | Saved x/y | Offset | Effective touch x/y |
| --- | --- | --- | --- |
| BTN_THUMBR (R3/R4) | 1551,846.5 | 94.5 | 1645.5,941 |
| BTN_THUMBL (L3/L4) | 1660.5,646 | 94.5 | 1755,740.5 |

This is not a duplicate raw-code binding. The effective rear targets are near
existing skill-cast centers, consistent with the unintended-cast report, but
geometry alone does not identify the real (+) HUD centers or prove its cause.
Saved top-left values are not newly certified level-up measurements. Do not
silently strip offsets from the installed rear mappings or deploy guessed (+)
positions. The existing measured-only rear generator remains appropriate.

## Reproduced source defect and correction

The legacy floating fixed-key editor loads `key.x/key.y` as marker top-left,
then saves that position with its current pivot as the runtime offset. Opening
and saving a center-based binding (offset 0) therefore adds a marker-radius
shift. Changing marker size can change the shift again. This is reproduced
without MLBB, including the observed 189-pixel marker width / 94.5-pixel pivot.

The fixed-key component now places its marker around the **effective runtime
anchor** (`x+offset,y+offset`), preserves that anchor before layout, and exports
center coordinates with offset 0. It uses independent X/Y pivots, preserves
button identity and repeat interval, and updates position only when dragged.
Legacy offset profiles retain their effective positions on load/save; this fix
does not retrospectively certify or repair an already misplaced HUD target.

Legacy scaling also now scales the complete effective anchor, including its
offset. Previously only x/y scaled, leaving a fixed pixel offset. At the native
profile size behavior is unchanged. The scaling test now asserts the effective
injected position in addition to the serialized component values; no existing
assertion was suppressed to hide a failure. No keyContainer/inset change exists.

New tests cover untouched saves before/after layout, repeated editor cycles,
non-square pivots, drag position, repeat preservation and anisotropic scaling.
The Phase 1 MotionEvent integration additionally checks LT release while native
stick input remains displaced, no phantom repeat, disconnect and node-replaced
reconnect, with both repeating and ordinary attack configurations.

## Initial status by issue (before authorized staging)

- **#29:** profile attack-anchor displacement and editor/scaling defects are
  established. LT-triggered native movement/skill suppression is NOT yet
  reproduced with this candidate. PR #30 pressure/routing fixes are retained.
- **#26:** actual installed rear entries were exported read-only and compared;
  distinct codes exist, but real current Skill 1/2 (+) centers still need physical
  measurement. No active binding was corrected or claimed validated.
- **#14:** v0.2 generator and saved v0.3 LT/A roles, digital trigger routing and
  repeat interval are confirmed. Actual simultaneous gameplay remains pending.

## Staging authorization and rollback

The built repository debug APK has a different existing package identity from
installed s10etest, so side-by-side staging is possible without replacing the
active gaming APK. Installation and pausing the current mapper still require
explicit approval; never run two injecting mappers simultaneously. Preserve
both the original nine-profile backup and the new read-only snapshot/installed
APK copy. Import only a separately named staging copy after approval, preserving
all original profile associations. Correct only verified anchors in that copy.

In Hero Training with G8 connected: movement alone, LT hold, LT release without
centering; LT+RB, LT+RT, LT+LB and right-stick aim/cast; A spell; repeat versus
ordinary hold and physical screen attack. Capture Skill 1 (+) then Skill 2 (+)
through the real touchscreen, measured ABS ranges and rotation; test R4/R3 and
L4/L3 only after correct measured targets exist. Zero unintended Skill 2 casts
is required, including after RT aiming and without upgrade points.

The subsequent staging deployment requires mapper restoration. After the approved
staging session, stop only the staging mapper and restore the previously active
mapper through normal app flow; do not clear data/uninstall/replace the gaming
APK or restore unrelated profiles wholesale. Keep staging associations separate.

## Automated validation

`./gradlew :app:testDebugUnitTest :app:assembleDebug`: PASS, 165 tests with
zero failures/errors. Python: 4 tests PASS; both v0.1/v0.2 fixture checks and
native controller/mouse suites PASS. `git diff --check` PASS.
APK: app/build/outputs/apk/debug/app-debug.apk (not installed).
SHA-256: 3d0cca4dfb67613757e763179793809396835429dcd8ce59a4184c060fbec5a3
This initial result is superseded by the isolated gameplay follow-up below.

## Authorized isolated gameplay follow-up (2026-10-14)

After the initial read-only investigation, the user explicitly authorized installing
`xtr.keymapper.debug` separately, temporarily pausing the original
`xtr.keymapper.debug.s10etest` mapper, and granting staging-only root/overlay
permissions. The original APK, preferred profile, associations and backups remain
unchanged. The original mapper must be restored after staging.

Two existing October 11 export archives contain the same zero-offset v0.3
profile, including dedicated Skill 1/2 upgrade anchors. Their provenance and
coordinates are recorded in `mlbb-gamesir-g8-v0.3-anchors.json`; this is recovered
historical calibration, not newly guessed coordinates.

After enabling automatic profile selection and confirming the staging G8 reader,
the user verified repeated LT attack with continuous native left-stick movement
and clean release. Earlier observations made before the profile reader loaded
are excluded from candidate acceptance.

The user then reproduced an additional failure with LT released: RT aiming toward
Ultimate also cast Ultimate. Reducing the staging stick drag radius from 180 to
80 pixels, without changing any measured action center, removed that failure in
the user's full-direction RT test. The user also confirmed LT + movement + RT,
and LT + movement + RB/LB, with correct casts and no unintended skill activation.
This establishes an effective profile-level mitigation; the exact MLBB internal
hit-testing behavior is not established by Android tests.

Skill-first ordering, Battle Spell, rear upgrade targets and final lifecycle
smoke tests remain pending. The original gaming profile has not been repaired or
replaced. The newly built APK has not yet replaced the staging installation.

The user subsequently verified skill-first LT ordering and directional A/Flicker
with the 80-pixel radius. Rear-button testing initially occurred at hero level 15
with no (+) targets; those results are excluded. After sandbox Reset Hero made
upgrade targets visible, R4 upgraded Skill 2. A live, positively identified G8
raw-event capture showed that the physical R4 emits BTN_THUMBL DOWN/UP,
contrary to the expected R4/R3 pairing. The current controller rear assignment
must be distinguished from profile code bindings; L4 capture is in progress.

L4 also emitted BTN_THUMBL in its isolated raw capture. Thus both current rear
buttons share one identity; XtMapper cannot distinguish their source from those
events. The user authorized changing only R4 to mirror R3, leaving L4/L3 and
all other controller settings untouched. Verification is pending that change.
The recovered v0.3 reference remains unchanged; the separately generated
`mlbb-gamesir-g8-v0.3-tuned.txt` uses the physically validated 80-pixel radius.

R4 reassignment was explicitly authorized and verified as BTN_THUMBR. After
separate sandbox hero resets, the user confirmed R4 upgrades only Skill 1 and
L4 upgrades only Skill 2. Both historical measured (+) coordinates therefore
pass on the current HUD. No original app profile/association was changed.

Final automated validation: 167 Android unit/integration tests, zero failures or
errors; 9 Python tests; v0.1/v0.2/v0.3 reference and tuned fixture checks;
native controller capability and mouse reader suites all PASS. Gradle
`:app:testDebugUnitTest :app:assembleDebug` PASS. Final candidate SHA-256:
`ad4883e538a4368fdddc6b8175e32d12bbe756c5c02353510c8c0667fd3bf17d`.
It was installed only into separate `xtr.keymapper.debug` staging. A final
physical smoke test and original mapper restoration remain pending.

The staging startup also exposed a nullable activity callback crash in overlay
cursor migration. Migration now queues shutdown after returning from the remote
startup callback, safely handles an absent activity/disappearing remote, and has
two focused regression tests. The active gaming APK remains untouched.

Remaining causal limit: the displaced original LT anchor is established, but
no controlled original-offset versus corrected-offset gameplay experiment was
performed. Passing corrected staging does not prove which previous runtime or
profile defect caused every original LT symptom. No native joystick replacement
or game-specific event-handler workaround is introduced.

Final installed-candidate smoke test: user PASS for LT + movement + RT aim/cast,
clean release, no extra Ultimate cast, and physical touchscreen camera drag.
Staging was stopped and the original s10etest root mapper restored. Original
profiles.xml and device_mapping_v1.xml compare byte-for-byte to the initial
snapshot. The original preferred profile retains its existing offset/radius
values; installing/applying fixes there remains a separate authorization step.
No original APK was replaced, no original export/backup deleted. Temporary
phone import/diagnostic files were removed. R4's expressly authorized hardware
assignment is the only intentional change retained in the controller.

After restoration, the user reported the original profile again cast skills from
rear buttons. The user then explicitly authorized leaving the tested staging
setup active. Only the original mapper was stopped; staging's final tested APK,
corrected anchors and 80-pixel radius are now active. Original app data remains
preserved. Rollback: stop staging, then start the original s10etest mapper via
its normal UI; its old profile behavior will return. Never run both mappers.
