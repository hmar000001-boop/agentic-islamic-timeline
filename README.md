# Timeline agentique — Android source foundation (Phase 1)

**Version:** `0.1.0-phase1`  
**Architecture:** `Cahier_architecture_Timeline_agentique_v1.2.md`  
**Readiness:** `Rapport_final_readiness_demarrage_Timeline_agentique.md`  
**Package ID:** provisional `app.timeline.islamic`  
**Status:** initial implementation source; not a production release.

## What is actually in this archive

- Multi-module Android project: `app`, `core:common`, `core:timeline-api`, `core:agent-api`, `core:authorization`, `core:security`, `core:localization`, and `core:persistence`.
- Android API baseline: min 26, compile/target 36; Java/Kotlin bytecode target 17.
- Offline and Play-distribution product flavors. Both currently have **no INTERNET permission**; Play AI Delivery is not integrated.
- Room 2 schema v1 (**24 user-state tables**), DAO set, Timeline repository adapter, SQLCipher Room 2 helper factory, Keystore-wrapped random database passphrase.
- Database passphrase envelope under `noBackupFilesDir`, atomic file replacement, and fail-closed handling if database exists but the envelope/key cannot be recovered.
- Room tables also cover task leases/events, Canvas steps/artifacts/snapshots, action outbox/execution receipts, Hifz records, prayer profiles, attachments, installed corpus packs and installed models.
- DataStore locale preference contract and Arabic/English resources.
- Compose app shell with edge-to-edge system-bar insets; no LLM required to start the app shell.
- Unit tests for domain invariants and default-deny authorization; instrumented encrypted-database persistence tests.
- GitHub Actions workflow for build, unit tests, lint, merged-manifest permission audit, manually booted API 36 emulator instrumentation, dependency report, secret-pattern scan, and CycloneDX SBOM. Action references are pinned to a commit SHA.

## Honest verification status

This workspace has JDK 21 but **no Android SDK and no installed Gradle**. Outbound Maven/Gradle dependency resolution is unavailable from the authoring container. Consequently, the Android Gradle build and instrumentation suite have **not** been executed in this environment. The archive includes a Gradle 9.5.0 bootstrap script that retrieves the official distribution and verifies it against the SHA-256 value published by `services.gradle.org`; this is not the official Gradle Wrapper JAR. Run the first build on a machine with JDK 17+, Android SDK Platform 36, and network access.

Static repository checks, file inventory, manifest permission checks, script syntax checks, ZIP integrity, and SHA-256 are run before packaging. These checks do not substitute for a Gradle compile or a real-device SQLCipher/Keystore test.

## Toolchain

| Component | Version | Notes |
|---|---:|---|
| AGP | 9.3.1 | architecture baseline |
| Gradle distribution | 9.5.0 | AGP 9.3 minimum |
| Kotlin Gradle Plugin | 2.4.21 | AGP 9.3.1 compatibility range includes this version |
| KSP | 2.3.10 | documented with Kotlin 2.4.21; supports AGP 9 built-in Kotlin fixes |
| Compose compiler plugin | 2.4.21 | aligned with Kotlin plugin |
| Compose BOM | 2026.09.00 | architecture snapshot |
| Room | 2.8.5 | KSP generation |
| SQLCipher Android | 4.20.0 | Room 2 `SupportOpenHelperFactory` integration; verification gate |
| AndroidX SQLite | 2.7.0 | SQLCipher integration candidate |
| DataStore | 1.2.1 | locale preference |

AGP 9 built-in Kotlin is explicitly disabled in `gradle.properties` to allow the pinned standalone Kotlin Gradle Plugin 2.4.21 and Compose compiler plugin to be used consistently. This is a transitional compatibility choice, not an architectural dependency; migrate to AGP built-in Kotlin only after confirming the exact AGP/Kotlin/KSP/Compose toolchain in CI.

## Build

```bash
./gradlew --version
./gradlew :app:assembleOfflineSideloadDebug
./gradlew :app:testOfflineSideloadDebugUnitTest
./gradlew :app:lintOfflineSideloadDebug
./gradlew :app:connectedOfflineSideloadDebugAndroidTest
./gradlew cyclonedxBom
```

Windows:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleOfflineSideloadDebug
.\gradlew.bat :app:testOfflineSideloadDebugUnitTest
.\gradlew.bat :app:lintOfflineSideloadDebug
.\gradlew.bat cyclonedxBom
```

The wrapper bootstrap requires `curl` or `wget` (Linux/macOS) or PowerShell (Windows), and network access for the initial Gradle distribution and dependencies. The pinned SHA-256 matches Gradle’s official checksum reference. Android instrumentation requires an attached device or configured emulator.

Gradle dependency locking is enabled for all configurations, and dynamic dependency versions are rejected. This archive does **not** contain generated per-configuration lockfiles or `gradle/verification-metadata.xml`: dependency resolution could not complete in the authoring environment. Generate and review those files during the first successful network-enabled CI/build, commit them, and switch dependency verification to strict mode before release.

## Database and key recovery policy

- User database: app-private database storage; SQLCipher encrypted.
- Key envelope: `noBackupFilesDir/database-key.envelope`; excluded from Android backup by storage location. App manifest also disables backup and declares explicit full-backup, cloud-backup and device-transfer exclusions for app-private data.
- Wrapping key: non-exportable AES-GCM key in Android Keystore.
- First launch with no DB and no envelope: create a random 256-bit passphrase and persist the encrypted envelope atomically.
- Existing DB with missing envelope: stop with an explicit unrecoverable-key error; never generate a replacement key and never delete the database automatically.
- Invalid envelope, missing Keystore key, authentication failure or native SQLCipher load failure: fail closed; no plaintext fallback.
- Room schema: version 1; 24 private-state tables; no destructive migration fallback. The Room JSON snapshot is processor-generated on the first successful Gradle/KSP build and must be committed before the first release branch; it is not fabricated here. Add explicit migrations and migration tests before any schema upgrade.
- Sensitive user data must not be entered until the SQLCipher/Keystore instrumentation suite passes on supported API/ABI targets.

## Phase 1 boundaries

This foundation does not yet implement the full ActionProposal fingerprint/ApprovalReceipt/lease/outbox executor, domain engines, timeline feature UI, Canvas feature UI, corpus importer, RAG retrieval, model manager, LiteRT-LM, OCR, ASR, prayer calculations, Hifz evaluation, or any religious verdict. Those components remain subsequent implementation tranches and release gates. The authorization module here is a deliberately small fail-closed baseline, not a claim that the complete authorization platform is finished. The agent checkpoint/lease/outbox tables exist, but their crash-recovery state machine and executor are subsequent implementation work.

## Continuity and checkpoints

- Project folder: `/Timeline_Agentique/`
- Checkpoints: `/Timeline_Agentique/Checkpoints/`
- Every subsequent meaningful delivery must include an immutable checkpoint, ZIP and SHA-256 in the project folder. Never overwrite the previous checkpoint.
