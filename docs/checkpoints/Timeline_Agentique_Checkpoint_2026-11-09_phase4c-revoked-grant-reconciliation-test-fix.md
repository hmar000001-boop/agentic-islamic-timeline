# Timeline Agentique — checkpoint 0.5.16

**Date:** 2026-11-09
**Current source:** `0.5.16-phase4c-revoked-grant-reconciliation-test-fix`
**Application version:** versionCode `20`, versionName `0.5.16-phase4c-revoked-grant-reconciliation-test-fix`
**Baseline:** exact uploaded 0.5.15 archive
**Phase:** 4C CI stabilization; connected instrumentation

## Resume point

Continue from this 0.5.16 tree. Do not restart the project or redo the API 36 emulator, QEMU, AVD-home, persistence Android-test dependency or Context fixes. The latest hosted run now:

- passes headless QEMU dependency validation and API 36 emulator startup;
- registers the AVD and reaches connected instrumentation;
- compiles production Kotlin, Room KSP and all Android-test Kotlin sources;
- packages the app, persistence, corpus and retrieval Android-test APKs;
- finishes app (6), corpus (8) and retrieval (1) test runs;
- reports one persistence test failure in `AgentAuthorizationRoomTest.revokedScopedGrantIsRecheckedAtExecutorStart`, line 103.

## Diagnosis

The failing assertion expected the first `reconcileExecutingExecutions()` call to return `0`, left the claim `CLAIMED`, and then expected an immediate second call at the same time to return `1` and abort that same claim. This contradicts the method's documented behavior: at process startup it processes all leftover `CLAIMED` execution records because the external handler only runs after `ToolExecutionGateway` atomically changes execution and outbox to `EXECUTING`.

The production code is correct for the tested ordering. `beginClaimedExecution()` re-reads the grant row inside the Room transaction; `toHifzGrant()` preserves `revokedAtEpochMillis`; `HifzIntervalGrant.matches()` rejects a revoked grant before either execution or outbox transitions. The failed begin leaves `CLAIMED`; recovery then aborts it safely.

## Changes in 0.5.16

1. Corrected the Room instrumentation test to assert the real sequence: revoked grant → begin denied → gateway returns `ClaimRejected` → fake handler count stays zero → execution and outbox remain `CLAIMED` → one recovery pass changes both to `ABORTED_BEFORE_EXECUTION` → task becomes `INTERRUPTED` and lease is expired → second recovery pass returns zero.
2. Added assertions that no executor-start task event or audit event was written.
3. Extended `scripts/test-persistence-androidtest-contract.py` to 13 guards for this end-to-end behavior.
4. Bumped app versionCode to 20 and versionName 0.5.16.
5. No production source, authorization implementation, Room schema/migration, SQLCipher or runtime policy changed.

## Checks already passed

- persistence Android-test contract 14/14;
- API 36 startup 10/10;
- translations 6/6 and four-module verifier;
- AGP/Kotlin 11/11 and 15-module verifier;
- SDK metadata 10/10;
- Room dense-index 8/8;
- app compile/version contract 9/9;
- Room v5 migration/schema, Phase 4C foundation, SDK split, dependency-lock, bootstrap and secret checks;
- XML/TOML/YAML/shell/Python syntax;
- archive integrity, SHA-256 and clean-patch exact-tree reproduction (see report once packaged).

The authoring container cannot run Android Gradle connected tests because no Android SDK/KVM or cached Gradle distribution is available, and `services.gradle.org` is not resolvable. Do not claim the revised Android instrumentation test passed locally. A new Actions run is mandatory.

## Next action

1. Push 0.5.16.
2. Run the same connected test task group:
   `:app:connectedOfflineSideloadDebugAndroidTest :core:persistence:connectedDebugAndroidTest :core:corpus:connectedDebugAndroidTest :core:retrieval:connectedDebugAndroidTest`.
3. Review the complete XML/HTML test results for all modules. If another test fails, continue from 0.5.16 and preserve the previous changes.

## Project constraints retained

Native Kotlin/Jetpack Compose Android app; offline-only/no backend/no accounts/no analytics/no ads/no IAP; Sunni with four madhhabs and Arabic/English at launch; evidence-first corpus/RAG; strict approval for state changes; high-impact, external, financial, destructive and irreversible actions always require explicit approval. Production release gates remain: corpus rights, scholarly validation, signing/trust root, encryption/key recovery validation, model compatibility, distribution and device QA.
