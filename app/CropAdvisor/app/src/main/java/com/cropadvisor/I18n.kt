package com.cropadvisor

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Machine-made translations for the Indian languages other than English (and the Hindi advice text).
 * assets/i18n/<code>.json maps sha1(english.trim())[:12] -> translated text; "_t" lists UI templates with {0},{1} slots.
 * Anything missing falls back to the English text, never to a blank.
 */
object I18n {
    private var app: Context? = null
    private val maps = HashMap<String, Map<String, String>>()
    private val tpls = HashMap<String, List<Tpl>>()

    private class Tpl(val rx: Regex, val order: List<Int>, val out: String)

    fun init(c: Context) { app = c.applicationContext }

    internal fun key(en: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(en.trim().toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (i in 0 until 6) sb.append("%02x".format(d[i]))
        return sb.toString()
    }

    @Synchronized private fun load(l: Lang) {
        if (maps.containsKey(l.code)) return
        val m = HashMap<String, String>()
        val tl = ArrayList<Tpl>()
        try {
            val c = app ?: return
            val o = JSONObject(c.assets.open("i18n/${l.code}.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
            for (k in o.keys()) {
                if (k == "_t") {
                    val a = o.getJSONArray("_t")
                    for (i in 0 until a.length()) {
                        val pr = a.getJSONArray(i)
                        val en = pr.getString(0)
                        val order = ArrayList<Int>()
                        val sb = StringBuilder("^")
                        var last = 0
                        for (mt in Regex("\\{(\\d+)\\}").findAll(en)) {
                            sb.append(Regex.escape(en.substring(last, mt.range.first)))
                            sb.append("(.+?)")
                            order.add(mt.groupValues[1].toInt())
                            last = mt.range.last + 1
                        }
                        sb.append(Regex.escape(en.substring(last))).append("$")
                        tl.add(Tpl(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), order, pr.getString(1)))
                    }
                } else m[k] = o.getString(k)
            }
        } catch (e: Exception) { /* language file missing: stay English */ }
        maps[l.code] = m
        tpls[l.code] = tl
    }

    fun has(l: Lang): Boolean { if (l == Lang.EN) return true; load(l); return maps[l.code].orEmpty().isNotEmpty() }

    /** Translation of a fixed English text, or the English itself. */
    fun t(l: Lang, en: String): String {
        if (l == Lang.EN || en.isBlank()) return en
        load(l)
        return maps[l.code]?.get(key(en)) ?: en
    }

    /** Screen text: exact match, otherwise a template match (text with numbers or names filled in). Null if unknown. */
    fun ui(l: Lang, en: String): String? {
        load(l)
        maps[l.code]?.get(key(en))?.let { return it }
        for (tp in tpls[l.code].orEmpty()) {
            val m = tp.rx.matchEntire(en) ?: continue
            var out = tp.out
            tp.order.forEachIndexed { gi, n -> out = out.replace("{$n}", t(l, m.groupValues[gi + 1])) }
            return out
        }
        return null
    }
}
