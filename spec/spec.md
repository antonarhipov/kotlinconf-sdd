# Temperature CSV Importer — Spec

## Feature summary

A Kotlin + Spring Boot CLI that, on each run, scans a configured input directory for top-level `*.csv` files, parses each one as a header-driven CSV, and inserts the `name`, `datetime`, `temp` columns into a MySQL table `temperature_readings` whose `(name, datetime)` pair is unique. Per-row issues (malformed rows, duplicates against the database or against earlier rows seen in the same run) are logged with file and line, counted, and do not fail the file; only fatal errors (unreadable file, missing required header, DB outage) move the file to a `failed/` subdirectory. After processing, successful files are moved to a `processed/` sibling subdirectory. A per-file line and a final totals line are printed to stdout. The DB schema is owned by the app via Flyway. Integration tests use Testcontainers MySQL (no H2).

## Resolved ambiguities

1. **CSV input mechanism — configured directory.** Single Spring property `importer.input-dir` (overridable via CLI `--importer.input-dir=...` and env var), idiomatic Spring Boot externalized config; no positional args. Rationale: matches the existing Spring Boot scaffold.
2. **Scan scope — non-recursive `*.csv`.** Top-level only, case-insensitive `*.csv` extension; subdirectories (including `processed/` and `failed/`) are ignored. Rationale: predictable drop-folder usage; trivially safe re-runs.
3. **File disposition — move on completion.** Files successfully processed are moved to `<input-dir>/processed/`; files that hit a fatal error are moved to `<input-dir>/failed/`. Both subdirectories are auto-created if missing and excluded from the scan.
4. **File success/failure semantics — only fatal errors fail a file.** Per-row malformed rows and per-row duplicate rejections do not turn a file into `failed`; only I/O errors, missing required headers, encoding errors, and unrecoverable DB errors do. Rationale: per-row issues are data quality, not file failure.
5. **CSV format — header-driven, comma-separated, RFC 4180.** A header row is required. Columns `name`, `datetime`, `temp` are matched by header name, case-insensitively, in any position; other columns are ignored. Comma delimiter, RFC 4180 quoting and escaping. Missing any of the three required headers is a fatal file error.
6. **Datetime format — ISO local.** Parsed as `LocalDateTime` accepting `yyyy-MM-dd'T'HH:mm:ss` and the tolerant alternate `yyyy-MM-dd HH:mm:ss`; fractional seconds optional. Stored in MySQL `DATETIME`. No timezone handling.
7. **Temperature type — `DECIMAL(5,2)`.** Parsed as `BigDecimal` with `Locale.ROOT` (decimal point `.`); stored as MySQL `DECIMAL(5,2)` (range -999.99..999.99). Values that don't fit the precision/scale count as malformed rows.
8. **Malformed-row handling — skip, log, count.** A malformed row is skipped, logged at WARN with file name, 1-based line number, and reason, and increments the `malformed` counter shown in the summary. The file is not failed.
9. **Duplicate detection — DB + in-batch, per-row log.** A row is a duplicate if `(name, datetime)` already exists in the database OR has already been observed earlier in the current run (same or other file). Each duplicate is logged at INFO with file, 1-based line, and the `(name, datetime)` key, and is counted in the summary. Enforced via `UNIQUE KEY uk_name_datetime (name, datetime)` plus `INSERT ... ON DUPLICATE KEY UPDATE` semantics yielding zero affected rows, combined with an in-memory set for the current run.
10. **Summary output — per-file lines + totals, to stdout.** For every processed file (including failed files): `<file>: inserted=<n>, duplicates=<n>, malformed=<n>` (failed files print zeros and an additional `FAILED: <reason>` line). After all files: `TOTAL: files=<n>, inserted=<n>, duplicates=<n>, malformed=<n>, failed_files=<n>`.
11. **Schema ownership — Flyway-managed.** Migration `V1__create_temperature_readings.sql` creates `temperature_readings(id BIGINT AUTO_INCREMENT PK, name VARCHAR(255) NOT NULL, datetime DATETIME NOT NULL, temp DECIMAL(5,2) NOT NULL, UNIQUE KEY uk_name_datetime (name, datetime))`.

## Explicit assumptions

