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

- **S10e MLBB profile v0.1:** measured fullscreen HUD calibration, reusable
  configuration, held-key mouse skill aiming, fixed mouse attacks, camera
  inspection/follow behavior, and user-verified 1v1 Hero Training gameplay.
  See [profile and validation](../profiles/mlbb/samsung-s10e/README.md).

## Current

- Maintain community documentation with clear tested and unvalidated status,
  contribution paths, and reusable profile plans.
- Maintain and broaden the tested S10e MLBB v0.1 HUD/hero coverage.

## Next

1. **Gameplay tuning:** refine mappings from recorded gameplay observations
   and validate additional heroes, HUDs, and camera preferences.
2. **Reusable/versioned profiles:** expand the existing `profiles/mlbb/`
   collection with device/model, measured coordinates, HUD requirements,
   runtime version/commit, and physical gameplay validation.
3. **Broader device testing:** retain stable descriptor-based device grouping
   and validate reconnection behavior across additional hardware.
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
