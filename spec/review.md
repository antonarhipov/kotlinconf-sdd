# Spec Review: Temperature CSV Importer

## Summary
- Feature: Temperature CSV Importer
- Verdict: PASS WITH CONDITIONS
- Counts: 0 blockers, 1 major, 1 minor
- Action: Apply the Fix Plan below (single rerun of the `rules` skill), then rerun `review`.

## Discipline Check

Sampled the trace chain end-to-end and found it clean: spot-checked `Covers:` lines on AC-1 (B-1), AC-22 (B-10, B-11), AC-46 (B-24), and the two non-B references (AC-49 → "Explicit assumptions, File encoding"; AC-51 → "Out of scope, H2 …") — all point to real targets. Every B-1 … B-26 appears in at least one AC `Covers:` line. The `rules.md` Cross-Reference lists AC-1 … AC-52 with no orphans; spot-checks AC-1→RULE-4, AC-35→RULE-2, and AC-46→RULE-20/RULE-22 match the rule bodies. Every AC uses an EARS template (`When …`, `If … then …`, `While …`); no template-free entries.

## Conflicts

None. Spot-checked the highest-risk seams:

- `AC-31` ("the importer shall not insert a new row") vs `RULE-12` (`INSERT … ON DUPLICATE KEY UPDATE id = id`, treating `updateCount=0` as duplicate) — the `id=id` no-op upsert is consistent with "no new row" semantics under MySQL's affected-rows accounting and is not in conflict.
- `AC-43` ("exit with code 0") vs `RULE-18` ("return normally, no `System.exit`") — aligned.
- `RULE-1` declines Spring Batch; no other rule references Spring Batch APIs. No negative-decision violations.
- Design section says synchronous `CommandLineRunner`; no rule introduces async, messaging, or scheduling. Consistent.
- `Out of scope` / `Coverage exclusions` / `Design exclusions` items are not reintroduced.

## Codebase Grounding

Findings below; remainder confirmed.

### MAJOR-1: Rule requires Bean Validation that is not on the classpath
- **Source:** `spec/rules.md` (RULE-5); `build.gradle.kts`
- **Issue:** RULE-5 states: "**MUST** annotate `ImporterProperties.inputDir` with `@field:NotNull` (or equivalent Bean Validation) and enable `@ConfigurationPropertiesScan` so a missing `importer.input-dir` causes a `BindException` at startup before any `CommandLineRunner` runs." However, `build.gradle.kts` declares only `spring-boot-starter-data-jdbc`, `spring-boot-starter-flyway`, `flyway-mysql`, `kotlin-reflect`, and runtime/test deps — **no `spring-boot-starter-validation`**, so `@NotNull` from `jakarta.validation.constraints` is not on the runtime classpath and `@Validated`/JSR-303 binding cannot run.
- **Impact:** As written, RULE-5 cannot be implemented without first adding `org.springframework.boot:spring-boot-starter-validation`. The implementing agent will either silently add an unrecorded dependency, drop the annotation and leave AC-2 enforced only by Kotlin null-safety on `Path` (different error shape — `BindException` wraps a `ConversionFailedException`, not a constraint violation), or get stuck. Either way the rule does not match reality and AC-2's "log an ERROR identifying the missing property" guarantee becomes fuzzy.
- **Resolution:** Rerun the `rules` skill to either (a) add a rule introducing `spring-boot-starter-validation` and keep RULE-5 as-is, or (b) restate RULE-5 to rely on Kotlin's non-nullable `Path` binding (the binder already throws on null property without validation), and update the `Reason:` accordingly. Update the Key dependencies subsection of the Design section in either case.

### MINOR-1: Apache Commons CSV builder method name is potentially stale
- **Source:** `spec/rules.md` (RULE-2)
- **Issue:** RULE-2 prescribes `CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setAllowDuplicateHeaderNames(false).build()`. In Commons CSV 1.10+, the API renamed `setAllowDuplicateHeaderNames(boolean)` to `setDuplicateHeaderMode(DuplicateHeaderMode)` (with `ALLOW_ALL` / `ALLOW_EMPTY` / `DISALLOW`).
- **Impact:** A literal copy of the rule into code will fail to compile against the latest Commons CSV. Will be corrected at first compile, no behavioral effect.
- **Resolution:** When `rules` is next rerun for MAJOR-1, also generalize RULE-2's builder snippet (e.g., "disallow duplicate headers via the duplicate-header-mode setting") rather than pinning a deprecated method name.

## EARS ↔ Test Strategy

Every EARS pattern present in `criteria.md` has a corresponding test approach in `rules.md`:

