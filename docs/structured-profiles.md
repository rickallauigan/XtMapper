# Structured profile files v1 (#24)

JSON is canonical; equivalent human-editable YAML is supported using pinned
SnakeYAML 2.5 with SafeConstructor, bounded scanning and explicit tag/anchor/alias
rejection. No TOML, scripts, object construction or new injection engine.
[JSON Schema](../schema/v1/profile.schema.json) describes field shapes; the app
also enforces finite/runtime numeric ranges, effective viewport bounds, canonical
input ownership, native grammar constraints and modifier conflicts.

Open **Tools • Profile files JSON/YAML** to import, or profile details →
**JSON/YAML • metadata and enabled bindings** to export/edit an existing profile.
Files are selected through Android SAF. Import → preview → validate → explicitly
confirm new/replace/duplicate → atomic save. Cancel/Back write no preferences.
Imports are blocked from selection until **Explicitly allow profile selection**;
file `isEnabled` is retained separately from that local activation gate. Allowing
selection does not assign a device group or preferred binding. No root API,
service start/stop, deployment or physical-device access belongs to this screen.

Root shape: `schema_version: 1`, `profile` (`name`, `description`, `game_package`,
`isEnabled`, `screen.width/height`) and `bindings`. Each binding has `input`,
`action`, optional user-editable `name`/`description`, `isEnabled` (default true)
and action fields. A top-level `camera` is accepted and normalized into bindings;
`stick_camera` there may omit input (implicit RIGHT_STICK). Disabled entries
retain all fields and appear in file editor/profile details. Their native lines
are omitted from runtime projection; whole-profile disable/pending import clears
all runtime actions while retaining the source document. No phantom pointers.

Action fields (unknown/mixed flat/nested fields reject):

| Action | Additional fields |
| --- | --- |
| fixed_touch | position.x/y, offset (default 0), optional repeat_ms 60..60000 |
| stick_aim | position, aim.radius, aim.sensitivity.x/y, aim.dead_zone, aim.invert_y |
| mouse_skill_aim | position, aim.radius, aim.sensitivity.x/y |
| stick_camera | position, sensitivity.x/y, dead_zone; input RIGHT_STICK |
| camera | position, sensitivity.x/y, toggle, auto_active; input trigger |
| chord | modifier, position, offset (default 0); input trigger |
| movement_pad | editor_position, position (center), radius, size.width/height, directions.up/down/left/right; input is four codes joined with + |
| swipe | position, end_input, end_position |
| macro | macro_id (safe local ID; no paths or embedded payload) |
| mouse_walk | position, radius; input MOUSE_WALK |
| mouse_aim | position, limited_bounds, size, click_position, sensitivity, nonlinear; input MOUSE_AIM |

Position objects use x/y. MOUSE_LEFT/RIGHT fixed touch have no offset/repeat.
All coordinates are profile pixels, global origin 0,0; no cutout offset. Existing
S10e positions and 80-pixel radius remain unchanged. Existing native models still
perform viewport scaling. Fixed/chord offsets retain effective anchor meaning.
Legacy macro references export without executing/loading payloads; copy existing
local macro data separately, and verify availability before activating.

Security limits: UTF-8 1 MiB, depth 32, 200 bindings, JSON 20000 nodes/YAML 30000
scanner tokens, printable names ≤80 and descriptions ≤1000. Unsupported schema
versions, duplicate/ambiguous inputs, nonfinite numbers, unsafe tags, aliases,
path IDs, unknown fields and stale writes fail before persistence. JSON parser is
strict (no trailing commas, duplicate keys or unquoted keys). YAML booleans are
lowercase true/false. Files are parsed on a worker; no document-selected path is
passed to a shell or macro loader. SAF export writes only the explicitly chosen
URI; preserve a separate original file if editing externally.

Atomic storage uses the existing profile string-set with a single
`STRUCTURED_PROFILE_V1` metadata line containing Base64 canonical JSON and an
optional local PENDING activation marker. Native projection and metadata commit
in one SharedPreferences transaction and are verified on read. Existing text/ZIP
backup paths retain the metadata line; legacy runtime parser remains the source
of truth for native actions. Older APKs ignore this line and cannot edit disabled
metadata safely: export JSON before downgrading; do not edit structured profiles
with an old APK. Keep untouched legacy references and nine-profile backups.

Legacy/visual editor saves merge by action/input identity, preserving matching
names/descriptions and all disabled entries. New active input conflicting with a
retained disabled definition rejects; enable/remove it in the file editor first.
Disabled configurations rescale using existing runtime models when editor plane
changes. Binding enable/disable and metadata editing use the dedicated file
editor; structured profile details include disabled entries. Capture snippets
already match binding shapes: insert a real snippet into a full profile document,
not as a standalone profile import. Never invent Quick Buy measurements.

Automated evidence covers all native action families, JSON/YAML equivalence,
Unicode metadata, malicious/negative/fuzz inputs, atomic conflict rejection,
activation gate and editor preservation. Physical installation, file-picker UX,
service/gameplay and final branding approval remain pending. No device access or
deployment was performed in Phase 3.

Validation: 196 Android tests, 12 Python tests, all six profile-generator checks,
native controller/mouse suites and debug assembly PASS. The generated schema
is checked with `python tools/profile_schema.py --check`. No physical validation
is claimed. SAF provider behavior and older-APK editing remain future gates.
