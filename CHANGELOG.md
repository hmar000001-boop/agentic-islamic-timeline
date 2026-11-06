# Changelog

## 0.1.0-phase1 — 2026-11-06
- Materialized the actual Gradle multi-module Android project and package structure (previous workspace contained only project documentation).
- Added Room schema v1 with 24 user-state tables, DAOs and Timeline repository adapter.
- Added SQLCipher Room 2 integration candidate and Android Keystore AES-GCM database-key envelope with fail-closed recovery rules.
- Added Arabic/English resources and DataStore locale preference contract.
- Added offline/Play flavors (neither currently requests INTERNET), tests, CI and documentation.
- Build and Android instrumentation remain unexecuted here because Android SDK/Gradle/dependency resolution are unavailable in the authoring environment.
- Added task lease/event and Canvas step entities; isolated Keystore test aliases to prevent test interaction with the application database key.
- Added explicit Android backup and device-transfer exclusion resources; expanded authorization DAO with execution receipt access.


## 0.2.0-phase2 — 2026-11-06
- Added dedicated `:feature:timeline` and `:feature:canvas` modules; wired them into the existing application without replacing the Phase 1 project.
- Replaced the placeholder app shell with a Room-backed daily Timeline, local-date/week navigation, filters, event/task/habit entry, and completion/cancellation for user-owned tasks and habits.
- Corrected daily retrieval to include events overlapping midnight; added DST-aware date window boundaries.
- Added persistent Canvas workspace create/edit/archive, versioned draft encoding, compare-and-set updates, atomic session/snapshot writes, and snapshot hashes.
- Added English/Arabic feature resources and Android per-app locale metadata.
- Added date-window, filter, Canvas codec unit tests and instrumented midnight-window / Canvas snapshot tests; expanded CI to execute new unit-test modules.
- Local Kotlin-only compilation and seven targeted test methods passed using a local harness. Full Android Gradle/KSP build and device tests remain unverified because the container has no Android SDK and DNS resolution for services.gradle.org fails.
