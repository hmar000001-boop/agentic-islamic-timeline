# Security policy

## Data and trust boundaries

- User-private state belongs in the encrypted Room database or separately encrypted attachment storage.
- Public source packs and imported model files are untrusted input. No pack is executable code.
- No model output is an authorization decision.
- No action adapter may be called directly from UI or model runtime; all mutations must pass `AuthorizationEngine` and `ToolExecutionGateway`.
- Model output is only planner input; it cannot create an approval, grant, execution claim or Keystore audit record.
- Each approval is bound to one proposal ID and SHA-256 of canonical parameters. Any parameter/target/action change requires a new proposal and approval.
- High-impact, sensitive, external, financial, destructive, irreversible or unknown-reversibility actions require strong confirmation; persistent grants do not cover them.
- The only persistent-grant action in Phase 3 is `hifz.adjust_review_interval`, limited to exact plan/revision/schedule, bounded delta, current policy version, expiry and revocation.
- Tool start atomically revalidates proposal, approval/grant, task status and lease owner/fencing token immediately before transitioning `CLAIMED` to `EXECUTING`.
- `CLAIMED` after process death is safe to abort before effect. `EXECUTING` after process death is `OUTCOME_UNKNOWN`; never replay it automatically.
- Execution outcome mutation is tied to the full claim and its fencing token. A stale worker cannot requeue or interrupt a newer worker's task.
- No product network client, analytics, ad, billing, crash-reporting or account SDK is allowed by current product decisions.

## Key handling

- Database passphrase is random and generated locally.
- A non-exportable Android Keystore AES-GCM key wraps the passphrase.
- Wrapped passphrase is stored only in `noBackupFilesDir`.
- Missing key envelope for an existing database is a hard recovery failure. Do not reset or recreate keys automatically.
- Never log passphrases, key bytes, envelope plaintext, SQLCipher parameters or user database content.
- The local audit table stores payload hashes rather than private payloads; event HMACs are created with an Android Keystore HMAC-SHA256 key and verified before agent recovery.
- Audit chaining is tamper-evident for modified/reordered records and missing historical keys, but it has no independent anti-truncation anchor. Deletion of the full tail or table cannot be detected from the remaining local chain alone.

## Release requirements not satisfied by this source tranche

- Execute SQLCipher + Room migration/reopen/recovery tests on API 26, 29, 33 and 36, arm64-v8a and x86_64; verify migration 1→2→3→4 and commit KSP-generated schema v4 JSON.
- Run instrumented concurrency tests for simultaneous `beginExecution` claims, grant revocation/expiry races, task lease fencing and atomic execution/outbox/audit writes.
- Key invalidation and restore/recovery threat analysis on supported Samsung/Pixel devices.
- Dependency vulnerability and license review for every release dependency.
- OWASP MASVS review and privacy/Play declaration review.
- Pin and review a production Ed25519 publisher root key; define root-key rotation and incident response; do not enable pack imports before this gate.
- Test canonical keyring parsing, publisher-key history/revocation, transactional update rollback, SAF import, Room migration 3→4 and pack quarantine/removal lifecycle on supported Android versions.
- The local monotonic keyring version has no independent hardware anti-rollback anchor against restoration of an older full encrypted database image.


## Agent recovery lifecycle

Audit verification and outbox/lease reconciliation run once per Android process under a mutex. Activity recreation reuses the successful recovery result and does not reinterpret a live execution as an interrupted process. A new process verifies the audit chain and reconciles any `CLAIMED`/`EXECUTING` operation before agent entry points become available.


## Corpus publisher trust and lifecycle (Phase 4B)

- Publisher keyrings are accepted only when the exact canonical payload verifies under a root public key injected by reviewed application build configuration. No default or demo production root key exists.
- Keyring bytes, detached Ed25519 signature, version and installation timestamp are stored inside SQLCipher-protected Room table `publisher_keyring_state`; the persistence adapter uses a transactional compare-and-set against the expected current version.
- Keyring refresh/install operations are serialized in-process. Publisher key IDs, key bytes and activation times cannot be rebound; historical keys cannot be removed; a recorded revocation cannot be reversed.
- Corpus pack updates only activate a strictly newer version if the expected old version is still active. The registry transition is atomic and prior versions remain `SUPERSEDED` until explicit removal.
- Startup revalidation quarantines a pack if its canonical manifest, publisher signature/active window, signed index hash, license profile, SQLite metadata or private filesystem path fails verification.
- Removal enters `REMOVAL_PENDING` before file deletion; deletion refuses symlinks and out-of-root canonical paths; an interrupted removal is retried at startup.
- SAF import adapters accept user-selected `content://` URIs only and stream through bounded parsers. App-level picker/management UI and application startup wiring remain incomplete.
- Residual: compare-and-set prevents concurrent/ordinary stale updates, not a full old-database restore by an attacker with control of app data. There is no independent hardware rollback counter. Publisher-root rotation also requires a reviewed application update.
