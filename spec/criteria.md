# Acceptance Criteria: Temperature CSV Importer

## Functional

### AC-1: Input directory configured via Spring property
**Covers:** B-1

When the application starts with `importer.input-dir` set via `application.properties`, CLI argument, or environment variable, the importer shall use that path as the scan root.

### AC-2: Missing configuration fails startup
**Covers:** B-2, B-23

If `importer.input-dir` is unset at startup, then the importer shall fail to start, log an ERROR identifying the missing property, and exit with a non-zero code without creating `processed/` or `failed/`.

### AC-3: Non-existent input path fails startup
**Covers:** B-2, B-23

If `importer.input-dir` points to a path that does not exist, then the importer shall fail to start, log an ERROR identifying the path, and exit with a non-zero code.

### AC-4: Non-directory input path fails startup
**Covers:** B-2, B-23

If `importer.input-dir` points to an existing path that is not a directory, then the importer shall fail to start, log an ERROR identifying the path, and exit with a non-zero code.

### AC-5: Scan is non-recursive
**Covers:** B-3

When the importer scans the input directory, the importer shall consider only regular files that are direct children of the input directory and shall not descend into any subdirectory.

### AC-6: CSV extension filter is case-insensitive
**Covers:** B-3

When the importer scans the input directory, the importer shall select files whose name ends in `.csv` matched case-insensitively (including `.CSV`, `.Csv`) and shall ignore files with any other extension.

### AC-7: Hidden and partial files are skipped
**Covers:** B-3

When the importer scans the input directory, the importer shall exclude files whose name starts with `.` and files whose name ends with `.tmp` from the selection.

### AC-8: Files processed in ascending filename order
**Covers:** B-4

When the importer has selected the set of eligible files, the importer shall process them in ascending order by filename using case-sensitive natural ordering.

### AC-9: `processed/` and `failed/` are auto-created
**Covers:** B-5

When the importer starts a scan and `<input-dir>/processed/` or `<input-dir>/failed/` does not exist, the importer shall create the missing subdirectory before processing any files.

### AC-10: `processed/` and `failed/` are excluded from scan
**Covers:** B-5

When the importer scans the input directory, the importer shall not select any file located under `<input-dir>/processed/` or `<input-dir>/failed/`.

### AC-11: CSV parsed as RFC 4180 with comma delimiter
**Covers:** B-6

When the importer reads a CSV file, the importer shall parse it as RFC 4180 with a comma delimiter and a required header row.

### AC-12: Required columns identified by case-insensitive header name
**Covers:** B-6

When the importer parses the header row, the importer shall identify the `name`, `datetime`, and `temp` columns by case-insensitive header match in any position.

### AC-13: Unknown columns ignored
**Covers:** B-6

When the header contains columns other than `name`, `datetime`, and `temp`, the importer shall ignore those columns and proceed without error.

### AC-14: Missing required header is fatal
**Covers:** B-7

If any of the headers `name`, `datetime`, or `temp` is absent from a file's header row, then the importer shall move the file to `<input-dir>/failed/`, print a `FAILED: missing required header(s): ...` line naming the missing headers, and continue with the next file.

### AC-15: Duplicate required header is fatal
**Covers:** B-7

If a required header (`name`, `datetime`, or `temp`) appears more than once in a file's header row, then the importer shall move the file to `<input-dir>/failed/`, print a `FAILED:` line identifying the duplicated header, and continue with the next file.

### AC-16: UTF-8 BOM is stripped before header parsing
**Covers:** B-8

When a CSV file begins with a UTF-8 BOM, the importer shall remove the BOM before parsing the header row so that header matching succeeds.

### AC-17: ISO `T`-separated datetime accepted
**Covers:** B-9

When a row's `datetime` field matches `yyyy-MM-dd'T'HH:mm:ss` with optional fractional seconds, the importer shall parse it as a `LocalDateTime` and use it as the row's datetime value.

### AC-18: Space-separated datetime accepted
**Covers:** B-9

When a row's `datetime` field matches `yyyy-MM-dd HH:mm:ss` with optional fractional seconds, the importer shall parse it as a `LocalDateTime` and use it as the row's datetime value.

### AC-19: `temp` parsed with `Locale.ROOT`
**Covers:** B-10

When a row's `temp` field is a decimal number using `.` as the decimal separator and fits `DECIMAL(5,2)`, the importer shall parse it as `BigDecimal` and use it as the row's temperature value.

### AC-20: `temp` boundary — within range
**Covers:** B-10, B-11

When a row's `temp` field is `999.98` (within the `DECIMAL(5,2)` range), the importer shall accept the row and pass `999.98` to the insert pipeline.

### AC-21: `temp` boundary — at maximum
**Covers:** B-10, B-11

