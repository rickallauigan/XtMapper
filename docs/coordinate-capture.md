# Coordinate Capture and Phase 2 controller mapping

Baseline: `dev` at `1aa2f00` (PRs #30/#31). Repository-side implementation for
#28/#27/#9; Phase 2 has **not been installed or physically tested** on the S10e.
Read-only diagnostics on 2026-10-14 confirmed SM-G970F / beyond0lte and the
existing `xtr.keymapper.debug` mapper/root runtime still active. Development did
not install an APK, stop/restart it, modify profiles, associations or firmware.

## Capture workflow

Open **Tools • Coordinate Capture** on the main screen for standalone capture,
or a profile's details → **Capture Coordinates** for its game and saved plane.
This does not select/activate that profile or modify a binding.

The editor uses its existing mapping pause/release acknowledgement and
`EditorSessionGuard` lease. Start is disabled until the runtime acknowledges that
mapping is paused and all touches are released. Without a remote pause
connection, capture remains disabled rather than guessing that injection is safe.
Grant overlay permission through the provided Android settings action and reopen
capture. The permission-denied fullscreen fallback cannot see a game HUD and is
explicitly labeled; it does not claim live game calibration.

1. In a safe game sandbox, use **Open game behind overlay** when a profile was
   chosen. Match the profile's orientation. Move the palette by its header so it
   does not cover the desired target.
2. Choose **Start Capture**. Tap the center of the visible control, or drag for
   precision. X/Y and the crosshair update live. Enable **Touch movement trace**
   to draw the path; the final point freezes when the finger lifts.
3. Choose **Stop Capture** before naming/editing. Nudge X/Y by ±1 or ±10 pixels;
   **Undo** restores the prior gesture/adjustment, **Reset** clears the selection
   and is itself undoable. Edges clamp to the profile plane.
4. Supply a name and optional description/HUD notes. Choose an input for copied
   snippets; this selection does **not** create a binding. **Save target** stores
   the validated measurement explicitly in the local library.
5. **New target** starts another independent capture. **Saved targets** lets you edit, remove (with confirmation), and copy targets.
   **Cancel / Done** discards unsaved edits and removes the overlay. Earlier
   explicit saves remain saved. Remove targets individually to delete them.

The capture window consumes calibration touches; they do **not** reach the game.
It also consumes touches while stopped: a visible stopped message explains that
closing returns normal game touch interaction. This avoids a palette that
silently records passing gameplay taps. There is no screen capture, root touch
tracer, new event reader, global raw listener, injected preview, network transfer
or background measurement. Traces are bounded (512 points), visible and temporary;
they are not written to the library. Undo history is bounded (64 selections).

Screen-off, orientation configuration changes, loss of focus while capture is
active, removal of a connected controller, overlay permission revocation, runtime
stop/unpause or remote failure close the capture editor. Activity destruction
also disposes the composition/guard and releases the lease. Closing releases the
existing auto-profile editor lease; manual-profile mode uses the existing
resume/reload exit. No new service restart or profile activation engine is added.
A process death removes Android's overlay; no synthetic pointer is created by
capture. Restart/reopen after interruption; unsaved edits are not recovered.

## Coordinates and storage

The capture surface reuses `TargetPlane`. Actual surface pixels are converted
once into the selected profile's full plane. Insets/density affect palette chrome
only; they are not added to mapping coordinates. Standalone capture uses actual
display dimensions. Crosshair/trace positions use the inverse transform.

For tested S10e MLBB: **2280 × 1080**, global origin **0,0**. The landscape
cutout is not an extra 116-pixel mapping offset. No device dimensions or MLBB
positions are hardcoded in capture/runtime code. Orientation mismatch prevents
Start; configuration changes close capture instead of mixing measurements.

`CapturedTargetStore` uses separate `captured_targets_v1` preferences. Records
contain stable UUID, name, description, profile-space X/Y and resolution,
source surface dimensions/orientation, timestamp, optional profile/game identity
and snippet input. Only explicit Save/Remove writes. Each transaction validates
the entire library and compares its original snapshot before committing; stale
writers and corrupt data are rejected without overwriting. Limits: 200 targets,
80-character printable name, 1000-character description, finite in-plane numbers.
Duplicate display names are allowed; duplicate UUIDs are rejected.

Targets are separate from legacy profile ZIP backups. **Those backups do not
include the target library.** Copy measurement records for independent backup;
no measurement-record importer is implemented in this change. Runtime profile
lines remain fully compatible with existing exports and imports.

**Copy X/Y**, **Copy JSON** and **Copy YAML** place text on the system clipboard.
The latter snippets follow #24's `name`, `description`, `input`, `action`,
`isEnabled: true`, `position: {x, y}` shape. They are **not directly importable
profiles** until #24 provides structured import/export. **Copy measurement
record** additionally includes provenance/plane metadata for the generator.
Coordinates/snippets only leave the app when the user explicitly copies them.

## Apply a saved target

Open a profile's **Visual Controller Edit**, choose the input, then **Saved target**.
Only targets with matching game (or no game association) and exact profile-plane
dimensions are enabled. An old/new preview asks **Replace in draft / Cancel**.
A fixed target becomes a zero-offset, nonrepeating fixed touch; an existing
STICK_AIM retains its radius, sensitivity, dead zone and inversion while changing
only the center. The final **Save Profile** validates and rejects a changed,
renamed or deleted profile. Nothing automatically changes device associations.

A uniquely matching named target supplies the readable action label in the
visual editor and profile details (for example Quick Buy). Labels do not change
Linux codes/routing. If the library is removed, ambiguous, or the binding moves,
the generic action label returns. This is measurement-library presentation,
not full per-binding structured metadata persistence from #24.

## S10e D-pad Up Quick Buy (#27)

The authoritative action is **DPAD_UP → Quick Buy**, superseding historical
Turret/Right-Quick-Buy guesses. There is no measured Quick Buy anchor in the
repository, so both new candidates intentionally leave DPAD_UP unbound:

- `profiles/mlbb/samsung-s10e/mlbb-gamesir-g8-phase2.txt`: exact tuned v0.3
  actions, including standalone SELECT → Ultimate (+).
- `mlbb-gamesir-g8-phase2-select-chords.txt`: explicit SELECT-chord variation
  described below. Both reuse all tested KBM/gamepad anchors and 80-pixel radius.

After separately approved deployment, enter Hero Training with a visible
recommended-item Quick Buy control. Open capture from the separately named
Phase 2 profile, move the palette away, Start, tap its actual center, Stop, and
save **Quick Buy** with current HUD/date/accuracy notes and snippet input DPAD_UP.
No capture tap purchases an item. Apply the saved target to DPAD_UP via the
reviewed fixed-touch replacement; Save Profile. If a D-pad binding already
exists, review the old/new mapping and explicitly confirm replacing it.

Alternatively copy its **measurement record** to a local JSON file and run:

```sh
python profiles/mlbb/samsung-s10e/generate-gamesir-profile.py --phase2 \
  --quick-buy-target /path/to/measured-quick-buy.json \
  --output /tmp/mlbb-gamesir-g8-phase2-calibrated.txt
```

The generator requires the real target record's MLBB package, 2280×1080 plane,
positive capture timestamp, notes, enabled DPAD_UP fixed_touch and finite
`position.x/y`. It refuses repeat settings and existing DPAD_UP conflicts. Its
metadata checks do not prove that someone physically measured a supplied file.
Never substitute synthetic unit-test data for calibration. Import the generated
text only through the normal editor/backup workflow as a separately named profile.

The merged runtime canonicalizes ABS_HAT0Y=-1 and BTN_DPAD_UP to DPAD_UP.
Press then release produces DOWN/UP at one fixed point, no repeat scheduler or
purchase macro. Holding emits no further injected DOWN. Up→Down releases Up
before pressing Down. Neutral, duplicates, reconnect and keyboard KEY_UP remain
independent. Down/Left/Right are preserved, including any previously measured
utilities, and the native left stick is unchanged.

## Explicit SELECT chord variation (#9)

**Edit controller chords** in the Visual Controller Editor reads/writes the
existing CHORD grammar and human-readable labels. Choose modifier/trigger and a
saved measured target, confirm Apply, then Save Profile. Existing chord targets
can be selected, retargeted or removed. Invalid/conflicting changes leave the
draft intact. A modifier with an ordinary binding is rejected; explicitly remove
that standalone action first only if this is the variation you want. Undo/Reset
restore the original draft, and Cancel never saves it.

The separate SELECT candidate explicitly removes `BTN_SELECT 1860 536 0` and
adds these measured historical anchors:

| Chord | Target | X/Y |
| --- | --- | --- |
| SELECT + RB | Skill 1 (+) | 1551, 846.5 |
| SELECT + RT | Skill 2 (+) | 1660.5, 646 |
| SELECT + LB | Ultimate (+) | 1860, 536 |

Rear R3/R4 and L3/L4 upgrades remain. LT/A, skills, 80-pixel aiming, camera and
native movement stay as in tuned v0.3. Use this candidate only if you explicitly
prefer SELECT as a modifier; the default candidate preserves standalone SELECT.

Audit confirms the existing engine handles modifier-first precedence,
trigger-first base-action retention, early modifier release, duplicate protection,
independent pointer ownership, disconnect/SYN_DROPPED/reload/pause/stop cleanup
and parser/Parcelable/import/export compatibility. No replacement engine was
written. Audit found legacy nonzero chord offsets were not scaled with their
anchors, and advanced markers showed top-left instead of effective center.
Those paths now use effective-anchor scaling and zero-offset centered exports,
with pre/post-layout/drag regression coverage; zero-offset profiles are unchanged.

## Verification and outstanding physical acceptance

Automated suites cover capture start/stop/trace/cancel/nudge/undo, one-pixel and
scaled transforms, snippets and target storage/conflicts, explicit profile
replacement, UI landscape rendering, interruption/disposal, chord precedence,
legacy offsets, canonical D-pad transitions, generator variants, existing native
input and Phase 1 concurrent LT/movement/skill tests. Local previews are generated
under ignored `app/build/ui-previews/`, not committed as gameplay evidence.

Local validation on 2026-10-14: `./gradlew :app:testDebugUnitTest
:app:assembleDebug` PASS, **187 tests**, zero failures/errors/skips. Python:
**12 tests** PASS; all six generator/reference checks PASS; native controller
and mouse suites PASS; `git diff --check` PASS. Debug APK:
`app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`298e1025fecb9a6447dff16973845863ce983e732df416e9dfbb9b1850eb17c5`.
No APK was installed. Automated UI render inspection covers landscape; actual
S10e overlay/density/permission/rotation behavior still needs physical validation.

**Physical Phase 2 acceptance is pending**. After separately approving installation
and any mapper/profile change, check in Hero Training:

1. Capture Quick Buy center; live X/Y/trace/crosshair align and no capture tap
   purchases/casts. Drag palette, nudge, Undo, Stop/Start and Cancel work.
2. Save/reopen/edit/delete targets; copy snippets; apply DPAD_UP only after the
   old/new review. Other bindings and associations stay intact.
3. D-pad Up press/release buys the intended visible recommended item once;
   holding, rapid presses, reversals and neutral never create a purchase macro
   or stuck touch. This spends only sandbox in-match gold, never account currency.
4. If opting into SELECT chords, each upgrades only its intended skill. Check
   both press orders and early SELECT release, normal skill aiming without SELECT,
   LT + movement + aiming and physical touch.
5. Rotation, screen lock, permission loss, mapper shutdown and controller
   reconnect leave no blocking overlay/stuck pointer. Reopen after interruption.

Keep #28/#27/#9 open until their physical acceptance is established. Compilation
and automated input tests do not prove MLBB behavior.

## Rollback

This repository work did not change the installed Phase 1 setup or its backups.
Keep the existing nine-profile baseline and `/tmp/xtmapper-phase1-completion/`
snapshot untouched. Deployment remains a separate approval step. Export the
current profile before later testing; import candidates under distinct names.
Cancel capture/editor drafts without writing, close their overlays, and select
an untouched tuned v0.3 profile if a Phase 2 candidate is unsuitable. Keep the
original `.s10etest` APK/data as the earlier backup; never run two injecting
mappers. Do not clear data or restore unrelated profiles wholesale.
