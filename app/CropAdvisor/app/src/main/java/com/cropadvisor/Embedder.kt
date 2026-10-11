package com.cropadvisor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.LongBuffer
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * "Understands what you mean" for voice and typed commands, in all 13 languages.
 *
 * It uses multilingual-e5-small (MIT licence, about 118 MB once compressed): a small model that turns a sentence into 384 numbers
 * so that sentences with the same meaning get nearby numbers, whatever the language. The app keeps the numbers of example
 * sentences for every command; what the farmer says is compared with them and the closest command wins.
 * It is only a back-up for the plain word lists in [VoiceNav]: if the files are not downloaded, or nothing is close enough,
 * the app falls back to the action list. Runs fully offline once downloaded.
 */
object Embedder {
    const val MODEL_FILE = "embed.onnx"
    const val VOCAB_FILE = "embed_vocab.tsv"
    private const val CACHE_FILE = "exemplars.bin"
    private const val CACHE_VERSION = 1
    private const val MAX_TOKENS = 32

    /** Minimum cosine to the closest example, and minimum share after the softmax (temperature 0.01, as the model's authors advise). */
    const val MIN_COSINE = 0.80f
    const val MIN_SHARE = 0.50f

    fun dir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "embed").also { it.mkdirs() }
    fun installed(ctx: Context): Boolean = File(dir(ctx), MODEL_FILE).length() > 0 && File(dir(ctx), VOCAB_FILE).length() > 0

    // ── state (all guarded by this object's lock) ──
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null
    private var pieces: HashMap<String, Int>? = null
    private var scores: FloatArray = FloatArray(0)
    private var maxLen = 1
    private var exemplarVecs: Array<FloatArray> = emptyArray()
    private var exemplarCmd: Array<Cmd?> = emptyArray()   // null = "not a command" example
    @Volatile var ready = false
        private set
    @Volatile var loading = false
        private set
    @Volatile var lastError: String? = null
        private set

    /** Loads the model and the example numbers on a background thread; harmless to call again. */
    fun warmUp(ctx: Context) {
        if (ready || loading || !installed(ctx)) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            try { load(app); lastError = null } catch (t: Throwable) { lastError = t.message ?: t.toString(); Log.w("Embedder", "load failed", t) }
            loading = false
        }.start()
    }

    /** Frees the memory (about 150 MB) — before Llama loads, for example. It loads again by itself on the next command. */
    @Synchronized
    fun release() {
        try { session?.close() } catch (_: Throwable) {}
        session = null; pieces = null; scores = FloatArray(0); exemplarVecs = emptyArray(); exemplarCmd = emptyArray(); ready = false
    }

    @Synchronized
    private fun load(ctx: Context) {
        val d = dir(ctx)
        // vocabulary: one "piece<TAB>score" per line, the line number is the token id
        val map = HashMap<String, Int>(300_000)
        val sc = ArrayList<Float>(260_000)
        var longest = 1
        File(d, VOCAB_FILE).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val t = line.lastIndexOf('\t')
                val p = if (t < 0) line else line.substring(0, t)
                val s = if (t < 0) 0f else line.substring(t + 1).toFloatOrNull() ?: 0f
                map.putIfAbsent(p, sc.size)
                sc.add(s)
                if (p.length > longest) longest = p.length
            }
        }
        pieces = map; scores = sc.toFloatArray(); maxLen = minOf(longest, 40)
        session = env.createSession(File(d, MODEL_FILE).absolutePath, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
        buildExemplars(d)
        ready = true
    }

    // ───────────────────────── tokenizer (SentencePiece "unigram", as XLM-R uses) ─────────────────────────
    private fun encode(text: String): LongArray {
        val map = pieces ?: return longArrayOf(0, 2)
        val norm = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC).replace(Regex("\\s+"), " ")
        if (norm.isEmpty()) return longArrayOf(0, 2)
        val s = "▁" + norm.replace(' ', '▁')
        val n = s.length
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(n + 1)
        val id = IntArray(n + 1)
        best[0] = 0f
        for (i in 0 until n) {
            if (best[i] == Float.NEGATIVE_INFINITY) continue
            var found = false
            val hi = minOf(n, i + maxLen)
            for (j in i + 1..hi) {
                val k = map[s.substring(i, j)] ?: continue
                val sc = best[i] + scores[k]
                if (sc > best[j]) { best[j] = sc; from[j] = i; id[j] = k }
                found = true
            }
            if (!found || best[i + 1] == Float.NEGATIVE_INFINITY) {      // character not in the vocabulary: <unk>
                val sc = best[i] - 20f
                if (sc > best[i + 1]) { best[i + 1] = sc; from[i + 1] = i; id[i + 1] = 3 }
            }
        }
        val out = ArrayList<Long>()
        var p = n
        while (p > 0) { out.add(id[p].toLong()); p = from[p] }
        out.reverse()
        val body = if (out.size > MAX_TOKENS - 2) out.subList(0, MAX_TOKENS - 2) else out
        return LongArray(body.size + 2).also { a -> a[0] = 0; body.forEachIndexed { i, v -> a[i + 1] = v }; a[a.size - 1] = 2 }
    }

    // ───────────────────────── the model ─────────────────────────
    private fun embedRaw(text: String): FloatArray {
        val sess = session ?: throw IllegalStateException("embedder not loaded")
        val ids = encode("query: $text")
        val t = ids.size
        val inputs = HashMap<String, OnnxTensor>()
        try {
            if ("input_ids" in sess.inputNames) inputs["input_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), longArrayOf(1, t.toLong()))
            if ("attention_mask" in sess.inputNames) inputs["attention_mask"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(t) { 1L }), longArrayOf(1, t.toLong()))
            if ("token_type_ids" in sess.inputNames) inputs["token_type_ids"] = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(t)), longArrayOf(1, t.toLong()))
            sess.run(inputs).use { res ->
                @Suppress("UNCHECKED_CAST")
                val out = (res[0].value as Array<Array<FloatArray>>)[0]          // [tokens][384]
                val dim = out[0].size
                val v = FloatArray(dim)
                for (row in out) for (k in 0 until dim) v[k] += row[k]
                var norm = 0f
                for (k in 0 until dim) { v[k] /= out.size; norm += v[k] * v[k] }
                val inv = 1f / sqrt(norm.coerceAtLeast(1e-12f))
                for (k in 0 until dim) v[k] *= inv
                return v
            }
        } finally { inputs.values.forEach { it.close() } }
    }

    /** Sentences that are NOT commands, so chatter such as "thank you" does not open something by accident. */
    private val chatter = listOf("hello", "thank you", "good morning", "I do not know", "what is your name", "ok", "no", "yes please", "never mind", "nothing")

    /** Example sentences per command (English; the model links them to the same meaning in the other languages) plus every table word of every language. */
    private val phrases: Map<Cmd, List<String>> = mapOf(
        Cmd.CAMERA to listOf("take a photo of the leaf", "open the camera", "I want to take a picture", "click a photo of my crop", "photograph the plant"),
        Cmd.GALLERY to listOf("choose a photo from the gallery", "pick an existing picture", "select a saved photo", "open my photos"),
        Cmd.LISTEN to listOf("read the answer aloud", "tell me the result by voice", "speak it to me", "play the advice"),
        Cmd.NEW_CHECK to listOf("check another leaf", "start a new check", "do it again", "new photo please"),
        Cmd.BACK to listOf("go back", "go to the home screen", "close this", "return to the start"),
        Cmd.HELP to listOf("I need help", "how do I use this app", "show me the help", "call the helpline for farmers"),
        Cmd.CALL to listOf("call an agriculture expert", "phone the kisan call centre", "I want to talk to an expert", "ring the expert"),
        Cmd.DIARY to listOf("open the season diary", "write a note about my crop", "show my farm notes", "record what I did today"),
        Cmd.WEATHER to listOf("what is the weather going to be", "will it rain", "weather warning for my crop", "forecast for my field"),
        Cmd.STAGES to listOf("which growth stage is my crop in", "show crop stages", "what should I do at this stage", "crop calendar"),
        Cmd.SETTINGS to listOf("open settings", "change the app settings", "show preferences"),
        Cmd.LIBRARY to listOf("show the list of crop diseases", "open the disease library", "look up a plant disease in the book"),
        Cmd.PLOT to listOf("show my field report", "my plots and fields", "report for my farm plot"),
        Cmd.YELLOW to listOf("my leaves are turning yellow", "yellow leaves nutrient problem", "why is the plant yellow"),
    )

    private fun allExemplars(): List<Pair<Cmd?, String>> {
        val out = ArrayList<Pair<Cmd?, String>>()
        phrases.forEach { (c, ps) -> ps.forEach { out.add(c to it) } }
        VoiceNav.allWords().forEach { (c, w) -> out.add(c to w) }
        chatter.forEach { out.add(null to it) }
        return out
    }

    private fun buildExemplars(d: File) {
        val list = allExemplars()
        val cache = File(d, CACHE_FILE)
        val modelStamp = File(d, MODEL_FILE).length()
        try {
            if (cache.exists()) DataInputStream(cache.inputStream().buffered()).use { ins ->
                if (ins.readInt() == CACHE_VERSION && ins.readLong() == modelStamp && ins.readInt() == list.size) {
                    val dim = ins.readInt()
                    val vecs = Array(list.size) { FloatArray(dim) { ins.readFloat() } }
                    exemplarVecs = vecs; exemplarCmd = Array(list.size) { list[it].first }
                    return
                }
            }
        } catch (_: Throwable) { cache.delete() }
        val vecs = Array(list.size) { embedRaw(list[it].second) }
        exemplarVecs = vecs; exemplarCmd = Array(list.size) { list[it].first }
        try {
            DataOutputStream(cache.outputStream().buffered()).use { o ->
                o.writeInt(CACHE_VERSION); o.writeLong(modelStamp); o.writeInt(list.size); o.writeInt(vecs[0].size)
                vecs.forEach { v -> v.forEach { o.writeFloat(it) } }
            }
        } catch (_: Throwable) { /* the cache is only a speed-up */ }
    }

    // ───────────────────────── asking ─────────────────────────
    data class Verdict(val cmd: Cmd?, val cosine: Float, val share: Float)

    /** Scores `heard` against every command. Returns null when the model is not ready. */
    @Synchronized
    fun score(heard: String): Verdict? {
        if (!ready || heard.isBlank()) return null
        return try {
            val q = embedRaw(heard)
            val cmds = Cmd.entries
            val bestPer = FloatArray(cmds.size) { -1f }
            var bestChatter = -1f
            for (i in exemplarVecs.indices) {
                var dot = 0f
                val v = exemplarVecs[i]
                for (k in v.indices) dot += v[k] * q[k]
                val c = exemplarCmd[i]
                if (c == null) { if (dot > bestChatter) bestChatter = dot } else if (dot > bestPer[c.ordinal]) bestPer[c.ordinal] = dot
            }
            // softmax over the commands and the "chatter" class
            val all = FloatArray(cmds.size + 1) { if (it < cmds.size) bestPer[it] else bestChatter }
            val mx = all.max()
            val ex = FloatArray(all.size) { exp(((all[it] - mx) / 0.01f).toDouble()).toFloat() }
            val sum = ex.sum()
            val top = all.indices.maxByOrNull { all[it] } ?: return null
            if (top == cmds.size) Verdict(null, all[top], ex[top] / sum) else Verdict(cmds[top], all[top], ex[top] / sum)
        } catch (t: Throwable) { Log.w("Embedder", "score failed", t); null }
    }

    /** The command that the best of several recogniser guesses clearly means, or null. */
    fun matchCommand(guesses: List<String>): Cmd? {
        if (!ready) return null          // never wait for a load on the main thread
        var best: Verdict? = null
        for (g in guesses.take(3)) {
            val v = score(g) ?: continue
            if (v.cmd != null && (best == null || v.share > best.share)) best = v
        }
        val b = best ?: return null
        return if (b.cosine >= MIN_COSINE && b.share >= MIN_SHARE) b.cmd else null
    }
}
