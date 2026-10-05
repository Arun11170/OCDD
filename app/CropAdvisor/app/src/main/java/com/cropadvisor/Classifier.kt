package com.cropadvisor

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import kotlin.math.exp

data class Guess(val classId: String, val prob: Float)

data class Prediction(
    val top: List<Guess>,          // best first, up to 3
    val sure: Boolean,             // passes the confidence gate
    val millis: Long,
)

/**
 * Disease model: MobileNetV3, int8, exported with ExecuTorch (XNNPACK).
 * Input  : float32 RGB 0..1, shape [1,3,224,224]  (normalisation is inside the model)
 * Output : temperature-scaled logits for the classes in classes.json
 */
class Classifier(ctx: Context) {
    val classes: List<String>
    private val module: Module

    init {
        classes = readClasses(ctx)
        val f = File(ctx.filesDir, MODEL_FILE)
        val assetLen = ctx.assets.openFd(MODEL_FILE).use { it.length }
        if (!f.exists() || f.length() != assetLen) {
            ctx.assets.open(MODEL_FILE).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        module = Module.load(f.absolutePath)
    }

    fun predict(photo: Bitmap): Prediction {
        val t0 = System.nanoTime()
        val input = toTensor(photo)
        val out = module.forward(EValue.from(input))[0].toTensor().dataAsFloatArray
        val p = softmax(out)
        val order = p.indices.sortedByDescending { p[it] }.take(3)
        val top = order.map { Guess(classes[it], p[it]) }
        return Prediction(top, top[0].prob >= GATE, (System.nanoTime() - t0) / 1_000_000)
    }

    /** Same pre-processing as training/eval: shorter side to 256, centre crop 224. */
    private fun toTensor(src: Bitmap): Tensor {
        val s = 256f / minOf(src.width, src.height)
        val w = maxOf(224, Math.round(src.width * s))
        val h = maxOf(224, Math.round(src.height * s))
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val x0 = (w - 224) / 2
        val y0 = (h - 224) / 2
        val px = IntArray(224 * 224)
        scaled.getPixels(px, 0, 224, x0, y0, 224, 224)
        if (scaled !== src) scaled.recycle()
        val f = FloatArray(3 * 224 * 224)
        val plane = 224 * 224
        for (i in px.indices) {
            val c = px[i]
            f[i] = ((c shr 16) and 0xFF) / 255f             // R
            f[plane + i] = ((c shr 8) and 0xFF) / 255f      // G
            f[2 * plane + i] = (c and 0xFF) / 255f          // B
        }
        return Tensor.fromBlob(f, longArrayOf(1, 3, 224, 224))
    }

    private fun softmax(z: FloatArray): FloatArray {
        val m = z.max()
        val e = FloatArray(z.size) { exp((z[it] - m).toDouble()).toFloat() }
        val s = e.sum()
        return FloatArray(e.size) { e[it] / s }
    }

    private fun readClasses(ctx: Context): List<String> {
        val text = ctx.assets.open("classes.json").bufferedReader().use { it.readText() }.trim()
        return if (text.startsWith("[")) {
            val a = JSONArray(text); List(a.length()) { a.getString(it) }
        } else {   // {"0": "name", ...} or {"name": 0, ...}
            val o = JSONObject(text)
            val keys = o.keys().asSequence().toList()
            if (keys.all { it.toIntOrNull() != null }) keys.sortedBy { it.toInt() }.map { o.getString(it) }
            else keys.sortedBy { o.getInt(it) }
        }
    }

    companion object {
        const val MODEL_FILE = "vision_int8.pte"
        /** Below this the app says "not sure". From the test report: >=0.8 answers 94% of photos at 97.3% accuracy. */
        const val GATE = 0.80f
    }
}
