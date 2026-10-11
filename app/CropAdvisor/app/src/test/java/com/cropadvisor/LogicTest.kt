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
    }
}
