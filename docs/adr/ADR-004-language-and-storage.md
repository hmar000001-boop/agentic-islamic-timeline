# ADR-004 — Arabic/English and local persistence

- Status: accepted
- Date: 2026-11-06

## Decision

Arabic and English are the launch languages. Locale identifiers remain BCP-47 tags; the model does not hard-code only two languages. Room is the structured private-state store; SQLCipher is the encryption provider candidate; Android Keystore wraps the database passphrase. A public read-only corpus store will be separate from user-private state in a later tranche.

## Consequences

RTL resources and locale preference contract exist in Phase 1. SQLCipher/Room/Keystore remains a release gate until instrumented tests pass across the supported device/API matrix.
