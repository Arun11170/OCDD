package com.cropadvisor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Text for the extra features: English, Hindi, and the machine translation (beta) when one exists for this exact English text. */
fun tr(l: Lang, en: String, hi: String): String = when (l) { Lang.EN -> en; Lang.HI -> hi; else -> I18n.ui(l, en) ?: en }

/**
 * "Spray or wait": turns the measured damage, the weather and the crop stage into one of four plain answers.
 * It never names a product or an amount. Chemicals stay in the checked list on the result screen.
 */
object SprayPlan {
    enum class Verdict { NO_ACTION, WAIT_RECHECK, ACT_NOW, REMOVE_PLANTS }
    data class Plan(val verdict: Verdict, val recheckDays: Int, val reasons: List<String>)

    /** wet: rain, dew or humid cloudy weather in the last few days (null = not sure). stage: 0 early, 1 flowering or fruiting, 2 near harvest. */
    fun plan(l: Lang, type: String, band: Band?, wet: Boolean?, stage: Int, hasTreatments: Boolean): Plan {
        val why = ArrayList<String>()
        if (type == "viral") {
            why.add(tr(l, "Virus cannot be cured by spraying the leaf.", "वायरस पत्ते पर छिड़काव से ठीक नहीं होता।"))
            return Plan(Verdict.REMOVE_PLANTS, 3, why)
        }
        var risk = when (band) { Band.VERY_LIGHT -> 0; Band.MILD -> 1; Band.MODERATE -> 2; Band.SEVERE -> 3; null -> 1 }
        why.add(when (band) {
            null -> tr(l, "Damage could not be measured, so be careful.", "नुकसान नापा नहीं जा सका, इसलिए सावधानी रखें।")
            Band.VERY_LIGHT -> tr(l, "Damage is very light.", "नुकसान बहुत हल्का है।")
            Band.MILD -> tr(l, "Damage is mild.", "नुकसान हल्का है।")
            Band.MODERATE -> tr(l, "Damage is moderate.", "नुकसान मध्यम है।")
            Band.SEVERE -> tr(l, "Damage is severe.", "नुकसान गंभीर है।")
        })
        if (wet == true && (type == "fungal" || type == "bacterial")) {
            risk += 1
            why.add(tr(l, "Wet or humid weather makes it spread faster.", "गीला या नम मौसम इसे तेज़ी से फैलाता है।"))
        } else if (wet == false && risk > 0) {
            risk -= 1
            why.add(tr(l, "Dry weather slows it down.", "सूखा मौसम इसकी रफ़्तार धीमी करता है।"))
        }
        if (stage == 1) {
            risk += 1
            why.add(tr(l, "Flowering and fruiting is the stage where a loss hurts most.", "फूल और फल का समय है, इस समय नुकसान सबसे भारी पड़ता है।"))
        }
        if (stage == 2) why.add(tr(l, "Near harvest: read the waiting period on any product label before using it.", "कटाई पास है: कोई भी उत्पाद लेने से पहले लेबल पर प्रतीक्षा अवधि पढ़ें।"))
        if (!hasTreatments) why.add(tr(l, "No checked product is listed for this problem, so rely on the field steps.", "इस समस्या के लिए कोई जाँचा हुआ उत्पाद सूचीबद्ध नहीं है, इसलिए खेत के उपाय अपनाएँ।"))
        return when {
            risk <= 0 -> Plan(Verdict.NO_ACTION, 7, why)
            risk <= 2 -> Plan(Verdict.WAIT_RECHECK, if (wet == true) 2 else 4, why)
            else -> Plan(Verdict.ACT_NOW, 2, why)
        }
    }

    fun title(l: Lang, v: Verdict) = when (v) {
        Verdict.NO_ACTION -> tr(l, "No spray needed now", "अभी छिड़काव की ज़रूरत नहीं")
        Verdict.WAIT_RECHECK -> tr(l, "Wait and re-check", "रुकें और दोबारा जाँचें")
        Verdict.ACT_NOW -> tr(l, "Act now", "अभी कदम उठाएँ")
        Verdict.REMOVE_PLANTS -> tr(l, "Remove affected plants", "प्रभावित पौधे हटाएँ")
    }

