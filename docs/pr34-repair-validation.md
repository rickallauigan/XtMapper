# PR #34 repair validation

The existing quality-baseline branch retains merged PR #33/#35 behavior and
restores `branding-preparation.md` verbatim from `dev`. A quality-tool regression
checks that this linked document remains present and nonempty.

The activity destruction path now calls the actual no-argument `EditorUI.hideView`
API, preserving its idempotent overlay/capture/lifecycle cleanup. ZIP import and
profile rename use checked synchronous transactions under the shared save lock.
Existing-name collisions are rejected rather than partially replacing profiles;
ZIP duplicate names are rejected. Immutable copies preserve prior metadata and
application association lines. Failed persistence triggers checked rollback;
rollback failure is reported explicitly, never as success. Rename failure is
shown to the user without firing its success callback.

## Local evidence

- Java/Kotlin compilation and debug assembly passed.
- 201 Android tests passed, zero failures/errors/skips, including exactly five
  new storage transaction tests and existing lifecycle/structured-profile tests.
- 12 profile Python tests and four quality-tool tests passed (the fourth checks
  branding collateral); both native suites, all six generator checks, schema
  drift, source secret guard and whitespace checks passed.
- Android lint completed with zero errors and 60 visible warnings. These include
  accessibility, private/discouraged API, localization, resources/icons and
  dependency notices; they remain findings rather than security clearance.
- OSV queried 104 Maven runtime components and returned no advisory records.
  This point-in-time result is not proof of absence of vulnerabilities.
- Cached-POM license inventory still cannot resolve the license declaration for
  `com.google.guava:listenablefuture:1.0`; manual follow-up is required.
- Local debug APK SHA-256:
  `a6064295438b3f4a80a9ca9b1e818b5ef9ea697bf6fcfc9101ae76011fc05dd0`.
  This identifies the local artifact, not a reproducible-build guarantee.

The first local lint attempt exhausted the JVM adapter code cache. The same
checks passed with a command-local resource adjustment, without suppressing any
checks or changing pinned dependencies:

```sh
./gradlew -Dorg.gradle.jvmargs='-Xmx4096m -XX:ReservedCodeCacheSize=1024m -Dfile.encoding=UTF-8' :app:testDebugUnitTest :app:lintDebug :app:dependencyInventory :app:assembleDebug
```

Physical gameplay, device overlays and privileged helper behavior were not
validated. Existing privileged-helper global-setting/SELinux concerns remain
separate security work. No device access, deployment, release, signing/package
identity change or automatic merge is authorized by these results. Exact-head
GitHub CI must also pass before this correction is considered review-ready.

## Final ZIP security follow-up

The bounded importer described in [ZIP import security](zip-import-security.md)
adds archive/entry/decompressed/line limits, central-directory and CRC checks,
strict UTF-8, early name validation and recoverable Activity errors.

Final local validation: **214 Android tests passed**, zero failures/errors/skips,
including **13 ZIP security regressions** and all **five storage transaction
tests**. Cross-buffer CRLF and temporary-file deletion failure are covered.
Lint: **0 errors, 60 warnings**. All 12 profile and four quality Python tests,
both native suites, all six generators, schema/secret/diff checks passed.
OSV queried 104 Maven components without advisory records; the unresolved
listenablefuture:1.0 license declaration remains.

Final local debug APK SHA-256: `6ff24a715bee67d6369af51cc3ab4239ae5493d24cc0baedb8263e59d836f62d`.
No physical/device acceptance is claimed. The first 201-test record above
describes the earlier transaction repair; this section records the ZIP follow-up.
