package com.cropadvisor

enum class Band { VERY_LIGHT, MILD, MODERATE, SEVERE }

/** One treatment option named by a source. Never carries a dose. */
data class Treatment(
    val kind: String,
    val activeIngredient: String,
    val sourceUrl: String,
    val indianSource: Boolean,
    val cibrcStatus: String,
)

data class Ask(val question: String, val ifYes: String, val ifNo: String)

data class Source(val title: String, val url: String, val publisher: String)

/** One verified advice entry (one per disease class). Text fields may have a Hindi twin in kb_hi.json. */
data class Entry(
    val classId: String,
    val crop: String,
    val name: String,
    val type: String,
    val cause: String,
    val symptoms: List<String>,
    val lookAlikes: String?,
    val actions: List<String>,
    val treatments: List<Treatment>,
    val whenToAct: String?,
    val ask: List<Ask>,
    val sources: List<Source>,
    val caveats: String?,
) {
    /** Severity % only makes sense for leaf lesions, not for viruses, nutrients or healthy leaves. */
    val hasLesions: Boolean get() = type == "fungal" || type == "bacterial"
    val isHealthy: Boolean get() = type == "healthy"
}
