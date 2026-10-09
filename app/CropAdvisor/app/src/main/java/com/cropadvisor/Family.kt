package com.cropadvisor

/** Looks like several kinds of one family of pest (all weevils, all borers ...): better to say so than to guess one. */
object Family {
    private val keys = listOf("weevil", "borer", "cutworm", "hopper", "thrips", "midge", "mite", "aphid", "worm", "beetle", "fly", "cricket", "grub")

    fun of(classId: String): String? {
        if (!classId.startsWith("pest__")) return null
        val id = classId.lowercase()
        return keys.firstOrNull { id.contains(it) }
    }

    /** The family key when the top possibilities are different kinds of the same pest family, else null. */
    fun shared(top: List<Guess>): String? {
        val near = top.filter { it.prob >= 0.05f }
        if (near.size < 2) return null
        val fams = near.map { of(it.classId) }
        val f = fams.firstOrNull() ?: return null
        return if (fams.all { it == f } && near.map { it.classId }.distinct().size >= 2) f else null
    }
}
