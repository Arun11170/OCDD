package com.cropadvisor

import java.util.Locale

enum class Lang(val label: String, val locale: Locale, val code: String, val rtl: Boolean = false, val beta: Boolean = false) {
    EN("English", Locale("en", "IN"), "en"),
    HI("हिन्दी", Locale("hi", "IN"), "hi"),
    BN("বাংলা", Locale("bn", "IN"), "bn", beta = true),
    TE("తెలుగు", Locale("te", "IN"), "te", beta = true),
    MR("मराठी", Locale("mr", "IN"), "mr", beta = true),
    TA("தமிழ்", Locale("ta", "IN"), "ta", beta = true),
    GU("ગુજરાતી", Locale("gu", "IN"), "gu", beta = true),
    UR("اردو", Locale("ur", "IN"), "ur", rtl = true, beta = true),
    KN("ಕನ್ನಡ", Locale("kn", "IN"), "kn", beta = true),
    OR("ଓଡ଼ିଆ", Locale("or", "IN"), "or", beta = true),
    ML("മലയാളം", Locale("ml", "IN"), "ml", beta = true),
    PA("ਪੰਜਾਬੀ", Locale("pa", "IN"), "pa", beta = true),
    AS("অসমীয়া", Locale("as", "IN"), "as", beta = true),
}

/** All fixed screen text. Disease facts come from the advice database, not from here. */
class S(val l: Lang) {
    private fun p(en: String, hi: String): String = when (l) { Lang.EN -> en; Lang.HI -> hi; else -> I18n.ui(l, en) ?: en }

