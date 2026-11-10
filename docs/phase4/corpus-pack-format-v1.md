# Timeline Corpus Pack format v1

Status: implemented importer/index contract; no production corpus or publisher key is bundled.

## 1. Purpose and trust model

A Corpus Pack is a user-imported, offline, signed package containing one immutable SQLite FTS5 index. The app does not fetch packs from a service. The caller may provide an `InputStream` backed by Android's Storage Access Framework (SAF); SAF UI and app wiring are intentionally separate from this module.

The pack is accepted only when all of the following are true:

1. The archive is structurally valid and contains exactly the three allowed entries.
2. `manifest.txt` is canonical UTF-8 and follows the exact field order below.
3. `manifest.sig` is a 64-byte Ed25519 signature over the exact bytes of `manifest.txt`.
4. `key_id` resolves to an app-pinned publisher key that is active and not revoked at install time.
5. The SQLite file SHA-256 matches the signed manifest.
6. SQLite integrity, foreign keys, schema/application identifiers, counts, languages, rights/review metadata, normalizer version, original-text hashes, and stable evidence identities pass validation.
7. The verified staging directory is moved into app-private storage with an atomic same-filesystem rename. The Room registry record is added only after the move. Failure to atomically move or register fails closed.

The installed registry and filesystem cannot share one database transaction. A process death between rename and Room commit can leave an unregistered directory; `removeUnregisteredPackDirectories()` removes those orphans and must be called during the app's corpus startup/reconciliation path before pack management is exposed.

## 2. ZIP envelope

The ZIP must contain exactly these entries, once each, at the archive root:

- `manifest.txt`
- `manifest.sig`
- `corpus.sqlite`

Directories, nested paths, duplicate names, unknown entries, and other files are rejected. There is no path from an archive entry into a caller-chosen filesystem path. Current limits: 2 GiB compressed archive, 64 KiB manifest, exactly 64 signature bytes, 4 GiB index and 4 GiB aggregate expanded payload. An oversized archive is rejected before it becomes visible.

## 3. Canonical manifest

Encoding is UTF-8 without BOM, LF line endings, exactly one final LF, no blank trailing line, and no whitespace around keys or values. Fields must appear in this exact order, one `key=value` per line:

```text
format=timeline-corpus-pack
schema_version=1
pack_id=quran-core
pack_version=1.0.0
created_at_epoch_ms=1800000000000
key_id=publisher-2026
license_profile=public-domain
source_count=1
passage_count=6236
index_file=corpus.sqlite
index_sha256=<64 lowercase hexadecimal characters>
languages=ar,en
```

The example is illustrative only; it does not identify a real publisher, prove a license, or contain religious source content. Language codes are unique and sorted (`ar,en`). Schema v1 supports Arabic and English only. `index_file` is fixed to `corpus.sqlite`.

The Ed25519 message is the exact byte array of the canonical manifest including its final LF. The signature is raw 64-byte RFC 8032 Ed25519. Publisher public keys are raw 32-byte encodings; they are not X.509/SPKI blobs. The app keyring must pin publisher key IDs, activation times and revocation state in app code or a signed app-controlled trust update. A caller cannot provide a new key as part of the pack itself.

## 4. SQLite database contract

The producer must execute `core/corpus/src/main/resources/corpus-index-v1.sql` using an SQLite build with FTS5 enabled. It must preserve the required `DELETE` journal mode so the database is self-contained (no unshipped WAL/SHM sidecars). Required identifiers:

- `PRAGMA application_id = 1413563971` (`0x54414643`)
- `PRAGMA user_version = 1`
- metadata `schema_version=1`
- metadata `pack_id` and `pack_version` matching the manifest
- metadata `license_profile` matching the manifest
- metadata `normalizer_version=timeline-normalizer-v1`
- nonblank `builder_version`

`pack_metadata` is a key/value table with a primary key. `source` has one record per distinct source/edition record. `passage` stores exact `original_text` separately from search-only `normalized_text`. FTS5 external content points to `passage(rowid)` and indexes `normalized_text`, not the original transcription. Triggers keep FTS synchronized during offline pack construction. Optional metadata is bounded (`author` 512, `publisher` 256, `license_url` 2,048 and `source_grade` 128 characters) to avoid unbounded result materialization.

