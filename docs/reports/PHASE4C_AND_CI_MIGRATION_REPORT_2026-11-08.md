# Timeline agentique — Phase 4C and AGP 9 CI migration report

**Date:** 2026-11-08  
**Source baseline:** Phase 4B / 0.4.3  
**Version:** `0.5.0-phase4c-agp9-built-in-kotlin`  
**Package ID:** provisional `app.timeline.islamic`  
**Status:** source tranche and static verification complete; hosted Gradle/Android CI still required.

## 1. Continuity

This delivery continues from the 0.4.3 source tree. It retains all prior Timeline, Canvas, Phase 3 agent/authorization, Room/SQLCipher, signed Corpus Pack, publisher-keyring, pack lifecycle, FTS5 and RRF implementations. No earlier phase was recreated or replaced.

The project has 15 Gradle modules, including the new `:feature:corpus-management` module. Room schema is v5 with 27 entity registrations and explicit migrations 1→2→3→4→5.

## 2. Phase 4C application wiring

- Added `:feature:corpus-management` Compose module with English and Arabic resources.
- Connected a Corpus destination to the application navigation.
- Connected Android Storage Access Framework `OpenDocument` selection for the signed publisher-keyring archive and corpus-pack archives. File contents are consumed by the existing bounded SAF import adapters; the app does not take broad storage permissions.
- Exposes current pinned-root state, signed-keyring version/readiness, installed pack inventory and lifecycle statuses, and the last revalidation summary.
- Import/update requires an explicitly selected document. Removing a pack requires an explicit confirmation dialog.
- Publisher-root trust remains fail-closed. No fake or locally generated publisher root was added. The app accepts a configured, reviewed public root through build properties; without it, publisher imports are disabled and the app avoids making trust-based quarantine decisions.
- After publisher-keyring update, startup revalidation, pack import/update and lifecycle transitions, derived dense-index generations belonging to any non-`INSTALLED` pack are deleted.

## 3. Bounded local embedding-index foundation

### Implemented contracts and behavior

- `LocalTextEmbeddingModel` is a local-only interface. There is no remote embedding client or cloud fallback.
- `LocalEmbeddingIndexBuilder` streams original source passage text from an already validated, installed corpus index in bounded batches (default 24; maximum 64).
- Each embedding is shape-checked, L2-normalized and symmetrically quantized to signed int8. The persisted vector contains no passage text.
- Evidence keys must be valid stable SHA-256 identities; source cursor batches must be ordered and advance strictly.
- Builds are staged. A generation is activated only after all passages have been embedded and stored. Model failure, invalid output or cancellation aborts the staged generation without replacing the previous active generation.
- Each generation is capped at 128 MiB; the encrypted store has a 256 MiB total active/staging payload ceiling.
- All vectors are stored only in the existing SQLCipher-protected Room database. The earlier plaintext sidecar-index approach is not used.
- Room v4→v5 adds dense-generation and vector tables, the relevant indexes and foreign-key cascade behavior. The migration is registered in the encrypted database factory.
- Added SQLCipher Room instrumentation source tests for active-generation replacement/removal and abandoned staging recovery.

### Deliberate scope boundary

No concrete embedding model, model weights or inference runtime is bundled. This is not a functional dense retriever yet. No placeholder vectors are generated. FTS5/BM25 remains the active retrieval path until a reviewed compatible on-device model is provisioned and tested in Phase 5. This preserves truthful diagnostics and prevents dense search from being advertised when it is unavailable.

## 4. AGP 9 built-in Kotlin migration

The 0.4.3 CI logs showed old external-Kotlin/new-DSL opt-out configuration and associated AGP migration warnings. The source now completes the structural built-in Kotlin migration:

- Removed `org.jetbrains.kotlin.android` from all Android modules and the version catalog.
- Removed `android.builtInKotlin=false` and `android.newDsl=false`; built-in Kotlin and the new DSL are now enabled by AGP defaults.
- Converted module Android blocks to typed public `ApplicationExtension`/`LibraryExtension` configuration.
- Replaced deprecated eager `buildConfigField()` with the lazy `ApplicationAndroidComponentsExtension.onVariants` and variant `buildConfigFields` API.
- Removed project references to `applicationVariants`, `libraryVariants`, `testVariants`, `unitTestVariants`, `kotlinOptions` and legacy eager BuildConfig DSL from the Gradle build scripts.
- Compose uses `org.jetbrains.kotlin.plugin.compose` aligned to Kotlin 2.4.21.
- KSP remains explicitly pinned at published 2.3.13. Room KSP generation must still be verified on the hosted runner.
- Root buildscript explicitly selects KGP 2.4.21 and KSP 2.3.13 on the AGP 9 built-in Kotlin path.

The selected versions are AGP 9.3.1, Gradle 9.5.0, Kotlin 2.4.21 and KSP 2.3.13. Kotlin 2.4.21 is within the published fully supported Gradle/AGP compatibility range for Gradle 9.5.0 and AGP 9.3.1. The built-in Kotlin migration follows Android's documented migration steps (external Kotlin Android plugin removal, compiler DSL migration and source-set review).