When a row's `temp` field is `999.99` (the inclusive maximum of `DECIMAL(5,2)`), the importer shall accept the row and pass `999.99` to the insert pipeline.

### AC-22: `temp` boundary — beyond maximum
**Covers:** B-10, B-11

If a row's `temp` field is `1000.00` (beyond the `DECIMAL(5,2)` range), then the importer shall treat the row as malformed, skip it, log a WARN with file name, 1-based line number, and reason `temp out of range`, and increment the file's `malformed` counter without failing the file.

### AC-23: `name` boundary — within length
**Covers:** B-11

When a row's `name` field is a non-empty string of 254 characters after trimming, the importer shall accept the row and use the trimmed value as the row's name.

### AC-24: `name` boundary — at maximum length
**Covers:** B-11

When a row's `name` field is a string of exactly 255 characters after trimming, the importer shall accept the row and use the trimmed value as the row's name.

### AC-25: `name` boundary — beyond maximum length
**Covers:** B-11

If a row's `name` field is longer than 255 characters after trimming, then the importer shall treat the row as malformed, skip it, log a WARN with file name, 1-based line number, and reason `name too long`, and increment the file's `malformed` counter without failing the file.

### AC-26: Empty required field is malformed
**Covers:** B-11, B-12

If a row's `name`, `datetime`, or `temp` field is empty after trimming, then the importer shall skip the row, log a WARN with file name, 1-based line number, and reason identifying the empty field, and increment the file's `malformed` counter without failing the file.

### AC-27: Wrong field count is malformed
**Covers:** B-11, B-12

If a data row's field count does not equal the header's field count, then the importer shall skip the row, log a WARN with file name, 1-based line number, and reason `field count mismatch`, and increment the file's `malformed` counter without failing the file.

### AC-28: Unparseable `datetime` is malformed
**Covers:** B-11, B-12

If a row's `datetime` field matches neither `yyyy-MM-dd'T'HH:mm:ss` nor `yyyy-MM-dd HH:mm:ss` (with optional fractional seconds), then the importer shall skip the row, log a WARN with file name, 1-based line number, and reason `unparseable datetime`, and increment the file's `malformed` counter without failing the file.

### AC-29: Non-numeric `temp` is malformed
**Covers:** B-11, B-12

If a row's `temp` field is not a valid decimal number in `Locale.ROOT`, then the importer shall skip the row, log a WARN with file name, 1-based line number, and reason `non-numeric temp`, and increment the file's `malformed` counter without failing the file.

### AC-30: Non-duplicate, well-formed rows are inserted
**Covers:** B-13

When a row is well-formed and its `(name, datetime)` key has not been observed in the DB or earlier in the run, the importer shall insert `(name, datetime, temp)` into `temperature_readings` and increment the file's `inserted` counter.

### AC-31: DB duplicate detected
**Covers:** B-14

If a row's `(name, datetime)` key already exists in `temperature_readings` from a prior run, then the importer shall not insert a new row, shall log an INFO with file name, 1-based line number, and the `(name, datetime)` key, and shall increment the file's `duplicates` counter.

### AC-32: In-batch duplicate within the same file
**Covers:** B-15

If a row's `(name, datetime)` key was already observed in an earlier row of the same file during the current run, then the importer shall not issue an INSERT, shall log an INFO with file name, 1-based line number, and the `(name, datetime)` key, and shall increment the file's `duplicates` counter.

### AC-33: In-batch duplicate across files in the same run
**Covers:** B-15

If a row's `(name, datetime)` key was already observed in an earlier file processed during the current run, then the importer shall not issue an INSERT, shall log an INFO with file name, 1-based line number, and the `(name, datetime)` key, and shall increment the file's `duplicates` counter.

### AC-34: `name` comparison is case- and accent-insensitive
**Covers:** B-16

When two rows have `name` values that differ only by letter case or accent (e.g. `Café` vs `cafe`) and share the same `datetime`, the importer shall treat the second row as a duplicate for both DB uniqueness and in-memory in-batch detection.

### AC-35: Blank body lines are ignored
**Covers:** B-17

When the importer encounters a completely blank line in a file body, the importer shall skip it without inserting, without logging, and without incrementing any counter.

### AC-36: Successful file moves to `processed/`
**Covers:** B-18

When a file completes processing without a fatal error, the importer shall move the file to `<input-dir>/processed/` preserving its filename.

### AC-37: Failed file moves to `failed/`
**Covers:** B-18, B-19

If a file hits a fatal error during processing, then the importer shall move the file to `<input-dir>/failed/`, count it in `failed_files`, and continue processing the remaining files.

### AC-38: Name collision in destination is resolved with epoch suffix
**Covers:** B-18

When the destination of a move (`processed/` or `failed/`) already contains a file with the same name, the importer shall rename the moved file by appending `-<epochMillis>` before its extension and complete the move under that name.

