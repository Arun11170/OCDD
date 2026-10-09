package com.cropadvisor

import android.content.Context
import org.json.JSONObject

/** Low-cost / organic / cultural options and "what favours it" from public extension pages. Names only, never doses. */
data class OrganicInfo(val options: List<String>, val favoured: String, val sources: List<String>)

object Organic {
    private var map: Map<String, OrganicInfo> = emptyMap()

    fun load(ctx: Context) {
        map = try {
            val o = JSONObject(ctx.assets.open("organic.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            val out = HashMap<String, OrganicInfo>()
            for (k in o.keys()) {
                val x = o.getJSONObject(k)
                val oa = x.optJSONArray("o"); val sa = x.optJSONArray("s")
                out[k] = OrganicInfo(
                    List(oa?.length() ?: 0) { oa!!.getString(it) },
                    x.optString("f"),
                    List(sa?.length() ?: 0) { sa!!.getString(it) },
                )
            }
            out
        } catch (e: Exception) { emptyMap() }
    }

    fun of(classId: String, lang: Lang = Lang.EN): OrganicInfo? {
        val o = map[classId]?.takeIf { it.options.isNotEmpty() || it.favoured.isNotBlank() } ?: return null
        if (lang == Lang.EN) return o
        return OrganicInfo(o.options.map { I18n.t(lang, it) }, I18n.t(lang, o.favoured), o.sources)
    }
}
