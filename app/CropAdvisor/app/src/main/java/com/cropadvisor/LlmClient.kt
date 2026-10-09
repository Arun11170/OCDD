package com.cropadvisor

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.io.File

data class LlmAnswer(val text: String, val tokens: Int, val ms: Long) {
    val tokensPerSecond: Float get() = if (ms <= 0) 0f else tokens * 1000f / ms
}

/** Is there enough free RAM to load Llama right now? */
data class Gate(val availMb: Long, val totalMb: Long, val enough: Boolean)

object MemoryGate {
    /**
     * Measured on a real phone: Llama 3.2 1B (QLORA int4) = about 1.9 GB (PSS 1,935 MB).
     * 2.4 GB = that plus a 0.5 GB safety margin. Recalibrate after the 4 GB phone test.
     */
    const val NEED_MB = 1800L

    fun check(ctx: Context): Gate {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val avail = mi.availMem / (1024 * 1024)
        return Gate(avail, mi.totalMem / (1024 * 1024), avail >= NEED_MB && !mi.lowMemory)
    }
}

object LlmFiles {
    data class Found(val model: String, val tokenizer: String)

    /** Looks for the .pte and tokenizer.model in the app's own folders, then in /data/local/tmp/llama. */
    fun find(ctx: Context, dirName: String = "llama"): Found? {
        val dirs = listOfNotNull(
            ctx.getExternalFilesDir(null)?.let { File(it, dirName) },
            File(ctx.filesDir, dirName),
            File("/data/local/tmp/$dirName"),
        )
        for (d in dirs) {
            val pte = d.listFiles { f -> f.isFile && f.name.endsWith(".pte") }?.firstOrNull()
            val tok = File(d, "tokenizer.model")
            if (pte != null && tok.exists()) return Found(pte.absolutePath, tok.absolutePath)
        }
        return null
    }
}

/** Talks to LlmService in the other process. One request at a time; replies are matched by id. */
class LlmClient(private val ctx: Context) {
    @Volatile private var svc: Messenger? = null
    @Volatile private var connected = CompletableDeferred<Unit>()
    @Volatile private var started = false
    @Volatile private var pending: CompletableDeferred<Bundle>? = null
    @Volatile private var pendingId = 0
    private var nextId = 0

    private val replies = Messenger(Handler(Looper.getMainLooper()) { m: Message ->
        val p = pending
        if (p != null && m.data.getInt("id") == pendingId) {
            if (m.what == LlmService.R_ERROR) p.completeExceptionally(IllegalStateException(m.data.getString("message")))
            else p.complete(m.data)
        }
        true
    })

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            svc = Messenger(service)
            connected.complete(Unit)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            // the :llm process died (low memory or unload): fail the waiting request and allow a fresh bind
            svc = null
            started = false
            connected = CompletableDeferred()
            pending?.completeExceptionally(IllegalStateException("llm process ended"))
        }
    }

    private fun ensureBound() {
        if (started) return
        started = true
        ctx.bindService(Intent(ctx, LlmService::class.java), conn, Context.BIND_AUTO_CREATE)
    }

    private suspend fun call(what: Int, data: Bundle, timeoutMs: Long): Bundle {
        ensureBound()
        withTimeout(10_000) { connected.await() }
        val target = svc ?: throw IllegalStateException("llm service not connected")
        val p = CompletableDeferred<Bundle>()
        val id = ++nextId
        data.putInt("id", id)
        pendingId = id
        pending = p
        target.send(Message.obtain(null, what).apply { this.data = data; replyTo = replies })
        return withTimeout(timeoutMs) { p.await() }
    }

    /** Returns load time in ms. */
    suspend fun load(files: LlmFiles.Found): Long =
        call(LlmService.MSG_LOAD, Bundle().apply { putString("model", files.model); putString("tokenizer", files.tokenizer) }, 120_000).getLong("ms")

    suspend fun ask(prompt: String): LlmAnswer {
        val b = call(LlmService.MSG_ASK, Bundle().apply { putString("prompt", prompt) }, 120_000)
        return LlmAnswer(b.getString("text").orEmpty(), b.getInt("tokens"), b.getLong("ms"))
    }

    fun stop() {
        try { svc?.send(Message.obtain(null, LlmService.MSG_STOP)) } catch (_: Exception) {}
    }

    /** Frees all of Llama's memory by ending its process. */
    fun unload() {
        pending?.completeExceptionally(IllegalStateException("unloaded"))
        try { svc?.send(Message.obtain(null, LlmService.MSG_UNLOAD)) } catch (_: Exception) {}
        if (started) { try { ctx.unbindService(conn) } catch (_: Exception) {} }
        svc = null
        started = false
        connected = CompletableDeferred()
    }
}
