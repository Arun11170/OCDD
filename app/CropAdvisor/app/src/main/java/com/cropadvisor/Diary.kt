package com.cropadvisor

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** Short notes the farmer adds to a saved check ("sprayed on Monday", "rain last night"). Kept only on this phone. */
object DiaryNotes {
    private fun prefs(c: Context) = c.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE)
    private fun all(c: Context) = try { JSONObject(prefs(c).getString("diary_notes", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
    fun get(c: Context, ts: Long): String = all(c).optString(ts.toString(), "")
    fun set(c: Context, ts: Long, text: String) {
        val o = all(c)
        if (text.isBlank()) o.remove(ts.toString()) else o.put(ts.toString(), text.trim().take(200))
        prefs(c).edit().putString("diary_notes", o.toString()).apply()
    }
}

/** Plain CSV of the whole diary including notes and the change since the earlier check of the same problem. */
fun diaryCsv(ctx: Context, items: List<HistoryItem>): String {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
    val sb = StringBuilder("date,detected_class,confidence_percent,damage_percent,change_since_last,note\n")
    items.sortedBy { it.ts }.forEach { h ->
        val prev = items.filter { it.classId == h.classId && it.ts < h.ts }.maxByOrNull { it.ts }
        val ch = if (h.damage != null && prev?.damage != null) (h.damage - prev.damage).toString() else ""
        sb.append(f.format(java.util.Date(h.ts))).append(',').append(h.classId).append(',').append((h.conf * 100).toInt()).append(',')
            .append(h.damage?.toString() ?: "").append(',').append(ch).append(',').append('"').append(DiaryNotes.get(ctx, h.ts).replace("\"", "'")).append("\"\n")
    }
    val o = Outcomes.csv(ctx)
    if (o.lines().size > 2) sb.append("\n").append(o)
    return sb.toString()
}

@Composable
fun DiaryDialog(lang: Lang, items: List<HistoryItem>, onExport: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr(lang, "Season diary", "मौसम की डायरी")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (items.isEmpty()) Text(tr(lang, "No checks saved yet. Each photo you check is saved here, only on this phone.", "अभी कोई जाँच सहेजी नहीं है। आपकी हर जाँच सिर्फ़ इसी फोन पर यहाँ सहेजी जाती है।"), fontSize = 14.sp)
                items.forEach { h ->
                    val e = Kb.get(h.classId, lang)
                    val prev = items.filter { it.classId == h.classId && it.ts < h.ts }.maxByOrNull { it.ts }
                    val thumb = remember(h.file) {
                        try {
                            val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                            android.graphics.BitmapFactory.decodeFile(h.file, o)?.asImageBitmap()
                        } catch (t: Throwable) { null }
                    }
                    var note by remember(h.ts) { mutableStateOf(DiaryNotes.get(ctx, h.ts)) }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (thumb != null) Image(thumb, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f)) {
                                Text(e?.name ?: h.classId, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                val d = h.damage
                                Text(History.dateText(h.ts, lang) + if (d != null) " · $d%" else "", fontSize = 13.sp, color = Color(0xFF444444))
                                if (d != null && prev?.damage != null) {
                                    val diff = d - prev.damage
                                    val t = when {
                                        diff >= 5 -> tr(lang, "Worse by $diff points since the earlier check", "पिछली जाँच से $diff अंक बढ़ा")
                                        diff <= -5 -> tr(lang, "Better by ${-diff} points since the earlier check", "पिछली जाँच से ${-diff} अंक घटा")
                                        else -> tr(lang, "About the same as the earlier check", "पिछली जाँच जैसा ही")
                                    }
                                    Text(t, fontSize = 13.sp, color = if (diff >= 5) Color(0xFFB71C1C) else if (diff <= -5) Color(0xFF2E7D32) else Color(0xFF444444))
                                }
                            }
                        }
                        OutlinedTextField(
                            value = note, onValueChange = { note = it.take(200); DiaryNotes.set(ctx, h.ts, note) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                            placeholder = { Text(tr(lang, "Note (what you did, weather)", "नोट (क्या किया, मौसम)"), fontSize = 13.sp) },
                        )
                    }
                }
            }
        },
        dismissButton = { if (items.isNotEmpty()) TextButton(onClick = { onExport(diaryCsv(ctx, items)) }) { Text(tr(lang, "Export", "निर्यात")) } },
        confirmButton = { TextButton(onClick = onClose) { Text(tr(lang, "Close", "बंद करें")) } },
    )
}
