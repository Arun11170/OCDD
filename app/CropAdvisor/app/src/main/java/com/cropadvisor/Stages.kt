package com.cropadvisor

import android.content.Context
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
import org.json.JSONObject

/**
 * Crop stage guide. Every line is a sentence already in the checked advice that names a growth stage,
 * grouped by crop and stage (assets/stages.json, made from kb.json and organic.json). Nothing is added from memory.
 */
object Stages {
    data class Item(val classId: String, val text: String)
    private var data: JSONObject = JSONObject()
    val order = listOf("seedling", "vegetative", "flowering", "fruiting", "harvest")

    fun load(ctx: Context) {
        data = try { JSONObject(ctx.assets.open("stages.json").bufferedReader(Charsets.UTF_8).use { it.readText() }) } catch (e: Exception) { JSONObject() }
    }

    fun items(crop: String, stage: String): List<Item> {
        val a = data.optJSONObject(crop)?.optJSONArray(stage) ?: return emptyList()
        return List(a.length()) { a.getJSONObject(it).let { o -> Item(o.getString("c"), o.getString("t")) } }
    }

    fun available(crop: String) = order.filter { items(crop, it).isNotEmpty() }

    fun label(l: Lang, st: String) = when (st) {
        "seedling" -> tr(l, "Seedling / planting", "पौध / रोपाई")
        "vegetative" -> tr(l, "Growing", "बढ़वार")
        "flowering" -> tr(l, "Flowering", "फूल")
        "fruiting" -> tr(l, "Fruit / grain", "फल / दाना")
        else -> tr(l, "Harvest / storage", "कटाई / भंडारण")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropCalendarCard(lang: Lang, crop: String?) {
    var stage by rememberSaveable { mutableStateOf("") }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Crop stage guide", "फसल अवस्था गाइड"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            val avail = if (crop == null) emptyList() else Stages.available(crop)
            if (crop == null || avail.isEmpty()) {
                Text(if (crop == null) tr(lang, "Check a leaf first. This guide uses the crop of your latest check.", "पहले एक पत्ता जाँचें। यह गाइड आपकी पिछली जाँच की फसल के हिसाब से है।")
                     else tr(lang, "There is no stage guide for this crop yet.", "इस फसल के लिए अभी अवस्था गाइड नहीं है।"), fontSize = 14.sp)
                return@Column
            }
            val cur = if (stage in avail) stage else ""
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                avail.forEach { st -> FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = cur == st, onClick = { stage = st }, label = { Text(Stages.label(lang, st)) }) }
            }
            if (cur.isEmpty()) {
                Text(tr(lang, "Choose a stage.", "एक अवस्था चुनें।"), fontSize = 14.sp)
            } else {
                Stages.items(crop, cur).take(8).forEach { it2 ->
                    val e = Kb.get(it2.classId, lang)
                    Text(e?.name ?: it2.classId, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(I18n.t(lang, it2.text), fontSize = 13.sp, color = Color(0xFF444444))
                }
            }
            Text(tr(lang, "Taken from the checked advice, not a full crop calendar.", "जाँची हुई सलाह से लिया गया है, पूरा फसल कैलेंडर नहीं।"), fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}
