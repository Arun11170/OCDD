package com.cropadvisor

import java.util.Locale

enum class Lang(val label: String, val locale: Locale) {
    EN("English", Locale("en", "IN")),
    HI("हिन्दी", Locale("hi", "IN")),
}

/** All fixed screen text. Disease facts come from the advice database, not from here. */
class S(val l: Lang) {
    private fun p(en: String, hi: String) = if (l == Lang.HI) hi else en

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
    val noTreat = p("No chemical is listed by the sources for this. Use the field steps and ask your KVK.", "स्रोतों में इसके लिए कोई रसायन नहीं बताया गया। खेत के उपाय अपनाएँ और KVK से पूछें।")
    val whenTitle = p("When to act", "कब करें")
    val lookAlike = p("Can be confused with", "इनसे भ्रम हो सकता है")
    val askTitle = p("Check to be sure", "पक्का करने के लिए जाँचें")
    val yes = p("Yes", "हाँ")
    val no = p("No", "नहीं")
    val consistent = p("Your answer fits this result.", "आपका उत्तर इस नतीजे से मेल खाता है।")
    val uncertain = p("Your answer does not fit well. Retake the photo or ask an expert.", "आपका उत्तर ठीक मेल नहीं खाता। दोबारा फोटो लें या विशेषज्ञ से पूछें।")
    val switched = p("Based on your answer, here is the matching advice.", "आपके उत्तर के आधार पर यह संबंधित सलाह है।")
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
    val smartNoFiles = p("The Llama model files are not on this phone.", "Llama मॉडल की फ़ाइलें इस फोन में नहीं हैं।")
    val smartEnglishOnly = p("Smart answers work in English only for now.", "स्मार्ट उत्तर अभी केवल अंग्रेज़ी में हैं।")
    val smartBlocked = p("Llama's answer failed the safety check, so the verified text is shown instead.", "Llama का उत्तर सुरक्षा जाँच में नहीं टिका, इसलिए जाँचा हुआ पाठ दिखाया गया है।")
    val smartFailed = p("Smart answers could not run. The verified text is shown instead.", "स्मार्ट उत्तर नहीं चल सका। जाँचा हुआ पाठ दिखाया गया है।")
    /** Farmer-facing note about where the advice comes from (the detailed developer caveats stay in the database). */
    fun regionText(e: Entry): String {
        val indian = e.sources.any { it.publisher == "TNAU" || it.publisher == "ICAR" }
        return if (indian) p(
            "This advice follows Tamil Nadu Agricultural University and ICAR guidance. Practices and approved products differ by state and season, so check with your KVK.",
            "यह सलाह तमिलनाडु कृषि विश्वविद्यालय और ICAR के मार्गदर्शन पर आधारित है। राज्य और मौसम के अनुसार तरीके और मान्य दवाएँ अलग हो सकती हैं, इसलिए अपने KVK से पूछें।",
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
}
