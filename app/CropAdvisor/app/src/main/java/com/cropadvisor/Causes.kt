package com.cropadvisor

import android.content.Context
import org.json.JSONObject

/** When the photo shows damage (or a virus), which pest causes or spreads it. Links to the pest's own advice when we have it. */
data class CauseLink(val pestClass: String?, val nameEn: String, val nameHi: String, val rel: String)

object Causes {
    private var map: Map<String, List<CauseLink>> = emptyMap()

    fun load(ctx: Context) {
        map = try {
            val o = JSONObject(ctx.assets.open("causes.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            val out = HashMap<String, List<CauseLink>>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                out[k] = List(a.length()) {
                    val x = a.getJSONObject(it)
                    CauseLink(if (x.isNull("pest")) null else x.getString("pest"), x.optString("name_en"), x.optString("name_hi"), x.optString("rel"))
                }
            }
            out
        } catch (e: Exception) { emptyMap() }
    }

    fun of(classId: String): List<CauseLink> = map[classId] ?: emptyList()
}
