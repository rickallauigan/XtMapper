<p align="center">
<a href="#" target="_blank"><img src="https://github.com/Xtr126/XtMapper/assets/80520774/2093a10b-f63f-4687-a4c9-d803f66d4e82" width="300px" height="300px"/></a>
</p>

<h1 align="center">
  XtMapper
</h1>
<p align="center">
  XtMapper, a free and open source keymapper. <br>
  Play your Android games with keyboard and mouse<br>
</p>

<p align="center">
  <a href="https://github.com/Xtr126/XtMapper/releases">
     <img src="https://img.shields.io/github/downloads/Xtr126/XtMapper/total.svg?style=for-the-badge&logo=android" height="30px"/>
  </a>
 </p>

## Community fork and tested status

Community-maintained XtMapper fork focused on tested Android gaming setups.
The first gameplay-validated setup is Samsung Galaxy S10e with Mobile Legends:
Bang Bang (MLBB), using measured keyboard/mouse touch mappings.

This repository, [rickallauigan/XtMapper](https://github.com/rickallauigan/XtMapper),
is based on [Xtr126/XtMapper](https://github.com/Xtr126/XtMapper). Credit for the
original application belongs to Xtr126 and upstream contributors. This is a
community fork, not the official upstream project; the original GPLv3
attribution remains intact. Upstream release links and screenshots below are
reference resources and do not establish this fork's compatibility.

| Device | Keyboard / mouse | MLBB profile | GameSir | Status |
| --- | --- | --- | --- | --- |
| Samsung Galaxy S10e | Evision RGB Keyboard + Lenovo M300 tested | v0.1 tested in Hero Training | Planned; unvalidated | Primary development/test device |
| Huawei Mate 9 | Planned; unvalidated | Planned | Planned; unvalidated | Future gaming-device target |
| Other Android devices | Upstream/device dependent; unvalidated by this fork | Community testing welcome | Unvalidated by this fork | Community testing welcome |

The fork builds, installs, and runs on the S10e in our environment. The MLBB
v0.1 profile was physically validated in 1v1 Hero Training with the
listed peripherals; this does not establish compatibility with every HUD,
hero, device, or peripheral. No performance or latency improvements are claimed.

- [Compatibility status](COMPATIBILITY.md)
- [Galaxy S10e workflow and validation](docs/S10E.md)
- [Huawei Mate 9 planning](docs/MATE9.md)
- [Roadmap](docs/ROADMAP.md)

### Completed foundation

- Small-landscape editor/settings usability work for the Galaxy S10e.
- Editable profile configuration import/export with malformed-profile validation.
- Reset before Save restores the configuration loaded when the editor opened;
  Save persists changes for reopening.
- Export to clipboard and Share using the current edited configuration text.
- CAMERA trigger serialization round-trip fix.
- Landscape software-keyboard/IME usability work for the S10e import/export dialog.

These changes provide the editor and configuration infrastructure used by the
measured S10e MLBB profile. See
[PR #1](https://github.com/rickallauigan/XtMapper/pull/1) and
[PR #3](https://github.com/rickallauigan/XtMapper/pull/3) for the completed work.

## Mobile Legends: Bang Bang — first targeted game

The [S10e profile v0.1](profiles/mlbb/samsung-s10e/README.md) uses measured HUD
anchors in the full **2280×1080** global display plane. In 1v1 Hero Training,
the user verified WASD and diagonals, Q/E/R mouse aiming, F/B/G utilities,
LMB Basic Attack, RMB Skill 2, camera movement, simultaneous inputs, and
physical touchscreen interaction. Results apply to the documented Dyrroth HUD.

### Quick start

1. Install the tested fork build identified in the profile notes; this session
   uses **XtMapper(Debug)** (`xtr.keymapper.debug`) on the rooted S10e.
2. Import [mlbb-kbm-v0.1.txt](profiles/mlbb/samsung-s10e/mlbb-kbm-v0.1.txt)
   through the editor's configuration importer and save as **MLBB KBM S10e v0.1**.
3. In Device & Mapping Manager, bind **My Gaming Setup** (Evision + Lenovo M300)
   and `com.mobile.legends` to that profile. Enable automatic profiling.
4. Start the mapping service and open MLBB. Verify the documented HUD anchors
   in Training before using a different hero or HUD.

WASD moves; Q/E/R cast skills; F/B/G activate Battle Spell/Recall/Regen;
LMB attacks; RMB aims Skill 2. Hold a skill, move the mouse, release to cast.
Mouse pans the camera. While stationary, the view stays for inspection;
while moving, a 250 ms mouse pause resumes hero-follow. Camera travel is limited
by the game's battlefield drag and screen dimensions. See the profile notes
for exact validation and limitations.

## Planned controller investigation

Investigate the **GameSir G8 Galileo** and **GameSir G8+**, including their
behavior with XtMapper and game-specific mappings. Controller support is not
yet validated in this fork, including either GameSir device.

## Huawei Mate 9 — future gaming target

The Huawei Mate 9 is a future dedicated-gaming target. Planned work includes
XtMapper compatibility, an MLBB profile, keyboard/mouse and controller testing,
reversible gaming/performance tuning, and LeaOS evaluation if useful. None of
these areas is complete or validated on the Mate 9, and no performance gains
are promised. See [Mate 9 planning](docs/MATE9.md).

## Reusable game/device profiles

The first profile is [MLBB / Samsung S10e v0.1](profiles/mlbb/samsung-s10e/README.md),
with configuration, hardware, coordinate assumptions, HUD requirements, and
physical gameplay results. Mate 9 profiles remain future work. Generic held
skill aiming and mouse-button syntax are documented in [PROFILE-AIM.md](docs/PROFILE-AIM.md).

## Upstream about and features
https://xtr126.github.io/XtMapper-docs/guides/about  
[Watch video on YouTube](https://www.youtube.com/watch?v=Slcu43xBV3M)  

## Upstream screenshots

<details>

<summary>Click to expand</summary>

|   |   |   |
| ------------- | ------------- | ------------- |
|  <img src="https://raw.githubusercontent.com/Xtr126/XtMapper/refs/heads/dev/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png"/>  |  <img src="https://github.com/user-attachments/assets/d5866a7b-241f-4ab6-9f1f-79538fe116d3"/>  |  <img src="https://github.com/user-attachments/assets/b7bd4346-c22e-485e-85b0-b05659afe183"/>  |
|  <img src="https://github.com/user-attachments/assets/9f8cabe9-9fea-4bf8-b215-d843bb6f15d6"/>  |  <img src="https://github.com/user-attachments/assets/a58848b0-c9a9-4c6f-a0aa-01c051dfb611"/>  |  <img src="https://github.com/user-attachments/assets/d39b871f-2554-4f71-b211-d06372f67ae9"/>  |
<img width="2340" height="992" alt="image" src="https://github.com/user-attachments/assets/b494cd0d-5503-4b7d-8a6f-cb86adefc4e1" />

</details>


## Development

### Build
- Run `./gradlew assembleDebug` or `./gradlew.bat assembleDebug` at the base directory of the project 

## Community contributions and support

Contributions are welcome: bug reports, device compatibility reports,
keyboard/mouse testing, controller testing, game profiles, documentation, and
code contributions.

Report fork-specific bugs or propose work in
[this fork's issue tracker](https://github.com/rickallauigan/XtMapper/issues).
For testing reports, include the device/model, Android version, fork
version/commit, input hardware, setup, steps, and observed results. For profiles,
include coordinate assumptions, HUD requirements, and gameplay test notes;
label untested mappings clearly. Code and documentation contributions can be
submitted as pull requests to this fork.

For upstream XtMapper resources, see the
[upstream documentation](https://xtr126.github.io/XtMapper-docs/guides/about),
[upstream issues](https://github.com/Xtr126/XtMapper/issues), and
[upstream discussions](https://github.com/Xtr126/XtMapper/discussions).
Fork-specific reports should start here rather than in the upstream tracker.

## Using on waydroid
https://xtr126.github.io/XtMapper-docs/waydroid/1-overview/
## Credits
@guobao2333 - [Chinese translation](https://github.com/Xtr126/XtMapper/pull/101)  
@muhammadbahaa2001 - [Arabic translation](https://github.com/Xtr126/XtMapper/pull/106)  
@KSMaan45 - [Punjabi translation](https://github.com/Xtr126/XtMapper/pull/109)  


Help us translate on [Crowdin](https://crowdin.com/project/xtmapper/) or GitHub  


And everyone else not mentioned here who took their time reporting bugs and making suggestions.

Open source libraries used:
- [Starlight](https://github.com/withastro/starlight) - Documentation framework  
- [Material Design Components](https://github.com/material-components/material-components-android) - User interface
- [FloatingActionButtonSpeedDial](https://github.com/leinardi/FloatingActionButtonSpeedDial) - Controls in editor
- [libsu](https://github.com/topjohnwu/libsu) - RootService  
- [Logo](https://github.com/Xtr126/XtMapper/assets/80520774/2093a10b-f63f-4687-a4c9-d803f66d4e82) - Made with [Blender](https://www.blender.org/)


[Some code](./app/src/main/java/com/genymobile/scrcpy) from the [scrcpy](https://github.com/Genymobile/scrcpy) project was used for implementing multi-touch support in the keymapper.  
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
     alt="Get it on IzzyOnDroid"
     height="80">](https://apt.izzysoft.de/fdroid/index/apk/xtr.keymapper)
## Copyright and License
The source code is licensed under the GPL v3.   
```
XtMapper
Copyright (C) 2022 Xtr126

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; version 3.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License 
along with this program. If not, see https://www.gnu.org/licenses/.
```
Do not publish unofficial APKs to the play store. It hurts open source projects like ours.
