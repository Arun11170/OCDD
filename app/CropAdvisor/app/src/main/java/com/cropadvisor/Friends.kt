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

/** Lines of the checked advice that talk about helpful insects, predators and parasitoids, gathered into one card. */
object Friends {
    private val rx = Regex("natural enem|predator|parasit|ladybird|lady beetle|lacewing|trichogramma|coccinell|spiders|beneficial|biocontrol|biological control|hoverfl|chrysop|bracon", RegexOption.IGNORE_CASE)

    fun lines(classId: String, lang: Lang): List<String> {
        val en = Kb.get(classId, Lang.EN) ?: return emptyList()
        val loc = Kb.get(classId, lang) ?: en
        val out = LinkedHashSet<String>()
        en.actions.forEachIndexed { i, a -> if (rx.containsMatchIn(a)) out.add(loc.actions.getOrElse(i) { a }) }
        val oe = Organic.of(classId, Lang.EN)?.options.orEmpty()
        val ol = Organic.of(classId, lang)?.options.orEmpty()
        oe.forEachIndexed { i, a -> if (rx.containsMatchIn(a)) out.add(ol.getOrElse(i) { a }) }
        return out.toList().take(4)
    }
}

@Composable
fun FriendsCard(lang: Lang, classId: String) {
    val ls = Friends.lines(classId, lang)
    if (ls.isEmpty()) return
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr(lang, "Helpful insects and friends", "मददगार कीट और मित्र जीव"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            ls.forEach { Text("•  $it", fontSize = 14.sp) }
        }
    }
}
