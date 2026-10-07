# Controller bindings and absolute stick aiming

Controller support extends existing profile keys and the `AimKey` model. It
uses the existing root getevent stream and multi-touch injector. No fake mouse
movement, controller grabs, or game/device-specific coordinates are used.

## Syntax

```
BTN_GAMEPAD 100 200 0
DPAD_UP 300 400 0
STICK_AIM BTN_TR 500 600 180 1 1 0.15 0
```

Fixed controller keys use the existing `code x y offset` key representation.
`STICK_AIM` fields are: trigger, x, y, maximum radius, X sensitivity, Y
sensitivity, radial dead zone, invert-Y (0/1). Radius and sensitivities must
be positive and finite; dead zone is in [0,1). At most eight total aim
bindings are accepted. Duplicate/conflicting aim triggers are rejected.
Existing `KEY_*`, `AIM_KEY`, mouse, camera and keyboard DPAD lines retain
syntax and behavior. Persistence uses BTN/DPAD codes, never a KEY prefix
prepended to a controller code.

Buttons supported: BTN_GAMEPAD, BTN_EAST, BTN_WEST, BTN_NORTH, BTN_TL, BTN_TR,
BTN_TL2, BTN_TR2, BTN_THUMBL, BTN_THUMBR, BTN_SELECT, BTN_START, BTN_MODE.
D-pad directions: DPAD_UP/DOWN/LEFT/RIGHT. Linux BTN_A/BTN_SOUTH aliases
canonicalize to BTN_GAMEPAD. Editor capture understands raw buttons and
nonzero hat directions. Display labels use A/B/X/Y, LB/RB/LT/RT, L3/R3,
SELECT/START/MODE and D-UP/D-DOWN/D-LEFT/D-RIGHT. Labels do not change binding
identity. Existing aim markers preserve the complete stick configuration
through editor export/import and Parcelable transport.

## Runtime

`ControllerDeviceMonitor` reads capabilities and current ABS_RX/RY values via
read-only native ioctl probes. Candidates need BTN_GAMEPAD plus ABS_X/Y/RX/RY;
BTN_TOUCH and accelerometer devices are rejected. It discovers event nodes
instead of storing paths. A 250 ms monitor detects removal and replaced device
inodes, releases owned touches, and accepts renumbered nodes. Capabilities
(including rejected interfaces) are cached by device/inode identity; polling
uses stat rather than reopening input drivers. Replacement or SYN_DROPPED
invalidates that cache. A first controller button also checks discovery before
routing, so reconnect cannot create an unmatched generic-key touch. The controller
is not EVIOCGRAB'ed, so Android/MLBB retains native input. One controller owns
mapped input; other controllers are not merged into its touch state.

Getevent ABS changes are combined at SYN_REPORT. Stick ranges normalize about
the range midpoint (rounded up for integer unsigned ranges), with separate
negative/positive extents. G8's 0..255 range centers at 128. Existing displaced
stick state is read during discovery, so a trigger press need not wait for a
new stick event. Runtime never assumes 0..255 for another controller.

A radial dead zone removes center noise. Magnitude outside the zone remaps
linearly to [0,1]; directions retain 360-degree coverage. Sensitivity and Y
inversion apply to that vector, then its length clamps to 1. The touch offset
is vector × radius. At a display edge the whole vector shrinks together,
preserving direction rather than skewing it by independently clipping axes. Returning to center moves back
to the skill anchor. Coordinates scale by viewport axes, radius by the smaller
scale; dimensionless stick sensitivities and dead zone do not scale.

On digital button DOWN, the touch starts at the configured center and then
moves to the current vector. Button repeats do not duplicate DOWN. Latest
held trigger owns aiming; releasing each trigger releases its independent
pointer. Controller pointers 50..57 are separate from fixed keys 0..35,
mouse 36..38, DPAD 39..41 and mouse-aim keys 42..49. The existing injector
converts these logical IDs to available Android touch IDs.

Analog ABS_Z/RZ trigger values are intentionally not activation sources.
ABS_X/Y left-stick values never inject movement. Hat transitions release the
previous direction before pressing a replacement, including -1 → +1 without
a neutral sample. Unconfigured buttons inject no touch; Start/Mode therefore
retain native behavior. SYN_DROPPED, device removal, service stop/pause and
profile reload clear held state. Existing focus changes pause mapping and
therefore release controller pointers. Wayland controller input is not
implemented; the controller monitor runs only for Android profiles with
controller bindings.

## Verification

Controller runtime tests exercise normalization, vector math, dead zone,
inversion, radius, button/aim lifecycle, independent pointers, aliases,
Parcelable/scaling, trigger de-duplication, SYN frames, native-left-stick
preservation, hat transitions, removal and reconnect. Native probe tests
exclude touchpad/sensors/invalid ranges and handle removed nodes. Existing
KBM and Device Mapping Manager tests remain enabled.

Physical gameplay results belong to each hardware/game profile's validation
record. Parsing, native capability tests, and compilation are not gameplay
validation.
