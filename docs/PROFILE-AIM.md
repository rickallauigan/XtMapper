# Held mouse aim and fixed mouse buttons

Profile syntax extends the existing configuration importer/exporter. Legacy
profiles without these optional elements retain their original settings.
All coordinates refer to the `SCREENSIZE` global display plane and scale with it.

```
AIM_KEY KEY_Q x y radius xSensitivity ySensitivity
AIM_KEY BTN_RIGHT x y radius xSensitivity ySensitivity
MOUSE_LEFT x y
MOUSE_RIGHT x y
CAMERA x y xSensitivity ySensitivity toggle triggerKey [autoActive]
```

`AIM_KEY` presses a touch at `(x,y)` when the trigger goes down. Relative mouse
motion drags that touch, clamped to `radius` and the display bounds. Release
lifts it at the current aim point. Quick presses work as ordinary button taps.
Radius and sensitivities must be positive and finite. Supported triggers are
`KEY_*`, `BTN_RIGHT`, and `BTN_MOUSE` (physical left mouse button). Up to eight
unique triggers are accepted. Duplicate triggers and simultaneous fixed/aim
mappings of one trigger, plus conflicting D-pad/swipe/camera/macro triggers, are rejected by configuration validation.

Aim touches use independent pointer IDs after the keyboard, mouse and three
D-pad reservations. Triggers at the exact same anchor are aliases: one DOWN,
one UP after the last alias releases. With multiple independent skills held,
the latest held trigger owns mouse motion. The previous held trigger resumes
when it releases. Camera/mouse-aim motion pauses while any aim trigger is held.
The prior mouse mode resumes when the last trigger releases.

`MOUSE_LEFT` and `MOUSE_RIGHT` map physical button DOWN and UP to the same
fixed anchor, independently of cursor position and active camera mode. An
explicit fixed mapping takes precedence over global right-click shortcuts.

`CAMERA` accepts its original six values, plus optional `autoActive` of `0` or
`1`. Omitted means `0`. A value of `1` activates camera mode when the profile
loads. Its trigger key still toggles/holds the mode according to `toggle`.
Use a safe camera-drag anchor appropriate for the game. Automatic camera mode
starts its touch on the first mouse movement, clamps at display bounds during continuous
mouse movement, and holds the view for inspection while no D-pad direction is
held. When D-pad movement starts, or while it continues, 250 ms without mouse
motion releases the camera drag so games can follow the player again. It also releases
on skill aiming, pause, stop, or profile reload; movement after skill release
starts a fresh drag at the anchor. Camera travel is limited to the game's
battlefield-drag behavior and available screen space. Legacy camera mode
without autoActive retains its reset-at-bounds behavior.

The editor displays movable AIM_KEY and LMB markers and preserves their complete
configuration on save/export. Range, trigger and sensitivity can be changed
through text import; this version does not add a separate aim settings dialog.
The camera settings dialog preserves its imported autoActive value.

Pausing, stopping and reloading stop event producers, cancel pending drags and
release active injected touches. Repeated DOWN, unmatched UP and late MOVE are
ignored. Device Mapping Manager identities and fullscreen editor inset behavior
are unchanged.


## Native mouse reader regression tests

The native reader validates mouse capabilities, filters input by event type,
uses a bounded poll so stop needs no physical input, joins before deleting JNI
references, and checks the opened node against the current path after reconnect.
EOF/read failure sends an internal cancellation signal to release mapped mouse
and held-aim touches; it never replays a stale event. No event path or device
name is persisted by this mechanism.

On Linux with a JDK and C compiler, run
`app/src/test/native/run-mouse-reader-tests.sh`. Tests mock JNI/ioctl and exercise
packet filtering, EOF, idle stop, incomplete reads, replaced nodes, cancellation,
and JVM attachment failure. The temporary executable is removed automatically.
