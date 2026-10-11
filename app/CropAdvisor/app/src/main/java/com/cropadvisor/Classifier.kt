package com.cropadvisor

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.io.File
import kotlin.math.exp

data class Guess(val classId: String, val prob: Float)

data class Prediction(
    val top: List<Guess>,          // best first, up to 3
    val sure: Boolean,             // passes the confidence gate
    val millis: Long,
    val ranked: List<Guess> = emptyList(),   // best first, up to 6 (used by "Check to be sure")
    val cropMass: Float = 1f,                // how much of the photo model's belief lies inside the crop the farmer chose
    val usedSpecialist: Boolean = false,
    val healthyMass: Float = 0f,             // total belief that the leaf is healthy (any crop)
)

/**
 * Disease model: MobileNetV3, exported to ONNX and run with ONNX Runtime (works on every 64-bit ARM phone).
 * Input  : float32 RGB 0..1, shape [1,3,224,224]  (normalisation is inside the model)
 * Output : temperature-scaled logits for the classes in classes.json
 */
class Classifier(private val ctx: Context) {
    val classes: List<String> = readClasses(ctx)
    init { loadGates(ctx) }
    private val env = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null
    private var inputName: String = "image"

    val crops: List<String> = classes.map { it.substringBefore("__") }.distinct().filter { it != "pest" }.sorted()

    @Synchronized
    private fun main(): OrtSession {
        session?.let { return it }
        val f = File(ctx.filesDir, MODEL_FILE)
        val assetLen = ctx.assets.openFd(MODEL_FILE).use { it.length }
        if (!f.exists() || f.length() != assetLen) {
            ctx.assets.open(MODEL_FILE).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        val s = env.createSession(f.absolutePath, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
        inputName = s.inputNames.first()
        session = s
        return s
    }

    /** Frees the photo models (before Llama loads). They reload by themselves on the next photo. */
    @Synchronized
    fun release() {
        try { session?.close() } catch (_: Throwable) {}
        session = null
        LeafSeg.release(); LesionSeg.release()
    }

    /** One general model for every crop and pest: the photo alone decides. */
    fun predict(photo: Bitmap): Prediction {
        val t0 = System.nanoTime()
        val p = softmax(run(main(), inputName, photo))
        val ranked = p.indices.sortedByDescending { p[it] }.take(6).map { Guess(classes[it], p[it]) }
        val top = ranked.take(3)
        val healthy = classes.indices.filter { "healthy" in classes[it] }.sumOf { p[it].toDouble() }.toFloat()
        val sure = answers(top[0].classId, top[0].prob, healthy)
        return Prediction(top, sure, (System.nanoTime() - t0) / 1_000_000, ranked, 1f, false, healthy)
    }

    @Suppress("UNCHECKED_CAST")
    private fun run(s: OrtSession, input: String, photo: Bitmap): FloatArray =
        toTensor(photo).use { x -> s.run(mapOf(input to x)).use { r -> (r[0].value as Array<FloatArray>)[0] } }

    /** Same pre-processing as training/eval: shorter side to 256, centre crop 224. */
    private fun toTensor(src: Bitmap): OnnxTensor {
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
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(f), longArrayOf(1, 3, 224, 224))
    }

    private fun softmax(z: FloatArray): FloatArray {
        val m = z.max()
        val e = FloatArray(z.size) { exp((z[it] - m).toDouble()).toFloat() }
        val s = e.sum()
        return FloatArray(e.size) { e[it] / s }
    }

    private fun readClasses(ctx: Context): List<String> = readList(ctx.assets.open("classes.json").bufferedReader().use { it.readText() })

    private fun readList(raw: String): List<String> {
        val text = raw.trim()
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
        const val MODEL_FILE = "vision.onnx"
        /** Below this the app says "not sure". From the test report: >=0.8 answers 94% of photos at 97.3% accuracy. */
        const val GATE = 0.80f
        /** Per-crop gates raised above GATE where the 1500-photo accuracy test (accuracy_lab.py) showed too many wrong answers at 0.80: chilli and pests. Never lower than GATE. */
        private var gates: Map<String, Float> = emptyMap()
        /** A pest answer is refused when the model also believes the leaf may be healthy. In the 1500-photo test this never fired on a correct pest answer (95th percentile 0.011), so it costs nothing there. */
        const val PEST_HEALTHY_MAX = 0.03f
        fun answers(classId: String, prob: Float, healthyMass: Float): Boolean =
            prob >= gateFor(classId) && !(classId.startsWith("pest__") && healthyMass >= PEST_HEALTHY_MAX)
        fun gateFor(classId: String): Float = maxOf(GATE, gates[classId.substringBefore("__")] ?: GATE)
        private fun loadGates(ctx: Context) {
            try {
                val o = JSONObject(ctx.assets.open("gates.json").bufferedReader().use { it.readText() })
                gates = o.keys().asSequence().associateWith { o.getDouble(it).toFloat().coerceIn(GATE, 0.95f) }
            } catch (e: Exception) { gates = emptyMap() }
        }
        /** With a crop chosen, at least this share of the general model's belief must lie in that crop (or pests). */
        const val MIN_CROP_MASS = 0.5f
    }
}
