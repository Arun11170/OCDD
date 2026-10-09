package com.cropadvisor

import android.content.Context
import org.json.JSONObject
import kotlin.math.ln

data class CheckFeature(val id: String, val kind: String, val en: String, val hi: String) {
    fun text(l: Lang): String = when {
        l == Lang.EN -> en
        l == Lang.HI && hi.isNotBlank() -> hi
        else -> I18n.t(l, en)
    }
}

/**
 * The symptom tree (assets/symptom_tree.json): for every crop a short list of "can you see this?" questions, and for each
 * problem of that crop how typical that sign is. Written from the verified advice text, not measured field data.
 */
object Checks {
    private var features: Map<String, CheckFeature> = emptyMap()
    private var cropOfFeature: Map<String, String> = emptyMap()
    private var tables: Map<String, Map<String, Double>> = emptyMap()
    private var classesByCrop: Map<String, List<String>> = emptyMap()
    private var questionsByCrop: Map<String, List<String>> = emptyMap()

    fun load(ctx: Context) {
        if (features.isNotEmpty()) return
        val j = JSONObject(ctx.assets.open("symptom_tree.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
        val fm = LinkedHashMap<String, CheckFeature>()
        val cf = HashMap<String, String>()
        val tm = HashMap<String, MutableMap<String, Double>>()
        val cls = HashMap<String, MutableSet<String>>()
        val qs = LinkedHashMap<String, MutableList<String>>()
        for (crop in j.keys()) {
            val a = j.getJSONArray(crop)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val id = o.getString("id")
                fm[id] = CheckFeature(id, crop, o.getString("en"), o.optString("hi"))
                cf[id] = crop
                qs.getOrPut(crop) { mutableListOf() }.add(id)
                val ans = o.getJSONObject("a")
                for (c in ans.keys()) {
                    tm.getOrPut(c) { HashMap() }[id] = ans.getDouble(c)
                    cls.getOrPut(crop) { linkedSetOf() }.add(c)
                }
            }
        }
        features = fm; cropOfFeature = cf; tables = tm
        classesByCrop = cls.mapValues { it.value.toList() }
        questionsByCrop = qs
    }

    fun feature(id: String): CheckFeature? = features[id]
    fun p(classId: String, featureId: String): Double? = tables[classId]?.get(featureId)
    fun classesOf(crop: String): List<String> = classesByCrop[crop] ?: emptyList()
    fun questionsOf(crop: String): List<String> = questionsByCrop[crop] ?: emptyList()
    fun healthyOf(crop: String): String? = classesOf(crop).firstOrNull { it.startsWith("${crop}__healthy") }
}

/**
 * Symptom-tree engine for "Check to be sure". It starts with EVERY problem of the crop(s) the photo points to (not only the
 * photo's top guesses), weighted by the photo. Each Yes/No answer multiplies every candidate by how typical that sign is for it,
 * so a Yes rules out the problems that never show that sign and a No rules out those that always do. The next question is the one
 * that best splits the problems still left. A small allowance for wrong answers keeps one slip from ruling out the true problem.
 */
class CheckEngine(ranked: List<Guess>) {
    private val prior: Map<String, Double>
    private val crops: List<String>
    private val questions: List<String>

    init {
        val byCrop = LinkedHashMap<String, Double>()
        for (g in ranked) byCrop.merge(g.classId.substringBefore("__"), g.prob.toDouble(), Double::plus)
        val sorted = byCrop.entries.sortedByDescending { it.value }
        val picked = mutableListOf<String>()
        for ((i, e) in sorted.withIndex()) {
            if (picked.size >= 2) break
            if (Checks.classesOf(e.key).isEmpty()) continue
            if (i == 0 || e.value >= CROP_MIN) picked += e.key
        }
        crops = picked
        val probs = ranked.associate { it.classId to it.prob.toDouble() }
        val m = LinkedHashMap<String, Double>()
        for (c in crops) for (cl in Checks.classesOf(c)) m[cl] = maxOf(probs[cl] ?: 0.0, FLOOR)
        if (m.isEmpty()) for (g in ranked.take(6)) m[g.classId] = maxOf(g.prob.toDouble(), FLOOR)
        val s = m.values.sum()
        prior = m.mapValues { it.value / s }
        questions = crops.flatMap { Checks.questionsOf(it) }
    }

