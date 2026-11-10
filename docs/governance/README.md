# Project governance and release evidence

This directory defines the minimum evidence required to treat project inputs, generated artifacts and religious-domain behavior as trustworthy. These documents establish process; they do **not** certify the current corpus, models, calculation profiles, legal interpretations or release.

## Status vocabulary

- **Verified** — directly checked against this source archive or by a named, reproducible test in the recorded environment.
- **Implemented / unverified** — code or a process exists, but the required build, device, independent or operational evidence is absent.
- **Pending** — no accepted evidence exists yet.
- **Blocked** — a required prerequisite prevents validation or release.
- **Rejected** — evidence or a source failed a stated acceptance criterion.

A passing source-level or Python contract check is not an Android build, a cryptographic validation, a scholarly review or a device test. Reports must keep those categories separate.

## Controlled records

| Record | Purpose |
|---|---|
| [Source and rights register](SOURCE_AND_RIGHTS_REGISTER.csv) | One record per source artifact/edition, including provenance, license and permitted use. Empty register means no source has been approved by this process. |
| [Scholarly review record template](SCHOLARLY_REVIEW_RECORD_TEMPLATE.md) | Independent, madhhab-aware review with exact version/hash and recorded disagreements. |
| [Release gates](RELEASE_GATES.md) | Explicit acceptance gates and no-go conditions. |
| [Threat model](THREAT_MODEL.md) | Assets, boundaries, threats, mitigations and residual risks. |
| [Change evidence policy](CHANGE_EVIDENCE_POLICY.md) | Required proof for source, security, data and release changes. |

## Authority and escalation

1. User-confirmed product requirements govern product behavior.
2. Security constraints in the repository `SECURITY.md` and the threat model govern implementation safety.
3. For religious content, the project does not manufacture religious authority: approved source editions and recorded qualified review are required. The model, retrieval ranking and application author are not scholarly authorities.
4. Where evidence is missing, ambiguous, disputed or outside the reviewed scope, the product must disclose that limitation and avoid presenting a definitive religious ruling.
5. A release gate cannot be waived by a green unit-test badge. Any exceptional waiver needs a named approver, a narrow scope, rationale, expiry and compensating control; religious correctness, offline privacy and explicit action-approval invariants are not waivable by engineering convenience.

## Baseline rule

The current working baseline is the exact source archive identified in the Part 0/1 checkpoint report. This is archive provenance only; it is not proof that the archive is identical to a remote Git commit. Future reports must record the source archive hash or Git commit and never silently combine workspaces.

The release gate is **NO-GO** while any mandatory evidence item remains pending, blocked or rejected.
