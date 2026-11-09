# Phase 6 CI hotfix report — ModelManagerTest JUnit 4 initialization

## Result

The failure was a JUnit 4 test-class signature violation, not a production Model Manager logic failure and not a Phase 6 domain-engine compilation error.

## Hosted CI evidence

The supplied GitHub Actions log shows `:core:model-manager:compileDebugKotlin` and `:core:model-manager:compileDebugUnitTestKotlin` succeeding, followed by one class initialization failure at `:core:model-manager:testDebugUnitTest`:

```text
ModelManagerTest > initializationError FAILED
org.junit.runners.model.InvalidTestClassError at ParentRunner.java:525
```

The visible log also shows successful completion of the Phase 6 Domain API, Prayer, Hifz, Travel, Halal, Zakat, Inheritance and Community Local unit-test tasks. It does not establish that every requested task after the failed task completed.

## Root cause

`ModelManagerTest.cancellationDuringImportRollsBackStagingAndRegistry()` was an expression-bodied Kotlin test ending with `scope.coroutineContext[Job]?.cancel()`. Because the final expression is nullable, Kotlin emitted a JVM method returning `kotlin.Unit`. JUnit 4 requires every `@Test` method to be public, parameterless and JVM `void`, and rejects the class during initialization otherwise.

A local `kotlinc` compilation of the production `ModelManager`/catalog and actual test source against API-shaped persistence/JUnit stubs, followed by bytecode inspection and reflection, reproduced the pre-fix `kotlin.Unit` signature. This was the only annotated test method with a non-void JVM return in the class.

## Fix

- Convert only the cancellation test to a block body, retaining its coroutine cancellation injection, cancellation assertions and cleanup checks.
- Add `scripts/test-model-manager-junit-signatures.py` to guard the JUnit-compatible declaration and preserve test assertions.
- Run the guard in GitHub Actions before Gradle unit tests.

## Local verification after fix

- Compiled the actual `ModelManager.kt`, `ModelCatalog.kt` and `ModelManagerTest.kt` source with `kotlinc` 1.9 / JDK 21 against API-shaped registry/JUnit stubs.
- Runtime reflection verified **28/28** annotated test methods are public, zero-argument and JVM `void`.
- The new JUnit signature source guard passed.
- Existing Model Manager source contract passed.
- Phase 6 domain engine contracts passed **56/56**.
- Phase 6 domain workspace / Room v7 source contracts passed **12/12**.
- Phase 5C dense retrieval source contracts passed **33/33**.
- Persistence Android-test source contract passed **14/14**.
- GitHub Actions YAML parsed; ZIP integrity passed; patch application was verified against a fresh extraction of the recovered 0.7.0 archive.

These are focused local compile/static/bytecode checks. They are not a Gradle JUnit execution.

## Impact

No production source, Room schema, dependency, permissions, model artifacts, app `versionCode` or `versionName` changed. This is a test/CI-only correction to the 0.7.0 candidate.

## Required CI confirmation

Rerun the same unit-test workflow. Acceptance for this specific hotfix is that `:core:model-manager:testDebugUnitTest` enters and completes the actual test methods without `initializationError`. Any following failure should be handled as a separate CI issue. Phase 5C and Phase 6 hosted Android/KSP/SQLCipher/API 36/device gates remain open as previously documented.

## Provenance

The patch and ZIP are based on the recovered Phase 6 0.7.0 archive. They are not certified as byte-identical to the user's original Git checkout. Reconcile the patch against the exact branch before merging if it has moved since that archive.
