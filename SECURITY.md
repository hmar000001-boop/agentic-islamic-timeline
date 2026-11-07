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

- Execute SQLCipher + Room migration/reopen/recovery tests on API 26, 29, 33 and 36, arm64-v8a and x86_64; verify migration 1→2→3 and commit KSP-generated schema v3 JSON.
- Run instrumented concurrency tests for simultaneous `beginExecution` claims, grant revocation/expiry races, task lease fencing and atomic execution/outbox/audit writes.
- Key invalidation and restore/recovery threat analysis on supported Samsung/Pixel devices.
- Dependency vulnerability and license review for every release dependency.
- OWASP MASVS review and privacy/Play declaration review.
- Corpus signing-key management, Ed25519 provider validation and signed-pack import checks.


## Agent recovery lifecycle

Audit verification and outbox/lease reconciliation run once per Android process under a mutex. Activity recreation reuses the successful recovery result and does not reinterpret a live execution as an interrupted process. A new process verifies the audit chain and reconciles any `CLAIMED`/`EXECUTING` operation before agent entry points become available.
