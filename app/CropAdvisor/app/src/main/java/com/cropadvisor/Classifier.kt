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

    /** Small per-crop model (assets/crops/<crop>.onnx + <crop>.json), loaded only while that crop is chosen. */
    private class Spec(val crop: String, val classes: List<String>, val session: OrtSession, val input: String)
    private var spec: Spec? = null
    private val specialistCrops: Set<String> = try {
        ctx.assets.list("crops")?.filter { it.endsWith(".onnx") }?.map { it.removeSuffix(".onnx") }?.toSet() ?: emptySet()
    } catch (e: Exception) { emptySet() }

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
        try { spec?.session?.close() } catch (_: Throwable) {}
        spec = null
        LeafSeg.release(); LesionSeg.release()
    }

    @Synchronized
    private fun specialist(crop: String): Spec {
        spec?.let { if (it.crop == crop) return it }
        try { spec?.session?.close() } catch (_: Throwable) {}     // only one crop model in memory at a time
        spec = null
        val bytes = ctx.assets.open("crops/$crop.onnx").use { it.readBytes() }
        val cls = readList(ctx.assets.open("crops/$crop.json").bufferedReader().use { it.readText() })
        val s = env.createSession(bytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
        return Spec(crop, cls, s, s.inputNames.first()).also { spec = it }
    }

    /** [crop] = the crop the farmer chose (null = any). With a crop, only that crop's problems and the pests can win. */
    fun predict(photo: Bitmap, crop: String? = null): Prediction {
        val t0 = System.nanoTime()
        if (crop != null && crop in specialistCrops) {
            try {
                val sp = specialist(crop)
                val p = softmax(run(sp.session, sp.input, photo))
                val ranked = p.indices.sortedByDescending { p[it] }.take(6).map { Guess(sp.classes[it], p[it]) }
                val top = ranked.take(3)
                return Prediction(top, top[0].prob >= gateFor(top[0].classId), (System.nanoTime() - t0) / 1_000_000, ranked, 1f, true)
            } catch (e: Throwable) { /* fall back to the general model */ }
        }
        val p = softmax(run(main(), inputName, photo))
        var mass = 1f
        val q = if (crop != null) {
            val allowed = BooleanArray(classes.size) { classes[it].substringBefore("__").let { c -> c == crop || c == "pest" } }
            mass = p.indices.filter { allowed[it] }.sumOf { p[it].toDouble() }.toFloat()
            if (mass > 1e-6f) FloatArray(p.size) { if (allowed[it]) p[it] / mass else 0f } else p
        } else p
        val ranked = q.indices.sortedByDescending { q[it] }.take(6).map { Guess(classes[it], q[it]) }
        val top = ranked.take(3)
        // if most of the model's belief lies OUTSIDE the chosen crop, the photo probably is not that crop: do not claim "sure"
        val sure = top[0].prob >= gateFor(top[0].classId) && (crop == null || mass >= MIN_CROP_MASS)
        return Prediction(top, sure, (System.nanoTime() - t0) / 1_000_000, ranked, mass, false)
    }

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
