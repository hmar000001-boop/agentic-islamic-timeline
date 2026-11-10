# Phase 0 — Reproducibility and baseline

## Scope delivered

- Added custom repository bootstrap launchers for POSIX (`./mvnw`) and Windows (`mvnw.cmd`); these are explicitly **not** the official Apache Maven Wrapper implementation.
- Pinned Apache Maven 3.10.0 by HTTPS distribution URL and the Apache-published SHA-512 checksum. Both launchers validate the checksum, extracted layout and exact Maven version before caching the distribution. The Windows launcher returns only ASCII version/hash tokens to `cmd.exe`, which reconstructs the Maven executable path from the original user-home environment variable.
- Enforced the exact Maven 3.10.0 version and JDK 21 at the Maven reactor boundary.
- Added shell fixture tests for a cold-cache install, checksum rejection, exact-version validation and cache reuse without requiring network access.
- Added a two-clean-build reproducibility check that compares SHA-256 hashes of all packaged JAR/WAR outputs.
- Added a Surefire/Failsafe XML test-inventory reporter for CI artifacts.
- Expanded CI to validate the launcher and full reactor on Linux and Windows, plus PostgreSQL-backed LangGraph4j/memory tests. Workflow actions are pinned to reviewed immutable commits.
- Added `.gitattributes` to normalize cross-platform line endings.
- Retained Maven's fixed `project.build.outputTimestamp`, Java 21 compiler settings and Maven user-cache installation; no Maven binaries are committed.

## Explicitly preserved

- No Java production source, domain contract, graph node/edge, checkpoint schema, memory implementation/policy, prompt, action policy or framework comparison semantics changed in WP-2.
- The `retail-agent-langchain4j` Agentic module and its source/dependencies remain unchanged.
- Maven remains pinned at 3.10.0, which Apache identifies as the current Maven 3 release. The exact version is enforced rather than accepting arbitrary later versions.

## Local verification

- POSIX shell syntax checks: `mvnw` and all shell scripts.
- Offline fixture test: cold-cache bootstrap, exact version, single download/cache reuse, and SHA-512 mismatch rejection.
- Python syntax and synthetic Surefire report-inventory checks.
- XML parsing for every Maven POM; workflow YAML parsing and structural assertions.
- Git whitespace/diff checks and archive-to-tag content comparison.

## External acceptance still outstanding

1. Execute the real pinned Maven distribution download on Linux and Windows hosted runners.
2. Run the full Maven reactor with JDK 21 on both operating systems.
3. Run the two-clean-build artifact comparison on Linux; investigate any differing packaged artifact hashes.
4. Run the PostgreSQL backend job and record the Surefire/Failsafe inventory.
5. Run dependency-tree and vulnerability checks as part of WP-1 acceptance; WP-2 does not claim the dependency graph is vulnerability-free.

This environment cannot resolve `downloads.apache.org`, has no system Maven and has no PowerShell executable, so real Maven invocation and Windows PowerShell execution cannot be claimed locally. CI must be green before Phase 0 is certified at acceptance level.

## Next phase

Phase 1: validate stable domain/application contracts, typed evidence/action/plan/decision models and framework-independent domain tests. Do not alter the memory or graph behavior as part of Phase 0.