    val appName = p("Crop Advisor", "फसल सलाहकार")
    val tagline = p("Take a photo of one leaf. Works without internet.", "एक पत्ते की फोटो लें। बिना इंटरनेट के चलता है।")
    val takePhoto = p("Take photo", "फोटो लें")
    val pickPhoto = p("Choose from gallery", "गैलरी से चुनें")
    val tipsTitle = p("For a good result", "सही नतीजे के लिए")
    val tips = listOf(
        p("Fill the frame with one leaf.", "फ्रेम में एक ही पत्ता रखें।"),
        p("Use daylight. Avoid strong shadow.", "दिन की रोशनी में लें। गहरी छाया से बचें।"),
        p("Hold steady and keep the leaf in focus.", "फोन स्थिर रखें और पत्ता साफ दिखे।"),
    )
    val analysing = p("Checking the leaf…", "पत्ते की जाँच हो रही है…")
    val sureTitle = p("Most likely", "सबसे संभावित")
    val notSureTitle = p("Not sure about this photo", "इस फोटो से पक्का नहीं कह सकते")
    val notSureBody = p(
        "The app is not confident enough to name the problem. Retake the photo closer, in daylight, or ask an expert.",
        "ऐप पक्का नहीं है। पत्ते के पास से, दिन की रोशनी में दोबारा फोटो लें, या किसी विशेषज्ञ से पूछें।",
    )
    val otherPossibilities = p("Other possibilities", "अन्य संभावनाएँ")
    val confidence = p("Confidence", "भरोसा")
    val retake = p("Retake photo", "दोबारा फोटो लें")
    val newCheck = p("New check", "नई जाँच")
    val callExpert = p("Call Kisan Call Centre (free)", "किसान कॉल सेंटर को कॉल करें (मुफ़्त)")
    val severityTitle = p("How much damage", "कितना नुकसान")
    val severityNote = p("Estimate from the photo, not an exact count.", "फोटो से अनुमान है, सटीक गिनती नहीं।")
    val severityNA = p("Damage % is not measured for this kind of problem.", "इस तरह की समस्या के लिए नुकसान % नहीं मापा जाता।")
    val showMarked = p("Show damaged area", "नुकसान वाला हिस्सा दिखाएँ")
    val showOriginal = p("Show original", "असली फोटो दिखाएँ")
    val leafSmall = p("The leaf is small in the photo. Move closer for a better estimate.", "फोटो में पत्ता छोटा है। बेहतर अनुमान के लिए पास से लें।")
    val leafFull = p("The leaf fills the whole photo. Edges may be cut off.", "पत्ता पूरे फोटो में फैला है। किनारे कट सकते हैं।")
    val severityFail = p("Could not find a leaf to measure.", "मापने के लिए पत्ता नहीं मिला।")
    fun bandRange(b: Band) = when (b) {
        Band.VERY_LIGHT -> p("under 5% of the leaf", "पत्ते का 5% से कम")
        Band.MILD -> p("about 5-15% of the leaf", "पत्ते का लगभग 5-15%")
        Band.MODERATE -> p("about 15-35% of the leaf", "पत्ते का लगभग 15-35%")
        Band.SEVERE -> p("more than 35% of the leaf", "पत्ते का 35% से ज़्यादा")
    }
    val cantMeasureTitle = p("Damage can't be measured from this photo", "इस फोटो से नुकसान नहीं मापा जा सकता")
    val cantMeasureBody = p(
        "The app sees signs of this problem, but it cannot outline the spots on this kind of leaf. This does not mean the leaf is fine. Look at the leaf and the plants nearby yourself.",
        "ऐप को इस समस्या के लक्षण दिखते हैं, पर इस तरह के पत्ते पर धब्बे नहीं माप पाता। इसका मतलब यह नहीं कि पत्ता ठीक है। पत्ते और आसपास के पौधों को खुद देखें।",
    )
    val cantMeasureAct = p(
        "Start the field steps below, check nearby plants, and look again in a few days. Ask your KVK if it spreads.",
        "नीचे दिए खेत के उपाय शुरू करें, आसपास के पौधे जाँचें और कुछ दिन बाद फिर देखें। फैलने पर KVK से पूछें।",
    )
    fun band(b: Band) = when (b) {
        Band.VERY_LIGHT -> p("Very light", "बहुत हल्का")
        Band.MILD -> p("Mild", "हल्का")
        Band.MODERATE -> p("Moderate", "मध्यम")
        Band.SEVERE -> p("Severe", "गंभीर")
    }
    val decisionTitle = p("Spray or wait?", "छिड़काव करें या रुकें?")
    fun decision(b: Band, type: String): String = when {
        type == "viral" -> p(
            "Viruses cannot be cured by spraying. Remove badly affected plants and control the insects that spread it.",
            "वायरस का इलाज छिड़काव से नहीं होता। बहुत बीमार पौधे हटाएँ और फैलाने वाले कीड़ों को रोकें।",
        )
        b == Band.VERY_LIGHT -> p(
            "Little damage measured on this leaf. The app can under-measure dark or brown patches, so look at the leaf yourself. Start the field steps below and check nearby plants.",
            "इस पत्ते पर कम नुकसान मापा गया। ऐप गहरे या भूरे धब्बों को कम माप सकता है, इसलिए पत्ता खुद भी देखें। नीचे दिए खेत के उपाय शुरू करें और आसपास के पौधे जाँचें।",
        )
        b == Band.MILD -> p(
            "Mild damage. Start the field steps below and check again in a few days.",
            "हल्का नुकसान है। नीचे दिए खेत के उपाय शुरू करें और कुछ दिन बाद फिर जाँचें।",
        )
        b == Band.MODERATE -> p(
            "Moderate damage. Act now with the steps below and ask your KVK about an approved spray.",
            "मध्यम नुकसान है। अभी नीचे दिए उपाय करें और अनुमोदित दवा के लिए अपने KVK से पूछें।",
        )
        else -> p(
            "Severe damage. Act today. Talk to your KVK or the Kisan Call Centre before spraying.",
            "गंभीर नुकसान है। आज ही कदम उठाएँ। छिड़काव से पहले KVK या किसान कॉल सेंटर से बात करें।",
        )
    }
    val healthyNote = p("No disease signs found. Keep checking leaves regularly.", "रोग के लक्षण नहीं मिले। पत्तों की नियमित जाँच करते रहें।")
    val whatYouSee = p("What you see", "आप क्या देखेंगे")
    val cause = p("Cause", "कारण")
    val whatToDo = p("What to do in the field", "खेत में क्या करें")
    val treatTitle = p("Treatment named by official sources", "सरकारी स्रोतों में बताया उपचार")
    val treatNote = p(
        "Names only, no amounts. Follow the product label and confirm with your KVK or agriculture officer that it is approved for your crop.",
        "केवल नाम, मात्रा नहीं। उत्पाद के लेबल का पालन करें और KVK या कृषि अधिकारी से पुष्टि करें कि यह आपकी फसल के लिए मान्य है।",
    )
    val cibrcOk = p("On India's registered pesticide list (CIB&RC, 31 Mar 2026)", "भारत की पंजीकृत कीटनाशक सूची में है (CIB&RC, 31 मार्च 2026)")
    val cibrcAsk = p("Not confirmed on the registered list: ask your KVK first", "पंजीकृत सूची में पुष्टि नहीं: पहले KVK से पूछें")
    val noTreat = p("No chemical is listed by the sources for this. Use the field steps and ask your KVK.", "स्रोतों में इसके लिए कोई रसायन नहीं बताया गया। खेत के उपाय अपनाएँ और KVK से पूछें।")
    val whenTitle = p("When to act", "कब करें")
    val lookAlike = p("Can be confused with", "इनसे भ्रम हो सकता है")
    val askTitle = p("Check to be sure", "पक्का करने के लिए जाँचें")
    val yes = p("Yes", "हाँ")
    val no = p("No", "नहीं")
    val consistent = p("Your answer fits this result.", "आपका उत्तर इस नतीजे से मेल खाता है।")
    val uncertain = p("Your answer does not fit well. Retake the photo or ask an expert.", "आपका उत्तर ठीक मेल नहीं खाता। दोबारा फोटो लें या विशेषज्ञ से पूछें।")
    val switched = p("Based on your answer, here is the matching advice.", "आपके उत्तर के आधार पर यह संबंधित सलाह है।")
    fun checksDone(n: Int, m: Int) = p("$n of $m checks answered", "$m में से $n जाँच का उत्तर दिया")
    fun verdict(c: Float) = when {
        c >= 0.85f -> p("Very likely this problem", "बहुत संभव है यही समस्या है")
        c >= 0.60f -> p("Likely this problem", "संभवतः यही समस्या है")
        c >= 0.40f -> p("Not sure yet. Answer the checks below", "अभी पक्का नहीं। नीचे की जाँच के उत्तर दें")
        else -> p("Probably not this problem. See the other possibilities", "शायद यह समस्या नहीं है। अन्य संभावनाएँ देखें")
    }
    fun traceLine(name: String, yes: Int, total: Int) = p("Following: $name — $yes of $total typical signs confirmed", "जाँच जारी: $name — $total में से $yes विशिष्ट लक्षण पक्के हुए")
    fun checkProgress(n: Int) = p("Question $n", "प्रश्न $n")
    val notSure = p("Not sure", "पक्का नहीं")
    val undoLast = p("Undo last answer", "पिछला उत्तर वापस लें")
    val answerYes = p("Yes", "हाँ")
    val answerNo = p("No", "नहीं")
    val answerSkip = p("Not sure", "पक्का नहीं")
    fun checkNoDisease(crop: String) = p("No disease found on this $crop leaf. It looks healthy. Check again in a few days.", "इस $crop के पत्ते पर कोई बीमारी नहीं मिली। यह स्वस्थ दिखता है। कुछ दिन बाद फिर जाँचें।")
    fun checkLikely(name: String, pct: Int) = p("Your answers point to $name ($pct%).", "आपके उत्तर $name की ओर इशारा करते हैं ($pct%)।")
    val checkUnsure = p("Still not sure. Retake a closer photo or ask an expert.", "अभी भी पक्का नहीं। नज़दीक से दोबारा फोटो लें या विशेषज्ञ से पूछें।")
    val evFits = p("Fits this problem. Confidence went up.", "इस समस्या से मेल खाता है। भरोसा बढ़ा।")
    val evNotFit = p("Does not fit well. Confidence went down.", "ठीक मेल नहीं खाता। भरोसा घटा।")
    fun evPoints(name: String) = p("Could be $name instead.", "इसके बजाय $name हो सकता है।")
    val seeIt = p("See it", "देखें")
    fun photoAlone(pct: Int) = p("photo alone: $pct%", "सिर्फ फोटो से: $pct%")
    val evidenceNote = p("Answers adjust the confidence with a simple rule. They help, but do not replace an expert or a lab test.", "उत्तर एक सरल नियम से भरोसा बदलते हैं। ये मदद करते हैं, पर विशेषज्ञ या लैब जाँच की जगह नहीं लेते।")
    val possibilityNote = p("You are viewing one of the possibilities, not the top result.", "आप संभावनाओं में से एक देख रहे हैं, शीर्ष नतीजा नहीं।")
    val askHeading = p("Ask about this problem", "इस समस्या के बारे में पूछें")
    val askHint = p("Type your question", "अपना सवाल लिखें")
    val askButton = p("Ask", "पूछें")
    val quickSymptoms = p("Symptoms", "लक्षण")
    val quickActions = p("What to do", "क्या करें")
    val quickTreat = p("Treatment", "उपचार")
    val quickWhen = p("When to act", "कब करें")
    val thinking = p("Thinking…", "सोच रहे हैं…")
    val fromAdvice = p("From the verified advice", "जाँची हुई सलाह से")
    val smartAnswer = p("Smart answer (Llama, checked against the verified facts)", "स्मार्ट उत्तर (Llama, जाँचे हुए तथ्यों से मिलान किया गया)")
    val safetyAnswer = p("Safety note", "सुरक्षा सूचना")
    val smartSwitch = p("Smart answers with Llama (English only)", "Llama से स्मार्ट उत्तर (केवल अंग्रेज़ी)")
    val smartLowRam = p("Not enough free memory for Smart answers right now. Close other apps and try again.", "अभी स्मार्ट उत्तर के लिए पर्याप्त मेमोरी नहीं है। दूसरे ऐप बंद करके फिर कोशिश करें।")
    val smartCpu = p("Smart answers need a newer phone processor. Photo diagnosis and the verified advice below work fully on this phone.", "स्मार्ट उत्तर के लिए नया फोन प्रोसेसर चाहिए। फोटो से पहचान और नीचे की सत्यापित सलाह इस फोन पर पूरी तरह काम करती है।")
    val smartNoFiles = p("The Llama model files are not on this phone.", "Llama मॉडल की फ़ाइलें इस फोन में नहीं हैं।")
    val smartEnglishOnly = p("Smart answers work in English only for now.", "स्मार्ट उत्तर अभी केवल अंग्रेज़ी में हैं।")
    val smartBlocked = p("Llama's answer failed the safety check, so the verified text is shown instead.", "Llama का उत्तर सुरक्षा जाँच में नहीं टिका, इसलिए जाँचा हुआ पाठ दिखाया गया है।")
    val smartFailed = p("Smart answers could not run. The verified text is shown instead.", "स्मार्ट उत्तर नहीं चल सका। जाँचा हुआ पाठ दिखाया गया है।")
    /** Farmer-facing note about where the advice comes from (the detailed developer caveats stay in the database). */
    fun regionText(e: Entry): String {
        val indian = e.sources.any { it.publisher == "TNAU" || it.publisher == "ICAR" || it.publisher == "SAU" }
        return if (indian) p(
            "This advice follows Tamil Nadu Agricultural University, ICAR and state university guidance. Practices and approved products differ by state and season, so check with your KVK.",
            "यह सलाह तमिलनाडु कृषि विश्वविद्यालय, ICAR और राज्य विश्वविद्यालयों के मार्गदर्शन पर आधारित है। राज्य और मौसम के अनुसार तरीके और मान्य दवाएँ अलग हो सकती हैं, इसलिए अपने KVK से पूछें।",
        ) else p(
            "The sources for this problem are mostly from outside India or from research papers. Please confirm with your KVK before acting.",
            "इस समस्या की जानकारी ज़्यादातर भारत के बाहर के स्रोतों या शोध पत्रों से है। कदम उठाने से पहले अपने KVK से पुष्टि करें।",
        )
    }
    val sourcesTitle = p("Sources", "स्रोत")
    val regionNote = p("Regional note", "क्षेत्र संबंधी जानकारी")
    val hindiMissing = p("Hindi text for this advice is not ready, so it is shown in English.", "इस सलाह का हिन्दी पाठ अभी तैयार नहीं है, इसलिए अंग्रेज़ी में दिखाया गया है।")
    val listen = p("Listen", "सुनें")
    val stop = p("Stop", "रोकें")
    val footer = p(
        "Advice is compiled from public agricultural sources and is a guide, not a replacement for an expert. Kisan Call Centre: 1800-180-1551 (free).",
        "सलाह सार्वजनिक कृषि स्रोतों से ली गई है और केवल मार्गदर्शन है, विशेषज्ञ का विकल्प नहीं। किसान कॉल सेंटर: 1800-180-1551 (मुफ़्त)।",
    )
    val askAssistant = p("Ask a question", "सवाल पूछें")
    val language = p("Language", "भाषा")
    val ttsMissing = p(
        "Voice for this language is not installed on the phone. Install it in the phone's text-to-speech settings.",
        "इस भाषा की आवाज़ फोन में इंस्टॉल नहीं है। फोन की text-to-speech सेटिंग में इंस्टॉल करें।",
    )
    val loadError = p("Could not start the disease model.", "रोग मॉडल शुरू नहीं हो सका।")
    val photoError = p("Could not open that photo.", "वह फोटो नहीं खुल सकी।")

