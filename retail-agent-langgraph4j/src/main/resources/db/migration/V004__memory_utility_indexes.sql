-- Phase 4: keep durable utility aggregation and consolidation checks indexed by subject scope.
CREATE INDEX IF NOT EXISTS ix_coala_memory_uses_item
    ON coala_memory_uses(subject_id, memory_kind, memory_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS ix_coala_semantic_consolidation
    ON coala_semantic_memory(subject_id, LOWER(subject_term), LOWER(predicate), updated_at DESC)
    WHERE lifecycle = 'ACTIVE';
