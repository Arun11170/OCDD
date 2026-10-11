package com.cropadvisor

import java.text.Normalizer

/** What a farmer can ask for by voice. */
enum class Cmd { CAMERA, GALLERY, LISTEN, NEW_CHECK, BACK, HELP, CALL, DIARY, WEATHER, STAGES, SETTINGS, LIBRARY, PLOT, YELLOW }

/**
 * Voice navigation: the phone's own speech recogniser turns speech into text, and this object matches key words to a command.
 * Every language also understands the English words, because many farmers mix them in ("photo", "camera", "help").
 * The word lists are short everyday words, written by machine: they need a native-speaker check (see the review pack).
 */
object VoiceNav {
    // Checked in this order: the more specific commands first ("photo from gallery" must not open the camera).
    private val order = listOf(Cmd.GALLERY, Cmd.YELLOW, Cmd.STAGES, Cmd.PLOT, Cmd.LIBRARY, Cmd.DIARY, Cmd.WEATHER, Cmd.SETTINGS, Cmd.HELP, Cmd.CALL, Cmd.NEW_CHECK, Cmd.LISTEN, Cmd.BACK, Cmd.CAMERA)

    // LANG COMMAND word,word,word
    private const val TABLE = """
EN CAMERA photo,camera,picture,snap,click
EN GALLERY gallery,choose,pick,select
EN LISTEN listen,read,speak,play,say
EN NEW_CHECK new,again,another,restart
EN BACK back,home,close,return,exit
EN HELP help,helpline
EN CALL call,expert,kisan,phone
EN DIARY diary,notes,record
EN WEATHER weather,rain,forecast
EN STAGES stage,calendar
EN SETTINGS setting
EN LIBRARY library,book,disease list,diseases
EN PLOT plot,field,farm
EN YELLOW yellow,nutrient
HI CAMERA फोटो,फ़ोटो,कैमरा,तस्वीर,खींच
HI GALLERY गैलरी,चुन
HI LISTEN सुन,पढ़,पढ,बोल,सुनाओ,सुनाइए
HI NEW_CHECK नई,नया,फिर,दोबारा,दुबारा
HI BACK वापस,पीछे,होम,बंद
HI HELP मदद,सहायता,हेल्प
HI CALL कॉल,कॉल,फोन,फ़ोन,विशेषज्ञ,किसान
HI DIARY डायरी,नोट
HI WEATHER मौसम,बारिश
HI STAGES अवस्था,चरण,स्टेज
HI SETTINGS सेटिंग
HI LIBRARY लाइब्रेरी,पुस्तकालय,बीमारी,रोग
HI PLOT प्लॉट,खेत
HI YELLOW पीले,पीला,पीली
BN CAMERA ছবি,ফটো,ক্যামেরা
BN GALLERY গ্যালারি,বাছ
BN LISTEN শুন,পড়,বল
BN NEW_CHECK নতুন,আবার
BN BACK ফিরে,পেছনে,হোম,বন্ধ
BN HELP সাহায্য,হেল্প
BN CALL কল,ফোন,বিশেষজ্ঞ,কিষান
BN DIARY ডায়েরি,নোট
BN WEATHER আবহাওয়া,বৃষ্টি
BN STAGES পর্যায়,স্টেজ,ধাপ
BN SETTINGS সেটিং
BN LIBRARY লাইব্রেরি,বই,রোগ
BN PLOT প্লট,জমি,ক্ষেত
BN YELLOW হলুদ
TE CAMERA ఫోటో,ఫొటో,కెమెరా,చిత్రం
TE GALLERY గ్యాలరీ,ఎంచు
TE LISTEN వినండి,విను,చదవ,చెప్ప
TE NEW_CHECK కొత్త,మళ్ళీ,మళ్లీ
TE BACK వెనక్కి,వెనుకకు,హోమ్,మూసివేయి
TE HELP సహాయం,హెల్ప్
TE CALL కాల్,ఫోన్,నిపుణ,కిసాన్
TE DIARY డైరీ,నోట్
TE WEATHER వాతావరణం,వర్షం
TE STAGES దశ,స్టేజ్
TE SETTINGS సెట్టింగ్
TE LIBRARY లైబ్రరీ,పుస్తక,వ్యాధి,తెగులు
TE PLOT ప్లాట్,పొలం
TE YELLOW పసుపు
MR CAMERA फोटो,कॅमेरा,कैमेरा
MR GALLERY गॅलरी,गैलरी,निवड
MR LISTEN ऐक,वाच,सांग
MR NEW_CHECK नवीन,नवा,पुन्हा
MR BACK मागे,परत,होम,बंद
MR HELP मदत,हेल्प
MR CALL कॉल,फोन,तज्ज्ञ,किसान
MR DIARY डायरी,नोंद
MR WEATHER हवामान,पाऊस
MR STAGES अवस्था,टप्पा,स्टेज
MR SETTINGS सेटिंग
MR LIBRARY लायब्ररी,ग्रंथालय,रोग,पुस्तक
MR PLOT प्लॉट,शेत
MR YELLOW पिवळ
TA CAMERA புகைப்பட,போட்டோ,கேமரா,படம்
TA GALLERY கேலரி,தேர்
TA LISTEN கேள்,படி,சொல்
TA NEW_CHECK புதிய,மீண்டும்
TA BACK பின்,திரும்ப,முகப்பு,மூடு,ஹோம்
TA HELP உதவி,ஹெல்ப்
TA CALL அழை,கால்,போன்,நிபுணர்,கிசான்
TA DIARY நாட்குறிப்பு,டைரி,டயரி,டையரி,டைறி,குறிப்பு
TA WEATHER வானிலை,மழை
TA STAGES நிலை,கட்ட,ஸ்டேஜ்
TA SETTINGS அமைப்பு,செட்டிங்
TA LIBRARY நூலகம்,லைப்ரரி,நோய்
TA PLOT பிளாட்,வயல்,நிலம்
TA YELLOW மஞ்சள்
GU CAMERA ફોટો,કેમેરા,તસવીર
GU GALLERY ગેલેરી,પસંદ
GU LISTEN સાંભળ,વાંચ,બોલ
GU NEW_CHECK નવું,નવી,ફરી
GU BACK પાછ,હોમ,બંધ
GU HELP મદદ,હેલ્પ,સહાય
GU CALL કૉલ,કોલ,ફોન,નિષ્ણાત,કિસાન
GU DIARY ડાયરી,નોંધ
GU WEATHER હવામાન,વરસાદ
GU STAGES અવસ્થા,તબક્કો,સ્ટેજ
GU SETTINGS સેટિંગ
GU LIBRARY લાઇબ્રેરી,પુસ્તકાલય,રોગ
GU PLOT પ્લોટ,ખેત
GU YELLOW પીળ
UR CAMERA فوٹو,کیمرہ,تصویر
UR GALLERY گیلری,منتخب,چن
UR LISTEN سن,پڑھ,بول
UR NEW_CHECK نیا,نئی,دوبارہ,پھر
UR BACK واپس,پیچھے,ہوم,بند
UR HELP مدد,ہیلپ
UR CALL کال,فون,ماہر,کسان
UR DIARY ڈائری,نوٹ
UR WEATHER موسم,بارش
UR STAGES مرحلہ,اسٹیج,مراحل
UR SETTINGS سیٹنگ,ترتیب
UR LIBRARY لائبریری,کتاب,بیماری
UR PLOT پلاٹ,کھیت
UR YELLOW پیلے,پیلا,پیلی
KN CAMERA ಫೋಟೋ,ಕ್ಯಾಮೆರಾ,ಚಿತ್ರ
KN GALLERY ಗ್ಯಾಲರಿ,ಆಯ್ಕೆ
KN LISTEN ಕೇಳ,ಓದ,ಹೇಳ
KN NEW_CHECK ಹೊಸ,ಮತ್ತೆ
KN BACK ಹಿಂದೆ,ವಾಪಸ್,ಹೋಮ್,ಮುಚ್ಚ
KN HELP ಸಹಾಯ,ಹೆಲ್ಪ್
KN CALL ಕರೆ,ಕಾಲ್,ಫೋನ್,ತಜ್ಞ,ಕಿಸಾನ್
KN DIARY ಡೈರಿ,ಟಿಪ್ಪಣಿ
KN WEATHER ಹವಾಮಾನ,ಮಳೆ
KN STAGES ಹಂತ,ಸ್ಟೇಜ್,ಅವಸ್ಥೆ
KN SETTINGS ಸೆಟ್ಟಿಂಗ್
KN LIBRARY ಗ್ರಂಥಾಲಯ,ಲೈಬ್ರರಿ,ರೋಗ
KN PLOT ಪ್ಲಾಟ್,ಹೊಲ
KN YELLOW ಹಳದಿ
OR CAMERA ଫଟୋ,କ୍ୟାମେରା,ଛବି
OR GALLERY ଗ୍ୟାଲେରୀ,ବାଛ
OR LISTEN ଶୁଣ,ପଢ,କୁହ
OR NEW_CHECK ନୂଆ,ନୂତନ,ପୁଣି
OR BACK ପଛକୁ,ଫେର,ହୋମ,ବନ୍ଦ
OR HELP ସାହାଯ୍ୟ,ହେଲ୍ପ
OR CALL କଲ,ଫୋନ,ବିଶେଷଜ୍ଞ,କିଷାନ
OR DIARY ଡାଏରୀ,ଟିପ୍ପଣୀ
OR WEATHER ପାଗ,ବର୍ଷା,ପାଣିପାଗ
OR STAGES ପର୍ଯ୍ୟାୟ,ସ୍ଟେଜ୍,ଅବସ୍ଥା
OR SETTINGS ସେଟିଂ
OR LIBRARY ଲାଇବ୍ରେରୀ,ପୁସ୍ତକ,ରୋଗ
OR PLOT ପ୍ଲଟ୍,ଜମି,ଚାଷ
OR YELLOW ହଳଦିଆ,ହଳଦୀ
ML CAMERA ഫോട്ടോ,ക്യാമറ,ചിത്രം
ML GALLERY ഗാലറി,തിരഞ്ഞെടുക്ക
ML LISTEN കേൾ,വായി,പറയ
ML NEW_CHECK പുതിയ,വീണ്ടും
ML BACK പിന്നോട്ട്,തിരികെ,ഹോം,അടയ്ക്ക
ML HELP സഹായം,ഹെൽപ്
ML CALL വിളി,കോൾ,ഫോൺ,വിദഗ്ധ,കിസാൻ
ML DIARY ഡയറി,കുറിപ്പ്
ML WEATHER കാലാവസ്ഥ,മഴ
ML STAGES ഘട്ടം,സ്റ്റേജ്,അവസ്ഥ
ML SETTINGS ക്രമീകരണ,സെറ്റിംഗ്
ML LIBRARY ലൈബ്രറി,ഗ്രന്ഥശാല,രോഗം
ML PLOT പ്ലോട്ട്,വയൽ,കൃഷിയിടം
ML YELLOW മഞ്ഞ
PA CAMERA ਫੋਟੋ,ਕੈਮਰਾ,ਤਸਵੀਰ
PA GALLERY ਗੈਲਰੀ,ਚੁਣ
PA LISTEN ਸੁਣ,ਪੜ੍ਹ,ਬੋਲ
PA NEW_CHECK ਨਵਾਂ,ਨਵੀਂ,ਦੁਬਾਰਾ,ਫਿਰ
PA BACK ਵਾਪਸ,ਪਿੱਛੇ,ਹੋਮ,ਬੰਦ
PA HELP ਮਦਦ,ਹੈਲਪ
PA CALL ਕਾਲ,ਫੋਨ,ਮਾਹਿਰ,ਕਿਸਾਨ
PA DIARY ਡਾਇਰੀ,ਨੋਟ
PA WEATHER ਮੌਸਮ,ਮੀਂਹ
PA STAGES ਪੜਾਅ,ਸਟੇਜ,ਅਵਸਥਾ
PA SETTINGS ਸੈਟਿੰਗ
PA LIBRARY ਲਾਇਬ੍ਰੇਰੀ,ਕਿਤਾਬ,ਬਿਮਾਰੀ
PA PLOT ਪਲਾਟ,ਖੇਤ
PA YELLOW ਪੀਲ
AS CAMERA ফটো,কেমেৰা,ছবি
AS GALLERY গেলাৰী,বাছ
AS LISTEN শুন,পঢ়,ক'
AS NEW_CHECK নতুন,পুনৰ
AS BACK উভতি,পিছলৈ,হোম,বন্ধ
AS HELP সহায়,হেল্প
AS CALL কল,ফোন,বিশেষজ্ঞ,কিষাণ
AS DIARY ডায়েৰী,টোকা
AS WEATHER বতৰ,বৰষুণ
AS STAGES পৰ্যায়,স্তৰ,ষ্টেজ
AS SETTINGS ছেটিং
AS LIBRARY লাইব্ৰেৰী,কিতাপ,ৰোগ
AS PLOT প্লট,পথাৰ,খেতি
AS YELLOW হালধীয়া
"""

