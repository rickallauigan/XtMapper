# Contributing

Community-maintained XtMapper fork focused on tested Android gaming setups.

Use a focused branch and PR against `dev`. Preserve unrelated local changes, existing profiles, GPLv3 attribution and app identity. Describe the concrete behavior, tests and physical validation gaps. Do not merge your own change automatically or install a build on a player's device without explicit authorization.

See [engineering checks](docs/engineering-quality.md) for reproducible commands and [security policy](SECURITY.md) for sensitive reports. New mapping actions need parse/export, disabled-state, scaling and pointer lifecycle tests. Runtime changes need overlap, disconnect, reload and stop coverage. CI results never establish gameplay compatibility.

Never commit keystores, signing credentials, device backups, HUD screenshots, raw controller traces or generated APKs. Existing credential templates must remain placeholders. Keep production signing separate from development builds.
