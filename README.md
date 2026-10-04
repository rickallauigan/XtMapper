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

## Fork purpose and status

This repository, [rickallauigan/XtMapper](https://github.com/rickallauigan/XtMapper),
is a fork of [Xtr126/XtMapper](https://github.com/Xtr126/XtMapper) and remains
based on upstream XtMapper. Credit for the original application belongs to
Xtr126 and upstream contributors. The upstream resources below are retained
for reference; upstream release links and screenshots do not establish this
fork's compatibility.

This fork focuses on Samsung Galaxy S10e Android gaming, small-landscape
editor usability, and a keyboard/mouse workflow for Mobile Legends: Bang Bang
(MLBB). Future work includes controller and GameSir G8/G8+ testing, followed
by Huawei Mate 9 compatibility.

The S10e is the primary development/test device. XtMapper builds successfully
in our environment, installs and runs on the S10e, and keyboard and mouse are
usable with our setup. [PR #1](https://github.com/rickallauigan/XtMapper/pull/1)
fixed editor/settings clipping on small landscape displays and was manually
tested on-device. Mapping coordinates in `keyContainer` were intentionally
left unchanged.

Controller/GameSir support, Mate 9 compatibility, a finished MLBB preset, and
performance or latency improvements versus upstream are not yet validated.

- [Compatibility status](COMPATIBILITY.md)
- [Galaxy S10e workflow and validation](docs/S10E.md)
- [Huawei Mate 9 planning](docs/MATE9.md)
- [Roadmap](docs/ROADMAP.md)

## About and features
https://xtr126.github.io/XtMapper-docs/guides/about  
[Watch video on YouTube](https://www.youtube.com/watch?v=Slcu43xBV3M)  

 ## Screenshots

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

## Help and support 
To report any bugs to help improve XtMapper please create an issue at https://github.com/Xtr126/XtMapper/issues

To share your thoughts on XtMapper/ ask any questions please create a post at https://github.com/Xtr126/XtMapper/discussions 

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