    fun cropName(crop: String) = when (crop) {
        "tomato" -> p("Tomato", "टमाटर")
        "chilli" -> p("Chilli", "मिर्च")
        "groundnut" -> p("Groundnut", "मूंगफली")
        "okra" -> p("Okra", "भिंडी")
        "apple" -> p("Apple", "सेब")
        "banana" -> p("Banana", "केला")
        "capsicum" -> p("Capsicum", "शिमला मिर्च")
        "grape" -> p("Grape", "अंगूर")
        "maize" -> p("Maize", "मक्का")
        "mango" -> p("Mango", "आम")
        "potato" -> p("Potato", "आलू")
        "rice" -> p("Rice", "धान")
        "sugarcane" -> p("Sugarcane", "गन्ना")
        "wheat" -> p("Wheat", "गेहूँ")
        "pest" -> p("Pest", "कीट")
        else -> crop.replaceFirstChar { it.uppercase() }
    }
    fun typeName(t: String) = when (t) {
        "fungal" -> p("Fungal disease", "फफूंद रोग")
        "bacterial" -> p("Bacterial disease", "जीवाणु रोग")
        "viral" -> p("Virus disease", "वायरस रोग")
        "pest" -> p("Pest damage", "कीट का नुकसान")
        "nutrient" -> p("Nutrient problem", "पोषक तत्व की समस्या")
        "healthy" -> p("Healthy", "स्वस्थ")
        else -> t
    }

