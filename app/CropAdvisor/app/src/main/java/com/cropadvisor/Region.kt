package com.cropadvisor

import android.content.Context
import org.json.JSONObject

/**
 * The farmer's state. It is only a mild hint: crops grown in that state get a small boost (x1.4) so that,
 * between two look-alike crops, the usual one for the area is preferred. The photo always decides first.
 */
object Region {
    data class State(val id: String, val hi: String)

    var states: List<State> = emptyList(); private set
    private var crops: Map<String, Set<String>> = emptyMap()
    var current: String? = null

    fun load(ctx: Context) {
        try {
            val o = JSONObject(ctx.assets.open("regions.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            val sa = o.getJSONArray("states")
            states = List(sa.length()) { State(sa.getJSONObject(it).getString("id"), sa.getJSONObject(it).getString("hi")) }
            val co = o.getJSONObject("crops")
            crops = co.keys().asSequence().associateWith { k -> val a = co.getJSONArray(k); List(a.length()) { a.getString(it) }.toSet() }
        } catch (e: Exception) { states = emptyList(); crops = emptyMap() }
        current = ctx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).getString("state", null)
    }

    fun save(ctx: Context, id: String?) {
        current = id
        ctx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).edit().putString("state", id).apply()
    }

    fun label(id: String, lang: Lang): String = when (lang) {
        Lang.EN -> id
        Lang.HI -> states.firstOrNull { it.id == id }?.hi ?: I18n.t(lang, id)
        else -> I18n.t(lang, id)
    }

    private fun boost(classId: String): Double {
        val st = current ?: return 1.0
        if (classId.startsWith("pest__")) return 1.0          // pests are not tied to a crop
        val crop = classId.substringBefore("__")
        val grown = crops[crop] ?: return 1.0
        return if (st in grown) 1.4 else 1.0
    }

    /** Re-weights the ranked guesses for the farmer's state. The rest of the probability mass is kept as it was. */
    fun apply(pred: Prediction): Prediction {
        if (current == null || pred.ranked.isEmpty()) return pred
        val rest = (1.0 - pred.ranked.sumOf { it.prob.toDouble() }).coerceAtLeast(0.0)
        val w = pred.ranked.map { it.prob * boost(it.classId) }
        val total = w.sum() + rest
        val ranked = pred.ranked.mapIndexed { i, g -> Guess(g.classId, (w[i] / total).toFloat()) }.sortedByDescending { it.prob }
        val top = ranked.take(3)
        return pred.copy(top = top, sure = top[0].prob >= Classifier.gateFor(top[0].classId) && (pred.usedSpecialist || pred.cropMass >= Classifier.MIN_CROP_MASS), ranked = ranked)
    }
}
