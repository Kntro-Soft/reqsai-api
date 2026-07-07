# 0016. Java code formatting with Spotless + Eclipse formatter

- Status: Accepted
- Date: 2026-06-18
- Deciders: Kntro-Soft team

## Context

The codebase has no enforced Java formatter, leading to inconsistent indentation, import ordering,
and brace style across bounded contexts maintained by different team members. Diffs include
whitespace noise that makes code review harder. A formatter enforced in CI eliminates the problem.

The two most popular Java formatters for Gradle are Google Java Format and Palantir Java Format.
Both use internal javac APIs (`Log.DeferredDiagnosticHandler`) that were removed in JDK 23. This
project uses JDK 25 (enforced by the Gradle toolchain), making both formatters incompatible.

## Decision

Use **Spotless** (`com.diffplug.spotless:8.6.0`) with the **Eclipse formatter** (`eclipse()`), which
is fully compatible with JDK 25 and does not rely on internal javac APIs.

- `spotlessCheck` is wired to run before `compileJava` — unformatted code fails the build.
- `spotlessApply` is the developer command to auto-format locally.
- The Lefthook pre-commit hook runs `spotlessApply` on staged `*.java` files before every commit.
- Kotlin Gradle DSL files (`*.gradle.kts`) are formatted with `ktfmt`.
- In CI, `spotlessCheck` is part of the `build` task chain (via `compileJava` dependency).

## Consequences

- Consistent style across all bounded contexts; no more whitespace noise in diffs.
- Eclipse formatter is less opinionated than Google/Palantir — produces readable output close to
  the IntelliJ default, which the team already uses.
- The first adoption requires a one-time "format all" commit on the branch where formatting is
  introduced; subsequent PRs only touch changed files via the pre-commit hook.
- If the team migrates to a JDK version that restores the removed APIs, switching to
  Palantir/Google format is a one-line change in `build.gradle.kts`.
