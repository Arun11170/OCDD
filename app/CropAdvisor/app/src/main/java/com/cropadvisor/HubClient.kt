package com.cropadvisor

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Asks the village hub (a Raspberry Pi on the local Wi-Fi) to run Llama for phones too small to do it themselves.
 *  Only the question prompt goes out; the phone still checks the answer against the verified advice. */
object HubClient {
    fun ask(base: String, prompt: String, maxTokens: Int = 160): String {
        val c = URL(ModelDownloader.clean(base) + "/ask").openConnection() as HttpURLConnection
        c.connectTimeout = 5000; c.readTimeout = 120_000
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        try {
            c.outputStream.use { it.write(JSONObject().put("prompt", prompt).put("max_tokens", maxTokens).toString().toByteArray()) }
            if (c.responseCode != 200) throw IOException("hub answered ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }).getString("text")
        } finally { c.disconnect() }
    }

    fun reachable(base: String): Boolean = try {
        val c = URL(ModelDownloader.clean(base) + "/health").openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 3000
        try { c.responseCode == 200 } finally { c.disconnect() }
    } catch (e: Exception) { false }
}