### AC-39: Filesystem move failure is fatal for that file only
**Covers:** B-19

If the filesystem move of a processed file fails (e.g. cross-device, permissions), then the importer shall log an ERROR identifying the file and reason, count the file in `failed_files`, leave the original file in place, and continue processing the remaining files.

### AC-40: Per-file summary line printed to stdout
**Covers:** B-20

When the importer finishes a file (successful or failed), the importer shall print exactly one line to stdout in the form `<file>: inserted=<n>, duplicates=<n>, malformed=<n>`.

### AC-41: Failed file emits additional FAILED line
**Covers:** B-20

When the importer finishes a file with a fatal error, the importer shall print an additional `FAILED: <reason>` line to stdout immediately after that file's summary line.

### AC-42: Totals line printed after all files
**Covers:** B-21

When the importer has finished processing all selected files, the importer shall print a single line to stdout in the form `TOTAL: files=<n>, inserted=<n>, duplicates=<n>, malformed=<n>, failed_files=<n>`.

### AC-43: Successful run exits zero regardless of per-file outcomes
**Covers:** B-22

When the importer has produced a summary, the importer shall exit with code `0` even if some files were classified as failed or some rows were malformed or duplicates.

### AC-44: Unreachable DB at startup is non-zero exit
**Covers:** B-23

If the database is unreachable at startup, then the importer shall fail to start, log an ERROR, and exit with a non-zero code without printing a TOTAL line.

### AC-45: Flyway migration failure is non-zero exit
**Covers:** B-23

If Flyway migration fails at startup, then the importer shall fail to start, log an ERROR, and exit with a non-zero code without printing a TOTAL line.

### AC-46: Flyway creates `temperature_readings` on startup
**Covers:** B-24

When the importer starts against a database without `temperature_readings`, the importer shall apply migration `V1__create_temperature_readings.sql` so that the table exists with columns `id BIGINT AUTO_INCREMENT PRIMARY KEY`, `name VARCHAR(255) NOT NULL`, `datetime DATETIME NOT NULL`, `temp DECIMAL(5,2) NOT NULL`, and a unique key `uk_name_datetime (name, datetime)`.

### AC-47: Empty input directory prints only TOTAL with zeros
**Covers:** B-25

When the input directory contains no eligible CSV files, the importer shall print exactly the line `TOTAL: files=0, inserted=0, duplicates=0, malformed=0, failed_files=0` to stdout and exit with code `0`.

### AC-48: Header-only file is a successful processed file
**Covers:** B-26

When a file contains a valid header row and zero data rows, the importer shall print `<file>: inserted=0, duplicates=0, malformed=0`, move the file to `<input-dir>/processed/`, and not increment `failed_files`.

### AC-49: Non-UTF-8 bytes are a fatal file error
**Covers:** Explicit assumptions, "File encoding"

If a CSV file contains byte sequences that are not valid UTF-8, then the importer shall move the file to `<input-dir>/failed/`, print a `FAILED: encoding error` line, and continue with the next file.

### AC-50: Per-row DB error (non-duplicate) is a fatal file error
**Covers:** Handled edge cases, "DB error during a single row insert"

If a single-row INSERT fails with a database error that is not a duplicate-key violation, then the importer shall stop processing the current file, move it to `<input-dir>/failed/`, log an ERROR with the underlying reason, count it in `failed_files`, and continue with the remaining files.

## Non-functional

### AC-51: Tests use Testcontainers MySQL
**Covers:** Out of scope, "H2 or any embedded DB for tests"; Feature summary, "Testcontainers MySQL (no H2)"

While the integration test suite runs, the importer shall execute against a MySQL instance provided by Testcontainers and shall not bind to any embedded database such as H2.

### AC-52: Deterministic stdout ordering
**Covers:** B-4, B-20, B-21

When the importer processes more than one file in a single run, the importer shall emit per-file summary lines in ascending filename order and emit the `TOTAL` line strictly after the last per-file line.

## Coverage exclusions

- **Performance / throughput:** the spec does not state latency or throughput thresholds (rows/sec, file size limits); explicitly out of scope per "Performance tuning ... implementation detail".
- **Security / authorization:** the application is a one-shot CLI with no user-facing authentication or authorization surface; no negative-auth criteria apply.
- **Accessibility:** no UI surface (CLI-only, stdout-only); not applicable.
- **Compatibility (OS / JVM matrix):** spec does not constrain supported OS or JVM versions beyond the existing Spring Boot scaffold; no threshold to verify.
- **Observability (metrics / tracing):** spec only mandates stdout summary lines plus WARN/INFO/ERROR logs already covered by functional ACs; no separate metrics or tracing requirement to verify.
- **Concurrent runs:** explicitly out of scope per "Concurrent runs ... not supported"; no criterion authored.
