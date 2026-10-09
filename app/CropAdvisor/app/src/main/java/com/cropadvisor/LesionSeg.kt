package com.cropadvisor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.nio.FloatBuffer

/**
 * Finds the diseased or damaged pixels (DeepLabV3 + MobileNetV3 trained on PlantSeg, assets/lesionseg.onnx, runs with ONNX Runtime).
 * If the model file is not in the app, [mask] returns null and Severity uses the colour rule inside the leaf instead.
 */
object LesionSeg {
    private const val FILE = "lesionseg.onnx"
    private const val S = 512
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var failed = false

    @Synchronized
    fun release() { try { session?.close() } catch (_: Throwable) {}; session = null }

    @Synchronized
    private fun ensure(ctx: Context): OrtSession? {
        session?.let { return it }
        if (failed) return null
        return try {
            val names = ctx.assets.list("") ?: emptyArray()
            if (FILE !in names) { failed = true; return null }
            val f = File(ctx.filesDir, FILE)
            val len = ctx.assets.openFd(FILE).use { it.length }
            if (!f.exists() || f.length() != len) ctx.assets.open(FILE).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            val e = OrtEnvironment.getEnvironment()
            env = e
            val s = e.createSession(f.absolutePath, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
            session = s
            s
        } catch (t: Throwable) { failed = true; null }
    }

    /** One byte per pixel of [bmp] (row by row): 1 = lesion. Null if the model is missing or fails. */
    fun mask(ctx: Context, bmp: Bitmap): ByteArray? {
        val sess = ensure(ctx) ?: return null
        val e = env ?: return null
        return try {
            val w = bmp.width
            val h = bmp.height
            val ks = S.toFloat() / maxOf(w, h)
            val nw = maxOf(1, (w * ks).toInt())
            val nh = maxOf(1, (h * ks).toInt())
            val sm = Bitmap.createScaledBitmap(bmp, nw, nh, true)
            val px = IntArray(nw * nh)
            sm.getPixels(px, 0, nw, 0, 0, nw, nh)
            if (sm !== bmp) sm.recycle()
            val ox = (S - nw) / 2
            val oy = (S - nh) / 2
            val plane = S * S
            val f = FloatArray(3 * plane)
            java.util.Arrays.fill(f, 0, plane, 124 / 255f)              // grey padding, same as in training
            java.util.Arrays.fill(f, plane, 2 * plane, 116 / 255f)
            java.util.Arrays.fill(f, 2 * plane, 3 * plane, 104 / 255f)
            for (y in 0 until nh) for (x in 0 until nw) {
                val c = px[y * nw + x]
                val i = (oy + y) * S + ox + x
                f[i] = ((c shr 16) and 0xFF) / 255f
                f[plane + i] = ((c shr 8) and 0xFF) / 255f
                f[2 * plane + i] = (c and 0xFF) / 255f
            }
            val tensor = OnnxTensor.createTensor(e, FloatBuffer.wrap(f), longArrayOf(1, 3, S.toLong(), S.toLong()))
            @Suppress("UNCHECKED_CAST")
            val prob = tensor.use { t ->
                sess.run(mapOf(sess.inputNames.first() to t)).use { r -> (r[0].value as Array<Array<Array<FloatArray>>>)[0][0] }
            }
            val out = ByteArray(w * h)
            for (y in 0 until h) {
                val sy = oy + ((y + 0.5f) * ks).toInt().coerceIn(0, nh - 1)
                for (x in 0 until w) {
                    val sx = ox + ((x + 0.5f) * ks).toInt().coerceIn(0, nw - 1)
                    if (prob[sy][sx] > 0.5f) out[y * w + x] = 1
                }
            }
            out
        } catch (t: Throwable) { null }
    }
}
