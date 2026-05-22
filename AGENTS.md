# AGENTS.md

This repository uses spec-driven development (SDD).

## Working on features

**To start a new feature**: write `spec/proposal.md`, then run the SDD pipeline skills in order: `spec`, `criteria`, `rules`, `review`, `tasks`. Each step produces an artifact under `/spec/` and gates the next step. Do not skip steps; downstream skills validate that upstream artifacts exist and are well-formed.

**To implement a feature**: invoke the `execute` skill. It reads `spec/tasks.yaml` and executes the plan task by task, validating each against the acceptance criteria and halting at checkpoints for approval.

## Constraints

- The `/spec/` directory is read-only during implementation. The single exception is `spec/status.md`, which the `execute` skill maintains.
- Implementation follows the plan as written. Deviations require a `REVISE` or `ROLLBACK` response at the next checkpoint, or a new pipeline run.

## File layout

| File               | Purpose                                            |
|--------------------|----------------------------------------------------|
| `spec/proposal.md` | Original feature request                           |
| `spec/spec.md`     | Resolved requirements                              |
| `spec/criteria.md` | Acceptance criteria (AC-N)                         |
| `spec/rules.md`    | Technical constraints (RULE-N)                     |
| `spec/review.md`   | Pipeline verdict and risk hotspots                 |
| `spec/tasks.yaml`  | Execution plan (phases, tasks)                     |
| `spec/status.md`   | Progress tracking, maintained by the execute skill |
