package com.cropadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** For two close problems of one crop: the one sign (from the symptom tree) whose typical-ness differs most between them. */
object Lookalike {
    data class Pair2(val otherClass: String, val featureId: String, val pShown: Double, val pOther: Double)

    fun find(shown: String, ranked: List<Guess>): Pair2? {
        val crop = shown.substringBefore("__")
        val other = ranked.firstOrNull { it.classId != shown && it.classId.substringBefore("__") == crop && !it.classId.contains("healthy") && it.prob >= 0.05f }
            ?: return null
        var best: Pair2? = null
        for (q in Checks.questionsOf(crop)) {
            val a = Checks.p(shown, q) ?: continue
            val b = Checks.p(other.classId, q) ?: continue
            if (Math.abs(a - b) >= 0.4 && (best == null || Math.abs(a - b) > Math.abs(best.pShown - best.pOther))) best = Pair2(other.classId, q, a, b)
        }
        return best
    }
}

@Composable
fun LookalikeCard(lang: Lang, shown: Entry, ranked: List<Guess>) {
    if (shown.isHealthy) return
    val h = Lookalike.find(shown.classId, ranked) ?: return
    val other = Kb.get(h.otherClass, lang) ?: return
    val q = Checks.feature(h.featureId)?.text(lang) ?: return
    val yesFavoursShown = h.pShown > h.pOther
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE7F6))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr(lang, "Is it ${other.name} instead?", "क्या यह ${other.name} है?"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(tr(lang, "The two look alike. This one sign tells them apart:", "दोनों एक जैसे दिखते हैं। यह एक लक्षण दोनों में फ़र्क बताता है:"), fontSize = 14.sp)
            Text(q, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("•  " + tr(lang, "Yes → points to ${if (yesFavoursShown) shown.name else other.name}", "हाँ → ${if (yesFavoursShown) shown.name else other.name} की ओर संकेत"), fontSize = 14.sp)
            Text("•  " + tr(lang, "No → points to ${if (yesFavoursShown) other.name else shown.name}", "नहीं → ${if (yesFavoursShown) other.name else shown.name} की ओर संकेत"), fontSize = 14.sp)
            Text(tr(lang, "A guide from the symptom notes, not proof. Use \"Check to be sure\" for a full check.", "लक्षण-सूची पर आधारित मार्गदर्शन, पक्का प्रमाण नहीं। पूरी जाँच के लिए \"पक्का करें\" इस्तेमाल करें।"), fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}
