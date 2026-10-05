# Compatibility

Status applies to this fork's validation, not every device or input accessory.

| Target | Status | Use | Keyboard | Mouse | Controller / GameSir | Small-landscape editor fix |
| --- | --- | --- | --- | --- | --- | --- |
| Samsung Galaxy S10e | Primary / actively tested | Android gaming; MLBB profile in development | Tested | Tested | Planned | Included; manually tested on-device |
| Huawei Mate 9 | Planned | Dedicated Android gaming target | Not yet validated | Not yet validated | Not yet validated | Not yet validated on Mate 9 |
| Other Android devices | Upstream/device dependent; community testing welcome | Depends on device/setup | Not specifically validated | Not specifically validated | Not specifically validated | Not specifically validated |

## Status definitions

- **Tested:** exercised with this fork on the stated device and setup. This
  does not establish compatibility with every peripheral or a finished game
  profile.
- **Planned:** intended investigation or validation; support is not confirmed.
- **Upstream compatibility:** devices may retain compatibility provided by
  [Xtr126/XtMapper](https://github.com/Xtr126/XtMapper), but this fork has not
  specifically validated them.

The S10e build, installation, runtime, keyboard/mouse use, and editor UI fix
are confirmed in our environment.

The completed profile import/export work includes editable configuration,
malformed-profile validation, Reset before Save, Save/reopen persistence,
clipboard Export, Share of current edited configuration text, the CAMERA
trigger serialization round-trip fix, and S10e landscape software-keyboard/IME
usability work. See [PR #3](https://github.com/rickallauigan/XtMapper/pull/3).
This infrastructure does not establish a validated game profile.

The S10e MLBB profile is in development and is not finished or published.
General controller support, GameSir G8 Galileo, and GameSir G8+ remain
unvalidated. Mate 9 compatibility and profiles are future work. No performance
or latency improvements versus upstream are claimed.

See [S10e validation](docs/S10E.md), [Mate 9 planning](docs/MATE9.md), and the
[roadmap](docs/ROADMAP.md).
