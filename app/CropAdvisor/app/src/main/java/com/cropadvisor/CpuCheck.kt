package com.cropadvisor

import java.io.File

/**
 * The prebuilt ExecuTorch library (used only for Llama / Smart mode) contains ARMv8.1 "atomics" instructions.
 * Older chips (Cortex-A53 / A73 only) do not have them and would crash with SIGILL, so Smart is switched off there
 * and the library is never loaded. The disease model uses ONNX Runtime, which works on every 64-bit ARM chip.
 */
object CpuCheck {
    val hasAtomics: Boolean by lazy {
        try {
            File("/proc/cpuinfo").useLines { lines ->
                lines.any { l ->
                    l.startsWith("Features") && l.substringAfter(":").trim().split(Regex("\\s+")).contains("atomics")
                }
            }
        } catch (e: Throwable) { false }      // cannot tell -> stay safe, no Smart
    }
}
