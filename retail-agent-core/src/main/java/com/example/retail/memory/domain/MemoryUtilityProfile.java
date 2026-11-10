package com.example.retail.memory.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Durable, evidence-backed usage summary for one memory item.
 * Exposure alone is deliberately not counted as positive utility.
 */
public record MemoryUtilityProfile(
        String subjectId,
        String memoryId,
        MemoryUseRecord.Kind memoryKind,
        long totalUses,
        long meaningfulUses,
        long supportedOutcomes,
        long contradictedOutcomes,
        long irrelevantOutcomes,
        long undeterminedOutcomes,
        Instant lastUsedAt) implements Serializable {

    public MemoryUtilityProfile {
        subjectId = required(subjectId, "subjectId");
        memoryId = required(memoryId, "memoryId");
        Objects.requireNonNull(memoryKind, "memoryKind");
        if (totalUses < 0 || meaningfulUses < 0 || supportedOutcomes < 0 || contradictedOutcomes < 0
                || irrelevantOutcomes < 0 || undeterminedOutcomes < 0) {
            throw new IllegalArgumentException("memory utility counters cannot be negative");
        }
        long attributions = supportedOutcomes + contradictedOutcomes + irrelevantOutcomes + undeterminedOutcomes;
        if (meaningfulUses > totalUses || attributions > totalUses) {
            throw new IllegalArgumentException("memory utility counters exceed total uses");
        }
        if ((lastUsedAt != null && totalUses == 0) || (lastUsedAt == null && totalUses > 0)) {
            throw new IllegalArgumentException("lastUsedAt must be present exactly when at least one use exists");
        }
    }

    public long evidenceBackedOutcomes() {
        return supportedOutcomes + contradictedOutcomes;
    }

    /** Beta(1,1) smoothed support rate; neutral is 0.5 until outcome evidence exists. */
    public double learnedSupportRate() {
        return (supportedOutcomes + 1.0) / (evidenceBackedOutcomes() + 2.0);
    }

    /**
     * Blend the candidate's intrinsic value with observed outcomes. Four evidence-backed outcomes
     * are the prior-strength constant: until then, learned feedback changes the value gradually.
     * This is an explicit engineering heuristic, not a formula prescribed by CoALA or Fan & Lan.
     */
    public double adjustDownstreamValue(double intrinsicValue) {
        if (!Double.isFinite(intrinsicValue) || intrinsicValue < 0.0 || intrinsicValue > 1.0) {
            throw new IllegalArgumentException("intrinsicValue must be finite and in [0,1]");
        }
        long observations = evidenceBackedOutcomes();
        if (observations == 0) return intrinsicValue;
        // Treat the candidate's intrinsic value as the prior mean, not a fixed neutral 0.5.
        double adjusted = (supportedOutcomes + (4.0 * intrinsicValue)) / (observations + 4.0);
        return Math.max(0.0, Math.min(1.0, adjusted));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
