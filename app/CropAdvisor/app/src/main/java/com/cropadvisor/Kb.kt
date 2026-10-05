package com.cropadvisor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Kb {
    private var en: Map<String, Entry> = emptyMap()
    private var hi: Map<String, Entry> = emptyMap()

    fun load(ctx: Context) {
        en = parse(read(ctx, "kb.json"))
        hi = try { parse(read(ctx, "kb_hi.json")) } catch (e: Exception) { emptyMap() }
        Grounding.knownCauses = en.values.map { it.cause }
    }

    /** Hindi entry if we have one, otherwise English. */
    fun get(classId: String, lang: Lang): Entry? =
        (if (lang == Lang.HI) hi[classId] else null) ?: en[classId]

    fun hasHindi(classId: String) = hi.containsKey(classId)

    fun allEnglish(): Collection<Entry> = en.values

    private fun read(ctx: Context, name: String) =
        ctx.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun parse(text: String): Map<String, Entry> {
        val arr = JSONObject(text).getJSONArray("entries")
        val out = LinkedHashMap<String, Entry>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val e = Entry(
                classId = o.getString("class_id"),
                crop = o.optString("crop"),
                name = o.optString("name_en"),
                type = o.optString("type"),
                cause = o.optString("cause"),
                symptoms = strings(o.optJSONArray("symptoms")),
                lookAlikes = o.optStringOrNull("look_alikes"),
                actions = strings(o.optJSONArray("cultural_actions")),
                treatments = treatments(o.optJSONArray("treatment_options")),
                whenToAct = o.optStringOrNull("when_to_act"),
                ask = asks(o.optJSONArray("ask_back")),
                sources = sources(o.optJSONArray("sources")),
                caveats = o.optStringOrNull("caveats"),
            )
            out[e.classId] = e
        }
        return out
    }

    private fun JSONObject.optStringOrNull(k: String): String? =
        if (isNull(k) || !has(k)) null else getString(k).takeIf { it.isNotBlank() }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    private fun treatments(a: JSONArray?): List<Treatment> =
        if (a == null) emptyList() else List(a.length()) {
            val o = a.getJSONObject(it)
            Treatment(
                o.optString("kind"), o.optString("active_ingredient"), o.optString("source_url"),
                o.optBoolean("indian_source", false), o.optString("cibrc_status", "unchecked"),
            )
        }

    private fun asks(a: JSONArray?): List<Ask> =
        if (a == null) emptyList() else List(a.length()) {
            val o = a.getJSONObject(it)
            Ask(o.optString("question"), o.optString("if_yes"), o.optString("if_no"))
        }

    private fun sources(a: JSONArray?): List<Source> =
        if (a == null) emptyList() else List(a.length()) {
            val o = a.getJSONObject(it)
            Source(o.optString("title"), o.optString("url"), o.optString("publisher"))
        }
}
