# kotlinconf-sdd

Demo project for a conference talk about **Specification-Driven Development (SDD)** with an AI-assisted workflow.

This repository is intentionally organized to show how a feature evolves across three branches:

- **`main`** — the starting point / initial project state
- **`spec`** — the branch where the specification artifacts are added and refined
- **`implementation`** — the branch where the agent implements the specification

The repository is handed over to conference attendees so they can inspect the project after the talk, compare branches, and follow the workflow end to end.

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

- `.claude/` — additional assistant-related project configuration
- `.junie/` — workflow command definitions used during the SDD process
- `build.gradle.kts` — Gradle build configuration for Kotlin + Spring Boot
- `compose.yaml` — local MySQL service definition for Docker Compose
- `gradle/`, `gradlew`, `gradlew.bat` — Gradle wrapper files
- `settings.gradle.kts` — Gradle project settings
- `spec/` — specification artifacts for the demo feature
- `src/` — application and test source code

### `.junie/commands`

This directory contains the command prompts that drive the workflow:

- `01-spec.md` — create or refine the functional specification
- `02-criteria.md` — define acceptance / verification criteria
- `03-rules.md` — establish implementation rules and constraints
- `04-review.md` — review the specification package for gaps or issues
- `05-plan.md` — break the work into an implementation plan / tasks
- `06-execute.md` — implement the plan

These files are the heart of the demo: they show how the assistant is guided step by step instead of jumping directly to code.

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

On the **`spec`** branch, the workflow commands in `.junie/commands` are used to turn the proposal into a complete implementation-ready specification.

Typical progression:

1. **spec** — clarify the feature and scope
2. **criteria** — define acceptance criteria and expected outcomes
3. **rules** — capture constraints, architecture guidance, and non-goals
4. **review** — inspect the spec package for ambiguity, gaps, and contradictions
5. **plan / tasks** — turn the reviewed specification into actionable implementation steps
6. **execute** — implement the agreed plan

This staged approach makes the development process explicit and reviewable.

### 3. Implement from the spec

On the **`implementation`** branch, the agent uses the produced specification artifacts to implement the feature.

This separation is deliberate:

- `main` shows the untouched starting point
- `spec` shows how the requirements were shaped
- `implementation` shows the resulting code

For attendees, comparing these branches helps answer three different questions:

- **What did we start with?** → `main`
- **How was the work specified?** → `spec`
- **What was finally built?** → `implementation`

## How to explore the demo after the talk

A good way to inspect the repository is:

1. Open **`main`** to see the baseline project.
2. Switch to **`spec`** to review the specification artifacts and workflow outputs.
3. Switch to **`implementation`** to inspect the completed implementation.
4. Compare branches to see how the repository changes over time.
5. Read the command files in `.junie/commands` to understand how each workflow step is prompted.

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

That makes the process easier to explain, review, and reproduce — which is exactly what this repository is meant to capture for conference attendees.
