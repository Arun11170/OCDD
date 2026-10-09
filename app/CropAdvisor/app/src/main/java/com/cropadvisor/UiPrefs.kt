package com.cropadvisor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Small on-phone display settings that the whole screen reads (kept in memory; saved by the Home screen switch). */
object UiPrefs {
    var bigText by mutableStateOf(false)
    var simple by mutableStateOf(false)
}
