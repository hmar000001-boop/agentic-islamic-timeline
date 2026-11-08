package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.EvidencePassage

/** A ranked result from one independent retrieval channel. Rank is one-based; raw scores are not compared. */
data class RankedEvidence(
    val channelId: String,
    val rank: Int,
    val evidence: EvidencePassage,
    val channelWeight: Double = 1.0
)

data class RrfContribution(val channelId: String, val rank: Int, val weightedScore: Double)
data class FusedEvidence(
    val evidence: EvidencePassage,
    val rrfScore: Double,
    val contributions: List<RrfContribution>
)

/** Reciprocal Rank Fusion. Scores are ranking utilities, never probabilities or confidence values. */
object RrfFusion {
    const val DEFAULT_K = 60

    private data class EvidenceIdentityKey(
        val sourceId: String,
        val editionId: String,
        val provenanceSha256: String,
        val locator: String,
        val languageCode: String,
        val stableEvidenceKey: String,
        val contentSha256: String
    )

    fun fuse(entries: Iterable<RankedEvidence>, maxResults: Int, k: Int = DEFAULT_K): List<FusedEvidence> {
        require(maxResults in 1..500)
        require(k in 1..1_000_000)
        val grouped = LinkedHashMap<EvidenceIdentityKey, MutableList<RankedEvidence>>()
        entries.forEach { hit ->
            require(hit.channelId.isNotBlank()) { "channelId must not be blank" }
            require(hit.rank >= 1) { "rank must be one-based" }
            require(hit.channelWeight.isFinite() && hit.channelWeight in 0.0..10.0 && hit.channelWeight > 0.0) {
                "channelWeight must be finite and in (0, 10]"
            }
            require(hit.evidence.stableEvidenceKey.isNotBlank()) { "stableEvidenceKey must not be blank" }
            require(hit.evidence.contentSha256.matches(Regex("[a-f0-9]{64}"))) { "contentSha256 must be lowercase SHA-256" }
            // Do not merge the same locator/text across different editions or provenance records.
            // A stable evidence key is meaningful only inside its source+edition+locator identity.
            val evidence = hit.evidence
            val key = EvidenceIdentityKey(
                sourceId = evidence.source.sourceId,
                editionId = evidence.source.editionId,
                provenanceSha256 = evidence.source.provenanceSha256,
                locator = evidence.locator,
                languageCode = evidence.language.code,
                stableEvidenceKey = evidence.stableEvidenceKey,
                contentSha256 = evidence.contentSha256
            )
            grouped.getOrPut(key) { mutableListOf() }.add(hit)
        }
        return grouped.map { (_, duplicates) ->
            // A channel contributes once per evidence item; duplicate rows cannot inflate its vote.
            val contributions = duplicates.groupBy { it.channelId }.map { (channel, sameChannel) ->
                require(sameChannel.map { it.channelWeight }.distinct().size == 1) {
                    "A retrieval channel must use one consistent weight per evidence item"
                }
                val best = sameChannel.minBy { it.rank }
                RrfContribution(channel, best.rank, best.channelWeight / (k.toDouble() + best.rank.toDouble()))
            }.sortedBy { it.channelId }
            FusedEvidence(
                evidence = duplicates.minWith(
                    compareBy<RankedEvidence>({ it.rank }, { it.channelId }, { it.evidence.packId },
                        { it.evidence.packVersion }, { it.evidence.passageId })
                ).evidence,
                rrfScore = contributions.sumOf { it.weightedScore },
                contributions = contributions
            )
        }.sortedWith(
            compareByDescending<FusedEvidence> { it.rrfScore }
                .thenBy { it.evidence.stableEvidenceKey }
                .thenBy { it.evidence.contentSha256 }
                .thenBy { it.evidence.source.sourceId }
                .thenBy { it.evidence.source.editionId }
                .thenBy { it.evidence.source.provenanceSha256 }
                .thenBy { it.evidence.locator }
                .thenBy { it.evidence.language.code }
                .thenBy { it.evidence.contentSha256 }
                .thenBy { it.evidence.packId }
                .thenBy { it.evidence.packVersion }
                .thenBy { it.evidence.passageId }
        ).take(maxResults)
    }
}
