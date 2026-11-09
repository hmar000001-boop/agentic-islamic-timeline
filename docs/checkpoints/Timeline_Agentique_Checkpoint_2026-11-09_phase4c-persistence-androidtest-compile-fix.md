# Timeline Agentique checkpoint — Phase 4C persistence Android-test compile fix

**Checkpoint date:** 2026-11-09
**Current source:** `0.5.15-phase4c-persistence-androidtest-compile-fix`
**Application version:** versionCode 19, versionName `0.5.15-phase4c-persistence-androidtest-compile-fix`
**Baseline:** 0.5.14 `phase4c-api36-avd-home-fix`

## Resume position

Continue from the complete 0.5.15 source tree. Do not restart Phase 4C or redo the API 36 emulator fixes. The previous hosted Actions run now passes emulator startup and reaches the Gradle instrumentation task. The current failure is `:core:persistence:compileDebugAndroidTestKotlin`.

## Latest hosted evidence

The log shows the app and core production Kotlin compilation, persistence KSP, and Android-test resource processing completing or being up to date. Kotlin then reports:

- `AgentAuthorizationRoomTest.kt` cannot resolve `app.timeline.islamic.core.authorization.AuthorizationEngine` and its public methods because the persistence Android-test source set has no dependency on `:core:authorization`.
- `RoomAuditLogTest.kt` references `context` in a test method, but `context` was a local variable in `setUp()` and was not visible to the test method.

These are test compile/classpath issues. No production implementation change is required.

## Changes in 0.5.15

1. Added `androidTestImplementation(project(":core:authorization"))` in `core/persistence/build.gradle.kts`. It is test-only and intentionally does not change the persistence production dependency graph.
2. Added `private lateinit var context: Context` to `RoomAuditLogTest`; initializes it in `setUp()` with `ApplicationProvider.getApplicationContext<Context>()`. The later historical-key test reuses that property.
3. Added `scripts/test-persistence-androidtest-contract.py` with six checks, and wired it into Android CI before Gradle.
4. Updated app versionCode to 19 and versionName to `0.5.15-phase4c-persistence-androidtest-compile-fix`.
5. Updated README, changelog and implementation report.

## Verification performed

- Persistence Android-test contract: 6/6 pass.
- API 36 emulator setup tests: 10/10 pass.
- Translation suite: 6/6 pass and English/Arabic resource parity verifier passes.
- AGP/built-in Kotlin: 11/11; SDK metadata: 10/10.
- Room dense-index contract: 8/8; app compile/version contract: 9/9.
- Phase 4 schema and Phase 4C foundation guards pass.
- AGP/Kotlin migration, SDK 37.0 split, dependency lock policy, Gradle bootstrap, secret scan pass.

This environment has no Android SDK and no cached Gradle 9.5.0 distribution, so actual `compileDebugAndroidTestKotlin` and connected instrumentation remain to be verified by GitHub Actions.

## Next actions

1. Push 0.5.15.
2. Confirm `:core:persistence:compileDebugAndroidTestKotlin` succeeds.
3. Inspect any subsequent runtime failures from app/persistence/corpus/retrieval connected tests.
4. Only after the complete connected suite passes, mark API 36 instrumentation stable and proceed to the next Phase 5 implementation work.

## Product constraints unchanged

Native Kotlin/Jetpack Compose Android app; local/offline first; no backend, accounts, analytics, tracking, ads or subscriptions. Timeline remains the primary daily-life UI; Canvas handles complex tasks. Sunni content across all four madhhabs with no default madhhab; Arabic and English launch languages; evidence-first RAG. State-changing agent actions require human approval by default; high-impact, external-sharing, financial, destructive and irreversible operations retain explicit approval. SQLCipher/Android Keystore, signed corpus pack, legal-rights and scholarly-validation gates remain in force.
