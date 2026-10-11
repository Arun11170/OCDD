package com.cropadvisor

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * One-time, farmer-started download of the Llama build that suits this phone. Resumes a partial file, checks the
 * SHA-256 from the server's manifest.json, and keeps the file only if it matches. Nothing else in the app uses the network.
 */
object ModelDownloader {
    /** Where the app looks first. Upload manifest.json, tokenizer.model and the .pte file(s) as assets of this GitHub release
     *  (the repository must be public), or change this to your own https address. */
    const val DEFAULT_SERVER = "https://github.com/Arun11170/OCDD/releases/download/model-v1"

    data class Item(val name: String, val sha256: String, val size: Long)
    data class Manifest(val full: Item?, val lite: Item?, val tokenizer: Item?)

    fun clean(base: String): String {
        var b = base.trim()
        if (b.isEmpty()) throw IOException("empty address")
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "http://$b"
        return b.trimEnd('/')
    }

    fun fetchManifest(base: String): Manifest {
        val c = URL(clean(base) + "/manifest.json").openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try {
            if (c.responseCode != 200) throw IOException("server answered ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            fun item(k: String): Item? = j.optJSONObject(k)?.let { Item(it.getString("file"), it.optString("sha256").lowercase(), it.optLong("size", 0)) }
            return Manifest(item("full"), item("lite"), item("tokenizer"))
        } finally { c.disconnect() }
    }

    fun targetDir(ctx: Context, tier: LlmTier): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, tier.dir).also { it.mkdirs() }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins -> val buf = ByteArray(1 shl 20); while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun verified(f: File, item: Item): Boolean {
        if (item.size > 0 && f.length() != item.size) return false
        return item.sha256.isEmpty() || sha256(f) == item.sha256
    }

    /** Returns true when the verified file is in place, false if cancelled. Throws on any failure. */
    fun fetch(base: String, item: Item, dir: File, onProgress: (Long) -> Unit, cancelled: () -> Boolean): Boolean {
        val out = File(dir, item.name)
        if (out.exists() && verified(out, item)) return true
        val part = File(dir, item.name + ".part")
        if (part.exists() && item.size > 0 && part.length() == item.size && verified(part, item)) {
            out.delete(); if (part.renameTo(out)) return true
        }
        if (part.exists() && item.size > 0 && part.length() > item.size) part.delete()
        var have = if (part.exists()) part.length() else 0L
        val c = URL(clean(base) + "/" + item.name).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 20000
        if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
        try {
            val code = c.responseCode
            if (code != 200 && code != 206) throw IOException("server answered $code")
            if (code == 200) have = 0                       // server ignored the resume request: start again
            RandomAccessFile(part, "rw").use { raf ->
                if (have == 0L) raf.setLength(0)
                raf.seek(have)
                c.inputStream.use { ins ->
                    val buf = ByteArray(256 * 1024)
                    var total = have
                    while (true) {
                        if (cancelled()) return false
                        val n = ins.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        total += n
                        onProgress(total)
                    }
                }
            }
        } finally { c.disconnect() }
        if (!verified(part, item)) { part.delete(); throw IOException("the file did not match its checksum") }
        out.delete()
        if (!part.renameTo(out)) throw IOException("could not save the file")
        return true
    }
}