    val wideTitle = p("This looks like a wide view", "यह दूर से ली गई फोटो लगती है")
    val wideBody = p(
        "Move close to one leaf or one insect so it fills the photo, in daylight, and take it again.",
        "एक पत्ते या एक कीड़े के पास जाएँ ताकि वह पूरी फोटो में दिखे। दिन की रोशनी में दोबारा फोटो लें।",
    )
    fun familyName(key: String) = when (key) {
        "weevil" -> p("weevil", "घुन (वीविल)")
        "borer" -> p("borer", "छेदक कीट")
        "cutworm" -> p("cutworm", "कटवर्म")
        "hopper" -> p("hopper", "फुदका (हॉपर)")
        "thrips" -> p("thrips", "थ्रिप्स")
        "midge" -> p("gall midge", "गॉल मिज")
        "mite" -> p("mite", "माइट (घुन)")
        "aphid" -> p("aphid", "माहू (एफिड)")
        "worm" -> p("worm / caterpillar", "इल्ली")
        "beetle" -> p("beetle", "भृंग (बीटल)")
        "fly" -> p("fly", "मक्खी")
        "cricket" -> p("cricket", "झींगुर")
        else -> p("grub", "सफेद गिडार")
    }
    fun familyNote(key: String) = p(
        "This looks like a ${familyName(key)}, but the app cannot tell which kind. Tap a possibility below, or take a closer photo.",
        "यह ${familyName(key)} जैसा लगता है, पर ऐप यह नहीं बता सकता कि कौन सा। नीचे से कोई संभावना चुनें या पास से फोटो लें।",
    )
    val aboutLink = p("About and licences", "ऐप के बारे में और लाइसेंस")
    val aboutTitle = p("About Crop Advisor", "फसल सलाहकार के बारे में")
    val builtWithLlama = p("Built with Llama", "Built with Llama")
    val closeText = p("Close", "बंद करें")

