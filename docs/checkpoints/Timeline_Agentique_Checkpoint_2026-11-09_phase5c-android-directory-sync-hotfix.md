# Timeline Agentique — Phase 5C CI hotfix checkpoint

**Date:** 2026-11-09
**Source baseline:** `0.6.2-phase5c-dense-retrieval`
**Current state:** Phase 5C dense retrieval implemented; one hosted Kotlin compile blocker corrected in source.

## Resume instruction

Continue from the complete `0.6.2-phase5c-dense-retrieval` source tree with the hotfix already integrated. Do not restart Phases 0–5B and do not replace Phase 5C dense-retrieval changes. For an existing 0.6.2 checkout, the incremental patch is `phase5c-android-directory-sync-hotfix.patch`; the full source ZIP already includes the changes.

## Fix summary

- Removed the non-public Android SDK reference `OsConstants.O_DIRECTORY` from `AndroidModelFileStore.syncDirectory`.
- Replaced it with public `Os.open(..., O_RDONLY, ...)`, pre/post `Os.lstat`, descriptor `Os.fstat`, `S_ISDIR`, device/inode identity checks, `Os.fsync`, and `Os.close` in `finally`.
- Added a static contract and dedicated CI step.
- Kept app version at 0.6.2 / versionCode 23; no new user-facing feature release.

## Validation state

- Static directory-sync contract: passed.
- Kotlin source compiled with a public-API-shaped Android shim that deliberately omits `O_DIRECTORY`.
- Local filesystem shim run: prepare/commit/removal completed; eight directory fsync operations and eight matching closes observed.
- Not run here: actual Gradle/AGP/KSP compile, API 36 Android `Os` instrumentation, SQLCipher connected migration tests, Galaxy S24 FE measurements. GitHub Actions must confirm the real SDK compiler and connected test.

## Next actions

1. Apply the patch to the current 0.6.2 tree.
2. Run the supplied Gradle unit-test command again.
3. Run API 36 connected instrumentation, especially `:core:model-manager:connectedDebugAndroidTest`.
4. If compilation passes but instrumentation fails, inspect the new failure rather than weakening directory durability checks.

**Do not mark Phase 5C or Phase 5 as production-ready until hosted CI, actual native model/vector inference and Galaxy S24 FE validation pass.**
