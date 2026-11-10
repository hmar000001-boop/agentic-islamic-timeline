package com.example.retail.memory.port;

import com.example.retail.memory.domain.MemoryAttributionRecord;
import com.example.retail.memory.domain.MemoryUseRecord;
import com.example.retail.memory.domain.MemoryUtilityProfile;
import java.util.Optional;

public interface MemoryAttributionRepository {
    void recordUse(MemoryUseRecord use);
    Optional<MemoryUseRecord> findUse(String subjectId, String useId);
    void recordAttribution(MemoryAttributionRecord attribution);
    Optional<MemoryAttributionRecord> findAttributionByUse(String subjectId, String useId);
    /** Aggregate use and evidence-backed outcome counts for one subject-scoped memory item. */
    MemoryUtilityProfile findUtilityProfile(String subjectId, String memoryId, MemoryUseRecord.Kind memoryKind);
}