### Source eligibility

Every source currently shipped in an installable v1 pack must satisfy all of these conditions at import time:

- source type is one of `QURAN`, `HADITH`, `TAFSIR`, `FIQH`, `REFERENCE`;
- `license_status=CLEARED`, with nonblank `license_id` and `rights_holder`;
- `license_verified_at_epoch_ms` is positive and not in the future;
- expiry is null or later than import time;
- `review_status=APPROVED`, with a nonblank reviewer ID and positive, nonfuture review timestamp;
- `provenance_sha256` is 64 lowercase hex characters.

`CLEARED` is a redistribution authorization gate, not a claim that public-domain status can be inferred. The publisher must retain the source license evidence, scope/territory/attribution/derivative terms, version and expiry evidence outside the pack. Legal review of those terms remains a release gate. The app rejects restricted/unknown/expired or pending/rejected sources; it does not weaken this rule to improve retrieval recall.

### Passage identity and integrity

`content_sha256` is SHA-256 of the exact UTF-8 bytes of `original_text` as stored. Do not hash the normalized search form, a rendered string, or an LLM paraphrase.

`stable_evidence_key` is SHA-256 over the domain prefix `timeline-evidence-key-v1\n`, followed by these four UTF-8 fields in order: `source_id`, `edition_id`, `locator`, `language_code`. Each field is preceded by its unsigned 32-bit big-endian byte length. This length-prefix encoding prevents delimiter ambiguity. The app recomputes both hashes over every passage during import and rechecks identity, the exact original-text hash, and the normalized search form when a passage is returned. The index validator also rejects oversized optional metadata and malformed source/passage fields.

`normalizer_version` on every passage and `pack_metadata.normalizer_version` must equal `timeline-normalizer-v1`. The normalizer uses NFKC, lowercasing, removal of combining marks and Arabic tatweel, and the explicitly versioned Arabic character mappings in `CorpusTextNormalizer`. Normalized text is only for retrieval, never an authoritative quotation. A future normalization change requires a new version and re-indexing; it must not silently mutate old packs.

## 5. Search semantics

- `passage_fts` is FTS5 with `unicode61 remove_diacritics 2`; the producer stores the app-normalized string in `normalized_text`. Unicode tokenization alone does not replace the app normalizer for Arabic harakat/alef folding.
- Lexical search applies license/review/time, language and madhhab predicates inside SQL and rechecks source eligibility, stable evidence identity, normalized text and exact original-text hash in Kotlin before results are returned.
- With no selected madhhab, there is no school-specific filter. When one school is selected, `ALL`, `COMPARATIVE` and the selected school's passages are eligible; other school-specific passages are excluded.
- Each installed pack database is a separate BM25 channel. BM25 raw scores from independent pack databases are not on a shared scale and are never compared.
- RRF combines one-based ranks. An evidence item contributes at most once from each channel. Different source, edition, provenance, locator or language identities are not deduplicated together. RRF score is a ranking utility, not a probability or scholarly confidence.
- A local dense retriever may supply independent ranked channels. No vector implementation is included in this tranche. No reranker is implemented and traces correctly leave `reranked=false`.

## 6. Versioning, updates and revocation limitations

Schema v1 currently disallows installing a second version of the same `pack_id`; replacement requires a future dedicated update workflow. This avoids accidentally overwriting a pack used by an active retrieval request.

The installer preserves the exact verified `manifest.txt` and `manifest.sig` beside `corpus.sqlite`, so a later app version can audit the signing key and signature without relying on the original archive. The installer verifies publisher key activation/revocation at import time. The production app keyring is not configured in this source tranche, and installed-pack revalidation/revocation enforcement plus its UI are not yet wired. Do not distribute this as a live trusted corpus until publisher keys, installed-pack revocation handling, SAF management UI, publisher signing process and scholarly governance are implemented and tested.

No actual Quran, hadith, tafsir or fiqh corpus content is included in this project source archive. The pack schema does not grant rights to any source and does not itself certify scholarly correctness.
