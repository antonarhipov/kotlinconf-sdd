# kotlinconf-sdd

Demo project for a conference talk about **Specification-Driven Development (SDD)** with an AI-assisted workflow.

This repository is intentionally organized to show how a feature evolves across three branches:

- **`main`** — the starting point / initial project state
- **`spec`** — the branch where the specification artifacts are added and refined
- **`implementation`** — the branch where the agent implements the specification

## What this project demonstrates

The demo application is a Kotlin CLI-style Spring Boot application intended to import temperature data from CSV files into a MySQL database.

The initial proposal in `spec/proposal.md` defines the target behavior at a high level:

- extract `name`, `datetime`, and `temp` columns from CSV files
- treat `name` + `datetime` as a unique key
- detect and ignore duplicates
- print a summary of inserted and duplicate records
- use Testcontainers for integration testing instead of H2

On the `main` branch, the project is mostly a generated Spring Boot/Kotlin skeleton. The interesting part of this repository is not only the final code, but the **workflow** used to move from idea → specification → implementation.

## Repository structure

### Root

- `.claude/` — Claude Code configuration and SDD agent skills
- `.junie/` — Junie configuration and SDD agent skills
- `build.gradle.kts` — Gradle build configuration for Kotlin + Spring Boot
- `compose.yaml` — local MySQL service definition for Docker Compose
- `gradle/`, `gradlew`, `gradlew.bat` — Gradle wrapper files
- `settings.gradle.kts` — Gradle project settings
- `spec/` — specification artifacts for the demo feature
- `src/` — application and test source code

### AI assistant configuration

This project is preconfigured for both **Junie** and **Claude Code**.

The `.junie` and `.claude` directories contain equivalent SDD-oriented agent skills and command definitions so the same workflow can be demonstrated with either assistant environment.

### Included SDD skills and commands

The command set included in this repository guides the workflow step by step:

- **spec** — create or refine the functional specification
- **criteria** — define acceptance and verification criteria
- **rules** — capture implementation rules, constraints, and non-goals
- **review** — inspect the specification package for ambiguity, gaps, or contradictions
- **tasks / plan** — break the work into actionable implementation steps
- **execute** — implement the agreed plan

In `.junie/commands`, these appear as:

- `01-spec.md`
- `02-criteria.md`
- `03-rules.md`
- `04-review.md`
- `05-plan.md`
- `06-execute.md`

These files are the core of the demo: they show how the assistant is guided deliberately through a structured workflow instead of jumping straight to implementation.

### `spec/`

- `proposal.md` — the starting feature proposal for the temperature-import application

Depending on the branch, this directory may contain additional artifacts produced during the workflow.

### `src/main`

- `src/main/kotlin/org/example/sdd/Application.kt` — Spring Boot application entry point
- `src/main/resources/application.properties` — basic application configuration

### `src/test`

- `src/test/kotlin/org/example/sdd/ApplicationTests.kt` — context load test
- `src/test/kotlin/org/example/sdd/TestApplication.kt` — test launcher wiring
- `src/test/kotlin/org/example/sdd/TestcontainersConfiguration.kt` — MySQL Testcontainers setup for tests

## Technology stack

The project uses:

- Kotlin
- Spring Boot
- Spring Data JDBC
- Flyway
- MySQL
- Testcontainers
- Gradle Kotlin DSL
- Java 21 toolchain

## SDD workflow used in this repository

This repository demonstrates a specification-driven workflow where the feature is developed in clearly separated stages.

### 1. Start from the proposal

Work begins with a short problem statement in `spec/proposal.md`.

This proposal describes *what* the software should do, without immediately jumping to implementation details.

### 2. Build the specification package

On the **`spec`** branch, the workflow commands in `.junie/commands` and their equivalents in `.claude` are used to turn the proposal into a complete, implementation-ready specification.

Typical progression:

```text
proposal.md
    |
    v
[spec] ---------> spec.md
    |
    v
[criteria] -----> criteria.md
    |
    v
[rules] --------> rules.md
    |
    v
[review] -------> review.md
    |
    v
[tasks / plan] -> plan.md / tasks.md
    |
    v
[execute] ------> implementation on the implementation branch
```

Artifact flow by step:

1. **spec** → produces the feature specification (`spec.md`)
2. **criteria** → produces acceptance criteria (`criteria.md`)
3. **rules** → produces implementation rules and constraints (`rules.md`)
4. **review** → produces a review of the specification package (`review.md`)
5. **tasks / plan** → produces an implementation breakdown (`plan.md` and/or `tasks.md`)
6. **execute** → produces the actual implementation from the approved specification artifacts

This staged approach makes the development process explicit and reviewable.

### 3. Implement from the spec

On the **`implementation`** branch, the agent uses the produced specification artifacts to implement the feature.

This separation is deliberate:

- `main` shows the untouched starting point
- `spec` shows how the requirements were shaped
- `implementation` shows the resulting code

## Running the project

This repository includes the standard Gradle wrapper and a Docker Compose file for MySQL.

Typical commands:

```bash
./gradlew test
./gradlew bootRun
```

If you want to start the local MySQL service separately:

```bash
docker compose up
```

> Note: branch contents may differ. The `main` branch is intentionally minimal, while later branches contain more of the full workflow and implementation.

## Why this repository matters

The goal of this demo is not only to show a Kotlin/Spring application, but to demonstrate a repeatable way to collaborate with an AI agent:

- start with a proposal
- turn it into a precise specification
- review and constrain the work
- break it into tasks
- execute against that specification

That makes the process easier to explain, review, and reproduce.
