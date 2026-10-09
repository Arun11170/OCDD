package com.cropadvisor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class AskSource { PLAIN, LLAMA, SAFETY }

sealed interface DlState {
    data object Idle : DlState
    data object Checking : DlState
    data class Running(val label: String, val done: Long, val total: Long) : DlState
    data class Done(val label: String) : DlState
    data class Failed(val message: String) : DlState
}

sealed interface AskState {
    data object Idle : AskState
    data object Thinking : AskState
    data class Done(val text: String, val source: AskSource, val note: String? = null) : AskState
}

data class ResultState(
    val photo: Bitmap,
    val prediction: Prediction,
    val severity: Severity.Result,
    val shownClass: String,          // which advice entry is on screen
    val fromPossibility: Boolean,    // user tapped a lower-ranked possibility
    val revealed: Boolean = false,   // advice may be shown (model sure, or farmer chose / answered)
    val answerNote: String? = null,  // outcome of an ask-back question
    val showMarked: Boolean = false,
    val answers: List<Pair<String, Boolean?>> = emptyList(),   // check answers: featureId to Yes/No (null = not sure)
    val engine: CheckEngine? = null,
    val wide: Boolean = false,       // looks like a wide field view, not a close-up
    val quality: List<Quality.Issue> = emptyList(),
    val prev: HistoryItem? = null,   // earlier check of the same problem (for follow-up comparison)
    val histTs: Long? = null,        // id of the saved history item, if saved
    val feedback: Int = 0,           // 0 not answered, 1 right, 2 wrong
    val extraPhotos: Int = 0,        // more photos of the same plant added to the evidence
    val foundPest: String? = null,   // a pest recognised in an extra photo
)

data class PlotRow(val classId: String, val count: Int, val avgDamage: Int?, val sample: Uri)
data class PlotState(val total: Int, val clear: Int, val rows: List<PlotRow>)

sealed interface Screen {
    data object Home : Screen
    data object Busy : Screen
    data class Result(val state: ResultState) : Screen
    data class Error(val message: String) : Screen
    data class Plot(val state: PlotState) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var lang by mutableStateOf(Lang.EN)
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var state by mutableStateOf<String?>(null)
        private set

    var cropChoice by mutableStateOf<String?>(null)
        private set
    var cropList by mutableStateOf<List<String>>(emptyList())
        private set

    fun setCrop(c: String?) { cropChoice = c; prefs().edit().apply { if (c == null) remove("crop_choice") else putString("crop_choice", c) }.apply() }

    fun chooseState(id: String?) { Region.save(getApplication(), id); state = id }

    var ask by mutableStateOf<AskState>(AskState.Idle)
        private set
    var smartOn by mutableStateOf(false)
        private set
    var smartInfo by mutableStateOf<String?>(null)
        private set