- **Ubiquitous** invariants (e.g., AC-51 Testcontainers MySQL) → `RULE-21` + `RULE-22` (integration tier with `@ServiceConnection`).
- **Event-driven** (`When …`) ACs (AC-1, AC-30, AC-36, AC-40, AC-42, AC-46, AC-47, AC-48, AC-52) → `RULE-22`'s `@SpringBootTest` slices "insert", "header-only file", and "end-to-end stdout summary".
- **Unwanted-behavior** (`If …, then …`) ACs (AC-2, AC-3, AC-4, AC-14, AC-15, AC-22, AC-25, AC-26, AC-27, AC-28, AC-29, AC-31, AC-32, AC-33, AC-37, AC-39, AC-44, AC-45, AC-49, AC-50) → covered by the "DB duplicate", "fatal file error" slices plus `RowParser` unit tests that assert WARN/INFO log shape per `RULE-17`.
- **Boundary triplets** (AC-20/21/22, AC-23/24/25) → unit tests on `RowParser` per `RULE-22` cover within/at/beyond directly.
- **Negative outcomes** (AC-2, AC-44, AC-45 demanding "without printing a TOTAL line"; AC-39 demanding "leave the original file in place") → `RULE-19` (no exception swallowing at startup) and `RULE-14` (do-not-delete-source on `IOException`) make the prohibited outcome assertable.

No EARS pattern is left without a fitting test shape.

## Risk Hotspots

1. **`INSERT … ON DUPLICATE KEY UPDATE id = id` affected-rows semantics** — Area: `TemperatureReadingRepository` (RULE-12, AC-31). Reason: MySQL Connector/J's `useAffectedRows` flag changes the return: with the default `useAffectedRows=false`, a no-op upsert returns `0` (matches the rule's "duplicate ≡ updateCount=0"); if the flag is flipped or upgraded, semantics shift. Mitigation: an integration test that inserts a duplicate and asserts both the absence of a second row and the duplicate counter increment — never relying on the return value alone.

2. **In-memory dedup key normalization vs MySQL collation drift** — Area: `RunDeduplicator` (RULE-13, AC-34). Reason: NFD-strip-marks + `lowercase(Locale.ROOT)` approximates `utf8mb4_0900_ai_ci` but is not byte-identical (e.g., German ß, Turkish dotted/dotless i, half-width kana). A row deduped in memory could still be a non-duplicate to MySQL, or vice versa. Mitigation: integration test pairs (`Café`/`cafe`, `MASSE`/`maße`, `İstanbul`/`istanbul`) asserting both detection paths agree; document known divergences.

3. **`Files.move` with `ATOMIC_MOVE` across container/host filesystems** — Area: `FileMover` (RULE-14, AC-38/39). Reason: `ATOMIC_MOVE` raises `AtomicMoveNotSupportedException` on cross-device moves common in Docker bind-mounts where `processed/` may live on a different layer than the drop zone. Mitigation: fall back to non-atomic `Files.move` for the collision-rename retry, and exercise the failure path with a Testcontainers volume layout that is known to be cross-device, asserting AC-39's "original file in place".

4. **UTF-8 strict decoding interacting with Commons CSV's lazy reads** — Area: `CsvFileProcessor` opening + BOM strip (RULE-8, AC-49). Reason: `MalformedInputException` is raised lazily on the offending bytes, possibly mid-file after some rows were already inserted. AC-49 expects a fatal file error and AC-50 expects file-level atomicity, but rows already committed cannot be rolled back. Mitigation: decide explicitly — either pre-scan the file for valid UTF-8 before opening for parsing, or accept partial inserts on mid-file encoding errors and surface that in the `Reason:` of the AC-49 path. Worth a one-line spec clarification if pre-scan is rejected.

5. **`@ConfigurationProperties("importer")` startup error shape** — Area: `ImporterProperties` (RULE-4, RULE-5, AC-2). Reason: tightly coupled to MAJOR-1. Even after rules is rerun, the actual exception type and log message that signal "missing property" differ between Kotlin null-binding (`BindException` wrapping a `ConversionFailedException`) and JSR-303 (`BindValidationException` with constraint violations); tests asserting the exact log line will be brittle. Mitigation: assert exit code + the property name appearing somewhere in stderr, not the exception class name.

## Fix Plan

Execution order: rules → review

### rules (rerun rules)
1. [cascades] MAJOR-1: Either add `org.springframework.boot:spring-boot-starter-validation` as a dependency in the Design section's "Key dependencies" and keep RULE-5 as-is, or rewrite RULE-5 to enforce non-null `importer.input-dir` via Kotlin's non-nullable `Path` binding (no Bean Validation), update its `Reason:` to reference the binder-level null check, and remove `@field:NotNull` from the rule statement. Cross-Reference for AC-2 stays mapped to RULE-5 either way.
   Downstream rerun note: no spec/criteria changes; only `review` reruns after this.
2. [localized] MINOR-1 (informational only, not in the verdict path): when editing RULE-2 anyway, replace `setAllowDuplicateHeaderNames(false)` with the version-agnostic "disallow duplicate headers" phrasing so the rule survives Commons CSV minor upgrades. Skip if RULE-2 is not otherwise touched.

### review
Rerun once the rules fix is in.
