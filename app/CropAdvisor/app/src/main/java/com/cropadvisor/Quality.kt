package com.cropadvisor

import android.graphics.Bitmap

/** Quick check of the photo itself (blur, light, size) so the farmer is warned before trusting a weak photo. */
object Quality {
    enum class Issue { BLUR, DARK, BRIGHT, SMALL }

    fun check(b: Bitmap): List<Issue> {
        val out = mutableListOf<Issue>()
        if (minOf(b.width, b.height) < 320) out += Issue.SMALL
        try {
            val sc = 256f / maxOf(b.width, b.height)
            val w = maxOf(8, (b.width * sc).toInt()); val h = maxOf(8, (b.height * sc).toInt())
            val t = Bitmap.createScaledBitmap(b, w, h, true)
            val px = IntArray(w * h); t.getPixels(px, 0, w, 0, 0, w, h)
            if (t !== b) t.recycle()
            val g = IntArray(w * h) { val c = px[it]; (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10 }
            val mean = g.average()
            if (mean < 55) out += Issue.DARK
            if (mean > 225) out += Issue.BRIGHT
            var s = 0.0; var s2 = 0.0; var n = 0
            for (y in 1 until h - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                val l = (4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]).toDouble()
                s += l; s2 += l * l; n++
            }
            val variance = s2 / n - (s / n) * (s / n)
            if (variance < 35.0) out += Issue.BLUR
        } catch (e: Throwable) { /* a warning only; never block a check */ }
        return out
    }
}
