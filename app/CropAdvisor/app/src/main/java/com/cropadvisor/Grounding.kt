package com.cropadvisor

/**
 * Keeps the Llama assistant honest.
 *  - dose questions are never sent to Llama
 *  - Llama only sees verified facts for the disease on screen
 *  - its answer is checked before anything is shown; if it fails, a plain answer built straight from the facts is shown
 * Pure Kotlin (no Android classes) so it can be tested on a laptop.
 */
data class Verdict(val ok: Boolean, val reason: String = "", val refusal: Boolean = false)

enum class Topic { SYMPTOMS, CAUSE, ACTIONS, TREATMENT, TIMING, LOOKALIKE }

object Grounding {
    const val REFUSAL_MARK = "do not have verified advice"
    const val KCC = "1800-180-1551"

    // ───────────── question screening ─────────────

    private val doseEn = Regex(
        """\b(how much|how many|dose|dosage|dosing|quantity|amount|mix|mixing|dilute|dilution|ratio|per litre|per liter|per acre|per hectare|per ha|ml|gram|grams|gm|kg|litre|litres|liter|liters|ppm|concentration|tablespoon|teaspoon|cup)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val doseHi = listOf("कितना", "कितनी", "मात्रा", "खुराक", "मिलाएँ", "मिलाएं", "घोल", "प्रति लीटर", "प्रति एकड़", "प्रति हेक्टेयर", "ग्राम", "मिलीलीटर", "किलो", "लीटर")

    fun isDoseQuestion(q: String) = doseEn.containsMatchIn(q) || doseHi.any { q.contains(it) }

    private const val DOSE_EN = "The app does not give amounts or mixing rates, because they depend on the product, the crop stage and the state. Read the product label and ask your KVK or agriculture officer. Kisan Call Centre (free):"
    private const val REFUSE_EN = "I do not have verified advice on that. Please call the Kisan Call Centre (free):"

    fun doseAnswer(l: Lang) = if (l != Lang.EN && l != Lang.HI) "${I18n.t(l, DOSE_EN)} $KCC" else if (l == Lang.HI)
        "यह ऐप मात्रा या घोल की जानकारी नहीं देता, क्योंकि यह उत्पाद, फसल की अवस्था और राज्य पर निर्भर करती है। उत्पाद का लेबल पढ़ें और अपने KVK या कृषि अधिकारी से पूछें। किसान कॉल सेंटर: $KCC (मुफ़्त)।"
    else
        "The app does not give amounts or mixing rates, because they depend on the product, the crop stage and the state. Read the product label and ask your KVK or agriculture officer. Kisan Call Centre: $KCC (free)."

    fun refusalAnswer(l: Lang) = if (l != Lang.EN && l != Lang.HI) "${I18n.t(l, REFUSE_EN)} $KCC" else if (l == Lang.HI)
        "इस बारे में मेरे पास जाँची हुई सलाह नहीं है। कृपया किसान कॉल सेंटर $KCC (मुफ़्त) पर बात करें।"
    else
        "I $REFUSAL_MARK on that. Please call the Kisan Call Centre on $KCC (free)."

    // ───────────── plain answers straight from the database ─────────────

    private val kw: Map<Topic, List<String>> = mapOf(
        Topic.SYMPTOMS to listOf("symptom", "look like", "looks like", "sign", "identify", "recognise", "recognize", "how do i know", "लक्षण", "कैसा दिखता", "पहचान"),
        Topic.CAUSE to listOf("cause", "why", "reason", "how did", "what is it", "what is this", "कारण", "क्यों", "यह क्या"),
        Topic.TREATMENT to listOf("spray", "fungicide", "pesticide", "insecticide", "medicine", "chemical", "treat", "which product", "which medicine", "छिड़काव", "दवा", "दवाई", "कीटनाशक", "फफूंदनाशक", "उपचार"),
        Topic.TIMING to listOf("when", "how often", "how soon", "how long", "wait", "late", "कब", "कितनी बार", "इंतज़ार"),
        Topic.LOOKALIKE to listOf("confus", "similar", "difference", "same as", "or is it", "फर्क", "अंतर", "मिलता"),
        Topic.ACTIONS to listOf("what to do", "what should", "what do i do", "what can i do", "manage", "control", "prevent", "stop", "remove", "cure", "fix", "help", "save", "spread", "फैलता", "क्या करें", "क्या करूँ", "उपाय", "रोकथाम", "बचाव", "इलाज"),
    )

    fun classify(q: String): Topic? {
        val t = q.lowercase()
        var best: Topic? = null
        var bestScore = 0
        for ((intent, words) in kw) {
            val sc = words.count { t.contains(it) }
            if (sc > bestScore) { bestScore = sc; best = intent }
        }
        return best
    }

    fun quickAnswer(e: Entry, intent: Topic, s: S): String {
        fun list(lines: List<String>) = lines.joinToString(" ") { it.trimEnd('.') + "." }
        return when (intent) {
            Topic.SYMPTOMS -> list(e.symptoms)
            Topic.CAUSE -> if (e.cause.isBlank()) refusalAnswer(s.l) else e.cause.trimEnd('.') + "."
            Topic.ACTIONS -> if (e.actions.isEmpty()) refusalAnswer(s.l) else list(e.actions)
            Topic.TIMING -> e.whenToAct ?: refusalAnswer(s.l)
            Topic.LOOKALIKE -> e.lookAlikes ?: refusalAnswer(s.l)
            Topic.TREATMENT -> {
                val names = e.treatments.filter { it.indianSource }.map { it.activeIngredient }.distinct()
                if (names.isEmpty()) s.noTreat else names.joinToString(", ") + ". " + s.treatNote
            }
        }
    }

    /** Answer a free-text question without Llama. */
    fun plainAnswer(e: Entry, q: String, s: S): String {
        if (isDoseQuestion(q)) return doseAnswer(s.l)
        val intent = classify(q) ?: return refusalAnswer(s.l)
        return quickAnswer(e, intent, s)
    }

    // ───────────── facts and prompt for Llama ─────────────

    fun facts(e: Entry, band: Band?, pct: Int?): String {
        val b = StringBuilder()
        b.append("Crop: ${e.crop}. Problem: ${e.name} (${e.type}).\n")
        if (e.cause.isNotBlank()) b.append("Cause: ${e.cause}\n")
        if (e.symptoms.isNotEmpty()) b.append("Symptoms: ${e.symptoms.joinToString(" ")}\n")
        if (e.actions.isNotEmpty()) b.append("Field steps: ${e.actions.joinToString(" ")}\n")
        e.whenToAct?.let { b.append("When to act: $it\n") }
        e.lookAlikes?.let { b.append("Confused with: $it\n") }
        val names = e.treatments.filter { it.indianSource }.map { it.activeIngredient }.distinct()
        if (names.isNotEmpty()) b.append("Treatments named by official sources (names only, always follow the product label): ${names.joinToString(", ")}\n")
        if (band != null && pct != null && e.hasLesions) b.append("Damage measured on this photo: ${S(Lang.EN).band(band)}, about $pct%.\n")
        return b.toString().trim()
    }

    private const val SYSTEM =
        "You are a careful farm-advice assistant. Answer ONLY from the FACTS. " +
            "If the FACTS do not contain the answer, reply exactly: I $REFUSAL_MARK on that. Please call the Kisan Call Centre on $KCC. " +
            "Never give amounts, doses or mixing rates. Never name a chemical, product, disease or insect that is not in the FACTS. " +
            "Use simple words. Answer in at most 3 short sentences."

    /** Llama 3.2 Instruct chat format. */
    fun llamaPrompt(facts: String, question: String): String =
        "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$SYSTEM<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\nFACTS:\n$facts\n\nQUESTION: ${question.trim()}<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"

    // ───────────── checking Llama's answer ─────────────

    private val chemWords = setOf(
        "mancozeb", "carbendazim", "chlorothalonil", "copper", "oxychloride", "sulphur", "sulfur", "metalaxyl", "ridomil",
        "imidacloprid", "thiamethoxam", "acephate", "dimethoate", "monocrotophos", "chlorpyrifos", "cypermethrin", "deltamethrin",
        "lambda", "cyhalothrin", "profenofos", "quinalphos", "malathion", "carbofuran", "fipronil", "spinosad", "abamectin",
        "emamectin", "hexaconazole", "propiconazole", "tebuconazole", "difenoconazole", "azoxystrobin", "pyraclostrobin",
        "trifloxystrobin", "tridemorph", "thiophanate", "streptomycin", "kasugamycin", "bordeaux", "zineb", "ziram", "captan",
        "iprodione", "cymoxanil", "dimethomorph", "fosetyl", "validamycin", "neem", "dicofol", "fenazaquin", "propargite",
        "spiromesifen", "diafenthiuron", "buprofezin", "pymetrozine", "acetamiprid", "clothianidin", "dinotefuran",
        "methyl", "demeton", "endosulfan", "glyphosate", "paraquat", "urea", "dap", "potash", "npk", "zinc", "borax", "gypsum",
        "pseudomonas", "trichoderma", "bacillus",
    )
    private val chemSuffix = Regex("""\b\w*(conazole|strobin|thrin|phos|prid|mectin|mycin|thion)\b""")

    private val pathogenWords = setOf(
        "phytophthora", "alternaria", "puccinia", "fusarium", "xanthomonas", "pseudomonas", "pythium", "rhizoctonia", "cercospora",
        "septoria", "phyllosticta", "oidium", "leveillula", "colletotrichum", "verticillium", "sclerotium", "botrytis", "peronospora",
        "pseudoperonospora", "passalora", "fulvia", "corynespora", "cladosporium", "tetranychus", "bemisia", "aphis", "thrips",
        "spodoptera", "helicoverpa", "liriomyza", "phaeoisariopsis", "ralstonia", "erwinia", "macrophomina", "aspergillus",
        "begomovirus", "tobamovirus", "cucumovirus", "whitefly", "aphid", "aphids", "mite", "mites", "caterpillar", "nematode", "nematodes",
    )
    private val diseaseWords = setOf("blight", "rust", "mildew", "mosaic", "wilt", "canker", "anthracnose", "smut", "scab", "rot", "leaf curl", "mould", "mold")

    /** All "cause" lines of the database (set once at start-up); organism names in them count as known organisms. */
    var knownCauses: Collection<String> = emptyList()

    private val num = Regex("""\d+(?:\.\d+)?""")
    private val word = Regex("""[A-Za-z][A-Za-z\-]+""")

    fun check(answer: String, e: Entry, question: String, factsText: String, pct: Int?): Verdict {
        val a = answer.trim()
        if (a.isEmpty()) return Verdict(false, "empty")
        if (a.lowercase().contains(REFUSAL_MARK)) return Verdict(true, refusal = true)
        if (a.split(Regex("\\s+")).size > 90) return Verdict(false, "too long")

        val allowedText = (factsText + " " + question).lowercase()
        val allowedNums = num.findAll(allowedText).map { it.value }.toMutableSet()
        if (pct != null) allowedNums.add(pct.toString())
        for (m in num.findAll(a)) if (m.value !in allowedNums) return Verdict(false, "number ${m.value} not in facts")

        val allowedWords = word.findAll(allowedText).map { it.value }.toSet()
        val lower = a.lowercase()
        val aWords = word.findAll(lower).map { it.value }.toList()

        for (w in aWords) {
            if (w in chemWords && w !in allowedWords) return Verdict(false, "chemical '$w' not in facts")
        }
        for (m in chemSuffix.findAll(lower)) {
            if (m.value !in allowedWords) return Verdict(false, "chemical-like '${m.value}' not in facts")
        }
        val kbPathogens = pathogenWords + knownCauses.flatMap { c -> word.findAll(c).map { it.value.lowercase() }.toList() }
            .filter { it.length > 5 }
        for (w in aWords) {
            if (w in kbPathogens && w !in allowedWords) return Verdict(false, "organism '$w' not in facts")
        }
        for (d in diseaseWords) {
            if (Regex("""\b${Regex.escape(d)}\b""").containsMatchIn(lower) && !Regex("""\b${Regex.escape(d)}""").containsMatchIn(allowedText)) {
                return Verdict(false, "disease word '$d' not in facts")
            }
        }
        return Verdict(true)
    }
}
