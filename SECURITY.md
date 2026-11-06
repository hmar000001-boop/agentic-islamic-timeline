# Security policy

## Data and trust boundaries

- User-private state belongs in the encrypted Room database or separately encrypted attachment storage.
- Public source packs and imported model files are untrusted input. No pack is executable code.
- No model output is an authorization decision.
- No action adapter may be called directly from UI or model runtime; all mutations must pass the future AuthorizationEngine execution gate.
- No product network client, analytics, ad, billing, crash-reporting or account SDK is allowed by current product decisions.

## Key handling

- Database passphrase is random and generated locally.
- A non-exportable Android Keystore AES-GCM key wraps the passphrase.
- Wrapped passphrase is stored only in `noBackupFilesDir`.
- Missing key envelope for an existing database is a hard recovery failure. Do not reset or recreate keys automatically.
- Never log passphrases, key bytes, envelope plaintext, SQLCipher parameters or user database content.

## Release requirements not satisfied by this source tranche

- SQLCipher + Room migration/reopen/recovery tests on API 26, 29, 33 and 36, arm64-v8a and x86_64.
- Key invalidation and restore/recovery threat analysis on supported Samsung/Pixel devices.
- Dependency vulnerability and license review for every release dependency.
- OWASP MASVS review and privacy/Play declaration review.
- Corpus signing-key management, Ed25519 provider validation and signed-pack import checks.