- **File encoding.** CSV files are UTF-8. A leading UTF-8 BOM, if present, is stripped before parsing. Non-UTF-8 byte sequences cause a fatal file error.
- **Number locale.** `temp` is parsed with `Locale.ROOT` (decimal point `.`); thousand separators are not accepted.
- **Name column.** `name` is a non-empty string up to 255 characters after trimming surrounding whitespace per RFC 4180 quoting rules; empty `name` is malformed; `name` longer than 255 chars is malformed.
- **Name comparison collation.** The `name` column uses MySQL's default `utf8mb4_0900_ai_ci` collation, so `(name, datetime)` uniqueness and in-batch duplicate detection are case- and accent-insensitive. The in-memory dedup set normalizes `name` consistently (Unicode-normalized lower-case) to match.
- **Run trigger.** The application runs once per JVM start: it scans, processes, prints the summary, and exits (non-web Spring Boot run, `CommandLineRunner`).
- **File processing order.** Files are processed in ascending filename order (`Comparator.naturalOrder()`, case-sensitive) for deterministic output.
- **Hidden / partial files.** Files whose name starts with `.` or ends with `.tmp` are ignored by the scan, to allow atomic drops by external producers.
- **Concurrent runs.** Only one importer process runs at a time against a given input directory; concurrent runs are not supported.
- **Exit code.** `0` if the application started, scanned successfully, and produced a summary — even if some files were classified `failed/` or some rows were malformed/duplicates. Non-zero only if the application could not start (e.g. `importer.input-dir` not configured, directory missing/unreadable, DB unreachable at startup, Flyway migration failure).

## Handled edge cases

- **Empty input directory.** Print only the `TOTAL` line with all zeros and exit `0`.
- **Input directory missing or not a directory.** Fatal startup error, non-zero exit, no `processed/`/`failed/` created.
- **`importer.input-dir` not configured.** Fatal startup error.
- **File with header but zero data rows.** Counts as a processed file with `inserted=0, duplicates=0, malformed=0`; moved to `processed/`.
- **File missing one of `name`, `datetime`, `temp` headers.** Fatal file error; file moved to `failed/`; summary line for the file shows zeros plus `FAILED: missing required header(s): ...`.
- **Duplicate required headers** (e.g. two `temp` columns). Fatal file error.
- **Extra unknown columns.** Ignored.
- **Row with wrong field count for the header.** Malformed row.
- **Blank line in body.** Ignored, not counted as malformed.
- **Row with empty `name`, empty `datetime`, or empty `temp`.** Malformed row.
- **Row with `temp` out of `DECIMAL(5,2)` range or non-numeric.** Malformed row.
- **Row with unparseable `datetime`.** Malformed row.
- **Row that duplicates an earlier row in the same file.** First wins, subsequent rows counted as duplicates.
- **Row that duplicates a row in another file processed earlier in the same run.** Counted as duplicate.
- **Row that duplicates a row already in the DB from a prior run.** Counted as duplicate.
- **DB error during a single row insert that is not a duplicate-key violation.** Fatal file error; file moved to `failed/`; remaining files in the run still processed; non-zero exit not required.
- **UTF-8 BOM at start of file.** Stripped silently before header parsing.
- **`processed/` or `failed/` already contain a file with the same name as the one being moved.** The moved file is renamed with a `-<epochMillis>` suffix before its extension; the move still counts as success/failure per the original outcome.
- **Filesystem move fails (e.g. cross-device, permissions).** Treated as a fatal post-processing error for that file: log ERROR, count it in `failed_files`, do not delete the original; continue with remaining files.

## Behaviors to verify

