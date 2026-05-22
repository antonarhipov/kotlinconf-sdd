# Spec Review: Temperature CSV Importer

## Summary
- Feature: Temperature CSV Importer
- Verdict: PASS
- Counts: 0 blockers, 0 majors, 0 minors
- Action: Proceed to the `plan` step; no upstream rerun required.

## Discipline Check

Sampled the trace chain end-to-end and found it clean: spot-checked `Covers:` on AC-1 (B-1), AC-22 (B-10, B-11), AC-46 (B-24), and the two non-B references (AC-49 → "Explicit assumptions, File encoding"; AC-51 → "Out of scope, H2 …") — all resolve to real targets. Every B-1 … B-26 appears in at least one AC `Covers:` line. `rules.md`'s Cross-Reference covers AC-1 … AC-52 with no orphans; spot-checks AC-1→RULE-4, AC-2→RULE-5, AC-35→RULE-2, AC-46→RULE-20/22 match the rule bodies. Every AC uses an EARS template (`When …`, `If …, then …`, `While …`); no template-free entries.

## Conflicts

None. Spot-checked the highest-risk seams:

- `AC-31` ("the importer shall not insert a new row") vs `RULE-12` (`INSERT … ON DUPLICATE KEY UPDATE id = id`, treating `updateCount = 0` as duplicate) — consistent with MySQL's affected-rows accounting; no conflict.
- `AC-43` ("exit with code `0`") vs `RULE-18` ("return normally … no `System.exit`") — aligned.
- `RULE-1` declines Spring Batch and `RULE-5` declines `spring-boot-starter-validation`; no other rule references Spring Batch APIs or Bean Validation annotations. No negative-decision violations.
- Design section describes a synchronous `CommandLineRunner`; no rule introduces async, messaging, or scheduling.
- `Out of scope` (spec), `Coverage exclusions` (criteria), and `Design exclusions` (rules) items are not reintroduced elsewhere.

## Codebase Grounding

The composed design fits the project state. `build.gradle.kts` declares Kotlin 2.2.21, Spring Boot 4.0.6, `spring-boot-starter-data-jdbc`, `spring-boot-starter-flyway` + `flyway-mysql`, MySQL Connector/J, and Testcontainers MySQL — every "Use existing" item in `rules.md` is on the classpath. The single new dependency (`org.apache.commons:commons-csv`) is published on Maven Central with an Apache-2.0 license, no conflicts. RULE-5 no longer requires Bean Validation, so the absence of `spring-boot-starter-validation` in the build is now intentional and the rule pins it as a negative decision; Kotlin's non-nullable `Path` binding on `@ConfigurationProperties` is supported by Spring Boot 4's `Binder` and produces a startup `BindException` for missing `importer.input-dir`, matching AC-2. Package `org.example.sdd.importer` is creatable under the existing `org.example.sdd` root. The test setup (`TestcontainersConfiguration` already wired with `@ServiceConnection`, `kotlin-test-junit5`) supports the `@SpringBootTest` slices described in RULE-22.

## EARS ↔ Test Strategy

Every EARS pattern present in `criteria.md` has a corresponding test approach in `rules.md`:

- **Event-driven** (`When …`) ACs (AC-1, AC-30, AC-36, AC-40, AC-42, AC-46, AC-47, AC-48, AC-52) → RULE-22's `@SpringBootTest` slices "insert", "header-only file", "end-to-end stdout summary".
- **Unwanted-behavior** (`If …, then …`) ACs (AC-2, AC-3, AC-4, AC-14, AC-15, AC-22, AC-25, AC-26, AC-27, AC-28, AC-29, AC-31, AC-32, AC-33, AC-37, AC-39, AC-44, AC-45, AC-49, AC-50) → RULE-22 "DB duplicate" / "fatal file error" slices plus `RowParser` unit tests asserting WARN/INFO log shape per RULE-17.
- **Ubiquitous** invariants (AC-51 Testcontainers MySQL) → RULE-21 + RULE-22 with `@ServiceConnection`.
- **Boundary triplets** (AC-20/21/22, AC-23/24/25) → `RowParser` unit tests in RULE-22 cover within/at/beyond directly.
- **Negative outcomes** (AC-2, AC-39, AC-44, AC-45 — "without printing a TOTAL line" / "leave the original file in place") → RULE-19 (no exception swallowing at startup) and RULE-14 (do-not-delete-source on `IOException`) make the prohibited outcome assertable.

No EARS pattern is left without a fitting test shape.

## Risk Hotspots

1. **`INSERT … ON DUPLICATE KEY UPDATE id = id` affected-rows semantics** — Area: `TemperatureReadingRepository` (RULE-12, AC-31). Reason: MySQL Connector/J's `useAffectedRows` flag changes the return value: with the default `useAffectedRows=false`, a no-op upsert returns `0` (matches the rule's "duplicate ≡ updateCount = 0"); flipping the flag (or a JDBC URL parameter creeping in) silently breaks duplicate accounting. Mitigation: integration test that inserts a duplicate and asserts both the absence of a second row and the duplicate counter increment — never relying on the return value alone; also assert the effective connection-string flags in a smoke test.

2. **In-memory dedup-key normalization vs MySQL collation drift** — Area: `RunDeduplicator` (RULE-13, AC-34). Reason: `NFD`-strip-marks + `lowercase(Locale.ROOT)` approximates `utf8mb4_0900_ai_ci` but is not byte-identical (German `ß` vs `ss`, Turkish dotted/dotless `i`, half-width kana). A row deduped in memory could still be a non-duplicate to MySQL or vice-versa. Mitigation: integration test pairs (`Café`/`cafe`, `MASSE`/`maße`, `İstanbul`/`istanbul`) asserting both detection paths agree; document known divergences in the importer's KDoc.

3. **`Files.move` with `ATOMIC_MOVE` across container/host filesystems** — Area: `FileMover` (RULE-14, AC-38/AC-39). Reason: `ATOMIC_MOVE` raises `AtomicMoveNotSupportedException` on cross-device moves common in Docker bind-mounts where `processed/` may live on a different layer than the drop zone. Mitigation: fall back to non-atomic `Files.move` (or `REPLACE_EXISTING`-free retry) for the collision-rename retry, and exercise the failure path with a Testcontainers volume layout known to be cross-device, asserting AC-39's "original file in place".

4. **UTF-8 strict decoding interacting with Commons CSV's lazy reads** — Area: `CsvFileProcessor` opening + BOM strip (RULE-8, AC-49, AC-50). Reason: `MalformedInputException` is raised lazily on the offending bytes, possibly mid-file after some rows have already been committed by RULE-12. AC-49 expects a fatal file error and AC-50 expects file-level atomicity for non-duplicate DB errors, but rows already committed cannot be rolled back row-by-row. Mitigation: choose explicitly — either pre-scan the file for valid UTF-8 before opening for parsing, or accept partial inserts on mid-file encoding errors and surface that in the failed-file `FAILED:` reason; document the choice in the importer's KDoc.

5. **Startup error shape for missing `importer.input-dir`** — Area: `ImporterProperties` (RULE-4, RULE-5, AC-2). Reason: With the Bean Validation path declined, the observable failure is a Spring `BindException` wrapping a `ConverterNotFoundException` / null-binding failure; the exact exception class and message wording can shift across Spring Boot patch releases. Mitigation: assert on the **exit code** + the property name (`importer.input-dir`) appearing in stderr — never on the exception class name.
