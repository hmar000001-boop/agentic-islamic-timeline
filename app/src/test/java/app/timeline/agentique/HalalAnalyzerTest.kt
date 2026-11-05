package app.timeline.agentique

import app.timeline.agentique.halal.HalalAnalyzer
import app.timeline.agentique.halal.HalalFile
import app.timeline.agentique.halal.Verdict
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HalalAnalyzerTest {
    private val file: HalalFile = Json { ignoreUnknownKeys = true }
        .decodeFromString(File("src/main/assets/halal_rules.json").readText())
    private val analyzer = HalalAnalyzer(file.rules)

    @Test fun rulesFileIsMarkedUnreviewed() { assertFalse(file.reviewed) }

    @Test fun porkDerivativeIsHaram() {
        val r = analyzer.analyze("Sugar, wheat flour, Lard, salt")
        assertEquals(Verdict.HARAM_FOUND, r.verdict)
    }

    @Test fun eNumbersMatchWithSpacesAndSuffixLetters() {
        val r = analyzer.analyze("Colour (E 120), emulsifier E472a")
        assertEquals(Verdict.DOUBTFUL_FOUND, r.verdict)
        val ids = r.findings.map { it.rule.id }
        assertTrue("carmine" in ids && "mono-diglycerides" in ids)
    }

    @Test fun arabicIngredientsWithPrefixAreMatched() {
        val r = analyzer.analyze("المكونات: سكر، جيلاتين، ملح")
        assertEquals(Verdict.DOUBTFUL_FOUND, r.verdict)
        assertTrue(r.findings.any { it.rule.id == "gelatin" })
    }

    @Test fun cleanTextFlagsNothingAndNoSubstringFalsePositives() {
        val r = analyzer.analyze("Water, sugar, collard extract, swineherd label, bloodless, pigment")
        assertEquals(Verdict.NONE_FLAGGED, r.verdict)
    }
}
