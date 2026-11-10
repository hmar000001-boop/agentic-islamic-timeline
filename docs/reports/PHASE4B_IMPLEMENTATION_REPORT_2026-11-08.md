# Phase 4B implementation report — publisher trust and pack lifecycle

**Date:** 2026-11-08  
**Version:** `0.4.1-phase4b-publisher-trust-lifecycle`  
**Baseline:** Phase 4A `0.4.0` (incremental continuation)  
**Project:** Timeline agentique Android

## Executive status

Phase 4B source tranche implemented on top of the Phase 4A project. Timeline, Canvas, Phase 3 task runtime/authorization, Phase 4A signed corpus pack and FTS5 retrieval code remain present. This is not a production release candidate and does not complete Phase 4.

## Implemented

1. **Publisher keyring:** canonical UTF-8 parser/encoder; signed detached root signature; sorted publisher key records; raw Ed25519 public keys; activation and revocation timestamps; bounded input. The keyring manager verifies the injected pinned root, enforces increasing versions and nondecreasing generation time, preserves all prior key IDs/public keys/activation times and irrevocable revocation history, revalidates stored exact bytes on refresh, fails closed, and serializes refresh/install under a coroutine mutex.
2. **Encrypted persistence:** Room schema v4 adds `PublisherKeyringStateEntity` and `publisher_keyring_state`; `MIGRATION_3_4` is registered by `EncryptedDatabaseFactory`. `RoomPublisherKeyringStore` uses transaction-backed compare-and-set against the expected current version. Exact signed payload and signature are stored as BLOBs within SQLCipher-protected Room.
3. **Keyring archive and SAF:** strict two-entry `keyring.txt`/`keyring.sig` ZIP importer with compressed and expanded size caps; SAF bridges accept user-selected `content://` URIs and stream into the bounded importer/installer without shared-storage extraction.
4. **Pack replacement:** installer update API checks the expected active version and strict version increase, validates signature/index/license/evidence, stages inside private storage and atomically renames. `RoomCorpusPackRegistry` atomically supersedes the previous version and inserts the replacement in one transaction. Stale/current-version conflict rejects the update.
5. **Revalidation and quarantine:** validates app-private canonical paths and symlinks, manifest canonical form/hash, publisher signature and active time window, index SHA-256 and SQLite metadata. Invalid active packs become `QUARANTINED` and disappear from active retrieval; a clean pass can restore them after trust update.
6. **Removal/recovery:** `REMOVAL_PENDING` is committed before filesystem removal. Safe deletion refuses symlinks and paths outside the corpus root. Startup recovery completes removals interrupted by process death. Room adapter limits state transitions and requires pending state for row deletion.
7. **Runtime coordinator and CI guard:** ordered keyring refresh → pending-removal recovery → orphan cleanup → revalidation, plus `scripts/check-phase4-schema.py` added to CI to verify Room v4/entity/migration registration and SQLite table shape/BLOB persistence.

## Source locations

- `core/knowledge-api/.../CorpusContracts.kt`
- `core/corpus/.../PublisherKeyringCodec.kt`
- `core/corpus/.../PublisherKeyringManager.kt`
- `core/corpus/.../SignedPublisherKeyringArchiveImporter.kt`
- `core/corpus/.../SafPublisherKeyringImporter.kt`
- `core/corpus/.../SafCorpusPackImporter.kt`
- `core/corpus/.../CorpusPackInstaller.kt`
- `core/corpus/.../InstalledCorpusPackRevalidator.kt`
- `core/corpus/.../CorpusPackLifecycleManager.kt`
- `core/corpus/.../CorpusPackRuntime.kt`
- `core/persistence/.../PublisherKeyringStateEntity` and `RoomPublisherKeyringStore.kt`
- `core/persistence/.../RoomCorpusPackRegistry.kt`
- `scripts/check-phase4-schema.py`

## Verification actually performed

| Check | Result | Qualification |
|---|---:|---|
| Phase 4A + 4B focused JVM suite | **29/29 passed** | Kotlin 1.9, local JUnit/coroutine shim runner; not Gradle/JUnit |
| Pack install/update/revalidation/quarantine/remove integration harness | **Passed** | Kotlin harness over actual compiled installer/lifecycle production code and test fakes; not Android Room runtime |
| Publisher root signature / Ed25519 RFC 8032 vector | **Passed** | Bouncy Castle 1.80 local jar; resolved Gradle artifact not fetched here |
| Room 3→4 migration/table-shape guard | **Passed** | Python + system SQLite in-memory test of exact SQL and BLOB read; not Room MigrationTestHelper |
| Corpus/publisher/Room adapter Kotlin compilation | **Passed** | Isolated compile against temporary Android/Room/coroutine API shims |
| Corpus installer and persistence instrumentation source compilation | **Passed syntactically** | Test sources compiled against lightweight AndroidX/JUnit shims; tests did not execute on Android |
| Secret scan / XML and version catalog / workflow syntax / ZIP integrity | **Passed** | Static checks only |
| Android Gradle build / KSP / generated schema v4 JSON | **NOT RUN** | No installed Gradle/Android SDK; outbound Gradle dependency access unavailable |
| SQLCipher Room migration/reopen tests, API 26–36 emulator and physical device | **NOT RUN** | Must run on network-enabled Android CI/device host |

Local 29 tests include Phase 4A source eligibility, evidence identity/hash, canonical pack manifest, Ed25519, RRF and retrieval fallback plus Phase 4B keyring canonicalization, signature/key history/revocation, and signed archive validation. The separate integration harness covers pack update lifecycle and trust rejection, but does not replace instrumentation of the actual Room transaction.

## Explicit non-claims / remaining gates

- No production root public key is supplied or pinned. Import must remain disabled until a reviewed Ed25519 root is wired into a production application composition.
- The keyring manager and SAF bridges exist, but the app's `TimelineApplication` startup composition and user-facing SAF pack/keyring management UI are not yet wired.
- Room keyring state is in the SQLCipher database, but no independent hardware anti-rollback counter exists for recovery from an older complete database image. Root rotation requires a reviewed app update/trust migration.
- No publisher private keys, real signed corpus packs or licensing evidence are included. Copyright/redistribution rights and scholarly review remain release gates.
- No vector embedding generation/index, concrete dense retriever, benchmarked reranker, evidence/citation UI or religious answer workflow yet.
- Actual AndroidX/Room/KSP/SQLCipher dependency resolution and runtime behavior, supported API/ABI test matrix and Galaxy S24 FE acceptance remain outstanding.

## Next continuation point

Continue with Phase 4C from this source and checkpoint. Priorities: first wire a disabled-by-default publisher trust configuration into app startup (without inventing root keys), then add corpus/keyring management UI with explicit user-selected SAF pickers and clear trust state; next build versioned local embedding/index generation and benchmark CPU/memory/battery on representative devices. Keep the app fully useful without model/vector retrieval and do not release religious guidance until legal and scholarly gates pass.
