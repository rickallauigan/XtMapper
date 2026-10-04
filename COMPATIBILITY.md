# Compatibility

Status applies to this fork's validation, not every device or input accessory.

| Target | Status | Use | Keyboard | Mouse | Controller / GameSir | Small-landscape editor fix |
| --- | --- | --- | --- | --- | --- | --- |
| Samsung Galaxy S10e | Primary / actively tested | MLBB / Android gaming | Tested | Tested | Planned | Included; manually tested on-device |
| Huawei Mate 9 | Planned | Dedicated Android gaming target | Not yet validated | Not yet validated | Not yet validated | Not yet validated on Mate 9 |
| Other Android devices | Upstream compatibility only | Depends on upstream support | Not specifically validated | Not specifically validated | Not specifically validated | Not specifically validated |

## Status definitions

- **Tested:** exercised with this fork on the stated device and setup. This
  does not establish compatibility with every peripheral or a finished game
  profile.
- **Planned:** intended investigation or validation; support is not confirmed.
- **Upstream compatibility:** devices may retain compatibility provided by
  [Xtr126/XtMapper](https://github.com/Xtr126/XtMapper), but this fork has not
  specifically validated them.

The S10e build, installation, runtime, keyboard/mouse use, and editor UI fix
are confirmed in our environment. General controller support and GameSir
G8/G8+ support remain unvalidated. A finished MLBB profile and performance or
latency improvements versus upstream are also unvalidated.

See [S10e validation](docs/S10E.md), [Mate 9 planning](docs/MATE9.md), and the
[roadmap](docs/ROADMAP.md).
