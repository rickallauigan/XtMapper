# Security policy

This community-maintained fork is privileged input-mapping software, not a security-certified product. Hardware/gameplay support is limited to the documented setups.

Report suspected vulnerabilities privately through the repository's GitHub **Report a vulnerability** facility when available. If private reporting is unavailable, contact the repository owner to arrange a private channel before sharing exploit details. Never post credentials, private keys, account data, device backups, or unredacted input traces in public issues. Maintainers should acknowledge reports, reproduce against a commit, agree on disclosure, and publish a reviewed fix; no response SLA is promised.

## Trust boundaries

Profiles, ZIP backups, controller events and overlay inputs are untrusted. Structured import must be bounded, version-checked and validated before an atomic save. Macro references do not grant permission to import executable payloads. Root/Shizuku access is a separate user decision; overlays and calibration must remain explicit and visible.

Known outstanding review: `RemoteServiceShell.java` currently attempts `setenforce 0`, an overlay permission grant and a global low-RAM overlay setting when started through the shell entry point. These legacy operations were **not executed** in Phase 3. Prefer the documented ordinary app flow; a separately reviewed privileged-helper change needs platform validation before claiming least-privilege runtime behavior. CI least privilege does not establish runtime least privilege.

## Automated coverage and limits

CI checks Android lint, tracked-text secret patterns and OSV advisories for resolved Maven debug-runtime dependencies. Scanner output redacts matched values. This does not cover Git history, binary secrets, all proprietary/native code, build plugins, OS packages or unpublished advisories. An empty advisory response is not a safety guarantee. License inventory entries marked UNRESOLVED require manual review before distribution.

Do not publish or deploy automatically. Release workflow dispatch remains an explicit owner action; configure required reviewers on `release-approval` before using it. An environment name alone does not create approval protection. Signing identities and installed-app compatibility must be verified independently.
