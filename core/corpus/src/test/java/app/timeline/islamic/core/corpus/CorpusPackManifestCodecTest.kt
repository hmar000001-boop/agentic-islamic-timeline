package app.timeline.islamic.core.corpus

import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.CorpusPackManifest
import org.junit.Assert.*
import org.junit.Test

class CorpusPackManifestCodecTest {
    private fun manifest() = CorpusPackManifest(
        schemaVersion = 1, packId = "quran-core", packVersion = "1.0.0", createdAtEpochMillis = 1_800_000_000_000,
        keyId = "publisher-2026", licenseProfile = "public-domain", sourceCount = 1, passageCount = 6236,
        indexFile = "corpus.sqlite", indexSha256 = "a".repeat(64), languages = setOf(CorpusLanguage.ARABIC, CorpusLanguage.ENGLISH)
    )

    @Test fun canonicalManifestRoundTripsByteForByte() {
        val encoded = CorpusPackManifestCodec.encode(manifest())
        val parsed = CorpusPackManifestCodec.parse(encoded)
        assertEquals(manifest(), parsed)
        assertArrayEquals(encoded, CorpusPackManifestCodec.encode(parsed))
    }

    @Test fun fieldReorderingIsRejectedEvenWhenValuesAreValid() {
        val text = CorpusPackManifestCodec.encode(manifest()).toString(Charsets.UTF_8)
        val lines = text.split('\n').toMutableList()
        val first = lines[1]
        lines[1] = lines[2]
        lines[2] = first
        assertThrows(IllegalArgumentException::class.java) {
            CorpusPackManifestCodec.parse(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
        }
    }

    @Test fun nonCanonicalLineEndingsAreRejected() {
        val encoded = CorpusPackManifestCodec.encode(manifest()).toString(Charsets.UTF_8).replace("\n", "\r\n")
        assertThrows(IllegalArgumentException::class.java) {
            CorpusPackManifestCodec.parse(encoded.toByteArray(Charsets.UTF_8))
        }
    }
}