    fun body(l: Lang, v: Verdict, days: Int) = when (v) {
        Verdict.NO_ACTION -> tr(l, "Keep the field clean and look again in $days days.", "खेत साफ रखें और $days दिन बाद फिर देखें।")
        Verdict.WAIT_RECHECK -> tr(l, "Do the field steps today and take a new photo in $days days. Decide after you see whether it grew.", "आज खेत के उपाय करें और $days दिन बाद नई फोटो लें। बढ़ा या नहीं, यह देखकर तय करें।")
        Verdict.ACT_NOW -> tr(l, "Do the field steps today. Check the product list below with your KVK or agriculture officer and read the label before any spray. Re-check in $days days.", "आज खेत के उपाय करें। नीचे की सूची अपने KVK या कृषि अधिकारी से मिलाएँ और छिड़काव से पहले लेबल पढ़ें। $days दिन बाद फिर जाँचें।")
        Verdict.REMOVE_PLANTS -> tr(l, "Pull out and destroy the affected plants, control the insects that carry the virus, and look at nearby plants in $days days.", "प्रभावित पौधे उखाड़कर नष्ट करें, वायरस फैलाने वाले कीट रोकें और $days दिन बाद आसपास के पौधे देखें।")
    }
}

/** Re-check reminders kept on this phone. Shown on the home screen when due. */
object Rechecks {
    private fun prefs(c: Context) = c.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE)
    data class Item(val due: Long, val cls: String)

    fun list(c: Context): List<Item> = try {
        val a = JSONArray(prefs(c).getString("rechecks", "[]"))
        List(a.length()) { Item(a.getJSONObject(it).getLong("due"), a.getJSONObject(it).getString("cls")) }
    } catch (e: Exception) { emptyList() }

    private fun save(c: Context, items: List<Item>) {
        val a = JSONArray(); items.forEach { a.put(JSONObject().put("due", it.due).put("cls", it.cls)) }
        prefs(c).edit().putString("rechecks", a.toString()).apply()
    }

    fun set(c: Context, cls: String, days: Int) {
        val at = System.currentTimeMillis() + days * 86_400_000L
        save(c, list(c).filter { it.cls != cls } + Item(at, cls))
        Notify.schedule(c, cls, at)
    }

    fun clear(c: Context, cls: String) { save(c, list(c).filter { it.cls != cls }); Notify.cancel(c, cls) }
    fun isSet(c: Context, cls: String) = list(c).any { it.cls == cls }
    fun due(c: Context, now: Long) = list(c).filter { it.due <= now }
}

