# Technical Design and Constraints: Temperature CSV Importer

## Overview

A one-shot Kotlin + Spring Boot CLI that scans a configured directory for `*.csv` files, parses each as RFC 4180, and upserts `(name, datetime, temp)` rows into MySQL `temperature_readings` with per-row duplicate / malformed handling and per-file disposition to `processed/` or `failed/`.

Stack: Kotlin 2.2.21, Spring Boot 4.0.6, JDK 21, `spring-boot-starter-data-jdbc`, Flyway 11.x (`flyway-mysql`), MySQL Connector/J, Testcontainers MySQL, JUnit 5 + `kotlin-test-junit5`. New dependency: Apache Commons CSV.

See: [spec/spec.md](./spec.md), [spec/criteria.md](./criteria.md), [spec/proposal.md](./proposal.md).

## Design

**Components** (all under `org.example.sdd.importer`):
- `ImporterProperties` — `@ConfigurationProperties("importer")` binding `input-dir`.
- `ImporterRunner` — `CommandLineRunner` orchestrating scan → per-file processing → totals print → exit.
- `DirectoryScanner` — lists top-level eligible CSV files in deterministic order; creates `processed/` and `failed/`.
- `CsvFileProcessor` — opens a file (UTF-8, BOM-stripped), drives Commons CSV, applies row rules, returns a `FileOutcome`.
- `RowParser` — pure functions parsing/validating `name`, `datetime`, `temp`; returns `RowResult` (Valid | Malformed).
- `TemperatureReadingRepository` — `NamedParameterJdbcTemplate`-backed inserter using `INSERT … ON DUPLICATE KEY UPDATE id=id` to detect DB duplicates by zero affected rows.
- `RunDeduplicator` — in-memory `MutableSet<DedupKey>` carried across the whole run.
- `FileMover` — moves files to `processed/`/`failed/` with `-<epochMillis>` collision suffix.
- `SummaryPrinter` — writes per-file and `TOTAL` lines to `System.out`.

**Boundaries:** the feature exposes no HTTP or messaging surface. Public input is the `importer.input-dir` property and the filesystem; public output is `stdout` plus MySQL rows. Domain types (`RowResult`, `FileOutcome`, `DedupKey`, `RunCounters`) are internal to the package.

**Flow:** (1) On startup Flyway applies `V1`. (2) `ImporterRunner` validates config and resolves the scan root. (3) `DirectoryScanner` returns the sorted file list and ensures sibling dirs exist. (4) For each file, `CsvFileProcessor` streams rows via Commons CSV; `RowParser` classifies each; valid rows go through `RunDeduplicator` then `TemperatureReadingRepository.insertIfAbsent`; counters accrue. (5) `FileMover` relocates the file based on the outcome. (6) `SummaryPrinter` emits per-file lines as they finish, then the `TOTAL` line. (7) Process exits `0`.

