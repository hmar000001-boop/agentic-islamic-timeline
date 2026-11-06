# Timeline agentique — checkpoint de reprise

**Date:** 2026-11-06 13:08 America/Toronto  
**Checkpoint:** PHASE1-LOCAL-FOUNDATION-001  
**Project:** `agentic-islamic-timeline`  
**Version:** `0.1.0-phase1`  
**Status:** SOURCE FOUNDATION MATERIALIZED; ANDROID BUILD NOT YET VERIFIED

## Continuity — do not restart

Continue in the existing workspace `/mnt/data/agentic-islamic-timeline`. Do not recreate the project or overwrite previous checkpoints. The earlier Library checkpoint from 2026-11-05 (`AUTHORIZATION-TEST-RECOVERY-000`) documented that the workspace then contained only documentation and no Gradle/Kotlin source. Phase 1 source was subsequently materialized in this same project directory.

Reference documents are now included at:

- `docs/reference/Cahier_architecture_Timeline_agentique_v1.2.md`
- `docs/reference/Rapport_final_readiness_demarrage_Timeline_agentique.md`

## Current implementation inventory

- Gradle multi-module project: `app`, `core:common`, `core:timeline-api`, `core:agent-api`, `core:authorization`, `core:security`, `core:localization`, `core:persistence`.
- Toolchain candidate pinned in Version Catalog: AGP 9.3.1, Gradle 9.5.0, Kotlin 2.4.21, KSP 2.3.10, Compose compiler plugin 2.4.21; compile/target API 36, min API 26, JVM target 17.
- Wrapper bootstrap scripts verify the official Gradle 9.5.0 distribution SHA-256, but are intentionally documented as a bootstrap wrapper rather than the official Gradle Wrapper JAR.
- Android Compose single-activity shell with edge-to-edge/system-bar insets and Arabic/English string resources.
- `offlineSideload` and `playDistribution` product flavors. Neither currently declares `INTERNET`; Play AI Delivery is not integrated.
- Room database schema version 1 with 24 entities, all registered in `AppDatabase`: timeline, context events, tasks, steps, checkpoints, leases, task events, action proposals, approvals, grants, Canvas sessions/steps/snapshots/artifacts, audit events, outbox, action executions, preferences, Hifz plans/reviews, prayer profiles, attachments, installed corpus packs and installed models.
- DAOs and a `RoomTimelineRepository` adapter.
- SQLCipher Room 2 `SupportOpenHelperFactory` adapter.
- Database-key manager: random 256-bit passphrase; encrypted AES-GCM envelope in `noBackupFilesDir`; non-exportable Android Keystore wrapping key; fail-closed if an existing database loses its envelope/key; no plaintext fallback.
- Keystore tests use isolated aliases and uniquely named test envelopes, avoiding the application alias.
- Android backup is disabled and explicit full-backup/cloud-backup/device-transfer exclusions are present.
- ADR-001 through ADR-004, security notes, third-party dependency notice, GitHub Actions workflow, secret-pattern scan and Room schema guidance.
- Reference architecture and readiness documents copied byte-for-byte into `docs/reference/`.

## Verification actually performed

Passed:

- `bash -n gradlew`.
- `python3 scripts/secret-scan.py`.
- XML parsing for all XML files, including manifest and backup rules.
- TOML parsing of `gradle/libs.versions.toml`.
- YAML parsing and structural check of `.github/workflows/android-ci.yml`.
- Version-catalog accessor check: all 29 Gradle catalog references resolve to declared aliases.
- Room static consistency check: 24 `@Entity` declarations and exactly the same 24 entity classes registered in `AppDatabase`.
- Manifest static check: no `uses-permission`, `allowBackup=false`, and both explicit exclusion resource references.
- Pure Kotlin compilation of common, timeline-api, agent-api and authorization sources using the installed local Kotlin 1.9 compiler (a syntax/contract check only; not the pinned Gradle Kotlin 2.4.21 build).
- Authorization policy tests executed locally with minimal JUnit-compatible stubs: 4/4 passed.

Not executed / not certified:

- Android Gradle build, KSP/Room generated code, lint, APK/AAB, Android unit-test tasks and instrumented tests.
- Physical-device SQLCipher/Keystore tests, including Galaxy S24 FE.
- The authoring environment has JDK 21 but no Android SDK and no preinstalled Gradle. The prior Gradle bootstrap attempt could not resolve `services.gradle.org` (DNS/network failure). Do not report the Android build as passing.
- The Room `schemas/1.json` is intentionally not fabricated; KSP must generate it during the first successful Gradle build and it must then be committed.
- Gradle dependency locking is configured, but resolved per-configuration lockfiles and `gradle/verification-metadata.xml` have not yet been generated because dependency resolution was unavailable.

## Remaining work before calling Phase 1 build-verified

1. Run the wrapper on a JDK 17+ host with Android SDK Platform 36 and network access.
2. Resolve any actual AGP/Kotlin/KSP/Compose/Room/SQLCipher compiler or dependency errors; do not infer compatibility from static metadata.
3. Generate and commit the Room schema snapshot `core/persistence/schemas/1.json`.
4. Generate and review Gradle lockfiles and dependency verification metadata; enable strict dependency verification before release.
5. Run unit tests, lint, offline merged-manifest audit, and API 36 emulator instrumentation.
6. Run SQLCipher/Keystore recovery and migration tests on API 26/29/33/36 and ARM64 physical hardware, including Galaxy S24 FE.
7. Do not store actual private user data until encryption and recovery gates pass.

## Scope boundary

This checkpoint records the Phase 1 local-infrastructure foundation, not the complete product. Full approval/grant matching, action execution gate, task runner, crash recovery, export/import, feature timeline UI, Canvas UI, corpus importer, RAG, on-device model runtime and domain engines remain later implementation tranches. No religious answers, calculations or AI capabilities are activated by this source foundation.

## Artifacts

- Source archive: `agentic-islamic-timeline-phase1-0.1.0.zip`
- Checksum: `agentic-islamic-timeline-phase1-0.1.0.zip.sha256`
- Implementation report: `docs/reports/PHASE1_IMPLEMENTATION_REPORT_2026-11-06.md`