    private var llm: LlmClient? = null
    private var llmLoaded = false
    private var llmTier: LlmTier? = null
    private var classifier: Classifier? = null
    private var initError: String? = null
    private val ready = CompletableDeferred<Boolean>()
    val s get() = S(lang)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                Kb.load(app)
                Checks.load(app)
                Causes.load(app)
                Region.load(app)
                Organic.load(app)
                Stages.load(app)
                loadServer()
                refreshDevice()
                state = Region.current
                refreshHistory()
                classifier = Classifier(app)
                cropList = classifier!!.crops
                cropChoice = prefs().getString("crop_choice", null)?.takeIf { it in cropList }
                ready.complete(true)
            } catch (e: Throwable) {
                Log.e("CropAdvisor", "start-up failed", e)
                initError = e.toString().take(400)
                ready.complete(false)
            }
        }
    }

    var history by mutableStateOf<List<HistoryItem>>(emptyList())
        private set

    // ───────── Llama download (farmer-started, one time) ─────────
    var dl by mutableStateOf<DlState>(DlState.Idle)
        private set
    var device by mutableStateOf<DeviceProfile.Info?>(null)
        private set
    var forceTier by mutableStateOf<LlmTier?>(null)       // null = automatic
    var server by mutableStateOf("")
        private set
    @Volatile private var dlCancel = false

    private fun prefs() = getApplication<Application>().getSharedPreferences("cropadvisor", android.content.Context.MODE_PRIVATE)

    var hub by mutableStateOf("")
        private set
    fun changeHub(v: String) { hub = v; prefs().edit().putString("hub_server", v).apply() }

    fun loadServer() {
        hub = prefs().getString("hub_server", "") ?: ""
 server = prefs().getString("model_server", null) ?: ModelDownloader.DEFAULT_SERVER }
    fun changeServer(v: String) { server = v; prefs().edit().putString("model_server", v).apply() }
    fun refreshDevice() { device = DeviceProfile.read(getApplication()) }
    fun installed(t: LlmTier) = LlmFiles.find(getApplication(), t.dir) != null
    fun cancelDownload() { dlCancel = true }

    fun startDownload() {
        if (dl is DlState.Running || dl is DlState.Checking) return
        val info = DeviceProfile.read(getApplication()); device = info
        val tier = forceTier ?: DeviceProfile.wanted(info)
        if (tier == null) { dl = DlState.Failed(s.smartHub); return }
        dlCancel = false
        dl = DlState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mf = ModelDownloader.fetchManifest(server)
                val item = (if (tier == LlmTier.FULL) mf.full else mf.lite)
                    ?: throw java.io.IOException(s.dlNotOnServer(tier == LlmTier.FULL))
                val tok = mf.tokenizer ?: throw java.io.IOException("tokenizer missing on the server")
                val mb = item.size / (1024 * 1024)
                if (item.size > 0 && !DeviceProfile.storageOk(info, mb)) throw java.io.IOException(s.dlNoSpace(mb, info.freeStorageMb))
                val dir = ModelDownloader.targetDir(getApplication(), tier)
                if (!ModelDownloader.fetch(server, tok, dir, {}, { dlCancel })) { dl = DlState.Idle; return@launch }
                val label = if (tier == LlmTier.FULL) "Full" else "Light"
                var last = 0L
                val finished = ModelDownloader.fetch(server, item, dir, { done ->
                    val now = System.currentTimeMillis()
                    if (now - last > 300) { last = now; dl = DlState.Running(label, done, item.size) }
                }, { dlCancel })
                dl = if (finished) DlState.Done(label) else DlState.Idle
                smartInfo = null
            } catch (e: Throwable) {
                dl = DlState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun refreshHistory() { history = History.list(getApplication()) }

    fun deleteHistory(ts: Long) { History.delete(getApplication(), ts); refreshHistory() }

    fun clearHistory() { History.clear(getApplication()); history = emptyList() }

    fun analyse(uri: Uri, save: Boolean = true) {
        ask = AskState.Idle
        screen = Screen.Busy
        viewModelScope.launch(Dispatchers.Default) {
            val ok = ready.await()
            if (!ok) { screen = Screen.Error(s.loadError + "\n\n" + initError); return@launch }
            val bmp = ImageUtil.load(getApplication(), uri)
            if (bmp == null) { screen = Screen.Error(s.photoError); return@launch }
            val pred = try { Region.apply(classifier!!.predict(bmp, cropChoice)) } catch (e: Throwable) {
                Log.e("CropAdvisor", "predict failed", e)
                screen = Screen.Error(s.loadError + "\n\n" + e.toString().take(400)); return@launch
            }
            val sev = try { Severity.estimate(bmp, getApplication()) } catch (e: Throwable) { Severity.Result(false) }
            val wide = !pred.sure && !sev.ok && Severity.wideShot(bmp)
            val quality = try { Quality.check(bmp) } catch (e: Throwable) { emptyList() }
            val now = System.currentTimeMillis()
            val prev = if (save && pred.sure) History.previous(getApplication(), pred.top[0].classId, now) else null
            var ts: Long? = null
            if (save && pred.sure) {
                ts = History.add(getApplication(), bmp, pred.top[0].classId, pred.top[0].prob, if (sev.ok && sev.percent >= Severity.MIN_REAL_PCT) Math.round(sev.percent) else null)
                refreshHistory()
            }
            screen = Screen.Result(ResultState(bmp, pred, sev, pred.top[0].classId, fromPossibility = false, engine = CheckEngine(pred.ranked),
                wide = wide, quality = quality, prev = prev, histTs = ts))
        }
    }

    /** Another photo of the same plant (leaf underside, other leaf, or the pest itself). Two views of the same problem
     *  are combined; a recognised pest is shown as the likely cause instead of being mixed into the disease guess. */
    fun addPhoto(uri: Uri) {
        val cur = (screen as? Screen.Result)?.state ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = ImageUtil.load(getApplication(), uri) ?: return@launch
            val p2 = try { Region.apply(classifier!!.predict(bmp, cropChoice)) } catch (e: Throwable) { return@launch }
            val firstPest = cur.prediction.top[0].classId.startsWith("pest__")
            val secondPest = p2.top[0].classId.startsWith("pest__")
            if (secondPest && p2.sure && !firstPest) {
                update { it.copy(foundPest = p2.top[0].classId, extraPhotos = it.extraPhotos + 1) }
                return@launch
            }
            val eps = 0.005
            val a = cur.prediction.ranked.associate { it.classId to it.prob.toDouble() }
            val b = p2.ranked.associate { it.classId to it.prob.toDouble() }
            val ids = (a.keys + b.keys)
            // two photos are not fully independent evidence, so the product is softened (power 0.75) before it is normalised
            val raw = ids.associateWith { Math.pow((a[it] ?: eps) * (b[it] ?: eps), 0.75) }
            val sum = raw.values.sum()
            val ranked = raw.entries.sortedByDescending { it.value }.take(6).map { Guess(it.key, (it.value / sum).toFloat()) }
            val top = ranked.take(3)
            val pred = cur.prediction.copy(top = top, ranked = ranked, sure = top[0].prob >= Classifier.gateFor(top[0].classId) && cur.prediction.cropMass >= Classifier.MIN_CROP_MASS)
            update { it.copy(prediction = pred, shownClass = top[0].classId, fromPossibility = false, engine = CheckEngine(ranked), answers = emptyList(), extraPhotos = it.extraPhotos + 1) }
        }
    }

    /** "Was this right?" — kept only on this phone, next to the history item. */
    fun setFeedback(right: Boolean) {
        val cur = (screen as? Screen.Result)?.state ?: return
        val ts = cur.histTs ?: return
        History.setFeedback(getApplication(), ts, if (right) 1 else 2, cur.shownClass)
        refreshHistory()
        update { it.copy(feedback = if (right) 1 else 2) }
    }

    /** Several photos of one plot: counts which problem shows up most and the average damage. */
    fun analysePlot(uris: List<Uri>) {
        if (uris.isEmpty()) return
        ask = AskState.Idle
        screen = Screen.Busy
        viewModelScope.launch(Dispatchers.Default) {
            if (!ready.await()) { screen = Screen.Error(s.loadError + "\n\n" + initError); return@launch }
            val per = LinkedHashMap<String, MutableList<Pair<Uri, Int?>>>()
            var clear = 0
            val used = uris.take(12)
            for (u in used) {
                val bmp = ImageUtil.load(getApplication(), u) ?: continue
                val pred = try { Region.apply(classifier!!.predict(bmp, cropChoice)) } catch (e: Throwable) { continue }
                if (!pred.sure) continue
                val c = pred.top[0].classId
                val e = Kb.get(c, Lang.EN)
                var dmg: Int? = null
                if (e != null && e.hasLesions) {
                    val sv = try { Severity.estimate(bmp, getApplication()) } catch (t: Throwable) { null }
                    if (sv != null && sv.ok && sv.percent >= Severity.MIN_REAL_PCT) dmg = Math.round(sv.percent)
                }
                per.getOrPut(c) { mutableListOf() }.add(u to dmg)
                clear++
            }
            val rows = per.map { (c, l) ->
                val d = l.mapNotNull { it.second }
                PlotRow(c, l.size, if (d.isEmpty()) null else Math.round(d.average().toFloat()), l[0].first)
            }.sortedByDescending { it.count }
            screen = Screen.Plot(PlotState(used.size, clear, rows))
        }
    }

    private fun update(f: (ResultState) -> ResultState) {
        val cur = screen
        if (cur is Screen.Result) screen = Screen.Result(f(cur.state))
    }

    fun toggleMarked() = update { it.copy(showMarked = !it.showMarked) }

    fun viewPossibility(classId: String) { ask = AskState.Idle; viewPossibilityInner(classId) }

    private fun viewPossibilityInner(classId: String) = update {
        it.copy(shownClass = classId, fromPossibility = classId != it.prediction.top[0].classId, answerNote = null, revealed = true)
    }

    /** Yes / No / Not sure (null) on the current check question. The diagnosis follows the answers. */
    fun answerCheck(featureId: String, yes: Boolean?) = update { st ->
        withAnswers(st, st.answers.filter { it.first != featureId } + (featureId to yes))
    }

    fun undoCheck() = update { st -> withAnswers(st, st.answers.dropLast(1)) }

    private fun withAnswers(st: ResultState, ans: List<Pair<String, Boolean?>>): ResultState {
        val eng = st.engine ?: return st
        val real = ans.any { it.second != null }
        val first = st.prediction.top[0].classId
        val top = if (real) eng.posterior(ans).maxByOrNull { it.value }?.key ?: first else first
        return st.copy(answers = ans, shownClass = top, fromPossibility = top != first, revealed = st.revealed || ans.isNotEmpty(), answerNote = null)
    }

    /** Ask-back answer: the database says which class the answer points to. */
    fun answer(target: String) { ask = AskState.Idle; answerInner(target) }

    private fun answerInner(target: String) = update { st ->
        when {
            target.contains("__") -> st.copy(shownClass = target, answerNote = s.switched, fromPossibility = false, revealed = true)
            target == "consistent" -> st.copy(answerNote = s.consistent, revealed = true)
            else -> st.copy(answerNote = s.uncertain, revealed = true)
        }
    }

    fun reset() {
        unloadLlm()
        ask = AskState.Idle
        screen = Screen.Home
    }

    // ───────── questions about the disease on screen ─────────

    fun switchSmart(on: Boolean) {
        if (!on) { smartOn = false; smartInfo = null; unloadLlm(); return }
        if (lang != Lang.EN) { smartInfo = s.smartEnglishOnly; return }
        val info = DeviceProfile.read(getApplication())
        val pick = DeviceProfile.pick(getApplication(), info)
        if (pick !is DeviceProfile.Pick.Use && hub.isNotBlank()) { smartOn = true; smartInfo = s.smartViaHub; return }
        when (val pk = pick) {
            is DeviceProfile.Pick.Use -> { smartOn = true; smartInfo = null }
            DeviceProfile.Pick.NoCpu -> smartInfo = s.smartCpu
            DeviceProfile.Pick.NoFiles -> smartInfo = s.smartNoFiles
            is DeviceProfile.Pick.NeedBuild -> smartInfo = s.smartNeedLite
            DeviceProfile.Pick.UseHub -> smartInfo = s.smartHub
            DeviceProfile.Pick.TooLittleNow -> smartInfo = s.smartLowRam + " (free ${info.availMb} MB)"
        }
    }

    fun askQuestion(question: String) {
        val q = question.trim()
        val cur = (screen as? Screen.Result)?.state ?: return
        if (q.isEmpty() || ask is AskState.Thinking) return
        val entryL = Kb.get(cur.shownClass, lang) ?: return
        val entryEn = Kb.get(cur.shownClass, Lang.EN) ?: return
        val sev = cur.severity
        val pct = if (sev.ok && entryEn.hasLesions && sev.percent >= Severity.MIN_REAL_PCT) Math.round(sev.percent) else null
        val band = if (pct != null) sev.band else null
        ask = AskState.Thinking
        viewModelScope.launch(Dispatchers.Default) {
            if (Grounding.isDoseQuestion(q)) {
                ask = AskState.Done(Grounding.doseAnswer(lang), AskSource.SAFETY); return@launch
            }
            val plain = Grounding.plainAnswer(entryL, q, s)
            var active: LlmClient? = null
            if (!smartOn || lang != Lang.EN) { ask = AskState.Done(plain, AskSource.PLAIN); return@launch }
            try {
                val pk = DeviceProfile.pick(getApplication())
                if (pk !is DeviceProfile.Pick.Use && hub.isNotBlank()) {
                    // phone cannot run Llama itself right now: ask the village hub, then check the answer exactly the same way
                    val facts = Grounding.facts(entryEn, band, pct)
                    val text = try { HubClient.ask(hub, Grounding.llamaPrompt(facts, q)) } catch (e: Throwable) { null }
                    if (text == null) { ask = AskState.Done(plain, AskSource.PLAIN, s.hubFailed); return@launch }
                    val v = Grounding.check(text, entryEn, q, facts, pct)
                    ask = when {
                        v.ok && !v.refusal -> AskState.Done(text, AskSource.LLAMA, s.viaHubNote)
                        v.refusal -> AskState.Done(plain, AskSource.PLAIN)
                        else -> AskState.Done(plain, AskSource.PLAIN, s.smartBlocked + " (" + v.reason + ")")
                    }
                    return@launch
                }
                if (pk !is DeviceProfile.Pick.Use) {
                    ask = AskState.Done(plain, AskSource.PLAIN, if (pk == DeviceProfile.Pick.NoFiles) s.smartNoFiles else s.smartLowRam)
                    return@launch
                }
                val files = pk.files
                if (llmLoaded && llmTier != pk.tier) { llm?.unload(); llm = null; llmLoaded = false }   // phone state changed: switch build
                llmTier = pk.tier
                if (!llmLoaded) classifier?.release()          // free the photo models first: Llama gets all the memory it can
                val client = llm ?: LlmClient(getApplication()).also { llm = it }
                active = client
                if (!llmLoaded) { client.load(files); llmLoaded = true }
                val facts = Grounding.facts(entryEn, band, pct)
                val a = client.ask(Grounding.llamaPrompt(facts, q))
                val v = Grounding.check(a.text, entryEn, q, facts, pct)
                ask = when {
                    v.ok && !v.refusal -> AskState.Done(a.text, AskSource.LLAMA, "%.1f tokens/s, %.1f s".format(a.tokensPerSecond, a.ms / 1000f))
                    v.refusal -> AskState.Done(plain, AskSource.PLAIN)
                    else -> AskState.Done(plain, AskSource.PLAIN, s.smartBlocked + " (" + v.reason + ")")
                }
            } catch (t: Throwable) {
                if (llm === active) { llm?.unload(); llm = null; llmLoaded = false }
                ask = AskState.Done(plain, AskSource.PLAIN, s.smartFailed)
            }
        }
    }

    /** Quick buttons: straight from the verified advice, no Llama involved. */
    fun quick(intent: Topic) {
        val cur = (screen as? Screen.Result)?.state ?: return
        val e = Kb.get(cur.shownClass, lang) ?: return
        ask = AskState.Done(Grounding.quickAnswer(e, intent, s), AskSource.PLAIN)
    }

    fun unloadLlm() {
        llm?.unload()
        llm = null
        llmLoaded = false
    }

    override fun onCleared() { unloadLlm() }
}
