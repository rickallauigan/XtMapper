# Legacy ZIP import boundaries

ZIP backups are parsed completely before any preference write. The importer
shares profile-name validation with checked storage transactions and reuses
`ProfileConfiguration.validate` (including structured-container validation).
Structured documents retain the existing 1 MiB decoded-document limit.

Explicit per-import limits:

| Resource | Limit |
| --- | ---: |
| Compressed archive/private temporary file | 20 MiB |
| Profile entries | 64 |
| Lines per entry (including blank/unterminated lines) | 4,096 |
| Decompressed bytes per entry | 2 MiB |
| Decompressed bytes across entries | 16 MiB |
| UTF-8 bytes per line, excluding LF | 1,399,125 |

The line budget accommodates a base64 container line for a maximal 1 MiB
structured document. Nine such profiles fit within the aggregate budget.
Legacy ZIPs exported with profile names as entry names remain supported.
Directories, traversal/absolute/control-character names, duplicates and existing
profile-name collisions are rejected; imports do not silently replace profiles.

Input is copied with a hard byte limit to an app-private temporary file, never
to archive-selected paths. `ZipFile` requires a central directory, unlike a local
entry-only streaming walk that can accept a truncated tail. All names/counts and
declared sizes are checked before decompression; actual bytes, line lengths,
entry sizes and CRCs are independently checked while reading. UTF-8 decoding is
strict. Temporary-file deletion completes before returning parsed profiles, so a
cleanup error also prevents persistence. Checked `File.delete()` preserves API
25 compatibility. No profile write occurs for a parsing/validation failure.

After successful parsing, the existing shared-lock, checked commit/rollback
transaction adds the profiles atomically. A final collision check inside that
lock protects against intervening changes. Import/rollback failure remains an
error. The Activity catches I/O and validation/storage errors and shows a
recoverable import-error toast instead of throwing an unchecked I/O exception.

Thirteen Robolectric regressions cover nine-profile legacy success, structured
metadata and disabled bindings, per-entry/line/aggregate/count limits, malformed
and truncated input, CRC corruption, forged declared size, duplicates and invalid
or conflicting names, late-entry rejection, bounded compressed-stream reads and
temporary cleanup, the Activity error path without profile mutation, many-short-line
rejection, exact line-count boundaries and malformed UTF-8. Tests also import
every existing legacy fixture and its structured container, split-buffer CRLF
boundaries and recoverable temporary-deletion failure without persistence.

These are finite resource ceilings, not a comprehensive archive-fuzzing proof.
Archives over these documented limits are rejected without partial import.
Concurrent import requests have individual limits; no process-wide scheduler or
quota is introduced. The bounded temporary file is private and removed on normal
success/failure; process death can leave an app-cache file for ordinary cache
cleanup. No device deployment or physical gameplay validation is implied.

The seven tracked legacy text fixtures contain 11–16 lines. Structured documents
permit at most 200 bindings; their ZIP containers add one base64 metadata line
to the validated runtime projection. The 4,096-line budget leaves substantial
headroom for these formats without letting a 2 MiB entry allocate hundreds of
thousands of split strings. It is a documented import ceiling, not a promise to
accept arbitrarily padded otherwise-valid files. Counts are enforced directly
on decompressed bytes before UTF-8 decoding and ProfileConfiguration splitting;
blank lines count, the final unterminated line counts, and CRLF counts once.
