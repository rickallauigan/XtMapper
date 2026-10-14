# Product identity decision and migration preparation

Status: **prepared, awaiting owner approval**. No final name or artwork has been selected. This document does not authorize a rename, installation, release, or migration.

Community-maintained XtMapper fork focused on tested Android gaming setups.

## Decision record

Recommend a distinctive owner-approved display name, with a clear community-fork subtitle and retained upstream attribution. Evaluate discoverability, pronounceability, accessibility at small sizes, translation, existing app/domain/repository collisions and artwork rights. Do not imply endorsement by XtMapper upstream, GameSir, Samsung or MLBB. A shortlist and collision review are pending; no trademark clearance is claimed.

The owner must approve the exact release display name, debug label, visual design, artwork source/license and attribution text. Keep the current XtMapper label and icons until then. A display rename should not become a package migration.

## Identity inventory

| Surface | Current value/source | Change policy |
| --- | --- | --- |
| Release application ID and namespace | `xtr.keymapper`, `app/build.gradle` | Preserve |
| Debug application ID suffix | `.debug`, `app/build.gradle` | Preserve |
| Release display name | `XtMapper`, `res/values/strings.xml:app_name` | Approval required |
| Debug display name | `XtMapper(Debug)`, Gradle `resValue(app_name)` | Approval required |
| Launcher reference | Manifest `@mipmap/ic_launcher` | Keep reference; approved assets only |
| Adaptive icon | `mipmap-anydpi-v26/ic_launcher.xml` | Foreground/background and monochrome currently share original asset family |
| Raster assets | `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_{foreground,background}.png` | Preserve until approved replacement |
| Theme/class/resource names | `Theme.XtMapper`, `XtMapperTheme`, package classes | Technical identifiers, no cosmetic rename needed |
| Root project | `XtMapper`, `settings.gradle` | Preserve |
| Shell service / logs | `xtmapper`, `xtmapper-server` | Preserve IPC identity |
| Binder contracts | `xtr.keymapper` AIDL/classes | Preserve |
| Profile preferences | `profiles`; `KeymapProfiles.java` | Preserve all names/records |
| Device mapping preferences | `device_mapping_v1`; `DeviceMappingStore.kt` | Preserve stable descriptors and associations |
| Signing configuration | Existing release configuration / ordinary debug signing | Preserve certificates; never expose credentials |
| Repository | `rickallauigan/XtMapper` | Preserve URL/name |
| Upstream | `Xtr126/XtMapper`, copyright Xtr126 and contributors | Retain attribution and GPLv3 |
| Documentation asset URL | InfoActivity `appassets.androidplatform.net/XtMapper-docs/` | Technical routing; retain |
| Output names | Existing `XtMapper-...` APK naming | Separate release-artifact decision, no rename in this phase |

The previously installed `.debug.s10etest` staging identity is documented historical device evidence, not this branch's application ID. No phone configuration was inspected in Phase 3.

## Display-string architecture

The manifest already uses `@string/app_name`, and debug overrides that same resource. Reuse this existing single display-name entry rather than introducing a second brand engine. For future dialogs/notifications, resolve `R.string.app_name`; parameterize translated permission instructions instead of copying a new name into every locale. Existing messages containing XtMapper, documentation, fastlane metadata and notification strings need an approved-name audit before a future change. Do not search-and-replace package strings, Binder identifiers, preference keys, URLs or attribution.

A future display-only PR should include release/debug resource-resolution tests, locale review and launcher/notification snapshots. No UI text was changed here, so current release/debug labels remain exact.

## Artwork handoff specification

Prepare editable source artwork and license/provenance records, separate full-color foreground/background layers and a legible single-color silhouette. Adaptive layers use 108×108dp with critical artwork inside the central 66×66dp safe region; avoid pre-applied launcher masks and outline shadows. Review circle/squircle masks, light/dark backgrounds and themed icons. A monochrome layer enables supported launchers' themed icons from Android 13. These requirements follow [Android adaptive-icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

Retain API 25 launcher compatibility and test resource resolution below adaptive-icon support; inventory the current launcher fallback before replacing any asset. Do not claim API 25 icon compatibility from a compile alone. Provide density exports where bitmaps are used and an accessible launcher description where relevant. Store/source marketing graphics are a separate deliverable from adaptive icons.

## Attribution and distribution

Keep LICENSE (GPLv3), upstream copyright/credits and source availability. README already credits Xtr126/upstream contributors and distinguishes this community fork from the official upstream project. Keep those paragraphs intact. Record third-party artwork authors, license grants and modification permission before shipping. Dependency notices and unresolved licenses require review alongside artwork attribution. A new product name does not remove copyleft obligations or grant rights to vendor/game trademarks.

## Migration and rollback

1. Approve display name/artwork in writing and review a narrowly scoped source PR.
2. Verify the built manifest/package, debug suffix, certificate continuity, IPC and preference filenames against this inventory. No package or signing change is part of display-only branding.
3. With separate deployment authorization, preserve the existing APK, nine-profile backup, preferred profile and device associations before testing any upgrade. Never clear data or uninstall to force an upgrade.
4. Test launcher, locales, permissions, editor, imports/ZIP backups and existing gaming mappings on the explicitly approved device.
5. Roll back a display-only source change by reverting its commit; any device APK rollback is independently authorized and uses a compatible signed APK plus untouched backup. Do not overwrite the original backup.

A different package ID or signing certificate is a **separate migration project**: Android treats package changes as another app and certificate mismatches can prevent upgrades. It requires an explicit transfer design and approval, not a branding shortcut.

## Owner acceptance gates

- [ ] Exact name and release/debug labels approved.
- [ ] Artwork/source rights and GPL/third-party attribution reviewed.
- [ ] Adaptive, monochrome, mask/density and API 25 fallback previews approved.
- [ ] Package, signing, IPC, preferences, repository and original attribution unchanged.
- [ ] Translated UI strings and accessibility reviewed.
- [ ] Backups, rollback and separate deployment authorization documented.
- [ ] Physical upgrade acceptance complete; owner separately authorizes publication.

Implementation readiness here means the inventory and migration plan are available. Final branding, artwork production, installation and publication remain pending; no Phase 3 device validation is implied.
