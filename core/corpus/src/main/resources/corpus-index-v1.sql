-- Timeline Corpus Pack index schema v1.
-- Build this database offline with SQLite compiled with SQLITE_ENABLE_FTS5.
-- Do not ship proprietary source data unless the rights holder/license permits redistribution.
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = DELETE;
PRAGMA application_id = 1413563971;
PRAGMA user_version = 1;

CREATE TABLE pack_metadata (
    key TEXT PRIMARY KEY NOT NULL,
    value TEXT NOT NULL
) WITHOUT ROWID;

CREATE TABLE source (
    source_id TEXT PRIMARY KEY NOT NULL CHECK (length(trim(source_id)) BETWEEN 1 AND 128),
    source_type TEXT NOT NULL CHECK (source_type IN ('QURAN','HADITH','TAFSIR','FIQH','REFERENCE')),
    title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 512),
    author TEXT CHECK (author IS NULL OR length(author) <= 512),
    publisher TEXT CHECK (publisher IS NULL OR length(publisher) <= 256),
    edition_id TEXT NOT NULL CHECK (length(trim(edition_id)) BETWEEN 1 AND 128),
    edition TEXT NOT NULL CHECK (length(trim(edition)) BETWEEN 1 AND 512),
    rights_holder TEXT NOT NULL CHECK (length(trim(rights_holder)) BETWEEN 1 AND 256),
    license_id TEXT NOT NULL CHECK (length(trim(license_id)) BETWEEN 1 AND 128),
    license_url TEXT CHECK (license_url IS NULL OR length(license_url) <= 2048),
    license_status TEXT NOT NULL CHECK (license_status IN ('CLEARED','RESTRICTED','UNKNOWN','EXPIRED')),
    license_verified_at_epoch_ms INTEGER,
    license_expires_at_epoch_ms INTEGER,
    review_status TEXT NOT NULL CHECK (review_status IN ('APPROVED','PENDING','REJECTED')),
    reviewer_id TEXT,
    reviewed_at_epoch_ms INTEGER,
    provenance_sha256 TEXT NOT NULL CHECK (length(provenance_sha256) = 64 AND provenance_sha256 NOT GLOB '*[^a-f0-9]*'),
    CHECK (license_expires_at_epoch_ms IS NULL OR license_verified_at_epoch_ms IS NULL OR license_expires_at_epoch_ms > license_verified_at_epoch_ms)
);

CREATE TABLE passage (
    rowid INTEGER PRIMARY KEY,
    passage_id TEXT NOT NULL UNIQUE CHECK (length(passage_id) BETWEEN 1 AND 160),
    source_id TEXT NOT NULL REFERENCES source(source_id) ON DELETE RESTRICT,
    locator TEXT NOT NULL CHECK (length(trim(locator)) BETWEEN 1 AND 512),
    language TEXT NOT NULL CHECK (language IN ('ar','en')),
    original_text TEXT NOT NULL CHECK (length(original_text) BETWEEN 1 AND 100000),
    normalized_text TEXT NOT NULL CHECK (length(normalized_text) BETWEEN 1 AND 100000),
    normalizer_version TEXT NOT NULL,
    madhhab_scope TEXT NOT NULL CHECK (madhhab_scope IN ('ALL','COMPARATIVE','HANAFI','MALIKI','SHAFII','HANBALI')),
    source_grade TEXT CHECK (source_grade IS NULL OR length(source_grade) <= 128),
    stable_evidence_key TEXT NOT NULL CHECK (length(stable_evidence_key) = 64 AND stable_evidence_key NOT GLOB '*[^a-f0-9]*'),
    content_sha256 TEXT NOT NULL CHECK (length(content_sha256) = 64 AND content_sha256 NOT GLOB '*[^a-f0-9]*')
);

CREATE INDEX passage_source_idx ON passage(source_id);
CREATE INDEX passage_language_idx ON passage(language);
CREATE INDEX passage_evidence_key_idx ON passage(stable_evidence_key);

CREATE VIRTUAL TABLE passage_fts USING fts5(
    passage_id UNINDEXED,
    normalized_text,
    content='passage',
    content_rowid='rowid',
    tokenize='unicode61 remove_diacritics 2'
);

CREATE TRIGGER passage_ai AFTER INSERT ON passage BEGIN
    INSERT INTO passage_fts(rowid, passage_id, normalized_text)
    VALUES (new.rowid, new.passage_id, new.normalized_text);
END;

CREATE TRIGGER passage_ad AFTER DELETE ON passage BEGIN
    INSERT INTO passage_fts(passage_fts, rowid, passage_id, normalized_text)
    VALUES ('delete', old.rowid, old.passage_id, old.normalized_text);
END;

CREATE TRIGGER passage_au AFTER UPDATE ON passage BEGIN
    INSERT INTO passage_fts(passage_fts, rowid, passage_id, normalized_text)
    VALUES ('delete', old.rowid, old.passage_id, old.normalized_text);
    INSERT INTO passage_fts(rowid, passage_id, normalized_text)
    VALUES (new.rowid, new.passage_id, new.normalized_text);
END;
