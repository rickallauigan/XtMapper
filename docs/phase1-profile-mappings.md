# Phase 1 profile mapping audit (#26 and #14)

Repository evidence: v0.1 uses A for repeated Attack and LT for Battle Spell;
v0.2 swaps those two identities only. v0.2 uses BTN_TL2 for Attack at the
validated KBM Basic Attack anchor, with 150 ms repeat, and BTN_GAMEPAD for
Battle Spell at the validated KBM F anchor. RB/RT/LB skill centers, camera,
Regen and Recall remain unchanged. ABS_Z/ABS_RZ must never activate either
binding; runtime digital-trigger and overlapping-input tests are separate
from this generator audit.

The v0.1/v0.2 rollback fixtures contain no rear leveling targets. The approved
October 11 v0.3 export has now been recovered read-only: its payload is identical
in the original v0.3 ZIP and the verified export ZIP. Source archive and payload
SHA-256 values are recorded in `mlbb-gamesir-g8-v0.3-anchors.json`.
The recovered measured actions are R4/R3 Skill 1 (+) at (1551, 846.5), L4/L3
Skill 2 (+) at (1660.5, 646), and SELECT Ultimate (+) at (1860, 536), all with
zero fixed-mapping offset. Historical archive recovery is not current physical
revalidation. The installed preferred profile may have subsequent local edits;
never overwrite it implicitly. This audit does not modify the phone, its
preferred profile, running service, or nine-profile backup baseline.

R4 mirrors R3 (BTN_THUMBR), and L4 mirrors L3 (BTN_THUMBL) in the documented
G8 mode. The shared code intentionally shares its action; separate rear
button identities are not claimed. Desired actions are R4/R3 Skill 1 level-up
and L4/L3 Skill 2 level-up. Do not use skill-cast centers as level-up centers.

## Measured-anchor generation

The generator accepts `--rear-leveling --output /tmp/mlbb-gamesir-rear-review.txt`
with version 0.2. It requires `mlbb-extra-anchors.json` alongside the generator
containing an `actions` object with `skill1_level_up` and `skill2_level_up`.
Each entry requires numeric `x`, `y` within the global 2280 x 1080 plane and a
nonempty `measurement` recording capture date, HUD and source evidence.
Coordinates must be distinct. R3 and L3 bindings are generated directly from
these labeled actions, avoiding independent copies or a swapped binding table.
Any existing SELECT+RB / SELECT+RT chord must use the same respective measured
position; inconsistent or duplicate skill targets are rejected. Existing
SELECT+LB Ultimate chords and other utilities remain unchanged.

`--version 0.3` now generates the recovered archive-identical fixture from the
labeled action JSON. It preserves v0.2 combat/camera bindings and appends the
three measured upgrades. `--rear-leveling` remains a separate opt-in path for
future approved measurements, and cannot be combined with version 0.3.
The rear-generation option refuses both committed rollback output paths. Normal
v0.1/v0.2 generation is unchanged; Python tests use synthetic positions only.

## Separate approved physical checkpoint

1. Preserve the existing nine-profile backup and export the preferred profile
   before making changes in a separately approved deployment session. Record
   package, profile name/version, active binding, APK commit and HUD screenshot.
2. In Hero Training with unspent skill points, physically tap the center of the
   Skill 1 (+), then Skill 2 (+), capturing raw internal-touchscreen coordinates
   and transforming with the measured rotation/ranges into global 2280 x 1080.
   Record the evidence; no inset offset belongs in the mapping plane.
3. Compare those measured centers with the exported THUMBR/THUMBL entries.
   Verify R4/R3 and L4/L3 emit the shared codes in the current controller mode.
4. If only R4 is wrong, correct only its THUMBR entry using Skill 1's measurement;
   preserve working THUMBL and all unrelated local bindings. The generator is
   a review aid for repository-derived profiles, not a replacement for an
   unknown locally customized preferred profile.
5. After separately approved import/reload, test each rear button once: only
   its intended skill levels, without repeat or held touch. Test SELECT leveling,
   LT+movement+skills, save/reload and reconnect independently. These results
   remain physically pending until actually observed.

Rollback: retain the untouched preferred-profile export and nine-profile backup;
restore only the changed profile/binding through normal import after approval.
Do not clear data, uninstall, restore unrelated profiles wholesale, or replace
an installed local v0.3 with v0.2 implicitly. The committed v0.1/v0.2/v0.3 fixtures
remain available for intentional preset rollback, not automatic deployment.
