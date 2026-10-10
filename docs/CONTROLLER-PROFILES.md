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

## Right-stick camera and held-button repeat

`STICK_CAMERA x y sensitivityX sensitivityY deadZone` reuses the Camera
model/marker, scaling and Parcelable transport. It is exclusive with CAMERA.
The controller uses a radial dead zone and integrates stick velocity at roughly
60 Hz (600 profile pixels/second at full deflection and sensitivity 1). Large
scheduler stalls cap at 50 ms. Bounds clamp without periodic drag resets.
Centering releases the drag; holding a skill first releases the camera pointer
and gives skill aim priority. Releasing the last skill resumes camera input
if the stick is still displaced. Logical pointer 58 is separate from skills.
Disconnect, reload, pause and stop release camera touches and cancel ticks.
The marker can be moved/exported in the editor; configuration text edits
sensitivity/dead zone. Mouse camera remains a separate CAMERA configuration.

A fixed KEY_* / BTN_* / DPAD_* line may optionally append a repeat interval:
`BTN_GAMEPAD x y offset intervalMs`. Four-field legacy bindings remain held
touches. The optional interval is 60..60000 ms; a repeated tap holds for up
to 40 ms and waits the rest of the interval. Repeated raw DOWN events do not
start another loop. UP, disconnect, pause, reload and stop cancel pending
callbacks and release the current tap. Editor/import/export/Parcelable preserve
the interval. The S10e GameSir v0.2 LT binding uses 150 ms (A in v0.1).

## Controller chords

`CHORD modifier trigger x y offset` defines a fixed touch using canonical
controller button codes. For example, SELECT + RB uses modifier `BTN_SELECT`
and trigger `BTN_TR`; enter actual measured coordinates through configuration
Import/Export. The editor/overlay displays `SELECT + RB`, supports moving or
removing the anchor, and preserves chord text on save/export. Button identities
are edited in configuration text rather than captured as simultaneous presses.

At most eight chords are accepted. Each trigger has one chord; modifiers and
triggers must differ. Modifiers cannot also have fixed, aim, camera, macro or
movement/swipe bindings, or be chord triggers. Invalid imports fail before
storage changes. X/Y scaling matches fixed keys; offset retains existing fixed
key semantics. Chords are included in Parcelable and ZIP persistence.

Modifier-first claims the trigger on DOWN and suppresses its ordinary action
through UP. Trigger-first retains its ordinary action, even when SELECT is
pressed later. Early modifier release does not change ownership; raw repeats
and duplicate DOWNs do not create another touch. A chord is a held fixed touch,
not an automatically repeated tap. Several chord triggers can coexist with
ordinary attack, aim and camera touches. Pointer IDs start at 59 (or beyond the
fixed-key list for large profiles), separate from aim 50..57 and camera 58.
Disconnect, SYN_DROPPED, node replacement, pause, reload and stop release owned
presses and clear modifiers. The existing injector retains its ten-touch limit.

D-pad hats now initiate controller discovery when they are the first event after
reconnect. Digital `BTN_DPAD_*` aliases canonicalize to `DPAD_*`, including
editor capture. Native keyboard arrow keys remain keyboard codes. On the S10e
G8 baseline the observed D-pad is ABS_HAT0X/Y, and all D-pad utility targets are
unbound: correct input routing alone cannot activate Shop without calibration.
No physical D-pad repair is claimed until the current device stream and HUD
bindings are tested on hardware.
