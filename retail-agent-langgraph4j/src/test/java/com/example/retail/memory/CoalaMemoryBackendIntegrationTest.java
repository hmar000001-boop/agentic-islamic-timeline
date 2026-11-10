package com.example.retail.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

import com.example.retail.agent.domain.Evidence;
import com.example.retail.memory.domain.MemoryAttributionRecord;
import com.example.retail.memory.domain.MemoryLifecycle;
import com.example.retail.memory.domain.MemoryProvenance;
import com.example.retail.memory.domain.MemoryUseRecord;
import com.example.retail.memory.domain.MemoryUtilityProfile;
import com.example.retail.memory.domain.SemanticConsolidationAssessment;
import com.example.retail.memory.domain.SemanticMemoryRecord;
import com.example.retail.memory.port.CoalaMemoryRepository;
import com.example.retail.memory.service.CoalaMemoryService;
import com.example.retail.memory.store.InMemoryCoalaMemoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Runs against the default in-memory adapter in the full-reactor job and the JDBC adapter in the
 * PostgreSQL CI job. The backend assertion prevents the PostgreSQL job from silently falling back
 * to process-local memory while still exercising the same service contract in both jobs.
 */
@SpringBootTest
class CoalaMemoryBackendIntegrationTest {
    @Autowired CoalaMemoryService memory;
    @Autowired CoalaMemoryRepository repository;
    @Value("${app.memory.backend:memory}") String backend;

    @Test
    void persistsEvidenceBackedUtilityAndRejectsOverlappingContradictions() {
        if ("postgres".equalsIgnoreCase(backend)) {
            assertThat(repository).isInstanceOf(JdbcCoalaMemoryRepository.class);
        } else {
            assertThat(repository).isInstanceOf(InMemoryCoalaMemoryRepository.class);
        }

        String subjectId = "phase4-ci-" + UUID.randomUUID();
        String idSuffix = UUID.randomUUID().toString();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(2);
        MemoryProvenance provenance = new MemoryProvenance(MemoryProvenance.Kind.USER_DECLARATION,
                "phase4-ci", "Phase 4 backend integration test", now);

        memory.recordEvidence(new Evidence("fact-evidence-" + idSuffix, "delivery", "preferred_method", "pickup",
                Evidence.EvidenceSource.USER, Evidence.Authority.USER_DECLARED, "profile", now, null,
                "test fixture"), subjectId);

        String factId = "fact-" + idSuffix;
        SemanticMemoryRecord candidate = new SemanticMemoryRecord(factId, subjectId, "delivery", "preferred_method",
                "pickup", 0.9, List.of(), provenance, MemoryLifecycle.CANDIDATE,
                now.minusSeconds(1), null, null, now, now);
        memory.proposeSemanticFact(candidate);
        memory.activateSemanticFact(subjectId, factId, List.of("fact-evidence-" + idSuffix), now.plusMillis(10));

        String useId = "use-" + idSuffix;
        String outcomeEvidenceId = "outcome-evidence-" + idSuffix;
        memory.recordEvidence(new Evidence(outcomeEvidenceId, "inventory", "delivery_method", "pickup",
                Evidence.EvidenceSource.FIRST_PARTY_SYSTEM, Evidence.Authority.SYSTEM_OF_RECORD,
                "order", now.plusMillis(20), null, "test fixture"), subjectId);
        memory.recordUse(new MemoryUseRecord(useId, subjectId, "execution-" + idSuffix, factId,
                MemoryUseRecord.Kind.SEMANTIC, MemoryUseRecord.Stage.USED_IN_PLAN, "planner", now.plusMillis(30)));
        memory.attributeUse(new MemoryAttributionRecord("attribution-" + idSuffix, subjectId,
                "execution-" + idSuffix, useId, "outcome-" + idSuffix,
                MemoryAttributionRecord.Relation.SUPPORTED, "verified by a system-of-record fixture",
                List.of(outcomeEvidenceId), "integration-test", now.plusMillis(40)));

        MemoryUtilityProfile profile = memory.utilityProfile(subjectId, factId, MemoryUseRecord.Kind.SEMANTIC);
        assertThat(profile.totalUses()).isEqualTo(1);
        assertThat(profile.meaningfulUses()).isEqualTo(1);
        assertThat(profile.supportedOutcomes()).isEqualTo(1);
        assertThat(profile.contradictedOutcomes()).isZero();
        assertThat(profile.lastUsedAt()).isEqualTo(now.plusMillis(30));
        assertThat(profile.adjustDownstreamValue(0.5)).isCloseTo(0.6, offset(1.0e-12));

        String contradictionId = "contradiction-" + idSuffix;
        SemanticMemoryRecord contradiction = new SemanticMemoryRecord(contradictionId, subjectId, "delivery",
                "preferred_method", "courier", 0.8, List.of(), provenance, MemoryLifecycle.CANDIDATE,
                now, null, null, now, now);
        memory.proposeSemanticFact(contradiction);
        SemanticConsolidationAssessment assessment = memory.assessSemanticCandidate(contradiction, now.plusSeconds(1));
        assertThat(assessment.outcome()).isEqualTo(SemanticConsolidationAssessment.Outcome.CONTRADICTION);
        assertThat(assessment.relatedActiveFactIds()).containsExactly(factId);
        assertThatThrownBy(() -> memory.activateSemanticFact(subjectId, contradictionId,
                List.of("fact-evidence-" + idSuffix), now.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CONTRADICTION");
        assertThat(memory.utilityProfile(subjectId, factId, MemoryUseRecord.Kind.SEMANTIC).supportedOutcomes()).isEqualTo(1);
    }
}
