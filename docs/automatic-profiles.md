# Automatic profile switching (#10 / #15)

## Confirmed causes before the fix

The root foreground observer polls tasks every five seconds and calls
TouchPointer's Binder callback. Unknown packages enter showEnableProfileDialog;
both Boolean answers enter createNewProfileForApp. No therefore opens the name
form with enabled=false; confirming that second form persists and activates a
disabled profile. Back/outside dismissal itself was not proven to persist data.

ProfileSelector.select also creates when its list is empty, so deletion between
observation and selection can re-enter provisioning. Handler posts, picker
callbacks and asynchronous root-service connection callbacks have no foreground
request identity. Display changes capture the startup profile indefinitely.
ShowKeymapService remains visible after pauseMouse. The asynchronous manual app
loader can show its picker after the loading dialog was cancelled.

DeviceMappingStore and DeviceGroupResolver already validate preferred references
and need no storage changes. pauseMouse uses InputService.stop to release
controller, key, repeat, D-pad, macro, swipe, camera and mouse state; reuse it.

## Resulting policy and lifecycle

AutomaticProfileResolver is a read-only decision step using the existing profile
store and DeviceGroupResolver. Unknown packages are Unconfigured; an enabled
singleton or valid preferred profile activates; a disabled choice stays paused.
Configured apps with multiple profiles and no valid preference can choose only
existing profiles. ProfileSelector.select never creates, even if profiles vanish.

ForegroundProfileSession versions observations, picker results, connection
callbacks, configuration refreshes and service stops. Previous input is paused
through pauseMouse/InputService.stop before resolving; ShowKeymapService is
stopped on every transition and restored only for enabled active profiles.
Connection commits recheck profile contents, enabled state and current device
resolution. Preference changes and external-device identity changes invalidate
pending selections; virtual-device notifications do not trigger restart loops.
Display changes resolve the current foreground profile rather than the startup
snapshot, and listeners/dialogs are removed on service destruction.

The root RuntimeRequestGate invalidates queued starts on pause/stop and publishes
only a still-current runtime to the event reader. A start invalidated during
initialization is stopped using the existing teardown. Runtime initialization
and Binder callbacks execute outside its monitor. These classes track request
identity, not held input: InputService remains the single input-cleanup authority.

Profile dialogs have one-shot Accept, Decline and Dismiss outcomes. No/Cancel,
Back/outside cancellation and dismissal cannot progress or save. Manual Add
Profile still selects an app and then confirms a valid, unique name. Explicit
creation can still request enabled=false; decline does not mean disabled.
Activity destruction dismisses owned dialogs, and cancelled asynchronous app
loading cannot reopen a picker. Change App uses this shared guarded picker.
Existing profiles and device bindings are never migrated or modified by this fix.

## Validation

Run from the repository root:

```sh
./gradlew testDebugUnitTest assembleDebug
git diff --check
app/src/test/native/run-controller-probe-tests.sh
app/src/test/native/run-mouse-reader-tests.sh
```

Coverage includes unknown-package storage integrity, singleton/preferred and
disabled resolution, stale preferred references, rapid A/B/A switching, queued
connections, stop/deletion/disconnect invalidation, virtual-device filtering,
No/Cancel/Back/outside outcomes, cancellation of asynchronous app loading,
activity destruction, manual positive creation, intentional disabled creation,
and runtime touch cleanup with rejection of input after pausing.

A measured v0.3 fixture verifies combat and dedicated-upgrade cleanup, right-stick
aim, and unbound native Chat/left-stick events. The controller profile files,
controller routing and native readers are unchanged. Synthetic tests do not
prove physical gameplay or Android's native passthrough behavior.

## S10e manual acceptance (after separate deployment)

No phone operations were performed for this PR. Do not deploy while the current
v0.3 physical gameplay session is in progress. A future testing build must retain
`xtr.keymapper.debug.s10etest` and its matching signing identity. The normal debug
artifact is `xtr.keymapper.debug`; do not install it over the original package.

1. Back up/export profiles and record device bindings; keep the original debug
   app untouched. Update only the parallel test app after checking package/cert.
2. Enable automatic profiling and show controls. With G8 connected and MLBB's
   preferred mapping set to v0.3, enter Hero Training and verify activation.
3. Hold mapped controls, leave MLBB for an unconfigured File Manager/Giraw, and
   wait longer than one observer interval. Expect released touches, paused
   mapping, no labels, no prompt, no synthetic touches and no new profile.
4. Return to MLBB, then repeat MLBB -> File Manager -> MLBB rapidly. Expect v0.3
   restoration without a stale picker or File Manager configuration.
5. For a configured test app with multiple profiles and no preference, cancel its
   existing-profile picker via Back/outside. Expect no selection or mutation.
6. Use explicit Add Profile: cancel the app loading/picker/name form in separate
   runs, then confirm a valid new profile. Test Change App and import separately.
   Confirm disabled profiles remain disabled. Compare exports/bindings after
   cancellation. The enable prompt is no longer reachable from automatic
   observation; No/Yes semantics are covered by UI regression tests.
7. Stop/restart the test service while a configured picker is open; disconnect
   G8 during switching; delete a selected test profile. Expect no stale activation
   or creation and clean recovery through existing valid preferences/selection.
8. Recheck LT tap/hold/release (150 ms), A spell, RB/RT/LB casts, right-stick aim,
   native movement, View/R4/L4 upgrades and native top-right menu Chat. Test
   simultaneous controls, G8 reconnect and profile restoration on app restart.

The existing five-second observer interval is unchanged. Cleanup follows
observation, so very short app visits between polls may not be observed; the fix
prevents stale actions relative to the latest observed package. Physical S10e
results remain pending.

## Rollback

This change does not deploy or modify phone data. To roll back repository code,
revert this PR's merge commit (use `git revert -m 1 <merge-commit>` for a merge).
After a future test deployment, stop the test mapper and reinstall its backed-up,
matching-certificate APK with `install -r`; do not uninstall or clear data.
Re-select the saved GameSir/MLBB v0.3 preferred profile if needed. Profiles,
enabled flags and device bindings need no migration or restoration for this fix.

## Changed files

- TouchPointer.kt: resolution, cleanup and guarded lifecycle.
- AutomaticProfileResolver.kt: pure policy and foreground request identity.
- ProfileDialogOperation.java: explicit one-shot outcomes.
- ProfileSelector.java / ProfilesApps.java: existing-only selection and safe manual dialogs/loading.
- MainActivity.java: shared guarded Change App flow.
- ActivityObserverService.java: safe polling and callback removal on stop.
- RemoteService.java / RuntimeRequestGate.java: queued-start cancellation and runtime publication.
- ShowKeymapService.java: safe teardown without an editor view.
- AutomaticProfileResolverTest.kt / ForegroundProfileWorkflowTest.kt: policy and service workflows.
- ProfileDialogOperationTest.java / ProfileSelectorTest.java: outcomes, storage and manual lifecycle.
- RuntimeRequestGateTest.java / MappingRuntimeTest.java: root queue and input cleanup, including v0.3.
- docs/automatic-profiles.md: investigation, architecture, validation and rollback.
