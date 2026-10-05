package com.cropadvisor

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.util.concurrent.Executors

/**
 * Runs Llama 3.2 1B in its OWN process (":llm", see the manifest).
 * Why: the model needs about 1.9 GB of RAM. If Android has to kill something, only this process dies; the
 * disease checker keeps working. Unloading kills the process, which returns all the memory at once.
 */
class LlmService : Service() {
    companion object {
        const val MSG_LOAD = 1      // data: model, tokenizer
        const val MSG_ASK = 2       // data: prompt
        const val MSG_STOP = 3
        const val MSG_UNLOAD = 4

        const val R_LOADED = 10     // data: ms
        const val R_ANSWER = 11     // data: text, tokens, ms
        const val R_ERROR = 12      // data: message
    }

    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var module: LlmModule? = null
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { m -> onMessage(m); true })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun reply(to: Messenger?, what: Int, id: Int, fill: Bundle.() -> Unit) {
        try { to?.send(Message.obtain(null, what).apply { data = Bundle().apply(fill).apply { putInt("id", id) } }) } catch (_: Exception) {}
    }

    private fun onMessage(m: Message) {
        val to = m.replyTo
        val id = m.data?.getInt("id") ?: 0
        when (m.what) {
            MSG_LOAD -> {
                val model = m.data.getString("model").orEmpty()
                val tok = m.data.getString("tokenizer").orEmpty()
                worker.execute {
                    try {
                        val t0 = System.nanoTime()
                        if (module == null) {
                            val mod = LlmModule(model, tok, 0.2f)   // low temperature = stays close to the facts
                            val rc: Any? = mod.load()                // Int status in some versions, Unit in others
                            if (rc is Int && rc != 0) throw IllegalStateException("load status $rc")
                            module = mod
                        }
                        reply(to, R_LOADED, id) { putLong("ms", (System.nanoTime() - t0) / 1_000_000) }
                    } catch (t: Throwable) {
                        reply(to, R_ERROR, id) { putString("message", "load failed: ${t.message}") }
                    }
                }
            }
            MSG_ASK -> {
                val prompt = m.data.getString("prompt").orEmpty()
                worker.execute {
                    val mod = module
                    if (mod == null) { reply(to, R_ERROR, id) { putString("message", "model not loaded") }; return@execute }
                    try {
                        val sb = StringBuilder()
                        var tokens = 0
                        var err: String? = null
                        val t0 = System.nanoTime()
                        val seqLen = minOf(2048, prompt.length / 3 + 256)
                        mod.generate(prompt, seqLen, object : LlmCallback {
                            override fun onResult(result: String) { sb.append(result); tokens++ }
                            override fun onStats(stats: String) {}
                            override fun onError(errorCode: Int, message: String) { err = message }
                        }, false)   // false = do not echo the prompt back
                        val text = sb.toString().replace(Regex("<\\|[^|]*\\|>"), "").trim()
                        val e = err
                        if (e != null && text.isEmpty()) reply(to, R_ERROR, id) { putString("message", e) }
                        else reply(to, R_ANSWER, id) {
                            putString("text", text)
                            putInt("tokens", tokens)
                            putLong("ms", (System.nanoTime() - t0) / 1_000_000)
                        }
                    } catch (t: Throwable) {
                        reply(to, R_ERROR, id) { putString("message", "generate failed: ${t.message}") }
                    }
                }
            }
            MSG_STOP -> try { module?.stop() } catch (_: Throwable) {}
            MSG_UNLOAD -> {
                module = null
                Process.killProcess(Process.myPid())   // gives all of the model's memory back immediately
            }
        }
    }
}
