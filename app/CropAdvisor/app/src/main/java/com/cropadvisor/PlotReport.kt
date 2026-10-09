package com.cropadvisor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Plot summary as one picture for WhatsApp or a KVK, plus a re-check reminder for the commonest problem. */
object PlotReport {
    fun affected(st: PlotState): Int = st.rows.filter { Kb.get(it.classId, Lang.EN)?.isHealthy == false }.sumOf { it.count }

    fun build(lang: Lang, st: PlotState, cropLabel: (String) -> String): Bitmap {
        val w = 1080; val pad = 40
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 52f; typeface = Typeface.DEFAULT_BOLD; color = AColor.rgb(46, 125, 50) }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 38f; typeface = Typeface.DEFAULT_BOLD; color = AColor.rgb(30, 30, 30) }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 33f; color = AColor.rgb(30, 30, 30) }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 27f; color = AColor.rgb(90, 90, 90) }
        fun lay(t: String, p: TextPaint) = StaticLayout.Builder.obtain(t, 0, t.length, p, w - 2 * pad).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.15f).build()
        val a = affected(st)
        val blocks = ArrayList<StaticLayout>()
        blocks.add(lay(tr(lang, "Plot report", "खेत की रिपोर्ट"), title))
        blocks.add(lay(tr(lang, "$a of ${st.clear} clear photos show a problem.", "${st.clear} साफ फोटो में से $a में समस्या दिखी।"), head))
        st.rows.take(6).forEachIndexed { i, r ->
            val e = Kb.get(r.classId, lang)
            val nm = if (e != null) "${cropLabel(e.crop)} — ${e.name}" else r.classId
            val dmg = r.avgDamage?.let { tr(lang, ", average damage about $it%", ", औसत नुकसान लगभग $it%") } ?: ""
            blocks.add(lay("${if (i == 0) "★ " else "•  "}$nm: ${r.count}/${st.clear}$dmg", body))
        }
        blocks.add(lay(tr(lang, "An estimate from a few photos, not a survey. No doses given. Ask your KVK or call the Kisan Call Centre 1800-180-1551 (free).",
            "कुछ फोटो से लगाया गया अनुमान, पूरा सर्वे नहीं। मात्रा नहीं दी गई है। अपने KVK से पूछें या किसान कॉल सेंटर 1800-180-1551 (मुफ़्त) पर बात करें।"), small))
        blocks.add(lay("Crop Advisor", small))
        val h = pad * 2 + blocks.sumOf { it.height + 20 }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(AColor.WHITE)
        var y = pad.toFloat()
        for (b in blocks) { c.save(); c.translate(pad.toFloat(), y); b.draw(c); c.restore(); y += b.height + 20 }
        return bmp
    }
}

@Composable
fun PlotShareCard(lang: Lang, st: PlotState, cropLabel: (String) -> String) {
    val ctx = LocalContext.current
    var set by rememberSaveable { mutableStateOf(false) }
    val first = st.rows.firstOrNull { Kb.get(it.classId, Lang.EN)?.isHealthy == false }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Share or follow up", "भेजें या आगे जाँचें"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedButton(onClick = {
                try { ShareCard.share(ctx, PlotReport.build(lang, st, cropLabel)) } catch (t: Throwable) {
                    android.widget.Toast.makeText(ctx, tr(lang, "Could not make the report.", "रिपोर्ट नहीं बन सकी।"), android.widget.Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(tr(lang, "Share plot report", "खेत की रिपोर्ट भेजें")) }
            if (first != null) {
                Button(enabled = !set, onClick = { Rechecks.set(ctx, first.classId, 4); set = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (set) tr(lang, "Reminder set for 4 days", "4 दिन बाद का रिमाइंडर लग गया") else tr(lang, "Remind me to walk the plot again in 4 days", "4 दिन बाद खेत फिर देखने की याद दिलाएँ"))
                }
            }
        }
    }
}