- **B-1** The system reads the input directory path from Spring property `importer.input-dir`, supporting CLI and environment overrides.
- **B-2** The system fails to start with a non-zero exit when `importer.input-dir` is unset, points to a non-existent path, or points to a non-directory.
- **B-3** The system scans only the top level of the input directory, selecting regular files whose name ends in `.csv` (case-insensitive) and is not hidden (`.`-prefixed) and not ending in `.tmp`.
- **B-4** The system processes selected files in ascending filename order.
- **B-5** The system auto-creates `<input-dir>/processed/` and `<input-dir>/failed/` if missing and excludes them from the scan.
- **B-6** The system parses each CSV as RFC 4180 with a required header row and a comma delimiter, identifying `name`, `datetime`, `temp` columns by case-insensitive header match in any position and ignoring all other columns.
- **B-7** The system treats a file as fatally failed when any of `name`, `datetime`, `temp` headers are missing, or when a required header appears more than once, moving the file to `failed/`.
- **B-8** The system strips a leading UTF-8 BOM before parsing the header row.
- **B-9** The system parses `datetime` values as `LocalDateTime` accepting both `yyyy-MM-dd'T'HH:mm:ss` and `yyyy-MM-dd HH:mm:ss` (with optional fractional seconds).
- **B-10** The system parses `temp` as `BigDecimal` using `Locale.ROOT` and rejects values that overflow `DECIMAL(5,2)` precision/scale as malformed.
- **B-11** The system treats a row as malformed when any required field is empty, the field count does not match the header, `datetime` is unparseable, `temp` is non-numeric or out-of-range, or `name` is empty or longer than 255 characters.
- **B-12** The system skips malformed rows, logs a WARN with file name, 1-based line number, and reason, and increments the per-file `malformed` counter without failing the file.
- **B-13** The system inserts non-duplicate, non-malformed rows into `temperature_readings(name, datetime, temp)` and counts them in `inserted`.
- **B-14** The system rejects rows whose `(name, datetime)` pair already exists in the database, counting them as `duplicates` and logging an INFO with file, 1-based line, and the key.
- **B-15** The system rejects rows whose `(name, datetime)` pair was already observed earlier in the same run (same or other file), counting them as `duplicates` with the same log line, without issuing a duplicate INSERT.
- **B-16** The system uses case- and accent-insensitive equality on `name` for both DB uniqueness (via `utf8mb4_0900_ai_ci`) and in-memory in-batch detection.
- **B-17** The system ignores blank lines in the file body without counting them.
- **B-18** The system moves a file to `<input-dir>/processed/` after non-fatal completion and to `<input-dir>/failed/` after a fatal error, renaming with a `-<epochMillis>` suffix on name collision.
- **B-19** The system continues processing remaining files when one file is moved to `failed/`.
- **B-20** The system prints one line per processed or failed file to stdout in the form `<file>: inserted=<n>, duplicates=<n>, malformed=<n>` and, for failed files, an additional `FAILED: <reason>` line.
- **B-21** The system prints, after all files, a totals line `TOTAL: files=<n>, inserted=<n>, duplicates=<n>, malformed=<n>, failed_files=<n>` to stdout.
- **B-22** The system exits with code `0` whenever it produced a summary, regardless of how many files failed or how many rows were malformed/duplicates.
- **B-23** The system exits with a non-zero code when it could not start: missing/invalid `importer.input-dir`, unreachable DB at startup, or Flyway migration failure.
- **B-24** The system applies Flyway migration `V1__create_temperature_readings.sql` on startup, creating `temperature_readings(id BIGINT AUTO_INCREMENT PK, name VARCHAR(255) NOT NULL, datetime DATETIME NOT NULL, temp DECIMAL(5,2) NOT NULL, UNIQUE KEY uk_name_datetime (name, datetime))`.
- **B-25** The system, on an empty input directory (no eligible CSVs), prints only the `TOTAL` line with all zeros and exits `0`.
- **B-26** The system processes a file with header but zero data rows as a successful processed file with all counters at zero.

## Out of scope

- Recursive directory scanning.
- Watching the input directory for new files / continuous run mode.
- Timezone or offset-aware datetime handling.
- Configurable CSV dialect (delimiters other than comma, alternate quoting, custom datetime patterns).
- Update semantics for duplicates (e.g. overwriting `temp` on conflict).
- Configurable `processed/` and `failed/` paths (fixed as subdirectories of the input directory).
- Concurrency / multi-process locking on the input directory.
- Schema management by an external DBA (Flyway owns the schema).
- HTTP/web endpoints; the application is a one-shot CLI.
- H2 or any embedded DB for tests (Testcontainers MySQL only, per proposal).
- Performance tuning (batch size, prepared-statement caching) beyond standard Spring Data JDBC defaults — implementation detail.

## External dependencies

None.