**Key dependencies:**
- **Add:** `org.apache.commons:commons-csv` — RFC 4180 parser with header-map API (AC-11..AC-13).
- **Use existing:** `spring-boot-starter-data-jdbc` (for `NamedParameterJdbcTemplate`), `spring-boot-starter-flyway` + `flyway-mysql`, `mysql-connector-j`, `spring-boot-testcontainers`, `testcontainers-mysql`, `kotlin-test-junit5`.
- **Considered and declined:** Spring Batch (canonical batch framework — declined because the spec demands a single-source, single-table, restart-by-rerun importer with no chunk/JobRepository requirements). FastCSV / Jackson-CSV / hand-rolled parser (declined in favor of Commons CSV's mature header-map API). H2 (forbidden by spec).

## Codebase Alignment

There is no `AGENTS.md` / `CLAUDE.md` / `GEMINI.md` in the repository; the only existing source is the empty `org.example.sdd.Application` plus `TestcontainersConfiguration` already wired with `@ServiceConnection`. Conventions inherited from the Spring Boot scaffold: package root `org.example.sdd`; Kotlin idioms with `-Xjsr305=strict`; Spring Boot externalized configuration; SLF4J via Spring Boot Logging; Flyway migrations under `src/main/resources/db/migration`; Spring Boot testing with Testcontainers via `@ServiceConnection`. This feature introduces a new sub-package `org.example.sdd.importer` for all importer code (deviation justified below in `RULE-3`); all other choices follow the existing scaffold.

## Rules

### RULE-1
**Covers:** project-wide
**MUST NOT** introduce Spring Batch (or any other batch-job framework) as a dependency.
**Reason:** Surveyed as the canonical batch framework; declined because the import is single-source, single-table, with no chunked-restart, partitioning, or `JobRepository` requirements in the spec. Reconsider if requirements grow to multi-source, partitioned, or resumable mid-file processing.

### RULE-2
**Covers:** AC-11, AC-12, AC-13, AC-16, AC-27, AC-35
**MUST** parse CSV input using `org.apache.commons:commons-csv` with `CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setAllowDuplicateHeaderNames(false).build()`.
**Reason:** RFC 4180 compliance, header-map lookup, blank-line skipping, and duplicate-header detection are required by ACs; Commons CSV provides them out of the box. Disabling `allowDuplicateHeaderNames` lets the parser surface the duplicate-required-header fatal case (AC-15) directly.

### RULE-3
**Covers:** project-wide
**MUST** place all feature code under the package `org.example.sdd.importer` (sub-packages allowed) and **MUST NOT** add classes directly under `org.example.sdd` except the existing `Application`.
**Reason:** Keeps the importer feature's classes co-located and grep-able; the scaffold has no existing layout convention to inherit, so this rule establishes one explicitly.

### RULE-4
**Covers:** AC-1
**MUST** expose configuration via a `@ConfigurationProperties("importer")` class with a non-nullable `inputDir: Path` property bound from `importer.input-dir`, and **MUST NOT** read environment variables or system properties directly from feature code.
**Reason:** Spring's externalized configuration already supports CLI / env overrides uniformly; direct env reads bypass the override chain and the binding validation used by AC-2.

### RULE-5
**Covers:** AC-2
**MUST** annotate `ImporterProperties.inputDir` with `@field:NotNull` (or equivalent Bean Validation) and enable `@ConfigurationPropertiesScan` so a missing `importer.input-dir` causes a `BindException` at startup before any `CommandLineRunner` runs.
**Reason:** AC-2 requires startup failure with non-zero exit when the property is unset; binding-time validation produces that behavior with a single, observable error path.

### RULE-6
**Covers:** AC-3, AC-4, AC-9, AC-10
**MUST** validate the input path with `Files.isDirectory` before scanning, **MUST** create `processed/` and `failed/` with `Files.createDirectories`, and **MUST** filter them (and any other subdirectory) out of the scan stream.
**Reason:** Centralizes the directory invariant; `Files.createDirectories` is idempotent, matching the "auto-create if missing" AC-9 behavior.

### RULE-7
**Covers:** AC-5, AC-6, AC-7, AC-8
**MUST** build the file list with `Files.list(inputDir)` filtered to regular files where (a) the lowercased filename ends in `.csv`, (b) the filename does not start with `.`, and (c) the filename does not end in `.tmp`; the resulting list **MUST** be sorted with `Comparator.naturalOrder()` on the filename (case-sensitive).
**Reason:** Locks deterministic, non-recursive scanning and the exact hidden / partial-file exclusions named in the spec.

### RULE-8
**Covers:** AC-16, AC-49
**MUST** open every CSV file via `Files.newBufferedReader(path, StandardCharsets.UTF_8)` wrapped in a BOM-stripping reader that consumes a single leading `U+FEFF` if present, and **MUST** treat any `MalformedInputException` / `UnmappableCharacterException` as a fatal file error with reason `encoding error`.
**Reason:** Guarantees strict UTF-8 decoding (so non-UTF-8 bytes become fatal per AC-49) while keeping BOM handling silent (AC-16). `InputStreamReader`'s default CodingErrorAction must not be relaxed.

### RULE-9
**Covers:** AC-17, AC-18, AC-28
**MUST** parse `datetime` with a single `DateTimeFormatter` built from `DateTimeFormatterBuilder` accepting `yyyy-MM-dd` followed by either `'T'` or a space, then `HH:mm:ss`, with an optional `.SSS…` fractional-second section, and **MUST** treat any `DateTimeParseException` as a malformed row with reason `unparseable datetime`.
**Reason:** A single formatter avoids "first format wins" ordering bugs and keeps AC-17/AC-18 symmetrical.

### RULE-10
**Covers:** AC-19, AC-20, AC-21, AC-22, AC-29
**MUST** parse `temp` via `BigDecimal(String)` (which is `Locale.ROOT`-equivalent and rejects thousand separators) and **MUST** reject the row as malformed (`temp out of range`) when `value.precision() - value.scale() > 3` or `value.scale() > 2` or `value.abs() > BigDecimal("999.99")`; throw / wrap `NumberFormatException` as malformed with reason `non-numeric temp`.
**Reason:** Pre-flight check enforces the `DECIMAL(5,2)` boundary deterministically before SQL would otherwise raise a data-truncation warning that drivers can swallow.

### RULE-11
**Covers:** AC-23, AC-24, AC-25, AC-26
**MUST** trim `name` with `String.trim()` (Unicode whitespace) before length and emptiness checks, classify empty as malformed (`empty name`), and classify `length > 255` as malformed (`name too long`); equivalent rules apply to empty `datetime` / `temp` strings.
**Reason:** Pins the exact trimming semantics referenced by AC-23/24/25 and the empty-required-field branch of AC-26.

### RULE-12
**Covers:** AC-30, AC-31, AC-32, AC-33, AC-50
**MUST** persist rows via `NamedParameterJdbcTemplate` using the SQL `INSERT INTO temperature_readings (name, datetime, temp) VALUES (:name, :datetime, :temp) ON DUPLICATE KEY UPDATE id = id`, treating an `updateCount` of `0` as a duplicate and an `updateCount` of `1` as an insert; any other `SQLException` **MUST** be propagated as a fatal file error.
**Reason:** The `id = id` no-op upsert lets the unique-key constraint distinguish duplicate from insert without selecting first (AC-31) and without partial writes; it also keeps each row's commit atomic, satisfying AC-50's "fatal file error" branch when a different `SQLException` occurs mid-file.

### RULE-13
**Covers:** AC-32, AC-33, AC-34
**MUST** implement in-memory dedup with a `MutableSet<Pair<String, LocalDateTime>>` whose `name` component is normalized as `Normalizer.normalize(name, Form.NFD).replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)`, scoped to the entire run (not per file).
**Reason:** Matches `utf8mb4_0900_ai_ci` semantics (accent- and case-insensitive) used by the DB unique key (AC-34) and prevents redundant INSERTs across files within the same run (AC-33).

### RULE-14
**Covers:** AC-36, AC-37, AC-38, AC-39
**MUST** move files with `Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE)`; on `FileAlreadyExistsException` the target name **MUST** be recomputed as `<base>-<System.currentTimeMillis()>.<ext>` and retried once; any other `IOException` (including `AtomicMoveNotSupportedException`) **MUST** be logged at ERROR, counted in `failed_files`, and **MUST NOT** delete the source.
**Reason:** Locks the collision-resolution behavior (AC-38), preserves the original on cross-device failures (AC-39), and keeps the destination semantics in one place.

### RULE-15
**Covers:** AC-40, AC-41, AC-42, AC-47, AC-52
**MUST** emit summary lines to `System.out` (not via SLF4J) using a single `PrintStream` reference captured at runner start; per-file lines **MUST** be flushed immediately after each file completes and the `TOTAL` line **MUST** be the last line written.
**Reason:** Separates user-facing summary output from operator logging so AC-40/41/42/47 are observable on `stdout` regardless of log configuration, and guarantees the strict ordering required by AC-52.

### RULE-16
**Covers:** AC-12, AC-13, AC-14, AC-15, AC-22, AC-25, AC-26, AC-27, AC-28, AC-29, AC-31, AC-32, AC-33, AC-39, AC-44, AC-45, AC-50
**MUST** distinguish three error categories with distinct types: (a) `StartupError` — propagated as exceptions out of Spring context startup so the JVM exits non-zero; (b) `FatalFileError` — a sealed Kotlin class collected per file and consumed by `FileMover` / `SummaryPrinter`; (c) `RowError` — a sealed Kotlin class counted into `malformed`/`duplicates` without aborting the file; **MUST NOT** let row-level exceptions escape the per-row try/catch.
**Reason:** Maps directly to the spec's three failure tiers; using sealed classes makes the boundary statically checked and prevents accidental file-fail on a single bad row.

### RULE-17
**Covers:** AC-12, AC-22, AC-25, AC-26, AC-27, AC-28, AC-29, AC-31, AC-32, AC-33
**MUST** log row-level events through SLF4J at the levels specified: `WARN` for malformed rows with `file=<name> line=<n> reason=<text>`, `INFO` for duplicates with `file=<name> line=<n> key=<name>|<datetime>`; structured key=value form **MUST** be used and PII-bearing temperatures **MUST NOT** be included in the duplicate-key log.
**Reason:** Locks the log-level contract referenced by every malformed/duplicate AC and standardizes the message shape so tests can assert on it.

### RULE-18
**Covers:** AC-43
**MUST** let `ImporterRunner` return normally (no `exitCode` bean, no `System.exit`) once the summary is printed, regardless of `failed_files` count.
**Reason:** Spring Boot then exits with `0`; using `System.exit(non-zero)` on per-file failures would violate AC-43.

### RULE-19
**Covers:** AC-44, AC-45
**MUST NOT** catch `DataAccessException` or Flyway exceptions during `ApplicationContext` startup; they **MUST** propagate so Spring Boot exits non-zero before any summary is written.
**Reason:** AC-44/45 require non-zero exit *without* a `TOTAL` line; swallowing the exception in a `@Configuration` or `CommandLineRunner` would cause a misleading summary.

### RULE-20
**Covers:** AC-46
**MUST** place the Flyway migration at `src/main/resources/db/migration/V1__create_temperature_readings.sql` with the exact column definitions and unique key from AC-46; **MUST NOT** alter the migration file after merge (any change is a new `V_N__` migration).
**Reason:** Locks Flyway's default scan location and immutability invariant; AC-46 is satisfied by Spring Boot's auto-configured Flyway.

### RULE-21
**Covers:** AC-51
**MUST NOT** add `com.h2database:h2` or any other embedded-database dependency; integration tests **MUST** boot the Spring context against the `MySQLContainer` already exposed via `TestcontainersConfiguration` (`@ServiceConnection`).
**Reason:** Explicit spec exclusion ("Testcontainers MySQL only, no H2"); recording as a hard `MUST NOT` because the temptation is high and the dependency is small.

### RULE-22
**Covers:** AC-30, AC-31, AC-32, AC-33, AC-34, AC-46, AC-50
**SHOULD** organize tests as: pure JUnit 5 unit tests for `RowParser` / `RunDeduplicator` / `FileMover`; one `@SpringBootTest` slice per externally observable behavior (insert, DB duplicate, accent-insensitive duplicate, fatal file error, header-only file, end-to-end stdout summary) using `@Import(TestcontainersConfiguration::class)`.
**Reason:** Keeps the Testcontainers tax on the integration tier only; unit tests stay millisecond-fast.

### RULE-23
**Covers:** project-wide
**MAY** add `BigDecimal.setScale(2, RoundingMode.UNNECESSARY)` to the parsed `temp` before insert as a defensive check.
**Reason:** Redundant after RULE-10 but harmless; documents that no rounding is intended.

## Cross-Reference

| AC    | Rules                          |
|-------|--------------------------------|
| AC-1  | RULE-4                         |
| AC-2  | RULE-5                         |
| AC-3  | RULE-6                         |
| AC-4  | RULE-6                         |
| AC-5  | RULE-7                         |
| AC-6  | RULE-7                         |
| AC-7  | RULE-7                         |
| AC-8  | RULE-7                         |
| AC-9  | RULE-6                         |
| AC-10 | RULE-6                         |
| AC-11 | RULE-2                         |
| AC-12 | RULE-2, RULE-16, RULE-17       |
| AC-13 | RULE-2, RULE-16                |
| AC-14 | RULE-2, RULE-16                |
| AC-15 | RULE-2, RULE-16                |
| AC-16 | RULE-2, RULE-8                 |
| AC-17 | RULE-9                         |
| AC-18 | RULE-9                         |
| AC-19 | RULE-10                        |
| AC-20 | RULE-10                        |
| AC-21 | RULE-10                        |
| AC-22 | RULE-10, RULE-16, RULE-17      |
| AC-23 | RULE-11                        |
| AC-24 | RULE-11                        |
| AC-25 | RULE-11, RULE-16, RULE-17      |
| AC-26 | RULE-11, RULE-16, RULE-17      |
| AC-27 | RULE-2, RULE-16, RULE-17       |
| AC-28 | RULE-9, RULE-16, RULE-17       |
| AC-29 | RULE-10, RULE-16, RULE-17      |
| AC-30 | RULE-12, RULE-22               |
| AC-31 | RULE-12, RULE-16, RULE-17, RULE-22 |
| AC-32 | RULE-12, RULE-13, RULE-16, RULE-17, RULE-22 |
| AC-33 | RULE-12, RULE-13, RULE-16, RULE-17, RULE-22 |
| AC-34 | RULE-13, RULE-22               |
| AC-35 | RULE-2                         |
| AC-36 | RULE-14                        |
| AC-37 | RULE-14, RULE-16               |
| AC-38 | RULE-14                        |
| AC-39 | RULE-14, RULE-16               |
| AC-40 | RULE-15                        |
| AC-41 | RULE-15                        |
| AC-42 | RULE-15                        |
| AC-43 | RULE-18                        |
| AC-44 | RULE-16, RULE-19               |
| AC-45 | RULE-16, RULE-19               |
| AC-46 | RULE-20, RULE-22               |
| AC-47 | RULE-15                        |
| AC-48 | RULE-14, RULE-15               |
| AC-49 | RULE-8                         |
| AC-50 | RULE-12, RULE-14, RULE-16      |
| AC-51 | RULE-21, RULE-22               |
| AC-52 | RULE-15                        |

## Design Exclusions

- **Spring Batch / chunk-restart semantics:** out of scope — see `RULE-1`; re-runs of the importer over the same `processed/` are not designed to resume mid-file.
- **Concurrency control on the input directory:** out of scope per spec; no file-lock or advisory-lock mechanism is included.
- **Configurable CSV dialect:** out of scope per spec; the Commons CSV format is hard-coded to RFC 4180 with comma delimiter.
- **HTTP / web surface:** intentionally absent; the application stays as a non-web `CommandLineRunner` (Spring Boot auto-detects this since `spring-boot-starter-web` is not on the classpath).
- **Metrics / tracing:** out of scope; the only required observability surface is stdout + WARN/INFO/ERROR logs already covered by `RULE-15` / `RULE-17`.
- **Performance tuning (batch INSERT, statement caching):** out of scope per spec; `NamedParameterJdbcTemplate` defaults apply.
- **Schema evolution beyond V1:** out of scope for this feature; any future change is a new `V_N__` migration per `RULE-20`.

## External Dependencies

None. Both judgment calls surfaced by the Ecosystem Survey (Spring Batch adoption; CSV parser selection) were resolved via Interactive Resolution: Spring Batch was declined (`RULE-1`) and Apache Commons CSV was selected (`RULE-2`).
