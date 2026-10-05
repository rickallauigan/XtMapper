# Roadmap

This community-maintained fork remains based on
[Xtr126/XtMapper](https://github.com/Xtr126/XtMapper). Planned milestones
describe intended work, not confirmed compatibility or release commitments.

## Completed

- **S10e editor compatibility:** small-landscape editor/settings clipping fix
  ([PR #1](https://github.com/rickallauigan/XtMapper/pull/1)), manually tested
  on-device; `keyContainer` mapping coordinates remain unchanged.
- **Documentation baseline:** fork attribution, compatibility status, and S10e
  and Mate 9 device notes.
- **Profile import/export infrastructure:** editable configuration,
  malformed-profile validation, Reset before Save, Save/reopen persistence,
  clipboard Export, Share of current edited text, CAMERA trigger round-trip
  fix, and S10e landscape software-keyboard/IME usability work
  ([PR #3](https://github.com/rickallauigan/XtMapper/pull/3)).

## Current

- Maintain community documentation with clear tested and unvalidated status,
  contribution paths, and reusable profile plans.
- S10e MLBB measurement/profile work: measure real device/game coordinate space,
  document the HUD layout, and develop keyboard/mouse mappings. A finished
  MLBB profile is not yet available or gameplay-validated.

## Next

1. **S10e MLBB profile v0.1:** publish only after measured coordinates,
   documented HUD requirements, and on-device gameplay validation.
2. **Gameplay tuning:** refine mappings from recorded gameplay observations.
3. **Reusable/versioned profile:** package configuration with device/model,
   logical resolution and coordinate assumptions, HUD requirements, tested
   XtMapper version/commit, and test notes. The proposed `profiles/mlbb/`
   device directories are a future structure, not existing finished profiles.
4. **GameSir investigation:** test general controller compatibility, GameSir
   G8 Galileo, and GameSir G8+. Controller support is not yet validated.
5. **Mate 9 work:** validate XtMapper compatibility and input devices, develop
   an MLBB profile, and consider reversible gaming/performance tuning and
   LeaOS evaluation if useful. These are future work; no performance gains
   are promised. See [Mate 9 planning](MATE9.md).
6. **CI/releases later:** establish automated checks and a repeatable fork
   release process after the device/profile workflow is validated.

Broader device testing, maintainability cleanup, and potential upstream
contributions remain future work. No upstream acceptance is implied.