    val causeTitle = p("Which pest is behind this?", "इसके पीछे कौन सा कीट है?")
    fun causeRel(rel: String) = when (rel) {
        "spreads" -> p("Spreads this disease:", "यह रोग फैलाता है:")
        "feeds" -> p("Helps it grow:", "इसे बढ़ाने में मदद करता है:")
        else -> p("Causes this damage:", "यह नुकसान करता है:")
    }
    val seePest = p("See this pest", "यह कीट देखें")
    val causeNote = p("Commonly linked in agricultural guidance. Look for the pest on the underside of the leaf before you decide.", "कृषि सलाह में आम तौर पर जुड़ा माना जाता है। फैसला करने से पहले पत्ते की निचली सतह पर कीट ढूँढें।")

    val stateLabel = p("Your state", "आपका राज्य")
    val stateNone = p("Not set (tap to choose)", "चुना नहीं (चुनने के लिए दबाएँ)")
    val stateHint = p("Used only as a small hint: crops usually grown in your state are preferred between look-alikes.", "सिर्फ़ छोटा संकेत: मिलते-जुलते पत्तों में आपके राज्य की आम फसलों को वरीयता मिलती है।")
    val stateClear = p("Clear", "हटाएँ")

    val historyTitle = p("Earlier checks on this phone", "इस फोन पर पिछली जाँचें")
    val historyClear = p("Clear history", "इतिहास मिटाएँ")
    val damageWord = p("damage", "नुकसान")
    val shareReport = p("Share summary", "सारांश साझा करें")
    val shareHeader = p("Crop Advisor result", "फसल सलाहकार का नतीजा")
    val shareFooter = p("Estimate from a photo. Check with your local agriculture officer (KVK) or call Kisan Call Centre 1800-180-1551.", "फोटो से अनुमान है। अपने स्थानीय कृषि अधिकारी (KVK) से पूछें या किसान कॉल सेंटर 1800-180-1551 पर कॉल करें।")

    val welcomeBody = p(
        "Take a photo of a leaf or a pest. The app names the problem, shows how much damage there is and gives advice from public agriculture sources. It works without internet.",
        "पत्ते या कीट की फोटो लें। ऐप समस्या का नाम बताता है, नुकसान कितना है यह दिखाता है और सार्वजनिक कृषि स्रोतों से सलाह देता है। यह बिना इंटरनेट के चलता है।",
    )
    val welcomeAi = p(
        "Questions are answered by Llama 3.2 running on this phone. Every answer is checked against the verified advice before you see it.",
        "सवालों के जवाब इसी फोन पर चल रहा Llama 3.2 देता है। आपको दिखाने से पहले हर जवाब को जाँची हुई सलाह से मिलाया जाता है।",
    )
    val welcomeCaution = p(
        "This is a decision aid, not a replacement for your agriculture officer (KVK). Always read the product label before you spray.",
        "यह निर्णय में मदद करने वाला साधन है, कृषि अधिकारी (KVK) का विकल्प नहीं। छिड़काव से पहले हमेशा उत्पाद का लेबल पढ़ें।",
    )
    val welcomeLicences = p("Licences and credits", "लाइसेंस और श्रेय")
    val welcomeContinue = p("Continue", "आगे बढ़ें")

    // ── photo quality ──
    fun quality(i: Quality.Issue) = when (i) {
        Quality.Issue.BLUR -> p("The photo looks blurry. Hold the phone steady and take it again.", "फोटो धुंधली लग रही है। फोन स्थिर रखकर दोबारा लें।")
        Quality.Issue.DARK -> p("The photo is dark. Use daylight.", "फोटो अँधेरी है। दिन की रोशनी में लें।")
        Quality.Issue.BRIGHT -> p("The photo is washed out by strong light. Move into light shade.", "तेज़ रोशनी से फोटो फीकी है। हल्की छाया में लें।")
        Quality.Issue.SMALL -> p("The photo is very small. Use the camera at full size.", "फोटो बहुत छोटी है। कैमरे से पूरे आकार में लें।")
    }
    val qualityTitle = p("Photo quality", "फोटो की गुणवत्ता")
    val qualityNote = p("The result may be less reliable.", "नतीजा कम भरोसेमंद हो सकता है।")

