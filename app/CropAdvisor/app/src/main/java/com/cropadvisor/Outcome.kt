package com.cropadvisor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Did it work?": the farmer's own answer after a re-check (better / same / worse), kept only on this phone.
 * It never changes the diagnosis by itself. It only decides whether to nudge towards an expert,
 * and gives the farmer a file to share with the KVK.
 */
object Outcomes {
    const val BETTER = 1
    const val SAME = 2
    const val WORSE = 3
    data class Item(val ts: Long, val cls: String, val result: Int)

    private fun prefs(c: Context) = c.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE)

    fun list(c: Context): List<Item> = try {
        val a = JSONArray(prefs(c).getString("outcomes", "[]"))
        List(a.length()) { val o = a.getJSONObject(it); Item(o.getLong("ts"), o.getString("cls"), o.getInt("r")) }
    } catch (e: Exception) { emptyList() }

    fun add(c: Context, cls: String, result: Int) {
        try {
            val a = JSONArray()
            (list(c) + Item(System.currentTimeMillis(), cls, result)).takeLast(200)
                .forEach { a.put(JSONObject().put("ts", it.ts).put("cls", it.cls).put("r", it.result)) }
            prefs(c).edit().putString("outcomes", a.toString()).apply()
        } catch (e: Exception) { /* a convenience; never break the app */ }
    }

    /** Trend from two measured damage values `days` apart. 0 = cannot tell. Same 5-point rule the diary uses. */
    fun trend(prevDamage: Int?, nowDamage: Int?): Int {
        if (prevDamage == null || nowDamage == null) return 0
        val d = nowDamage - prevDamage
        return when { d >= 5 -> WORSE; d <= -5 -> BETTER; else -> SAME }
    }

    /** Two "worse" answers in a row for the same problem, or the latest one is worse. */
    fun needsExpert(items: List<Item>, cls: String): Boolean = items.filter { it.cls == cls }.maxByOrNull { it.ts }?.result == WORSE

    /** Share of answered re-checks that got better, for one problem. Null until at least 3 answers exist. */
    fun betterShare(items: List<Item>, cls: String): Int? {
        val l = items.filter { it.cls == cls }
        return if (l.size < 3) null else l.count { it.result == BETTER } * 100 / l.size
    }

    fun csv(c: Context): String {
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder("date,detected_class,farmer_answer\n")
        list(c).sortedBy { it.ts }.forEach {
            sb.append(f.format(Date(it.ts))).append(',').append(it.cls).append(',')
                .append(when (it.result) { BETTER -> "better"; SAME -> "same"; else -> "worse" }).append('\n')
        }
        return sb.toString()
    }
}

fun callKisanCentre(ctx: Context) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:18001801551")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

/** Three big buttons. Shown on a due re-check reminder. */
@Composable
fun OutcomeButtons(lang: Lang, onAnswer: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tr(lang, "Is the plant better, the same or worse than before?", "क्या पौधा पहले से बेहतर, वैसा ही या ज़्यादा ख़राब है?"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onAnswer(Outcomes.BETTER) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr(lang, "Better", "बेहतर"), fontSize = 15.sp) }
            OutlinedButton(onClick = { onAnswer(Outcomes.SAME) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr(lang, "Same", "वैसा ही"), fontSize = 15.sp) }
            OutlinedButton(onClick = { onAnswer(Outcomes.WORSE) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(tr(lang, "Worse", "ख़राब"), fontSize = 15.sp) }
        }
    }
}

/** Shown when the plant got worse, damage is severe, or the diagnosis was unsure: a human should look. */
@Composable
fun EscalationCard(lang: Lang, cls: String, reason: String? = null) {
    val ctx = LocalContext.current
    val e = Kb.get(cls, lang)
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Please ask a person", "कृपया किसी जानकार से पूछें"), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFB71C1C))
            Text(reason ?: tr(lang, "The app cannot be sure here. Show the plant to your KVK or agriculture officer, or call the free Kisan Call Centre.",
                "यहाँ ऐप पक्का नहीं कह सकता। पौधा अपने KVK या कृषि अधिकारी को दिखाएँ, या मुफ़्त किसान कॉल सेंटर पर फ़ोन करें।"), fontSize = 15.sp)
            Button(onClick = { callKisanCentre(ctx) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(tr(lang, "Call Kisan Call Centre 1800-180-1551", "किसान कॉल सेंटर 1800-180-1551 पर फ़ोन करें"), fontSize = 15.sp)
            }
            val last = if (e != null && !e.isHealthy) History.list(ctx).firstOrNull { it.classId == cls } else null
            if (e != null && last != null) {
                OutlinedButton(onClick = {
                    try {
                        val bmp = android.graphics.BitmapFactory.decodeFile(last.file)
                        if (bmp != null) ShareCard.share(ctx, ShareCard.build(lang, e, bmp, null, tr(lang, "Please check this plant", "कृपया इस पौधे को देखें")))
                    } catch (t: Throwable) {}
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(tr(lang, "Send photo and result to an expert", "फोटो और नतीजा किसी जानकार को भेजें"), fontSize = 15.sp)
                }
            }
        }
    }
}

/** "Your re-checks that got better: 60%". Appears once there are at least three answers for this problem. Only the farmer's own answers. */
@Composable
fun OutcomeStat(lang: Lang, cls: String) {
    val ctx = LocalContext.current
    val share = Outcomes.betterShare(Outcomes.list(ctx), cls) ?: return
    Text(tr(lang, "Your re-checks that got better", "आपकी दोबारा जाँचें जो बेहतर हुईं") + ": $share%", fontSize = 14.sp, color = Color(0xFF444444))
}
