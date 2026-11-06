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
