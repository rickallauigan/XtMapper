# Saved mapping details (#16)

Tapping a Game Profile card opens a read-only dialog for its saved name. The
profile name and app identity appear separately. Selection requires Select
Profile; the existing launch/menu actions remain explicit. Inspection does not
start services, request permissions, execute/load macros or write preferences.
The dialog refreshes from storage and closes when its identity disappears.

`ProfileMappingSummary` is the typed adapter shared by card counts and rows.
Each fixed/repeat, held aim, chord, discrete direction, swipe, macro, mouse click,
mouse aim/walk or camera is one action. A compound movement D-pad is one action
with four inputs, not four independent touch targets. APPLICATION, ENABLED and
SCREENSIZE count zero. Stable rows expose saved parameters, never guessed game
semantics. Controller labels use the canonical binding adapter; unknown inputs
retain their readable original identity. Duplicate legacy rows have distinct keys.
Invalid records show an error without mutating storage. Device preferences are
reported per named group, with current connection evidence; absence is explicit.

The responsive scrollable Material 3 dialog inherits its host color scheme,
48dp controls and scalable typography. The current Game Profiles host provides
its existing pink/dark palette; this checkout has no Nord or Catppuccin selector.
No new theme engine is added. Actual icon falls back to a controller symbol.

## Automated evidence

131 Android/Robolectric tests: 0 failures/errors/skips. Four Compose UI tests
verify close/no side effects, explicit selection, two profiles of the same app
opening their exact mappings, and nested launch isolation. Seven adapter/storage
tests cover controller/KBM/counts/chords/advanced types, deterministic ordering,
legacy duplicate rows, preferences/binding snapshots, refresh/deletion and invalid
records. Android debug assembly, native controller/mouse executables, Python
2-test generator suite, v0.1/v0.2 checks and diff check pass. Existing PR #20 and
controller tests run unchanged. Local UI evidence is the Gradle HTML test report;
it is not a screenshot or physical-device acceptance.

## Physical acceptance and rollback

No device interaction during development. After an authorized matching-key
parallel test build, inspect installed KBM/G8 v0.3 cards without selection, close
via Back/outside/X, exercise explicit selection/edit and nested launch/menu.
Check font scaling, TalkBack, small portrait/landscape, long/empty/invalid profiles,
and rename/delete while open. Export before testing and compare preferences and
device groups after cancellation. Screenshots remain to be captured on-device.

Revert this feature's merge commit for source rollback. Binary rollback uses the
matching-certificate backed-up parallel APK with install -r, never uninstall/clear
app data or touch original XtMapper(Debug). The local standard debug APK targets
xtr.keymapper.debug; do not install it over the original app as a parallel build.
