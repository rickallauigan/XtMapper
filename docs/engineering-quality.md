# Engineering and release readiness

## Reproducible development checks

Baseline toolchain: JDK 21, Gradle wrapper 9.5.1, AGP 9.2.1, Kotlin/Compose plugin 2.4.0, compile SDK 37, target SDK 34, min SDK 25, NDK 28.0.13004108. Install these SDK components through the usual Android SDK tooling; configure `local.properties` locally. Use a writable `GRADLE_USER_HOME` if the environment's default cache is read-only. Debug builds do not require production signing properties.

```sh
./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:dependencyInventory :app:assembleDebug
python3 -m unittest discover -s profiles/mlbb/samsung-s10e -p 'test_*.py'
python3 -m unittest discover -s tools -p 'test_*.py'
bash app/src/test/native/run-controller-probe-tests.sh
bash app/src/test/native/run-mouse-reader-tests.sh
python3 tools/check_secrets.py
python3 tools/audit_dependencies.py app/build/reports/dependencies/inventory.json app/build/reports/dependencies/advisories.json
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Run all six generator fixture checks from `.github/workflows/validate-pr.yml`, including original v0.1/v0.2/v0.3, tuned 80px and both Phase 2 variations. After the structured-profile PR is merged, also run `python3 tools/profile_schema.py --check`.

## Regression matrix

| Layer | Automated evidence | Separate acceptance gate |
| --- | --- | --- |
| Android/JVM | Parser/editor/capture, LT overlap, multitouch, chord ownership, shutdown | Real game rendering/input behavior |
| Python | Generated reference profiles and rollback fixtures | New measured HUD anchors |
| Native | Controller discovery/routing and mouse reader | USB reconnect on real hardware |
| Static | Android lint and tracked-source secret patterns | Manual privileged-helper and threat review |
| Supply chain | Maven OSV query, dependency/license list, basic CycloneDX 1.6 inventory | Native/toolchain inventory, unresolved licenses, signing review |

The inventory task emits `inventory.json`, `licenses.json` and `sbom.json` under `app/build/reports/dependencies/`. It covers resolved debug-runtime artifacts, not a complete product SBOM or transitive dependency graph. Cached POM license declarations may be missing/inherited; UNRESOLVED means unknown, not unrestricted. The query follows the [OSV batch API](https://google.github.io/osv.dev/post-v1-querybatch/); pagination is rejected for manual review rather than silently reporting incomplete coverage. OSV network/API failures fail the check; review advisory relevance rather than suppressing the audit to make CI green.

## Static-analysis decisions

Lint remains a blocking check for errors. Existing deprecation, typography, resource and platform warnings remain in the uploaded report. Overlay icons now use explicit center constraints; API 30 system-bar calls use platform constants. API 25–28 device discovery uses nonzero VID/PID as a conservative external-device approximation: zero-ID external devices may be omitted there and need physical verification. API 29+ retains public `isExternal` behavior. Four privileged manifest declarations are individually annotated with their rationale; ordinary installations do not receive those permissions. No blanket lint suppression or generated baseline was introduced.

Gradle reports deprecated APIs ahead of Gradle 10; Java reports deprecated Android APIs. These warnings remain visible. Do not upgrade the wrapper past the supported plugin/toolchain combination without regression testing.

## CI and release controls

PR validation has read-only repository permissions and no signing, hardware or deployment credentials. Debug artifacts get SHA-256 evidence. Dependency and lint reports are retained even on failure. Dependabot reviews Maven and Actions updates weekly.

The release workflow is **dispatch-only**, with a named approval environment. The owner must configure required reviewers; this repository change does not configure GitHub settings. No release was dispatched or published. Debug CI builds are not automatically suitable upgrades for an installed app: verify certificate continuity rather than uninstalling to resolve a signature mismatch.

## Release checklist

- [ ] All regression jobs green on the exact commit; toolchain warnings reviewed.
- [ ] Advisory/license findings triaged; GPLv3 source and attribution available.
- [ ] Approved product identity and art, unchanged package/IPC/preferences unless a separately approved migration exists.
- [ ] Certificate continuity and target/min SDK behavior checked.
- [ ] Explicit device-install approval; protect nine-profile backup and device associations.
- [ ] Hero Training LT/movement/skills/aim/camera and rear level-up acceptance complete.
- [ ] Coordinate Capture interception, rotation, cancellation and structured import preview validated on-device.
- [ ] Artifact checksum, rollback APK/profile references and release notes reviewed.
- [ ] Owner manually authorizes publication; no automatic PR merge or release.

Rollback repository changes by reverting the reviewed commit. Device rollback is a separate approved operation: preserve the existing APK, original nine-profile backup and preferred profile associations; never clear data or uninstall as a routine rollback. Original v0.1/v0.2/v0.3 fixtures remain unchanged.
