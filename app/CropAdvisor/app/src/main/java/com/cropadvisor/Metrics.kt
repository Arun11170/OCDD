package com.cropadvisor

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.io.File

/**
 * Writes one line per measured step to  <app files>/metrics.csv  (Android/data/com.cropadvisor/files/metrics.csv).
 * This is the raw data for the report: latency, memory, battery level and temperature.
 * Columns: time_ms, step, lang, ms, tokens, tokens_per_s, mem_mb, battery_pct, battery_temp_c, charging
 */
object Metrics {
    private fun battery(ctx: Context): Triple<Int, Float, Boolean> {
        val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        return Triple(if (level >= 0) level * 100 / scale else -1, temp, charging)
    }

    @Synchronized
    fun log(ctx: Context, step: String, lang: String, ms: Long, tokens: Int = 0, memMb: Long = 0) {
        try {
            val (pct, temp, chg) = battery(ctx)
            val tps = if (ms > 0 && tokens > 0) tokens * 1000f / ms else 0f
            val f = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "metrics.csv")
            if (!f.exists()) f.writeText("time_ms,step,lang,ms,tokens,tokens_per_s,mem_mb,battery_pct,battery_temp_c,charging\n")
            f.appendText("${System.currentTimeMillis()},$step,$lang,$ms,$tokens,${"%.2f".format(tps)},$memMb,$pct,$temp,$chg\n")
        } catch (_: Throwable) {}
    }

    /** This process's memory (PSS) in MB. */
    fun myPssMb(): Long = android.os.Debug.getPss() / 1024
}
