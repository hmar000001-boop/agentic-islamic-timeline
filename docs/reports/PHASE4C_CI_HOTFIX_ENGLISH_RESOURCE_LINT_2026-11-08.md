# Phase 4C CI hotfix — English resource lint completeness

**Date:** 2026-11-08  
**Source version:** `0.5.8-phase4c-english-resource-lint-fix`  
**Previous source:** `0.5.7-phase4c-app-compile-fix`

## CI evidence

The hosted run completed app Kotlin compilation and APK assembly. The requested lint tasks for `:core:corpus`, `:core:retrieval`, and `:feature:corpus-management` passed. `:app:lintOfflineSideloadDebug` failed with three errors and fifteen warnings; the first reported error was `MissingTranslation` for `app_nav_timeline` in English. Inspection of the exact source archive found that the app's default and Arabic resource files contained three navigation strings not present in its explicit English locale file: `app_nav_timeline`, `app_nav_canvas`, and `app_nav_corpus`. These exactly account for the three missing keys indicated by the source resource comparison.

## Changes

- Added `Timeline`, `Canvas`, and `Knowledge` to `app/src/main/res/values-en/strings.xml`.
- Added `feature/corpus-management/src/main/res/values-en/strings.xml`, making its explicit English resource set match the default English file. Arabic entries were already present.
- Added `scripts/verify-string-translations.py`, which checks translatable string/plural/array key parity between default resources and both `values-en` and `values-ar`, and checks formatted placeholder signatures.
- Added six regression tests and invoked them plus the verifier in GitHub Actions before Gradle.
- Bumped app versionCode from 11 to 12.

## Scope and preserved behavior

No lint rule was suppressed, no baseline was added, and no resources were marked non-translatable to hide the issue. No runtime logic, Room schema, SQLCipher handling, signing, model behavior, compileSdk/targetSdk or emulator configuration changed.

## Verification actually performed here

The source strings were parsed and compared; the new verifier and tests run locally against the extracted project. The hosted Android Gradle lint task itself has not been rerun from this environment. The previous CI output reported 15 warnings as well as the three blocking errors; warnings remain visible to lint and have not been hidden.
