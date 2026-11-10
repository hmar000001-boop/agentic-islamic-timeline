package com.example.retail.memory.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Deterministic conflict assessment for a proposed semantic fact; it never activates the candidate. */
public record SemanticConsolidationAssessment(
        String subjectId,
        String candidateFactId,
        Outcome outcome,
        List<String> relatedActiveFactIds,
        Instant assessedAt,
        String explanation) implements Serializable {

    public SemanticConsolidationAssessment {
        subjectId = required(subjectId, "subjectId");
        candidateFactId = required(candidateFactId, "candidateFactId");
        Objects.requireNonNull(outcome, "outcome");
        relatedActiveFactIds = relatedActiveFactIds == null ? List.of()
                : relatedActiveFactIds.stream().map(id -> required(id, "related fact id")).distinct().toList();
        Objects.requireNonNull(assessedAt, "assessedAt");
        explanation = required(explanation, "explanation");
        if (outcome == Outcome.CLEAR && !relatedActiveFactIds.isEmpty()) {
            throw new IllegalArgumentException("a clear assessment cannot contain related active facts");
        }
        if (outcome != Outcome.CLEAR && relatedActiveFactIds.isEmpty()) {
            throw new IllegalArgumentException("a non-clear assessment requires related active facts");
        }
    }

    /** Activation remains a separate evidence-checked, explicit operation. */
    public boolean mayProceedToEvidenceValidation() { return outcome == Outcome.CLEAR; }

    public enum Outcome { CLEAR, DUPLICATE, CONTRADICTION }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