This is an AGP 9 built-in Kotlin/public DSL migration, not an AGP 10 version upgrade. Before a future AGP 10 upgrade, CI must confirm that no transitive third-party plugin still depends on removed legacy variant APIs.

## 5. CI corrections based on supplied logs

### 5.1 Legacy Kotlin/AGP DSL incompatibility and upcoming opt-out removal

The previous workaround flags and external Kotlin Android plugin have been removed. All modules now use AGP built-in Kotlin and the new public Android DSL. A dedicated guard scans the module build scripts for the old Kotlin Android plugin, both temporary flags, legacy variant APIs, `kotlinOptions`, and eager `buildConfigField()` use.

### 5.2 Dependency locking versus `failOnDynamicVersions`

The supplied log contained:

`Both dependency locking and fail on dynamic versions are enabled. You must choose between the two modes.`

The root build no longer calls `failOnDynamicVersions()` manually. For AGP `*RuntimeClasspath` and CycloneDX BOM configurations, the project explicitly calls `deactivateDependencyLocking()` because the respective plugins apply their own dynamic-version rejection rules. The explicit root KGP/KSP buildscript classpath and ordinary resolvable project configurations keep dependency locking enabled. All version-catalog entries and direct dependency declarations are statically checked for dynamic selectors.

CI uses `--write-locks` for successful Gradle tasks and uploads generated project `gradle.lockfile` plus root `buildscript-gradle.lockfile` artifacts. The root compiler buildscript classpath is included in locking. Because this authoring environment cannot resolve dependencies, no lockfiles are fabricated; generated locks still need review and commit after a successful hosted run.

### 5.3 Configuration-cache serialization failure

The supplied logs showed configuration-cache serialization errors in AGP navigation-resource processing and Gradle dependency reporting. Configuration cache is explicitly disabled in `gradle.properties`, and all CI Gradle invocations except the harmless `--version` probe pass `--no-configuration-cache`. This is a conservative compatibility setting; re-enable only after an actual hosted-runner test confirms those tasks serialize successfully.

### 5.4 Previous bootstrap regression

The earlier custom Gradle bootstrap executable-permission fix is retained. Its regression test remains in the workflow.

## 6. Local verification executed for this tranche

| Check | Result | Scope |
|---|---|---|
| `scripts/test-built-in-kotlin.py` | 11/11 passed | Built-in Kotlin, typed DSL, no old APIs, lock policy, KSP/Compose pins, lazy BuildConfig |
| `scripts/verify-built-in-kotlin.py` | Passed | 15 Android module build scripts; no transition flags or legacy API references |
| `scripts/verify-gradle-reproducibility.py` | Passed | Exact version catalog pins; selective lock policy; dynamic selector scan |
| `scripts/check-phase4-schema.py` | Passed | Room v5 and migrations; 27 entities; SQLCipher keyring/dense schema; constraints/indexes |
| `scripts/check-phase4c-foundation.py` | Passed | SAF/UI wiring; root gating; inactive-pack cleanup; embedding bounds; encrypted Room storage |
| `scripts/test-gradle-bootstrap.py` | Passed | Gradle extracted/cached executable permission regression |
| `scripts/secret-scan.py` | Passed | Common secret-pattern scan |
| Kotlin 1.9 smoke compilation/execution | Passed | Actual `CorpusContracts.kt`, quantized codec and embedding builder: batching, activation, int8/cosine, failure abort and 128 MiB cap |

These checks are local static/schema/source-level checks plus a focused Kotlin execution harness. They are not a substitute for Gradle/JUnit or Android runtime validation.

## 7. Not run / remaining verification gates

The authoring container has no installed Gradle distribution or Android SDK and cannot resolve Gradle/Maven dependencies. Therefore the following have **NOT RUN**:

- `./gradlew` project configuration/build and dependency resolution;
- AGP built-in Kotlin compiler integration and Kotlin 2.4.21 compilation;
- KSP 2.3.13 and Room 2.8.5 code generation, including generated Room schema `5.json`;
- actual Gradle/JUnit unit test tasks;
- Room/SQLCipher migration and dense-store instrumentation on Android;
- API 36 emulator or Galaxy S24 FE physical-device runs;
- CycloneDX SBOM generation on the hosted runner.

The next mandatory action is to run the versioned source through GitHub Actions. If AGP still reports obsolete `applicationVariants`/`libraryVariants` accesses, rerun with `-Pandroid.debug.obsoleteApi=true` to identify whether the caller is a third-party plugin; do not hide those warnings with a suppression property before identifying them.

## 8. Release gates retained

No production publisher root key or signed scholarly corpus is included. Corpus licensing, provenance and scholarly validation are mandatory. There is no embedding model or dense ranking implementation. SQLCipher/Keystore behavior, Room generated schemas, Android runtime integrations, API support, RTL, battery/performance and physical-device QA all remain release gates.