    // ── feedback ──
    val feedbackAsk = p("Was this result right?", "क्या यह नतीजा सही था?")
    val feedbackYes = p("Yes", "हाँ")
    val feedbackNo = p("No", "नहीं")
    val feedbackThanks = p("Thank you. Your answer is kept only on this phone.", "धन्यवाद। आपका जवाब सिर्फ इस फोन में रखा गया है।")
    val exportHistory = p("Share history (CSV)", "इतिहास भेजें (CSV)")
    val exportHeader = p("Crop Advisor history", "फसल सलाहकार का इतिहास")

    // ── follow-up ──
    val followTitle = p("Compared with your earlier check", "पिछली जाँच से तुलना")
    fun followBody(days: Int, old: Int, now: Int): String {
        val d = now - old
        val ago = if (days <= 0) p("earlier today", "आज ही पहले") else p("$days days ago", "$days दिन पहले")
        val trend = when {
            d >= 5 -> p("It looks worse. Act on the advice below and check nearby plants.", "यह बढ़ा हुआ लगता है। नीचे की सलाह पर अमल करें और आसपास के पौधे जाँचें।")
            d <= -5 -> p("It looks better. Keep following the advice.", "यह कम हुआ लगता है। सलाह पर चलते रहें।")
            else -> p("About the same.", "लगभग वैसा ही।")
        }
        return p("Damage was about $old% ($ago), now about $now%. $trend", "नुकसान लगभग $old% था ($ago), अब लगभग $now%। $trend")
    }
    val followNoDamage = p("You checked this problem before. Compare the two photos side by side yourself.", "आपने यह समस्या पहले भी जाँची थी। दोनों फोटो साथ रखकर खुद तुलना करें।")
    val followNote = p("Photo estimates vary by a few points, so only a big change means something.", "फोटो के अनुमान में कुछ अंकों का फ़र्क आता है, इसलिए बड़ा बदलाव ही मायने रखता है।")
    fun recheckTitle(crop: String, days: Int) = p("Check again: $crop, scanned $days days ago", "दोबारा जाँचें: $crop, $days दिन पहले जाँची थी")
    val recheckBody = p("See whether it has spread. Take a new photo of the same plant.", "देखें कि फैला या नहीं। उसी पौधे की नई फोटो लें।")

    // ── organic / safety ──
    val organicTitle = p("Low-cost and organic options", "कम खर्च और जैविक विकल्प")
    val organicNote = p(
        "From public university and extension pages (English only). Names only, no amounts. Ask your KVK before using.",
        "सार्वजनिक विश्वविद्यालय और विस्तार पृष्ठों से (सिर्फ अंग्रेज़ी में)। सिर्फ नाम, मात्रा नहीं। इस्तेमाल से पहले KVK से पूछें।",
    )
    val favouredTitle = p("What makes it worse", "किन हालात में बढ़ता है")
    val organicSource = p("Source", "स्रोत")
    val safetyTitle = p("Spray safety", "छिड़काव की सुरक्षा")
    val safetyLines = listOf(
        p("Read the product label first and follow it exactly.", "पहले उत्पाद का लेबल पढ़ें और ठीक वैसा ही करें।"),
        p("Wear gloves, a mask and full-sleeve clothes.", "दस्ताने, मास्क और पूरी बाँह के कपड़े पहनें।"),
        p("Do not spray against the wind or in the heat of the day.", "हवा के विपरीत या दिन की तेज़ गर्मी में छिड़काव न करें।"),
        p("Keep children, animals and water sources away. Never reuse empty containers.", "बच्चों, पशुओं और पानी के स्रोतों को दूर रखें। खाली डिब्बे दोबारा इस्तेमाल न करें।"),
        p("Wash hands and clothes after spraying. Respect the waiting period before harvest.", "छिड़काव के बाद हाथ और कपड़े धोएँ। कटाई से पहले का इंतज़ार-समय मानें।"),
        p("Protect bees: do not spray open flowers, and spray in the evening when bees are not flying.", "मधुमक्खियों को बचाएँ: खिले फूलों पर छिड़काव न करें और शाम को छिड़काव करें जब मधुमक्खियाँ नहीं उड़तीं।"),
        p("If someone feels sick after spraying, go to a doctor at once with the product label. Poison helpline (AIIMS, free, 24 hours): 1800-116-117.", "छिड़काव के बाद किसी की तबीयत बिगड़े तो उत्पाद का लेबल लेकर तुरंत डॉक्टर के पास जाएँ। ज़हर हेल्पलाइन (AIIMS, मुफ़्त, 24 घंटे): 1800-116-117।"),
    )

