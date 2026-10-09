package com.cropadvisor

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Past checks, kept only on this phone (photo + result), so a farmer can look again or compare after some days.
 *  fb: 0 = not answered, 1 = "yes, this was right", 2 = "no, it was wrong". fbCls = the advice that was on screen when answered. */
data class HistoryItem(
    val ts: Long, val classId: String, val conf: Float, val damage: Int?, val file: String,
    val fb: Int = 0, val fbCls: String = "",
)

object History {
    private const val MAX = 40
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE)
    private fun dir(ctx: Context) = File(ctx.filesDir, "history").also { it.mkdirs() }

    fun list(ctx: Context): List<HistoryItem> = try {
        val a = JSONArray(prefs(ctx).getString("history", "[]"))
        List(a.length()) {
            val o = a.getJSONObject(it)
            HistoryItem(o.getLong("ts"), o.getString("cls"), o.optDouble("conf", 0.0).toFloat(),
                if (o.isNull("dmg")) null else o.getInt("dmg"), o.getString("file"), o.optInt("fb", 0), o.optString("fbc", ""))
        }.sortedByDescending { it.ts }
    } catch (e: Exception) { emptyList() }

    private fun save(ctx: Context, items: List<HistoryItem>) {
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("ts", it.ts).put("cls", it.classId).put("conf", it.conf.toDouble())
                .put("dmg", it.damage ?: JSONObject.NULL).put("file", it.file).put("fb", it.fb).put("fbc", it.fbCls))
        }
        prefs(ctx).edit().putString("history", a.toString()).apply()
    }

    /** Returns the timestamp of the saved item (its id), or null if saving failed. */
    fun add(ctx: Context, photo: Bitmap, classId: String, conf: Float, damage: Int?): Long? = try {
        val ts = System.currentTimeMillis()
        val f = File(dir(ctx), "$ts.jpg")
        f.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        val items = (listOf(HistoryItem(ts, classId, conf, damage, f.absolutePath)) + list(ctx)).sortedByDescending { it.ts }
        items.drop(MAX).forEach { File(it.file).delete() }
        save(ctx, items.take(MAX))
        ts
    } catch (e: Exception) { null /* history is a convenience; never break a check because of it */ }

    fun setFeedback(ctx: Context, ts: Long, fb: Int, shownClass: String) {
        try { save(ctx, list(ctx).map { if (it.ts == ts) it.copy(fb = fb, fbCls = shownClass) else it }) } catch (e: Exception) {}
    }

    /** Latest earlier check of the same problem, at least 6 hours and at most 30 days old. */
    fun previous(ctx: Context, classId: String, now: Long): HistoryItem? =
        list(ctx).firstOrNull { it.classId == classId && now - it.ts >= 6 * 3600_000L && now - it.ts <= 30 * 86_400_000L }

    fun csv(ctx: Context): String {
        val sb = StringBuilder("date,detected_class,confidence_percent,damage_percent,feedback,advice_shown_when_answered\n")
        val f = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        list(ctx).forEach {
            sb.append(f.format(Date(it.ts))).append(',').append(it.classId).append(',').append((it.conf * 100).toInt()).append(',')
                .append(it.damage?.toString() ?: "").append(',').append(when (it.fb) { 1 -> "right"; 2 -> "wrong"; else -> "" }).append(',')
                .append(it.fbCls).append('\n')
        }
        return sb.toString()
    }

    /** Removes one saved check (and its photo). */
    fun delete(ctx: Context, ts: Long) {
        try {
            val items = list(ctx)
            items.filter { it.ts == ts }.forEach { File(it.file).delete() }
            save(ctx, items.filter { it.ts != ts })
        } catch (e: Exception) {}
    }

    fun clear(ctx: Context) {
        list(ctx).forEach { File(it.file).delete() }
        prefs(ctx).edit().remove("history").apply()
    }

    fun dateText(ts: Long, lang: Lang) = SimpleDateFormat("d MMM, HH:mm", lang.locale).format(Date(ts))
}
