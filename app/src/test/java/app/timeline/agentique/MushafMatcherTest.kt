package app.timeline.agentique

import app.timeline.agentique.quran.ArabicNormalizer
import app.timeline.agentique.quran.IndexedAyah
import app.timeline.agentique.quran.MushafMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MushafMatcherTest {
    @Test fun normalizerStripsDiacriticsAndUnifiesAlef() {
        val input = "\u0628\u0650\u0633\u0652\u0645\u0650 \u0671\u0644\u0644\u0651\u064E\u0647\u0650"
        assertEquals("\u0628\u0633\u0645 \u0627\u0644\u0644\u0647", ArabicNormalizer.normalize(input))
    }

    private fun ayah(sura: Int, aya: Int, vararg w: String) = IndexedAyah(sura, aya, w.toList())

    private val index = listOf(
        ayah(1, 1, "a1", "a2", "a3", "a4"),
        ayah(2, 1, "b1", "b2", "b3"),
        ayah(2, 2, "c1", "c2", "c3", "c4"),
        ayah(2, 3, "d1", "d2", "d3"),
        ayah(3, 1, "e1", "e2", "e3"),
    )

    @Test fun findsContiguousRunInOneSura() {
        val m = MushafMatcher.find("noise b1 b2 b3 c1 c2 c3 c4 d1 d2 d3 more", index)
        assertNotNull(m)
        assertEquals(2, m!!.sura); assertEquals(1, m.from); assertEquals(3, m.to)
    }

    @Test fun tooLittleOrUnrelatedTextGivesNoMatch() {
        assertNull(MushafMatcher.find("b1 b2", index))
        assertNull(MushafMatcher.find("x y z w v u", index))
    }
}
