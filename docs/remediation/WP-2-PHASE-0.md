# WP-2 — Phase 0 reproducibility and CI closure

**Date:** 2026-11-10

**Base:** `wp1-dependencies-upgraded-2026-11-10`

**Target tag:** `wp2-phase0-reproducibility-2026-11-10`
**Scope:** build bootstrap, toolchain enforcement, cross-platform CI, build reproducibility and test-result reporting only.

## Objective

Close the Phase 0 source-level gaps without changing the application. The bootstrap must use one exact Maven distribution and checksum on Linux/macOS and Windows; CI must exercise both launchers and the full reactor; reproducibility must be measurable rather than assumed.

## Maven launcher design

The repository contains custom bootstrap scripts (`mvnw`, `mvnw.cmd`, `scripts/bootstrap-maven.ps1`), **not** the official Apache Maven Wrapper implementation. The official wrapper distribution includes wrapper scripts/configuration and its wrapper JAR/bootstrap machinery. This repository instead intentionally retains its SHA-512-verified Apache binary bootstrap. Documentation now describes this accurately.

- Distribution: Apache Maven `3.10.0` binary tarball over HTTPS.
- Integrity: exact 128-character SHA-512 value in `.mvn/wrapper/maven-wrapper.properties`; verification occurs before extraction/cache installation. A SHA checksum detects corruption/tampering relative to the pinned value; it is not a substitute for an independently verified PGP signature.
- Cache: Maven user home under `wrapper/dists/apache-maven-3.10.0/<sha512>/`.
- Cache acceptance: stamp equals pinned SHA-512, executable exists, and `mvn --version` reports exactly 3.10.0.
- POSIX bootstrap: lock-directory coordination, bounded wait, temporary extraction, checksum verification and atomic move into cache.
- Windows bootstrap: equivalent HTTPS/checksum/version/cache checks. PowerShell writes only ASCII version/hash tokens; `cmd.exe` reconstructs the path from `USERPROFILE` / `MAVEN_USER_HOME`, avoiding a Unicode path round-trip through a console-codepage text file.
- The root Maven Enforcer rule now requires exactly `[3.10.0]`, in line with the pinned distribution, and Java remains `[21,22)`.

Apache identifies Maven 3.10.0 as its current Maven 3 stable release on the WP-2 date: https://maven.apache.org/download.cgi. The Apache release catalog lists its release date as 2026-10-01: https://releases.apache.org/catalog/maven.

## CI structure

The workflow `.github/workflows/verify.yml` has three independent jobs:

1. **Linux full verification:** shell syntax, offline launcher fixture test, real pinned Maven bootstrap, Java version, two consecutive `clean verify` executions and JAR/WAR hash comparison; uploads Surefire/Failsafe XML plus a test inventory.
2. **Windows full verification:** executes `mvnw.cmd --version`, then runs the complete `clean verify` reactor on JDK 21; uploads the test inventory and test XML.
3. **PostgreSQL integration:** starts PostgreSQL 16.6 and exercises LangGraph4j checkpoint plus CoALA memory JDBC backends using simulated LLM mode.

External GitHub Actions are pinned to immutable commit SHAs (reviewed releases on 2026-11-10):

| Action | Version | Commit SHA |
|---|---|---|
| `actions/checkout` | v7.0.1 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| `actions/setup-java` | v6.1.0 | `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` |
| `actions/upload-artifact` | v7.0.2 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` |

Release references: https://github.com/actions/checkout/releases, https://github.com/actions/setup-java/releases, https://github.com/actions/upload-artifact/releases.

## New verification tools

- `scripts/test-wrapper-bootstrap.sh`: creates a local fake Maven distribution and downloader to test cold-cache install, exact version, cache hit (no second download), bad SHA-512 rejection, HTTP URL rejection and duplicate-property rejection. It requires no external network.
- `scripts/verify-reproducible-build.sh`: runs two clean full-reactor `verify` builds with simulated/non-recording providers and compares SHA-256 of packaged JAR/WAR outputs. A difference fails the check. This measures same-runner repeatability; it does not alone prove reproducible builds across operating systems or machines.
- `scripts/report-test-inventory.py`: parses Surefire/Failsafe XML, totals tests/failures/errors/skips, writes a report and fails if no tests were executed or any failure/error is present.
- `.gitattributes`: normalizes shell, cmd and Maven/workflow file line endings.

## Local validation record

- [x] POSIX syntax checked for `mvnw` and all shell scripts with `sh -n`.
- [x] Offline bootstrap fixture passed cold-cache installation, exact-version check, cache reuse, SHA-512 mismatch rejection, HTTPS enforcement and duplicate-property rejection.
- [x] Test inventory Python source compiled and generated the expected totals from a synthetic Surefire XML report.
- [x] All four Maven POMs parsed as XML.
- [x] GitHub Actions workflow parsed as YAML; expected Linux, Windows and PostgreSQL jobs exist.
- [x] Every external GitHub Action is pinned to a 40-character commit SHA.
- [x] No Java production source changed; no graph, memory, domain, prompt or action behavior was modified.
- [ ] Execute actual Maven 3.10.0 bootstrap on Linux.
- [ ] Execute PowerShell/cmd bootstrap on Windows.
- [ ] Execute full reactor on both operating systems.
- [ ] Prove two real clean builds yield byte-identical packages.
- [ ] Execute PostgreSQL backend tests.
- [ ] Review hosted CI artifacts and test inventory.

## Environment limitation and status

The implementation environment has JDK 21 but no system Maven and no PowerShell runtime. A live `./mvnw --version` attempt cannot resolve `downloads.apache.org` (DNS error) and stops before Maven starts. The actual Windows bootstrap and all Maven/JUnit/PostgreSQL runs therefore remain hosted-CI acceptance gates. The offline fixture verifies launcher control flow only; it is not evidence that the real Maven archive was downloaded or that the project compiles.

**WP-2 source work is complete and independently checkpointed. Phase 0 is not acceptance-certified until the Linux, Windows and PostgreSQL workflow runs are green and the two-build reproducibility comparison passes.**

## Scope boundary

No changes were made to Java production sources, memory contracts or ranking, consolidation, graph topology, checkpoint schema, action authorization, prompts, model behavior, LangChain4j comparison semantics or the WP-1 dependency versions. The next package may address Phase 1 only after Phase 0 acceptance is green.