    private fun norm(t: String): String =
        Normalizer.normalize(t, Normalizer.Form.NFC).lowercase().replace("‍", "").replace("‌", "")

    private val words: Map<String, Map<Cmd, List<String>>> by lazy {
        val m = HashMap<String, HashMap<Cmd, MutableList<String>>>()
        TABLE.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            val p = line.split(' ', limit = 3)
            if (p.size == 3) {
                val c = try { Cmd.valueOf(p[1]) } catch (e: Exception) { null } ?: return@forEach
                m.getOrPut(p[0]) { HashMap() }.getOrPut(c) { ArrayList() }.addAll(p[2].split(',').map { norm(it.trim()) }.filter { it.isNotEmpty() })
            }
        }
        m
    }

    /** The command in [heard], or null. Tries the farmer's language first, then English. */
    fun match(lang: Lang, heard: String): Cmd? {
        val t = norm(heard)
        if (t.isBlank()) return null
        val own = words[lang.name]
        val en = words["EN"]
        for (c in order) {
            if (own?.get(c)?.any { it in t } == true) return c
            if (en?.get(c)?.any { it in t } == true) return c
        }
        return fuzzy(own, en, t)
    }

    /** Speech engines spell loan words their own way (டைரி / டயரி), so a word that is one or two letters off still counts. */
    private fun fuzzy(own: Map<Cmd, List<String>>?, en: Map<Cmd, List<String>>?, t: String): Cmd? {
        val tokens = t.split(Regex("[^\\p{L}\\p{M}]+")).filter { it.length >= 3 }
        if (tokens.isEmpty()) return null
        for (c in order) {
            val list = (own?.get(c) ?: emptyList()) + (en?.get(c) ?: emptyList())
            for (w in list) {
                if (w.length < 4) continue
                val allowed = if (w.length >= 7) 2 else 1
                for (tok in tokens) {
                    if (kotlin.math.abs(tok.length - w.length) > allowed) continue
                    if (editDistance(tok, w, allowed) <= allowed) return c
                }
            }
        }
        return null
    }

    /** Levenshtein distance, giving up (returns limit+1) as soon as it must exceed [limit]. */
    internal fun editDistance(a: String, b: String, limit: Int): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > limit) return limit + 1
            prev = cur
        }
        return prev[b.length]
    }

    private val langNames: Map<Lang, List<String>> = mapOf(
        Lang.EN to listOf("english", "अंग्रेज़ी", "अंग्रेजी"),
        Lang.HI to listOf("hindi", "हिन्दी", "हिंदी"),
        Lang.BN to listOf("bengali", "bangla", "বাংলা", "बंगाली", "बांग्ला"),
        Lang.TE to listOf("telugu", "తెలుగు", "तेलुगु", "तेलुगू"),
        Lang.MR to listOf("marathi", "मराठी"),
        Lang.TA to listOf("tamil", "தமிழ்", "तमिल"),
        Lang.GU to listOf("gujarati", "ગુજરાતી", "गुजराती"),
        Lang.UR to listOf("urdu", "اردو", "उर्दू"),
        Lang.KN to listOf("kannada", "ಕನ್ನಡ", "कन्नड़", "कन्नड"),
        Lang.OR to listOf("odia", "oriya", "ଓଡ଼ିଆ", "ओड़िया", "उड़िया"),
        Lang.ML to listOf("malayalam", "മലയാളം", "मलयालम"),
        Lang.PA to listOf("punjabi", "ਪੰਜਾਬੀ", "पंजाबी"),
        Lang.AS to listOf("assamese", "অসমীয়া", "असमिया"),
    )

    /** "Tamil", "தமிழ்", "हिंदी में" ... : the language the farmer asked for, or null. */
    fun matchLang(heard: String): Lang? {
        val t = norm(heard)
        return langNames.entries.firstOrNull { (_, names) -> names.any { norm(it) in t } }?.key
    }

    /** Every table word of every language, for the meaning-matcher's example list. */
    fun allWords(): List<Pair<Cmd, String>> = words.values.flatMap { m -> m.flatMap { (c, ws) -> ws.map { c to it } } }.distinct()

    /** First hit among several recogniser guesses. */
    fun matchAny(lang: Lang, guesses: List<String>): Cmd? = guesses.firstNotNullOfOrNull { match(lang, it) }

    /** Words shown on the "say something" prompt (examples). */
    fun examples(lang: Lang): String = when (lang) {
        Lang.EN -> "take photo, gallery, help, diary, weather, Tamil"
        Lang.HI -> "फोटो लें, गैलरी, मदद, डायरी, मौसम, तमिल"
        else -> words[lang.name]?.let { m -> listOf(Cmd.CAMERA, Cmd.GALLERY, Cmd.HELP, Cmd.DIARY, Cmd.WEATHER).mapNotNull { m[it]?.firstOrNull() }.joinToString(", ") }
            ?: "photo, help, diary"
    }
}
