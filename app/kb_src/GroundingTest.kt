import com.cropadvisor.*
import java.io.File

class P(val s: String) {
    var i = 0
    fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    fun parse(): Any? {
        ws()
        return when (s[i]) {
            '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws(); if (s[i] == '}') { i++; return m }
                while (true) { ws(); val k = str(); ws(); i++; m[k] = parse(); ws(); if (s[i] == ',') i++ else { i++; break } }; m }
            '[' -> { i++; val l = ArrayList<Any?>(); ws(); if (s[i] == ']') { i++; return l }
                while (true) { l.add(parse()); ws(); if (s[i] == ',') i++ else { i++; break } }; l }
            '"' -> str()
            't' -> { i += 4; true }
            'f' -> { i += 5; false }
            'n' -> { i += 4; null }
            else -> { val st = i; while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++; s.substring(st, i).toDouble() }
        }
    }
    fun str(): String {
        i++; val b = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') { i++; when (s[i]) { 'n' -> b.append('\n'); 't' -> b.append('\t'); 'u' -> { b.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }; else -> b.append(s[i]) } }
            else b.append(s[i])
            i++
        }
        i++; return b.toString()
    }
}
@Suppress("UNCHECKED_CAST")
fun entries(path: String): List<Entry> {
    val root = P(File(path).readText()).parse() as Map<String, Any?>
    return (root["entries"] as List<Map<String, Any?>>).map { o ->
        fun strs(k: String) = (o[k] as? List<String>) ?: emptyList()
        Entry(o["class_id"] as String, o["crop"] as String, o["name_en"] as String, o["type"] as String, (o["cause"] as? String) ?: "",
            strs("symptoms"), o["look_alikes"] as? String, strs("cultural_actions"),
            ((o["treatment_options"] as? List<Map<String, Any?>>) ?: emptyList()).map { t ->
                Treatment(t["kind"] as String, t["active_ingredient"] as String, t["source_url"] as String, (t["indian_source"] as? Boolean) ?: false, t["cibrc_status"] as String) },
            o["when_to_act"] as? String,
            ((o["ask_back"] as? List<Map<String, Any?>>) ?: emptyList()).map { Ask(it["question"] as String, it["if_yes"] as String, it["if_no"] as String) },
            ((o["sources"] as? List<Map<String, Any?>>) ?: emptyList()).map { Source(it["title"] as String, it["url"] as String, it["publisher"] as String) },
            o["caveats"] as? String)
    }
}
var fails = 0
fun expect(name: String, cond: Boolean) { println((if (cond) "PASS " else "FAIL ") + name); if (!cond) fails++ }

fun main() {
    val en = entries("/home/claude/ocdd/kb/kb.json")
    val hi = entries("/home/claude/ocdd/app/CropAdvisor/app/src/main/assets/kb_hi.json")
    expect("26 entries en/hi", en.size == 26 && hi.size == 26)
    Grounding.knownCauses = en.map { it.cause }
    val rust = en.first { it.classId == "groundnut__rust" }
    val tom = en.first { it.classId == "tomato__early_blight" }
    val s = S(Lang.EN)
    val facts = Grounding.facts(rust, Band.MODERATE, 22)
    println(facts); println("---")
    fun v(ans: String, q: String = "What is leaf rust in groundnut?", e: Entry = rust, f: String = facts, p: Int? = 22) = Grounding.check(ans, e, q, f, p)

    // the real wrong answer seen on the phone
    val wrong = "Leaf rust in groundnuts is a fungal disease caused by a fungus called Phytophthora species. Overwatering and high temperatures between 20 and 30 degrees make it worse. Using fungicides can control the disease."
    println(v(wrong)); expect("blocks real hallucination (Phytophthora, numbers)", !v(wrong).ok)
    expect("blocks organism only", !v("It is caused by Alternaria.").ok)
    expect("passes grounded answer", v("Look for raised pustules on the underside of the leaves. Remove crop residue after harvest.").ok)
    expect("passes cause from facts", v("It is caused by a fungus, Puccinia arachidis.", "what causes it").ok)
    expect("passes naming listed chemical", v("The official sources name mancozeb. Follow the product label.").ok)
    expect("blocks unlisted chemical", !v("Spray imidacloprid.").ok)
    expect("blocks chemical-like suffix", !v("Use hexaconazole twice.").ok)
    expect("blocks dose number", !v("Mix 2 g in a litre of water.").ok)
    expect("allows measured pct", v("The damage is about 22 percent, so act now.").ok)
    expect("blocks other disease word", !v("It looks like late blight.").ok)
    expect("allows disease word in facts/question", v("Rust makes raised pustules, not flat spots.").ok)
    expect("refusal ok", v("I do not have verified advice on that. Please call the Kisan Call Centre on 1800-180-1551.").refusal)
    expect("kcc number allowed in refusal", v(Grounding.refusalAnswer(Lang.EN)).ok)
    expect("blocks long", !v("word ".repeat(100)).ok)
    expect("blocks empty", !v("  ").ok)
    expect("blocks whitefly not in facts", !v("Whitefly spreads it.").ok)

    // question screening
    expect("dose q en", Grounding.isDoseQuestion("How much mancozeb per litre should I mix?"))
    expect("dose q hi", Grounding.isDoseQuestion("कितनी मात्रा डालूँ"))
    expect("not dose", !Grounding.isDoseQuestion("What are the symptoms?"))
    expect("dose answer en", Grounding.plainAnswer(rust, "how much to spray", s).contains("does not give amounts"))
    expect("intent symptoms", Grounding.classify("What are the symptoms?") == Topic.SYMPTOMS)
    expect("intent timing", Grounding.classify("When should I act?") == Topic.TIMING)
    expect("intent actions", Grounding.classify("What should I do now?") == Topic.ACTIONS)
    expect("intent treat", Grounding.classify("Which fungicide?") == Topic.TREATMENT)
    expect("intent hi", Grounding.classify("क्या करें") == Topic.ACTIONS)
    println(Grounding.plainAnswer(rust, "Which fungicide?", s))
    println(Grounding.plainAnswer(rust, "What are the symptoms?", s))
    println(Grounding.plainAnswer(rust, "will it rain tomorrow", s))
    val shi = S(Lang.HI)
    println(Grounding.plainAnswer(hi.first { it.classId == "groundnut__rust" }, "लक्षण क्या हैं", shi))
    println(Grounding.llamaPrompt(facts, "What should I do?").take(400))

    // every entry: facts build, no digit leakage beyond sources, plain answers never contain dose-like text
    val dose = Regex("""\d+(\.\d+)?\s*(ml|g|kg|l|ha|acre|ppm)\b""", RegexOption.IGNORE_CASE)
    for (e in en) for (it in Topic.values()) {
        val a = Grounding.quickAnswer(e, it, s)
        if (dose.containsMatchIn(a)) { println("DOSE-LIKE in ${e.classId}/$it: $a"); fails++ }
    }
    println(if (fails == 0) "ALL PASS" else "FAILS: $fails")
}
