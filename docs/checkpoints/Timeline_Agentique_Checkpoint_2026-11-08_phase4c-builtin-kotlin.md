# Timeline agentique — Phase 4C / AGP 9 built-in Kotlin checkpoint

**Date:** 2026-11-08  
**Checkpoint ID:** `PHASE4C-AGP9-BUILTIN-KOTLIN-001`  
**Source baseline:** Phase 4B / 0.4.3  
**Current source version:** `0.5.0-phase4c-agp9-built-in-kotlin`  
**Project path:** `/mnt/data/ta-work`  
**Library folder:** `/Timeline_Agentique/`

## 1. Continuity instruction

Resume from this exact source tree or the versioned 0.5.0 ZIP. Do not recreate the app or discard previous phases. Preserve all prior ZIPs/checkpoints as immutable artifacts.

## 2. Current module inventory

15 modules:

- `:app`
- `:core:common`
- `:core:timeline-api`
- `:core:agent-api`
- `:core:authorization`
- `:core:agent-runtime`
- `:core:security`
- `:core:localization`
- `:core:persistence`
- `:core:knowledge-api`
- `:core:corpus`
- `:core:retrieval`
- `:feature:timeline`
- `:feature:canvas`
- `:feature:corpus-management`

Room schema v5, 27 registered entities, explicit migrations 1→2→3→4→5.

## 3. Phase 4C implementation

- Corpus/keyring management UI in `feature/corpus-management`; English and Arabic strings.
- App navigation and SAF OpenDocument integration in `app`.
- Publisher root loaded only from explicit reviewed build properties; no generated/default root. Imports disabled if absent.
- Pack import/update, status visibility, startup revalidation, quarantine and explicit removal confirmation use the Phase 4B lifecycle implementation.
- Dense index cleanup follows trust or pack-lifecycle changes; only installed active pack versions retain dense vectors.
- `LocalTextEmbeddingModel` is an interface only; no fake or remote implementation.
- `LocalEmbeddingIndexBuilder` streams validated passage text in batches, validates model output, int8 quantizes embeddings and stages a new generation. Successful builds activate only after completion; failure/cancellation aborts staging.
- All vector bytes are stored in SQLCipher Room, never in plaintext sidecar storage or alongside corpus pack files.
- Per-generation cap 128 MiB; total active/staging cap 256 MiB.
- FTS5 remains the active retrieval path. Dense search is disabled until Phase 5 provides and validates a real local model/runtime.

Key files:

- `feature/corpus-management/src/main/java/app/timeline/islamic/feature/corpus/CorpusManagementFeature.kt`
- `app/src/main/java/app/timeline/islamic/MainActivity.kt`
- `app/src/main/java/app/timeline/islamic/TimelineApplication.kt`
- `core/knowledge-api/src/main/java/app/timeline/islamic/core/knowledge/CorpusContracts.kt`
- `core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/LocalEmbeddingIndexBuilder.kt`
- `core/retrieval/src/main/java/app/timeline/islamic/core/retrieval/QuantizedEmbeddingCodec.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/RoomDenseEmbeddingIndexStore.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabase.kt`
- `core/persistence/src/main/java/app/timeline/islamic/core/persistence/AppDatabaseMigrations.kt`

## 4. AGP/Kotlin migration status

- AGP 9.3.1; Gradle 9.5.0; Kotlin 2.4.21; KSP 2.3.13; Compose compiler plugin 2.4.21.
- Removed `org.jetbrains.kotlin.android` from all Android modules and catalog.
- Removed `android.builtInKotlin=false` and `android.newDsl=false`.
- All Android modules configure typed `ApplicationExtension`/`LibraryExtension` APIs.
- BuildConfig publisher key fields are registered with `ApplicationAndroidComponentsExtension.onVariants` and `variant.buildConfigFields`.
- No app build use of `buildConfigField()`; no legacy Android variants API or `kotlinOptions` in Gradle build scripts.
- The root buildscript pins KGP/KSP versions as required to override AGP's bundled Kotlin runtime version.

## 5. CI changes from 0.4.3 logs

- Dependency policy explicitly deactivates locking on AGP `*RuntimeClasspath` and CycloneDX BOM configurations; ordinary resolvable project configs activate locking.
- No manual `failOnDynamicVersions()` calls remain. Gradle does not permit locking and fail-on-dynamic on the same Configuration.
- Version catalog and direct dependency selector scan rejects dynamic versions.
- Configuration cache remains disabled; CI calls use `--no-configuration-cache` because previous AGP Navigation XML and Gradle dependency-report tasks failed cache serialization.
- Gradle bootstrap mode-0755 repair and regression remain.
- CI includes built-in Kotlin/DSL guard, reproducibility guard, Phase 4 Room v5 guard, Phase 4C feature/security guard and all Phase 4C unit-test tasks.
- Successful CI tasks use `--write-locks`; project and root buildscript classpath lockfiles are uploaded as an artifact. Review/commit generated locks after a successful run.

## 6. Local verification on 2026-11-08

- Built-in Kotlin migration test suite: 11/11 passed.
- Built-in Kotlin/public DSL scan: passed across 15 Android module scripts.
- Dependency reproducibility selector/locking guard: passed for all 19 version catalog pins.
- Phase 4 schema guard: passed for Room v5/27 entities/migrations/encrypted vector schema.
- Phase 4C UI/SAF/encryption/embedding guard: passed.
- Gradle bootstrap executable test: passed.
- Secret pattern scan: passed.
- Focused Kotlin 1.9 smoke compilation/execution of contracts, codec and builder: passed for batching, activation, quantization/cosine, failure abort and 128 MiB budget.

## 7. Not executed

The environment has Kotlin 1.9.0/JDK 21, but no Gradle distribution or Android SDK and cannot reach dependency repositories. No AGP configuration/build, KGP 2.4.21 compilation, KSP/Room code generation, Gradle/JUnit tests, AndroidX/SQLCipher instrumentation, emulator/device tests or CycloneDX run has been executed. Do not claim CI is green until the 0.5.0 source is run through GitHub Actions.

The Room KSP schema file `core/persistence/schemas/5.json` is not fabricated. Generate/review/commit it after the first successful Gradle/KSP build.

## 8. Immediate next actions

1. Push the 0.5.0 source ZIP to the connected repository manually and run GitHub Actions.
2. Resolve any first true AGP built-in Kotlin, KSP 2.3.13, Compose or Room compiler error from the new logs.
3. Check that the earlier dependency-lock conflict is gone. If it persists, capture `--info` resolution output and identify the exact configuration that is still locked before changing policy again.
4. If AGP emits obsolete variant API warnings, run with `-Pandroid.debug.obsoleteApi=true` to identify the calling plugin; do not just suppress warnings.
5. Generate and review Room schema `5.json`, module lockfiles and dependency verification metadata.
6. Execute Room/SQLCipher, migration, lifecycle, dense-index atomicity, API 36 emulator, RTL and Galaxy S24 FE tests.
7. Continue to Phase 5 for the real offline model manager/runtime and a verified model; do not enable dense ranking before then.

## 9. Release gates

- A reviewed publisher root public key has not been pinned.
- No rights-cleared or scholarly-reviewed corpus pack is bundled.
- No local embedding model/runtime adapter or weights are included.
- All Android build/codegen/instrumentation/physical-device gates remain open.