private val Soft = Color(0xFFF1F8E9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SprayPlanCard(lang: Lang, entry: Entry, band: Band?) {
    if (entry.isHealthy) return
    val ctx = LocalContext.current
    var wet by rememberSaveable { mutableStateOf(-1) }      // -1 not sure, 0 dry, 1 wet
    var stage by rememberSaveable { mutableStateOf(0) }
    var setTick by rememberSaveable { mutableStateOf(0) }
    val notifPerm = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    val plan = SprayPlan.plan(lang, entry.type, band, when (wet) { 1 -> true; 0 -> false; else -> null }, stage, entry.treatments.isNotEmpty())
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Soft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Spray or wait?", "छिड़काव करें या रुकें?"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(tr(lang, "Weather in the last few days", "पिछले कुछ दिनों का मौसम"), fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = wet == 1, onClick = { wet = 1 }, label = { Text(tr(lang, "Rain / humid", "बारिश / नमी")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = wet == 0, onClick = { wet = 0 }, label = { Text(tr(lang, "Dry", "सूखा")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = wet == -1, onClick = { wet = -1 }, label = { Text(tr(lang, "Not sure", "पता नहीं")) })
            }
            Text(tr(lang, "Crop stage", "फसल की अवस्था"), fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = stage == 0, onClick = { stage = 0 }, label = { Text(tr(lang, "Growing", "बढ़वार")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = stage == 1, onClick = { stage = 1 }, label = { Text(tr(lang, "Flowering / fruiting", "फूल / फल")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = stage == 2, onClick = { stage = 2 }, label = { Text(tr(lang, "Near harvest", "कटाई के पास")) })
            }
            val col = when (plan.verdict) {
                SprayPlan.Verdict.NO_ACTION -> Color(0xFF2E7D32)
                SprayPlan.Verdict.WAIT_RECHECK -> Color(0xFF8A6D00)
                else -> Color(0xFFB71C1C)
            }
            Text(SprayPlan.title(lang, plan.verdict), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = col)
            Text(SprayPlan.body(lang, plan.verdict, plan.recheckDays), fontSize = 15.sp)
            plan.reasons.forEach { Text("•  $it", fontSize = 13.sp, color = Color(0xFF444444)) }
            val isSet = setTick >= 0 && Rechecks.isSet(ctx, entry.classId)
            if (isSet) {
                OutlinedButton(onClick = { Rechecks.clear(ctx, entry.classId); setTick++ }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr(lang, "Reminder set. Tap to cancel", "याद दिलाना चालू है। रद्द करने के लिए दबाएँ"))
                }
            } else {
                Button(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    Rechecks.set(ctx, entry.classId, plan.recheckDays); setTick++
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr(lang, "Remind me in ${plan.recheckDays} days", "${plan.recheckDays} दिन बाद याद दिलाएँ"))
                }
            }
            Text(tr(lang, "A guide only, not a prescription. No amounts are given. Read the label.", "यह सिर्फ़ मार्गदर्शन है, पर्चा नहीं। मात्रा नहीं दी जाती। लेबल पढ़ें।"), fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}

/** Home-screen banner for reminders that are due. Asks "better, same or worse?" and sends a worse plant to an expert. */
@Composable
fun RecheckBanner(lang: Lang, onCamera: () -> Unit) {
    val ctx = LocalContext.current
    var tick by rememberSaveable { mutableStateOf(0) }
    var worse by rememberSaveable { mutableStateOf("") }
    val due = if (tick >= 0) Rechecks.due(ctx, System.currentTimeMillis()) else emptyList()
    due.forEach { r ->
        val e = Kb.get(r.cls, lang)
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(lang, "Time to re-check: ${e?.name ?: r.cls}", "दोबारा जाँचने का समय: ${e?.name ?: r.cls}"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(tr(lang, "Take a new photo of the same plant and compare.", "उसी पौधे की नई फोटो लें और तुलना करें।"), fontSize = 14.sp)
                OutcomeButtons(lang) { a ->
                    Outcomes.add(ctx, r.cls, a); Rechecks.clear(ctx, r.cls)
                    if (a == Outcomes.WORSE) worse = r.cls
                    tick++
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onCamera) { Text(tr(lang, "Take photo", "फोटो लें")) }
                    OutlinedButton(onClick = { Rechecks.clear(ctx, r.cls); tick++ }) { Text(tr(lang, "Done", "हो गया")) }
                }
            }
        }
    }
    if (worse.isNotEmpty() && Outcomes.needsExpert(Outcomes.list(ctx), worse)) EscalationCard(lang, worse,
        tr(lang, "You said it got worse. Spraying more is not the answer. Show it to your KVK or agriculture officer, or call the free Kisan Call Centre.",
            "आपने बताया कि हालत बिगड़ी है। और छिड़काव करना हल नहीं है। अपने KVK या कृषि अधिकारी को दिखाएँ, या मुफ़्त किसान कॉल सेंटर पर फ़ोन करें।"))
}