    val candidateCount get() = prior.size

    private fun lik(c: String, f: String, yes: Boolean): Double {
        val p = Checks.p(c, f) ?: return 0.5
        val q = NOISE + (1 - 2 * NOISE) * p
        return if (yes) q else 1 - q
    }

    fun posterior(answers: List<Pair<String, Boolean?>>): Map<String, Double> {
        val w = HashMap(prior)
        for ((f, y) in answers) {
            if (y == null) continue
            for (c in w.keys.toList()) w[c] = w.getValue(c) * lik(c, f, y)
        }
        val s = w.values.sum()
        return if (s <= 0.0) prior else w.mapValues { it.value / s }
    }

    /** Problems still possible after these answers (below 3% counts as ruled out). */
    fun left(answers: List<Pair<String, Boolean?>>): List<Pair<String, Double>> =
        posterior(answers).entries.filter { it.value >= RULED_OUT }.sortedByDescending { it.value }.map { it.key to it.value }

    private fun entropy(d: Map<String, Double>) = -d.values.sumOf { if (it > 0) it * ln(it) / ln(2.0) else 0.0 }

    private fun gain(cur: Map<String, Double>, f: String): Double {
        val py = cur.entries.sumOf { it.value * lik(it.key, f, true) }
        if (py <= 1e-9 || py >= 1 - 1e-9) return 0.0
        var h = 0.0
        for ((y, pr) in listOf(true to py, false to 1 - py)) {
            val n = cur.mapValues { it.value * lik(it.key, f, y) / pr }
            h += pr * entropy(n)
        }
        return entropy(cur) - h
    }

    /**
     * The next question to ask, or null when the farmer has answered enough.
     * Two phases: while no problem clearly leads, ask the question that best splits the candidates; once one leads (LEAD),
     * keep going through that problem's other typical signs one after another, until all are asked or the lead changes.
     */
    fun next(answers: List<Pair<String, Boolean?>>): CheckFeature? {
        val asked = answers.map { it.first }.toSet()
        if (asked.size >= MAX_QUESTIONS) return null
        val cur = posterior(answers)
        val lead = cur.maxByOrNull { it.value } ?: return null
        if (lead.value >= LEAD && asked.size >= MIN_BEFORE_TRACE) {
            val rest = questions.filter { it !in asked && (Checks.p(lead.key, it) ?: 0.0) >= TYPICAL }
            if (rest.isNotEmpty()) {
                val f = rest.maxWithOrNull(compareBy<String>({ Checks.p(lead.key, it) ?: 0.0 }, { gain(cur, it) }))
                if (f != null) return Checks.feature(f)
            }
        }
        val top = lead.value
        if (top >= SURE && asked.size >= 3) return null
        var best: String? = null
        var bestGain = 0.0
        for (f in questions) {
            if (f in asked) continue
            val g = gain(cur, f)
            if (g > bestGain) { bestGain = g; best = f }
        }
        val need = if (top >= SURE) 0.005 else MIN_GAIN
        return if (best != null && bestGain >= need) Checks.feature(best) else null
    }

    /** How many of the leading problem's typical signs the farmer has confirmed (for "5 of 6 signs confirmed"). */
    data class Trace(val classId: String, val confirmed: Int, val answered: Int, val total: Int)

    fun trace(answers: List<Pair<String, Boolean?>>): Trace? {
        val lead = posterior(answers).maxByOrNull { it.value }?.key ?: return null
        val typical = questions.filter { (Checks.p(lead, it) ?: 0.0) >= TYPICAL }
        if (typical.isEmpty()) return null
        val ans = answers.toMap()
        return Trace(lead, typical.count { ans[it] == true }, typical.count { ans[it] != null }, typical.size)
    }

    companion object {
        const val NOISE = 0.04
        const val SURE = 0.95
        const val MIN_GAIN = 0.03
        const val MAX_QUESTIONS = 14
        const val LEAD = 0.6
        const val TYPICAL = 0.7
        const val MIN_BEFORE_TRACE = 2
        const val FLOOR = 0.02
        const val CROP_MIN = 0.12
        const val RULED_OUT = 0.03
    }
}
