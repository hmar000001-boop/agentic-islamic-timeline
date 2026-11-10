# Change and evidence policy

Every change must be traceable to the exact source baseline, a stated requirement, implementation files and evidence. A green check must describe what actually ran; never infer success from a configured workflow step.

## Required change record

- Source archive SHA-256 or Git commit.
- Scope and non-goals.
- Files/modules changed.
- Data/schema/format compatibility impact.
- Security/privacy and user-consent impact.
- Build/test commands actually executed and their environment.
- Results: `PASS`, `FAIL`, `BLOCKED`, `NOT RUN`; include logs or artifact references.
- Remaining risks and acceptance owner.

## Evidence quality

1. Android build/KSP/lint/instrumentation on the pinned toolchain is required for Android changes.
2. JVM tests do not replace Android Keystore, SQLCipher, Room migration or emulator/device tests.
3. Static contract tests prove only their stated source-level contract.
4. Cryptographic code requires positive, negative, tamper, truncation, wrong-key/ID and interruption tests.
5. Religious content/rules require exact source edition, rights evidence, reviewer scope and version-bound scholarly sign-off.
6. A CI validation APK is not a signed release APK and does not prove store distribution readiness.

## Part 0/1 checkpoint discipline

The received archive is immutable. Work occurs in a fresh extracted tree and is delivered as a separate patch/archive. Do not copy generated or untracked files from previous workspaces. Do not fabricate Room schema snapshots, test results, citations, licenses or reviewer sign-offs.