/** One picture with the result, the main steps and the safety line, for WhatsApp or to show a KVK or shop. */
object ShareCard {
    fun build(l: Lang, e: Entry, photo: Bitmap, band: Band?, verdict: String): Bitmap {
        val w = 1080; val pad = 40
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 52f; typeface = Typeface.DEFAULT_BOLD; color = AColor.rgb(46, 125, 50) }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f; color = AColor.rgb(30, 30, 30) }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = AColor.rgb(90, 90, 90) }
        fun lay(t: String, p: TextPaint) = StaticLayout.Builder.obtain(t, 0, t.length, p, w - 2 * pad).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.15f).build()
        val dmg = when (band) {
            null -> ""
            Band.VERY_LIGHT -> tr(l, "Damage: very light (estimate)", "नुकसान: बहुत हल्का (अनुमान)")
            Band.MILD -> tr(l, "Damage: mild (estimate)", "नुकसान: हल्का (अनुमान)")
            Band.MODERATE -> tr(l, "Damage: moderate (estimate)", "नुकसान: मध्यम (अनुमान)")
            Band.SEVERE -> tr(l, "Damage: severe (estimate)", "नुकसान: गंभीर (अनुमान)")
        }
        val steps = e.actions.take(4).joinToString("\n") { "•  $it" }
        val blocks = ArrayList<StaticLayout>()
        blocks.add(lay(e.name, title))
        if (dmg.isNotEmpty()) blocks.add(lay(dmg, body))
        blocks.add(lay(verdict, body))
        if (steps.isNotEmpty()) { blocks.add(lay(tr(l, "What to do", "क्या करें"), title.let { TextPaint(it).apply { textSize = 38f } })); blocks.add(lay(steps, body)) }
        blocks.add(lay(tr(l, "No doses given. Read the product label and ask your KVK or agriculture officer. Kisan Call Centre 1800-180-1551 (free). Advice checked against CIB&RC lists.",
            "मात्रा नहीं दी गई है। उत्पाद का लेबल पढ़ें और अपने KVK या कृषि अधिकारी से पूछें। किसान कॉल सेंटर 1800-180-1551 (मुफ़्त)। सलाह CIB&RC सूची से जाँची गई है।"), small))
        e.sources.firstOrNull()?.let { blocks.add(lay(it.url, small)) }
        blocks.add(lay("Crop Advisor", small))
        val k = (w - 2 * pad).toFloat() / photo.width
        val ph = minOf((photo.height * k).toInt(), 720)
        val textH = blocks.sumOf { it.height + 18 }
        val bmp = Bitmap.createBitmap(w, pad + ph + 24 + textH + pad, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(AColor.WHITE)
        val scaled = Bitmap.createScaledBitmap(photo, w - 2 * pad, maxOf(1, (photo.height * k).toInt()), true)
        c.drawBitmap(scaled, android.graphics.Rect(0, 0, scaled.width, ph), android.graphics.Rect(pad, pad, pad + scaled.width, pad + ph), null)
        var y = (pad + ph + 24).toFloat()
        for (b in blocks) { c.save(); c.translate(pad.toFloat(), y); b.draw(c); c.restore(); y += b.height + 18 }
        return bmp
    }

    fun share(ctx: Context, bmp: Bitmap) {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "advice_card.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        val i = Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun ShareCardButton(lang: Lang, entry: Entry, photo: Bitmap, band: Band?) {
    val ctx = LocalContext.current
    if (entry.isHealthy) return
    OutlinedButton(
        onClick = {
            try {
                val plan = SprayPlan.plan(lang, entry.type, band, null, 0, entry.treatments.isNotEmpty())
                ShareCard.share(ctx, ShareCard.build(lang, entry, photo, band, SprayPlan.title(lang, plan.verdict)))
            } catch (t: Throwable) { android.widget.Toast.makeText(ctx, tr(lang, "Could not make the card.", "कार्ड नहीं बन सका।"), android.widget.Toast.LENGTH_SHORT).show() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(tr(lang, "Share advice card", "सलाह कार्ड भेजें"), fontSize = 16.sp) }
}
