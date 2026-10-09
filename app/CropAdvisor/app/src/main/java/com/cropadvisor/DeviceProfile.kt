package com.cropadvisor

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/** Which Llama build this phone should use right now. Both are the same Llama 3.2 1B; LITE is the memory-trimmed build. */
enum class LlmTier(val dir: String) { FULL("llama"), LITE("llama-lite") }

object DeviceProfile {
    data class Info(val totalMb: Long, val availMb: Long, val freeStorageMb: Long, val lowMemory: Boolean)

    /** Free memory needed while answering. FULL measured on a real phone (PSS 1,935 MB + margin). LITE is a plan figure: measure it, then fix it. */
    const val FULL_NEED_MB = 2300L
    const val LITE_NEED_MB = 1300L

    sealed interface Pick {
        data class Use(val tier: LlmTier, val files: LlmFiles.Found) : Pick
        data object NoCpu : Pick            // phone cannot run Llama at all
        data object NoFiles : Pick          // right build for this phone is not on it yet
        data class NeedBuild(val tier: LlmTier) : Pick
        data object TooLittleNow : Pick     // enough in total, but too much is in use right now (background apps)
        data object UseHub : Pick           // phone too small: ask the village hub
    }

    fun read(ctx: Context): Info {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val free = try { StatFs(ctx.filesDir.absolutePath).availableBytes / (1024 * 1024) } catch (e: Exception) { 0L }
        return Info(mi.totalMem / (1024 * 1024), mi.availMem / (1024 * 1024), free, mi.lowMemory)
    }

    /** Best build that fits the free memory right now AND is installed. Re-run before each question: background apps change. */
    fun pick(ctx: Context, i: Info = read(ctx), atomics: Boolean = CpuCheck.hasAtomics): Pick {
        if (!atomics) return Pick.NoCpu
        val full = LlmFiles.find(ctx, LlmTier.FULL.dir)
        val lite = LlmFiles.find(ctx, LlmTier.LITE.dir)
        if (i.totalMb < 2400) return Pick.UseHub                      // about a 3 GB phone is the smallest that can host LITE
        if (!i.lowMemory && i.availMb >= FULL_NEED_MB && full != null) return Pick.Use(LlmTier.FULL, full)
        if (!i.lowMemory && i.availMb >= LITE_NEED_MB && lite != null) return Pick.Use(LlmTier.LITE, lite)
        if (full == null && lite == null) return Pick.NoFiles
        if (i.availMb >= LITE_NEED_MB && lite == null && full != null) return Pick.NeedBuild(LlmTier.LITE)
        return Pick.TooLittleNow
    }

    /** Which build to download or copy onto this phone: by total memory, then by free storage. */
    fun wanted(i: Info): LlmTier? = when {
        i.totalMb >= 5000 -> LlmTier.FULL
        i.totalMb >= 2400 -> LlmTier.LITE
        else -> null
    }

    fun storageOk(i: Info, fileMb: Long) = i.freeStorageMb >= fileMb * 13 / 10 + 200
}
