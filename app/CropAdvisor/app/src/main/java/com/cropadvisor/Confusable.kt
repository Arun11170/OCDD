package com.cropadvisor

/**
 * Diseases that the photo model often mixes up (found in the 100-photos-per-crop test, and listed as look-alikes in the advice itself).
 * The result screen warns about them and points to "Check to be sure", because the right action differs.
 */
object Confusable {
    private val groups = listOf(
        listOf("apple__apple_scab", "apple__cedar_apple_rust", "apple__black_rot"),
        listOf("okra__alternaria_leaf_spot", "okra__cercospora_leaf_spot", "okra__phyllosticta_leaf_spot", "okra__downy_mildew"),
        listOf("rice__rice_stripes", "rice__rice_tungro"),
        listOf("rice__rice_blast", "rice__leaf_scald"),
        listOf("potato__early_blight", "potato__late_blight"),
        listOf("tomato__early_blight", "tomato__septoria_leaf_spot", "tomato__bacterial_spot"),
        listOf("tomato__mosaic_virus", "tomato__yellow_leaf_curl_virus"),
        listOf("banana__black_sigatoka_disease", "banana__yellow_sigatoka_disease"),
        listOf("maize__gray_leaf_spot", "maize__blight"),
        listOf("wheat__septoria", "wheat__stripe_rust"),
        listOf("groundnut__early_leaf_spot", "groundnut__late_leaf_spot"),
        listOf("pest__leafhopper", "pest__planthopper"),
        listOf("pest__rice_stem_borer", "pest__rice_leaf_folder", "pest__corn_borer", "pest__army_worm", "pest__cutworm"),
        listOf("rice__rice_stripes", "rice__leaf_scald"),
        listOf("okra__phyllosticta_leaf_spot", "okra__downy_mildew"),
        listOf("chilli__murda_complex", "chilli__nutritional_deficiency", "chilli__cercospora"),
    )

    /** The other diseases of this class's look-alike group (empty if it has none). */
    fun partners(classId: String): List<String> =
        groups.filter { classId in it }.flatMap { it }.filter { it != classId }.distinct()
}
