# Release gates and no-go criteria

A debug APK produced by CI is a **validation artifact**, not a production release. The application must not claim that a religious or AI-assisted function is release-ready merely because it compiles.

| Gate | Required evidence | Current status at Part 0 baseline |
|---|---|---|
| Source provenance | Exact archive hash or commit, reproducible checkout and change history | Verified for the received archive hash; Git commit identity not established |
| Build reproducibility | Pinned wrapper checksum, dependency locks, hosted clean build | Implemented / unverified in this environment |
| Offline flavor | Merged-manifest inspection proving no `INTERNET` permission; runtime network denial test | Static workflow check exists; actual merged manifest/device result pending |
| APK evidence | CI artifact containing offline debug APK and SHA-256; explicit debug/unsigned labeling | Configured in Part 0; hosted CI artifact creation/download still pending |
| Room schema | KSP-generated schema snapshot, reviewed migration and migration tests | Blocked until Android Gradle/KSP build generates `core/persistence/schemas/7.json` |
| At-rest encryption | SQLCipher tests across supported Android versions; Keystore invalidation, missing-key and tamper behavior | Existing SQLCipher path plus Part 1 attachment encryption implemented; device validation pending |
| Attachment protection | Authenticated encryption, ID-bound metadata, tamper tests, crash/orphan recovery, backup exclusion | Implemented in Part 1; Android Keystore/Room/device validation and backup-policy tests pending |
| Backup / restore | Cloud backup and device-transfer policy tests; explicit recovery design for keys and user data | Pending |
| Religious source rights | Filled source register with rights evidence for every shipped artifact and allowed processing mode | Pending; no blank template entry is approval |
| Scholarly validation | Signed, version/hash-bound review records for every released religious ruleset and claim | Pending |
| Agent authorization | Real handler path, explicit confirmation UI, authorization state-machine and negative side-effect tests | Not a Part 0/1 objective; release gate remains open |
| Model runtime | Exact licensed model artifact, integrity verification, real device load, resource/performance tests | Implemented foundation / runtime acceptance pending |
| Privacy and security | Threat-model review, dependency/SBOM review, no secrets, no telemetry, data lifecycle tests | Partial; release gate remains open |
| Accessibility/localization | Screen reader, touch target, RTL, Arabic/English content review and locale/device tests | Pending |

## No-go conditions

- Any unexplained permission or network request in the offline flavor.
- A missing, invalidated or unauthenticated database/attachment key causes a plaintext fallback or silent data reset.
- A religious conclusion is presented as authoritative without the exact source, rights basis and required qualified review.
- A model-generated proposal can mutate state without the configured approval path.
- A missing source, corpus, schema or model artifact is silently replaced, fabricated or treated as valid.
- The only evidence is a source-text grep, a mock, a JVM test or a locally generated debug APK where an Android/device test is required.

Release status is **NO-GO** until each applicable gate has a named owner, accepted evidence and an explicit sign-off.
