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

This is a community-maintained, open-source XtMapper fork focused on tested
Android gaming setups, beginning with the Samsung Galaxy S10e and
Mobile Legends: Bang Bang (MLBB). XtMapper maps keyboard and mouse input to
Android game controls.

This repository, [rickallauigan/XtMapper](https://github.com/rickallauigan/XtMapper),
is based on [Xtr126/XtMapper](https://github.com/Xtr126/XtMapper). Credit for the
original application belongs to Xtr126 and upstream contributors. This is a
community fork, not the official upstream project; the original GPLv3
attribution remains intact. Upstream release links and screenshots below are
reference resources and do not establish this fork's compatibility.

| Device | Keyboard / mouse | MLBB profile | GameSir | Status |
| --- | --- | --- | --- | --- |
| Samsung Galaxy S10e | Tested with our setup | In development | Planned; unvalidated | Primary development/test device |
| Huawei Mate 9 | Planned; unvalidated | Planned | Planned; unvalidated | Future gaming-device target |
| Other Android devices | Upstream/device dependent; unvalidated by this fork | Community testing welcome | Unvalidated by this fork | Community testing welcome |

The fork builds, installs, and runs on the S10e in our environment. Tested
keyboard/mouse use does not establish a finished MLBB profile or compatibility
with every peripheral. No performance or latency improvements are claimed.

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

These changes provide the editor and configuration infrastructure for upcoming
profiles; they do not constitute a validated game profile. See
[PR #1](https://github.com/rickallauigan/XtMapper/pull/1) and
[PR #3](https://github.com/rickallauigan/XtMapper/pull/3) for the completed work.

## Mobile Legends: Bang Bang — first targeted game

MLBB is the first targeted game. The S10e profile is in development and is not
finished or published. The upcoming workflow will use real measured device/game
coordinate space, a documented MLBB HUD layout, reusable XtMapper profile
configuration, keyboard/mouse mapping, and on-device gameplay validation.
Coordinates and mappings must be measured and tested before publication.

### Upcoming quick start (planned)

Once an S10e MLBB profile is validated and available, the intended workflow is:

1. Install the tested fork build identified by the profile documentation.
2. Activate the required XtMapper service/root setup for the tested device setup.
3. Import the S10e MLBB profile.
4. Match the documented MLBB HUD layout.
5. Save and test on-device.

This is a future workflow, not a usable profile quick start yet. See the
[S10e notes](docs/S10E.md) and [roadmap](docs/ROADMAP.md) for current status.

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

## Reusable game/device profiles (planned)

A future profile collection could be organized by game and device:

```text
profiles/
  mlbb/
    samsung-s10e/
    huawei-mate9/
```

This structure is planned; no finished profiles are provided yet. Each profile
should eventually include its configuration, device/model, logical resolution
and coordinate assumptions, game HUD requirements, tested XtMapper version or
commit, and test notes. Profiles should be reusable and versioned, with
validation tied to a specific setup.

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