    // ── plot mode ──
    val plotButton = p("Check a whole plot (several photos)", "पूरे खेत की जाँच (कई फोटो)")
    val plotTitle = p("Plot summary", "खेत का सारांश")
    fun plotSummary(total: Int, clear: Int) = p("$clear of $total photos were clear enough to count.", "$total में से $clear फोटो गिनने लायक साफ थीं।")
    fun plotRow(count: Int, clear: Int) = p("$count of $clear photos", "$clear में से $count फोटो")
    fun plotAvg(pct: Int) = p("average damage about $pct%", "औसत नुकसान लगभग $pct%")
    val plotMost = p("Most common problem", "सबसे आम समस्या")
    val plotSeeAdvice = p("See advice", "सलाह देखें")
    val plotNone = p("None of the photos was clear enough. Take close photos of single leaves in daylight.", "कोई भी फोटो काफी साफ नहीं थी। दिन की रोशनी में एक-एक पत्ते की पास से फोटो लें।")
    val plotNote = p("Walk across the field and photograph 5 to 10 plants in different places. This is an estimate, not a survey.", "खेत में घूमकर अलग-अलग जगह के 5 से 10 पौधों की फोटो लें। यह अनुमान है, पूरा सर्वे नहीं।")
    val backHome = p("Back", "वापस")

    // ── voice question ──
    val micButton = p("Speak your question", "बोलकर पूछें")
    val micMissing = p("Voice input is not available on this phone. Type your question instead.", "इस फोन पर बोलकर पूछना उपलब्ध नहीं है। सवाल लिखें।")
    val micNote = p("Speech to text is done by your phone. It works offline only if the offline speech pack is installed.", "बोली को लिखने का काम आपका फोन करता है। यह बिना इंटरनेट तभी चलेगा जब ऑफ़लाइन स्पीच पैक लगा हो।")

    // ── symptom tree ──
    fun treeLeft(left: Int, total: Int) = p("$left of $total possible problems left. The others are ruled out by your answers.", "$total संभावित समस्याओं में से $left बची हैं। बाकी आपके उत्तरों से बाहर हो गईं।")

    // ── Llama build by phone ──
    val smartNeedLite = p("This phone needs the light Llama build, which is not installed yet.", "इस फोन को हल्का Llama संस्करण चाहिए, जो अभी लगा नहीं है।")
    val smartHub = p("This phone is too small to run Llama itself. Connect to your village hub to use Smart answers.", "यह फोन Llama खुद चलाने के लिए बहुत छोटा है। Smart उत्तरों के लिए अपने गाँव के हब से जुड़ें।")

    // ── Llama download ──
    val dlTitle = p("Smart answers (Llama on this phone)", "Smart उत्तर (इसी फोन पर Llama)")
    fun dlPhone(totalMb: Long, availMb: Long, storageMb: Long) = p(
        "Your phone: ${"%.1f".format(totalMb / 1024.0)} GB memory (${availMb} MB free now), ${"%.1f".format(storageMb / 1024.0)} GB storage free.",
        "आपका फोन: ${"%.1f".format(totalMb / 1024.0)} GB मेमोरी (अभी ${availMb} MB खाली), ${"%.1f".format(storageMb / 1024.0)} GB स्टोरेज खाली।",
    )
    fun dlRecommended(t: LlmTier?) = when (t) {
        LlmTier.FULL -> p("Best build for this phone: Full", "इस फोन के लिए सही संस्करण: पूरा (Full)")
        LlmTier.LITE -> p("Best build for this phone: Light", "इस फोन के लिए सही संस्करण: हल्का (Light)")
        null -> p("This phone is too small. Use the village hub.", "यह फोन बहुत छोटा है। गाँव के हब का उपयोग करें।")
    }
    fun dlInstalled(full: Boolean, lite: Boolean) = p(
        "Installed: " + listOfNotNull(if (full) "Full" else null, if (lite) "Light" else null).ifEmpty { listOf("none") }.joinToString(", "),
        "लगे हुए: " + listOfNotNull(if (full) "पूरा" else null, if (lite) "हल्का" else null).ifEmpty { listOf("कोई नहीं") }.joinToString(", "),
    )
    val dlServerLabel = p("Model server address (for example 192.168.1.5:8000)", "मॉडल सर्वर का पता (जैसे 192.168.1.5:8000)")
    val dlStart = p("Download the right build", "सही संस्करण डाउनलोड करें")
    val dlCancel = p("Cancel", "रद्द करें")
    val dlChecking = p("Contacting the server…", "सर्वर से जुड़ रहे हैं…")
    fun dlProgress(label: String, doneMb: Long, totalMb: Long) = p("Downloading $label: $doneMb of $totalMb MB", "$label डाउनलोड हो रहा है: $totalMb MB में से $doneMb MB")
    fun dlDone(label: String) = p("$label build is ready. Switch on Smart answers when you ask a question.", "$label संस्करण तैयार है। सवाल पूछते समय Smart उत्तर चालू करें।")
    fun dlFailed(m: String) = p("Download failed: $m", "डाउनलोड नहीं हुआ: $m")
    fun dlNotOnServer(full: Boolean) = if (full) p("The server has no Full build.", "सर्वर पर पूरा संस्करण नहीं है।") else p("The server has no Light build.", "सर्वर पर हल्का संस्करण नहीं है।")
    fun dlNoSpace(needMb: Long, freeMb: Long) = p("Not enough storage: need about $needMb MB plus some spare, free is $freeMb MB.", "स्टोरेज कम है: लगभग $needMb MB चाहिए, खाली $freeMb MB है।")
    val dlWifi = p("Use Wi-Fi. The file is large (over 1 GB). The app uses the internet only for this download.", "Wi-Fi का उपयोग करें। फाइल बड़ी है (1 GB से अधिक)। ऐप इंटरनेट का उपयोग सिर्फ इस डाउनलोड के लिए करता है।")
    val dlAuto = p("Automatic", "अपने-आप")
    val dlFull = p("Full", "पूरा")
    val dlLite = p("Light", "हल्का")

