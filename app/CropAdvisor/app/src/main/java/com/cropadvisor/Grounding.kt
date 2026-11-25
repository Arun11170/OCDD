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

    // ───────────── the automatic advisory ─────────────

    private const val ADV_SYSTEM =
        "You are a careful farm-advice assistant for small farmers. Use ONLY the FACTS. " +
            "Never give amounts, doses or mixing rates. Never name a chemical, product, disease or insect that is not in the FACTS. " +
            "Write one short advisory in simple words, in exactly four short sentences, in this order: " +
            "what the problem is; how serious it is; what to do now (use the field steps in the FACTS); when to call an expert. " +
            "Do not use lists or numbering. Use at most 70 words."

    /**
     * Languages in which Llama is asked to write the advisory. Hindi is officially supported by Llama 3.2; the others are only
     * switched on when the on-device benchmark shows the answers pass the check (see Settings > Run benchmark, pass rate per language).
     */
    val llamaLangs: MutableSet<Lang> = mutableSetOf(Lang.EN, Lang.HI)

    private fun langName(l: Lang) = when (l) {
        Lang.EN -> "English"; Lang.HI -> "Hindi (Devanagari script)"; Lang.BN -> "Bengali (Bengali script)"; Lang.TE -> "Telugu (Telugu script)"
        Lang.MR -> "Marathi (Devanagari script)"; Lang.TA -> "Tamil (Tamil script)"; Lang.GU -> "Gujarati (Gujarati script)"
        Lang.UR -> "Urdu (Arabic script)"; Lang.KN -> "Kannada (Kannada script)"; Lang.OR -> "Odia (Odia script)"
        Lang.ML -> "Malayalam (Malayalam script)"; Lang.PA -> "Punjabi (Gurmukhi script)"; Lang.AS -> "Assamese (Bengali-Assamese script)"
    }

    /** Unicode block of each language's script (null = Latin / English). */
    fun scriptRange(l: Lang): CharRange? = when (l) {
        Lang.EN -> null
        Lang.HI, Lang.MR -> '\u0900'..'\u097F'
        Lang.BN, Lang.AS -> '\u0980'..'\u09FF'
        Lang.PA -> '\u0A00'..'\u0A7F'
        Lang.GU -> '\u0A80'..'\u0AFF'
        Lang.OR -> '\u0B00'..'\u0B7F'
        Lang.TA -> '\u0B80'..'\u0BFF'
        Lang.TE -> '\u0C00'..'\u0C7F'
        Lang.KN -> '\u0C80'..'\u0CFF'
        Lang.ML -> '\u0D00'..'\u0D7F'
        Lang.UR -> '\u0600'..'\u06FF'
    }

    /**
     * Prompt for the advisory. [lang] is the language of the reply. [verified] is the verified advice already written in that language;
     * Llama is told to reuse its words, which keeps non-English answers close to the checked text.
     */
    fun advisoryPrompt(facts: String, lang: Lang, severe: Boolean, verified: String = ""): String {
        val langLine = if (lang == Lang.EN) "Write the advisory in English."
        else "Write the whole advisory in ${langName(lang)}. Do not use English words except crop names." +
            (if (verified.isNotBlank()) "\nVERIFIED TEXT IN THAT LANGUAGE (reuse its words and sentences):\n$verified" else "")
        val urgent = if (severe) "\nSeverity: high. Tell the farmer to contact the agriculture extension worker or the Kisan Call Centre on $KCC today." else
            "\nIf unsure or the damage spreads, the farmer can call the Kisan Call Centre on $KCC."
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$ADV_SYSTEM<|eot_id|>" +
            "<|start_header_id|>user<|end_header_id|>\n\nFACTS:\n$facts$urgent\n\n$langLine<|eot_id|>" +
            "<|start_header_id|>assistant<|end_header_id|>\n\n"
    }

    /** Devanagari spellings of chemicals; a Hindi advisory that contains one is rejected (the facts are English, so none can be "in the facts"). */
    private val hiChem = listOf(
        "मैंकोजेब", "मैन्कोज़ेब", "मैन्कोजेब", "कार्बेन्डाजिम", "कार्बेंडाजिम", "क्लोरपायरीफॉस", "क्लोरपाइरीफॉस", "इमिडाक्लोप्रिड",
        "कॉपर", "ऑक्सीक्लोराइड", "सल्फर", "गंधक", "यूरिया", "डीएपी", "साइपरमेथ्रिन", "डेल्टामेथ्रिन", "एसीफेट", "डाइमेथोएट",
        "मोनोक्रोटोफॉस", "मेटालैक्सिल", "थायमेथोक्साम", "प्रोपिकोनाज़ोल", "हेक्साकोनाज़ोल", "टेबुकोनाज़ोल", "एज़ोक्सीस्ट्रोबिन",
        "स्ट्रेप्टोमाइसिन", "बोर्डो", "ब्लाइटॉक्स", "रिडोमिल", "नीम तेल", "ट्राइकोडर्मा", "फिप्रोनिल", "स्पिनोसैड", "इमामेक्टिन",
    )

    private val nativeDigits = Regex("[\\u0660-\\u0669\\u06F0-\\u06F9\\u0966-\\u096F\\u09E6-\\u09EF\\u0A66-\\u0A6F\\u0AE6-\\u0AEF\\u0B66-\\u0B6F\\u0BE6-\\u0BEF\\u0C66-\\u0C6F\\u0CE6-\\u0CEF\\u0D66-\\u0D6F]")
    private val wordRx = Regex("[\\p{L}\\p{M}]+")

    /**
     * Same checks as [check], plus script checks. Hindi: must be Devanagari and contain no chemical spelling.
     * Every other language: must be in its own script, and almost every long word must come from [vocab], the verified text in
     * that language (so no word, such as a chemical name, can appear that the verified text does not contain).
     */
    fun checkAdvisory(answer: String, e: Entry, facts: String, pct: Int?, lang: Lang, vocab: String = ""): Verdict {
        val a = answer.trim()
        if (a.length < 25) return Verdict(false, "too short")
        val v = check(a, e, "", "$facts $KCC", pct, if (lang == Lang.HI) 140 else 90)   // the helpline number is added by the prompt, so it is allowed
        if (!v.ok || v.refusal) return v
        val range = scriptRange(lang) ?: return Verdict(true)
        if (nativeDigits.containsMatchIn(a)) return Verdict(false, "native digits")
        val dev = a.count { it in range }
        val lat = a.count { it in 'A'..'Z' || it in 'a'..'z' }
        if (lang == Lang.HI) {
            if (dev < 20 || dev < lat) return Verdict(false, "not Hindi")
            for (w in hiChem) if (a.contains(w)) return Verdict(false, "chemical '$w' not in facts")
            return Verdict(true)
        }
        val letters = a.count { it.isLetter() }
        if (letters == 0 || dev * 100 / letters < 85) return Verdict(false, "not in ${lang.code} script")
        if (lat > 4) return Verdict(false, "English words")
        if (vocab.isBlank()) return Verdict(false, "no verified text to compare with")
        val known = wordRx.findAll(vocab).map { it.value }.toSet()
        val words = wordRx.findAll(a).map { it.value }.filter { it.length >= 5 && it[0] in range }.toList()
        val unknown = words.count { it !in known }
        if (words.isNotEmpty() && unknown * 100 / words.size > 25) return Verdict(false, "$unknown of ${words.size} long words are not in the verified text")
        return Verdict(true)
    }

    /** The advisory built straight from the verified database (used when Llama is not available or its answer fails the check). */
    fun composeAdvisory(e: Entry, band: Band?, pct: Int?, s: S): String {
        val b = StringBuilder()
        b.append(e.name.trimEnd('.')).append(". ")
        if (e.cause.isNotBlank()) b.append(e.cause.trimEnd('.')).append(". ")
        if (band != null && pct != null) b.append(s.band(band).trimEnd('.')).append(" (~").append(pct).append("%). ")
        e.actions.take(3).forEach { b.append(it.trimEnd('.')).append(". ") }
        e.whenToAct?.let { b.append(it.trimEnd('.')).append(". ") }
        b.append(s.advExpert.replace("{kcc}", KCC))
        return b.toString().trim()
    }

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

    fun check(answer: String, e: Entry, question: String, factsText: String, pct: Int?, maxWords: Int = 90): Verdict {
        val a = answer.trim()
        if (a.isEmpty()) return Verdict(false, "empty")
        if (a.lowercase().contains(REFUSAL_MARK)) return Verdict(true, refusal = true)
        if (a.split(Regex("\\s+")).size > maxWords) return Verdict(false, "too long")

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
            .filter { it.length > 5 && it != "disease" && it != "diseases" }
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
