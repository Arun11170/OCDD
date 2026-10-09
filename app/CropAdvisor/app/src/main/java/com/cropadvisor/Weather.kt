package com.cropadvisor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Weather watch": matches the weather you describe against the "what favours it" notes from public extension pages.
 * It is a pointer to what to look for, not a forecast and not a diagnosis.
 */
object WeatherRisk {
    data class Hit(val classId: String, val score: Int, val why: String)

    private val wetRx = Regex("wet|rain|moist|dew|splash|irrigation|humid|cloudy|fog|mist|overcast|monsoon")
    private val dryRx = Regex("\\bdry\\b|drought|dusty")
    private val coolRx = Regex("\\bcool|cold|low temp")
    private val warmRx = Regex("\\bwarm|moderate temp")
    private val hotRx = Regex("\\bhot\\b|high temp|heat")
    private val rangeRx = Regex("(\\d+)\\s*(?:to|-|–)\\s*(\\d+)\\s*(?:degrees|°)")

    /** wet: 1 rain/humid, 0 dry, -1 unknown. temp: 0 cool (under 20 C), 1 warm (20-30), 2 hot (over 30), -1 unknown. */
    fun hits(crop: String, wet: Int, temp: Int, lang: Lang): List<Hit> {
        val out = ArrayList<Hit>()
        for (e in Kb.allEnglish()) {
            if (e.isHealthy || e.classId.substringBefore("__") != crop) continue
            val f = Organic.of(e.classId, Lang.EN)?.favoured?.lowercase()?.takeIf { it.isNotBlank() } ?: continue
            var s = 0
            val fWet = wetRx.containsMatchIn(f); val fDry = dryRx.containsMatchIn(f)
            if (wet == 1 && fWet) s += 2
            if (wet == 0 && fDry) s += 2
            if (wet == 0 && fWet && !fDry) s -= 1
            if (temp >= 0) {
                val mid = when (temp) { 0 -> 15; 1 -> 25; else -> 35 }
                val r = rangeRx.find(f)
                s += if (r != null) { if (mid >= r.groupValues[1].toInt() - 4 && mid <= r.groupValues[2].toInt() + 4) 1 else -1 }
                else when {
                    temp == 0 && coolRx.containsMatchIn(f) -> 1
                    temp == 1 && warmRx.containsMatchIn(f) -> 1
                    temp == 2 && hotRx.containsMatchIn(f) -> 1
                    else -> 0
                }
            }
            if (s >= 2) out.add(Hit(e.classId, s, Organic.of(e.classId, lang)?.favoured ?: f))
        }
        return out.sortedByDescending { it.score }.take(4)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherWatchCard(lang: Lang, crop: String?, cropLabel: (String) -> String) {
    var wet by rememberSaveable { mutableStateOf(-1) }
    var temp by rememberSaveable { mutableStateOf(-1) }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Weather watch", "मौसम की चेतावनी"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (crop == null) {
                Text(tr(lang, "Pick your crop above to see which problems this weather favours.", "ऊपर अपनी फसल चुनें, फिर देखें कि यह मौसम किन समस्याओं को बढ़ावा देता है।"), fontSize = 14.sp)
                return@Column
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = wet == 1, onClick = { wet = 1 }, label = { Text(tr(lang, "Rain / humid", "बारिश / नमी")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = wet == 0, onClick = { wet = 0 }, label = { Text(tr(lang, "Dry", "सूखा")) })
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = temp == 0, onClick = { temp = 0 }, label = { Text(tr(lang, "Cool (under 20°C)", "ठंडा (20°C से कम)")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = temp == 1, onClick = { temp = 1 }, label = { Text(tr(lang, "Warm (20–30°C)", "गुनगुना (20–30°C)")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = temp == 2, onClick = { temp = 2 }, label = { Text(tr(lang, "Hot (over 30°C)", "गर्म (30°C से अधिक)")) })
            }
            if (wet == -1 && temp == -1) {
                Text(tr(lang, "Tell the weather of the last few days.", "पिछले कुछ दिनों का मौसम बताएँ।"), fontSize = 14.sp)
            } else {
                val hits = WeatherRisk.hits(crop, wet, temp, lang)
                if (hits.isEmpty()) {
                    Text(tr(lang, "No listed problem of this crop strongly matches this weather. Keep checking the leaves.", "इस फसल की कोई सूचीबद्ध समस्या इस मौसम से ज़्यादा मेल नहीं खाती। पत्ते देखते रहें।"), fontSize = 14.sp)
                } else {
                    Text(tr(lang, "Look out for:", "इन पर नज़र रखें:"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    hits.forEach { h ->
                        val e = Kb.get(h.classId, lang)
                        Text("•  ${e?.name ?: h.classId}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(h.why, fontSize = 13.sp, color = Color(0xFF444444))
                    }
                }
            }
            Text(tr(lang, "From public extension notes. Not a forecast or a diagnosis.", "सार्वजनिक कृषि-विस्तार जानकारी पर आधारित। यह पूर्वानुमान या निदान नहीं है।"), fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}
