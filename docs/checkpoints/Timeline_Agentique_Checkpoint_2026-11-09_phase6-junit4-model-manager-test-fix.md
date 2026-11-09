# Timeline Agentique checkpoint — Phase 6 CI hotfix: Model Manager JUnit signature

**Date:** 2026-11-09
**Source baseline:** `0.7.0-phase6-domain-engines` (versionCode 25)
**Scope:** test-only correction; no runtime behavior/schema/version bump
**Phase 6 domain code:** unchanged

## Hosted CI failure

The user's GitHub Actions run compiled the project and the Phase 6 domain modules, then failed at `:core:model-manager:testDebugUnitTest`:

```text
ModelManagerTest > initializationError FAILED
org.junit.runners.model.InvalidTestClassError at ParentRunner.java:525
```

## Root cause

`ModelManagerTest.cancellationDuringImportRollsBackStagingAndRegistry()` was an expression-bodied Kotlin test ending with `scope.coroutineContext[Job]?.cancel()`. Because this last expression is nullable, Kotlin emitted a JVM method returning `kotlin.Unit`; JUnit 4's validator requires every `@Test` method to return JVM `void`. It rejects the entire class before executing any of its tests.

A local `kotlinc` compilation of the production `ModelManager`/catalog and test source against API-shaped persistence/JUnit stubs, followed by `javap`, confirmed that this was the sole annotated method emitted as `kotlin.Unit`; all other `@Test` methods were JVM `void`.

## Fix

- Convert only the cancellation test to a block body, retaining `runBlocking`, cancellation injection, and all cleanup assertions.
- Add `scripts/test-model-manager-junit-signatures.py` to require the block-body form and ensure the cancellation regression assertions remain.
- Run the guard in GitHub Actions before Gradle unit tests.

## Local verification

- Before fix: reproduced bytecode signature of cancellation test as `kotlin.Unit` while other test methods were `void`.
- After fix: recompile and inspect all annotated method signatures; must all be `void` and have zero parameters.
- Run static JUnit-signature guard and existing Model Manager source contract.
- Run `git diff --check`, validate workflow YAML and verify patch applies to clean extraction of the delivered 0.7.0 ZIP.

## CI status

This hotfix is not yet validated by a new hosted CI execution. The user should rerun the same `:core:model-manager:testDebugUnitTest` command. Successful completion of that task will prove this specific initialization failure is corrected; the remainder of Phase 6 acceptance still depends on subsequent Gradle, KSP, lint and API 36 connected-test results.
