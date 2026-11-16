package com.cropadvisor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    private fun plan(type: String, band: Band?, wet: Boolean?, stage: Int, chem: Boolean = true) =
        SprayPlan.plan(Lang.EN, type, band, wet, stage, chem)

    @Test fun viralAlwaysRemovePlants() {
        assertEquals(SprayPlan.Verdict.REMOVE_PLANTS, plan("viral", Band.VERY_LIGHT, false, 0).verdict)
    }

    @Test fun veryLightAndDryMeansNoAction() {
        assertEquals(SprayPlan.Verdict.NO_ACTION, plan("fungal", Band.VERY_LIGHT, false, 0).verdict)
    }

    @Test fun severeWetFloweringMeansActNow() {
        assertEquals(SprayPlan.Verdict.ACT_NOW, plan("fungal", Band.SEVERE, true, 1).verdict)
    }

    @Test fun wetWeatherMakesFungalWorse() {
        val dry = plan("fungal", Band.MILD, false, 0).verdict.ordinal
        val wet = plan("fungal", Band.MILD, true, 0).verdict.ordinal
        assertTrue(wet >= dry)
    }

    @Test fun unmeasuredIsNotTreatedAsHealthy() {
        assertTrue(plan("fungal", null, null, 0).verdict != SprayPlan.Verdict.NO_ACTION)
    }

    @Test fun recheckDaysAreShortWhenWet() {
        assertEquals(2, plan("fungal", Band.MILD, true, 0).recheckDays)
    }

    @Test fun translationKeyMatchesPythonBuild() {
        // sha1("Apple scab")[:12], same as the asset files made by the translation scripts
        assertEquals("0b84251ea545", I18n.key("Apple scab"))
        assertEquals(I18n.key("Apple scab"), I18n.key("  Apple scab \n"))
    }

    @Test fun outcomeTrendUsesFivePointRule() {
        assertEquals(Outcomes.WORSE, Outcomes.trend(20, 25))
        assertEquals(Outcomes.BETTER, Outcomes.trend(30, 25))
        assertEquals(Outcomes.SAME, Outcomes.trend(20, 23))
        assertEquals(0, Outcomes.trend(null, 25))
    }

    @Test fun latestWorseAnswerNeedsExpert() {
        val l = listOf(Outcomes.Item(1, "a", Outcomes.WORSE), Outcomes.Item(2, "a", Outcomes.BETTER))
        assertTrue(!Outcomes.needsExpert(l, "a"))
        assertTrue(Outcomes.needsExpert(l + Outcomes.Item(3, "a", Outcomes.WORSE), "a"))
        assertTrue(!Outcomes.needsExpert(l, "b"))
    }

    @Test fun betterShareNeedsThreeAnswers() {
        val l = listOf(Outcomes.Item(1, "a", 1), Outcomes.Item(2, "a", 3))
        assertEquals(null, Outcomes.betterShare(l, "a"))
        assertEquals(66, Outcomes.betterShare(l + Outcomes.Item(3, "a", 1), "a"))
    }

    @Test fun gateIsNeverBelowBase() {
        assertTrue(Classifier.gateFor("tomato__early_blight") >= Classifier.GATE)
        assertTrue(Classifier.gateFor("unknown") >= Classifier.GATE)
    }

    @Test fun pestAnswerRefusedWhenLeafMayBeHealthy() {
        assertTrue(Classifier.answers("pest__aphids", 0.95f, 0.005f))
        assertFalse(Classifier.answers("pest__aphids", 0.95f, 0.05f))
        assertTrue(Classifier.answers("tomato__early_blight", 0.95f, 0.05f))
    }

    @Test fun voiceCommandsMatchInManyLanguages() {
        assertEquals(Cmd.CAMERA, VoiceNav.match(Lang.EN, "take photo"))
        assertEquals(Cmd.GALLERY, VoiceNav.match(Lang.EN, "choose photo from gallery"))
        assertEquals(Cmd.CAMERA, VoiceNav.match(Lang.HI, "फोटो लें"))
        assertEquals(Cmd.HELP, VoiceNav.match(Lang.TA, "உதவி"))
        assertEquals(Cmd.DIARY, VoiceNav.match(Lang.UR, "ڈائری کھولیں"))
        assertEquals(Cmd.CAMERA, VoiceNav.match(Lang.ML, "camera")) // English words work in every language
        assertEquals(null, VoiceNav.match(Lang.EN, "banana"))
        assertEquals(Cmd.DIARY, VoiceNav.match(Lang.TA, "டயரி திற")) // spelling differences are tolerated
        assertEquals(Cmd.DIARY, VoiceNav.match(Lang.TA, "diary"))
        assertEquals(Cmd.WEATHER, VoiceNav.match(Lang.EN, "wether today"))
        assertEquals(Cmd.WEATHER, VoiceNav.match(Lang.TA, "vaanilai"))      // typed in English letters
        assertEquals(Cmd.WEATHER, VoiceNav.match(Lang.TA, "வானிலை"))
        assertEquals(Cmd.WEATHER, VoiceNav.match(Lang.HI, "mausam batao"))
        assertEquals(Cmd.CAMERA, VoiceNav.match(Lang.TE, "photo tiyyi"))
        assertEquals(null, VoiceNav.match(Lang.EN, "tall bank"))             // near-misses of short English words must not fire
        assertEquals(Cmd.CALL, VoiceNav.match(Lang.EN, "call the kisan helpline"))
        assertEquals(Cmd.CALL, VoiceNav.match(Lang.HI, "किसान हेल्पलाइन पर फोन लगाओ"))
        assertEquals(Cmd.GALLERY, VoiceNav.match(Lang.BN, "ফোনের ছবি দেখাও"))
        assertEquals(Lang.TA, VoiceNav.matchLang("change to Tamil"))
        assertEquals(Lang.HI, VoiceNav.matchLang("हिंदी"))
    }

    // ───────── advisory safety checks ─────────

    private val entry = Entry(
        classId = "tomato__early_blight", crop = "tomato", name = "Early blight", type = "fungal",
        cause = "Fungus that spreads in warm, humid weather.", symptoms = listOf("Brown spots with rings on older leaves."),
        lookAlikes = null, actions = listOf("Remove badly infected leaves.", "Avoid wetting the leaves."),
        treatments = emptyList(), whenToAct = "Act at the first spots.", ask = emptyList(), sources = emptyList(), caveats = null,
    )
    private val facts = Grounding.facts(entry, Band.MILD, 12)

    @Test fun advisoryAcceptsAnswerThatUsesOnlyTheFacts() {
        val a = "This looks like early blight, a fungus that spreads in warm humid weather. About 12 percent of the leaf is damaged. Remove badly infected leaves and avoid wetting the leaves. If it keeps spreading, call the Kisan Call Centre on 1800-180-1551."
        assertTrue(Grounding.checkAdvisory(a, entry, facts, 12, Lang.EN).ok)
    }

    @Test fun advisoryRejectsInventedChemicalAndNumber() {
        val chem = "This is early blight. Spray mancozeb on the leaves and remove infected leaves, then call an expert for help."
        assertFalse(Grounding.checkAdvisory(chem, entry, facts, 12, Lang.EN).ok)
        val num = "This is early blight with 55 percent damage. Remove badly infected leaves and call an expert if it spreads further."
        assertFalse(Grounding.checkAdvisory(num, entry, facts, 12, Lang.EN).ok)
    }

    @Test fun hindiAdvisoryNeedsHindiAndNoChemicals() {
        val ok = "यह अर्ली ब्लाइट जैसा लगता है। पत्ते का लगभग 12 प्रतिशत हिस्सा खराब है। बहुत संक्रमित पत्ते हटा दें और पत्तों को गीला न करें। फैलता रहे तो विशेषज्ञ से बात करें।"
        assertTrue(Grounding.checkAdvisory(ok, entry, facts, 12, Lang.HI).ok)
        assertFalse(Grounding.checkAdvisory("This is early blight. Remove badly infected leaves and avoid wetting the leaves, then call an expert soon.", entry, facts, 12, Lang.HI).ok)
        assertFalse(Grounding.checkAdvisory(ok + " मैंकोजेब का छिड़काव करें।", entry, facts, 12, Lang.HI).ok)
        assertFalse(Grounding.checkAdvisory("पत्ते का १२ प्रतिशत हिस्सा खराब है। बहुत संक्रमित पत्ते हटा दें और विशेषज्ञ से बात करें, जल्दी करें।", entry, facts, 12, Lang.HI).ok)
    }

    @Test fun advisoryPromptCarriesFactsAndLanguage() {
        val p = Grounding.advisoryPrompt(facts, Lang.HI, severe = true)
        assertTrue(p.contains("Early blight") && p.contains("Hindi") && p.contains("today"))
        assertTrue(Grounding.advisoryPrompt(facts, Lang.EN, severe = false).contains("in English"))
    }

    @Test fun otherLanguageAdvisoryMustUseTheVerifiedWords() {
        val vocab = "பாதிக்கப்பட்ட இலைகளை அகற்றுங்கள் நிபுணரை தொடர்பு கொள்ளுங்கள்"
        val good = "பாதிக்கப்பட்ட இலைகளை அகற்றுங்கள். நிபுணரை தொடர்பு கொள்ளுங்கள். பாதிக்கப்பட்ட இலைகளை அகற்றுங்கள்."
        assertTrue(Grounding.checkAdvisory(good, entry, facts, 12, Lang.TA, vocab).ok)
        val invented = "இலைகளை அகற்றுங்கள். மேன்கோசெப் தெளியுங்கள். நிபுணரை தொடர்பு கொள்ளுங்கள்."
        assertFalse(Grounding.checkAdvisory(invented, entry, facts, 12, Lang.TA, vocab).ok)
        assertFalse(Grounding.checkAdvisory("Remove the badly infected leaves and call an expert soon please.", entry, facts, 12, Lang.TA, vocab).ok)
        assertFalse(Grounding.checkAdvisory(good, entry, facts, 12, Lang.TA, "").ok)
    }

    @Test fun everyLanguageHasAScript() {
        for (l in Lang.values()) assertTrue(l == Lang.EN || Grounding.scriptRange(l) != null)
    }
}