    // ── first-run setup ──
    val setupTitle = p("One more step", "बस एक कदम और")
    val setupBody = p(
        "To answer your questions on this phone, the app needs to download its Llama language model, about 1 GB. It is chosen to fit this phone and is downloaded only once.",
        "आपके सवालों के जवाब इसी फोन पर देने के लिए ऐप को अपना Llama भाषा मॉडल डाउनलोड करना होगा, लगभग 1 GB। यह इस फोन के हिसाब से चुना जाता है और सिर्फ एक बार डाउनलोड होता है।",
    )
    val setupDownload = p("Download now (Wi-Fi)", "अभी डाउनलोड करें (Wi-Fi)")
    val setupSkip = p("Skip for now", "अभी छोड़ें")
    val setupSkipNote = p("Photo checks and advice work without it. You can download it later from the home screen.", "फोटो जाँच और सलाह इसके बिना भी चलती हैं। बाद में होम स्क्रीन से डाउनलोड कर सकते हैं।")

    // ── crop picker ──
    val cropPickTitle = p("Which crop?", "कौन सी फसल?")
    val cropAny = p("Not sure / any", "पक्का नहीं / कोई भी")
    val libraryButton = p("Disease and pest library (works offline)", "रोग और कीट सूची (बिना इंटरनेट)")
    val libraryTitle = p("Library", "सूची")
    val libPests = p("Pests", "कीट")
    val bigTextLabel = p("Large text", "बड़े अक्षर")
    val cropPickNudge = p("Pick your crop first. The answer is clearly more accurate with it.", "पहले अपनी फसल चुनें। इससे जवाब काफ़ी ज़्यादा सही मिलता है।")
    fun mixedUp(names: String) = p(
        "Often mixed up with: $names. Answer the \"Check to be sure\" questions below before you act.",
        "अक्सर इनसे गड़बड़ हो जाती है: $names। कदम उठाने से पहले नीचे \"पक्का करने के लिए जाँचें\" के सवालों के जवाब दें।",
    )
    fun confLabel(c: Float) = when {
        c >= 0.80f -> p("High", "ऊँचा")
        c >= 0.60f -> p("Medium", "मध्यम")
        else -> p("Low", "कम")
    }
    val cropPickHint = p("Choosing the crop makes the answer more accurate and faster.", "फसल चुनने से जवाब ज़्यादा सही और तेज़ मिलता है।")
    fun offCrop(crop: String) = p("This photo does not look like $crop. Check the crop you chose, or choose 'Not sure'.", "यह फोटो $crop जैसी नहीं लगती। चुनी हुई फसल जाँचें, या 'पक्का नहीं' चुनें।")

    // ── more photos ──
    val morePhotoTitle = p("Add another photo of the same plant", "उसी पौधे की एक और फोटो जोड़ें")
    val morePhotoBody = p("The underside of the leaf, another leaf, or the insect itself. Two views make the answer more reliable.", "पत्ते की निचली सतह, दूसरा पत्ता, या कीड़े की फोटो। दो फोटो से जवाब ज़्यादा भरोसेमंद होता है।")
    val morePhotoButton = p("Take another photo", "एक और फोटो लें")
    fun morePhotoCount(n: Int) = p("$n extra photo(s) used.", "$n अतिरिक्त फोटो इस्तेमाल हुईं।")
    val pestFoundTitle = p("Pest seen in your photo", "आपकी फोटो में दिखा कीट")

    fun followRate(perWeek: Float): String {
        val n = Math.round(Math.abs(perWeek))
        return if (perWeek > 0) p("Spreading: about $n points more damage per week.", "फैल रहा है: हर हफ्ते लगभग $n अंक ज़्यादा नुकसान।")
        else p("Recovering: about $n points less damage per week.", "सुधर रहा है: हर हफ्ते लगभग $n अंक कम नुकसान।")
    }

    // ── village hub ──
    val hubLabel = p("Village hub address, optional (for small phones)", "गाँव के हब का पता, वैकल्पिक (छोटे फोन के लिए)")
    val smartViaHub = p("Smart answers will be produced by your village hub and checked on this phone.", "Smart उत्तर आपके गाँव का हब बनाएगा और इस फोन पर जाँचे जाएँगे।")
    val viaHubNote = p("Answered by the village hub, checked on this phone.", "गाँव के हब ने उत्तर दिया, इस फोन पर जाँचा गया।")
    val hubFailed = p("Could not reach the village hub. Showing the advice text instead.", "गाँव के हब से संपर्क नहीं हुआ। सलाह का पाठ दिखा रहे हैं।")
}
