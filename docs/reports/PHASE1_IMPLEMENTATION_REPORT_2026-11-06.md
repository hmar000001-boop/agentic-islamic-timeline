# Phase 1 — implementation report

**Project:** Timeline agentique  
**Date:** 6 November 2026  
**Version:** `0.1.0-phase1`  
**Verdict:** Source foundation prepared; Android build verification pending.

## 1. What was delivered

The source project is now a real multi-module Android Gradle project under `agentic-islamic-timeline/`, rather than an architecture-only directory. The project has eight Gradle modules, a Compose application shell, Arabic/English resources, local persistence contracts, a Room schema v1, SQLCipher integration adapter, Android Keystore database-key envelope, initial authorization policy, tests, ADRs, documentation, and GitHub Actions CI.

The database schema contains 24 entities. A static check confirmed that all 24 are declared with `@Entity` and that the same 24 classes are registered in `AppDatabase`. The schema export location is configured, but the JSON snapshot is processor-generated and is not fabricated in the archive.

The offline and Play distribution flavors currently have no `INTERNET` permission. The manifest disables Android backup and references explicit exclusion rules for cloud backup and device transfer. No analytics, accounts, advertising, billing or product backend has been added.

## 2. Security baseline

The database key manager creates a random 256-bit SQLCipher passphrase. It protects the passphrase with AES-GCM using a non-exportable Android Keystore key and stores the encrypted envelope under `noBackupFilesDir`. If a database exists but its key envelope is absent or cannot be authenticated/decrypted, the application fails closed; it does not create a new key, delete the database, or fall back to plaintext.

The test for key-envelope persistence now injects a unique Keystore alias and unique envelope filename, then removes both, so the test does not reuse the application alias. The explicit backup policy and private file location reduce backup exposure but do not replace the required device-level recovery and migration tests.

The authorization implementation is intentionally limited to a fail-closed policy baseline: expiration denies, sensitive/high-impact/external/financial/destructive/irreversible actions require strong confirmation, an exact one-time approval can scope an eligible low-impact action, and all other mutations ask each time. The full immutable proposal fingerprint, persistent scoped-grant evaluator, atomic claim, executor and outbox reconciliation remain later work.

## 3. Toolchain snapshot

| Component | Version |
|---|---:|
| Android Gradle Plugin | 9.3.1 |
| Gradle distribution | 9.5.0 |
| Kotlin Gradle Plugin | 2.4.21 |
| KSP | 2.3.10 |
| Compose compiler plugin | 2.4.21 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| SQLCipher Android | 4.20.0 |
| AndroidX SQLite | 2.7.0 |
| DataStore | 1.2.1 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |

AGP built-in Kotlin is disabled in `gradle.properties` to use the explicitly pinned standalone Kotlin Gradle Plugin and Compose compiler plugin consistently. This is a toolchain decision to be validated by the first complete CI build, not an architectural requirement.

The wrapper scripts download the Gradle distribution and check its SHA-256 before extracting. They are a project bootstrap helper, not the standard Gradle Wrapper JAR. The archive explicitly documents this limitation.

## 4. Verification results

| Check | Result |
|---|---|
| Kotlin pure-domain source compilation using local Kotlin 1.9 | PASS |
| Authorization policy tests | PASS — 4/4 |
| Secret-pattern scan | PASS |
| XML parsing | PASS |
| Version catalog TOML parsing | PASS |
| GitHub Actions YAML parse | PASS |
| Gradle version-catalog accessor consistency | PASS — 29 references |
| Room entity / AppDatabase registration | PASS — 24/24 |
| Offline manifest and backup rules static audit | PASS |
| Gradle shell bootstrap syntax | PASS |
| Android Gradle build / KSP | NOT RUN — no SDK/Gradle and dependency DNS unavailable |
| Android lint / APK / emulator tests | NOT RUN — depends on successful Android build |
| Physical device encryption/recovery | NOT RUN |

The successful pure Kotlin checks use the locally installed Kotlin 1.9 compiler and minimal JUnit-compatible stubs. They do not prove compilation with the pinned Kotlin 2.4.21 Gradle toolchain. Android build, lint, generated Room code, APK/AAB and instrumentation are deliberately reported as unverified.

## 5. Outstanding verification gates

- First successful Gradle resolution and build on a host with JDK 17+, Android SDK Platform 36 and network access.
- Room-generated `schemas/1.json` and explicit migration tests.
- Generated and reviewed Gradle dependency lockfiles and `verification-metadata.xml`; strict dependency verification before release.
- Successful API 36 emulator tests, then physical API/ABI tests.
- SQLCipher + Room open/close/reopen, key-envelope recovery, missing/corrupt key, crash, migration, backup exclusion and ARM64 tests on the Galaxy S24 FE and the supported API matrix.
- Legal review of all resolved dependencies and licenses.

## 6. Next development tranche

Once the first Android build is green, continue from this source tree. Do not regenerate the project. The next work should deepen the persistence layer (migration/schema proof and atomic lease acquisition), then implement the complete authorization evaluator and execution gate against persisted proposals/approvals/grants. Keep human confirmation as the default and do not introduce a production executor before its negative and crash-recovery tests pass.
