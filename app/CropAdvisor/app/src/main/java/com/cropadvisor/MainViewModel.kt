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
)

sealed interface Screen {
    data object Home : Screen
    data object Busy : Screen
    data class Result(val state: ResultState) : Screen
    data class Error(val message: String) : Screen
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var lang by mutableStateOf(Lang.EN)
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    var ask by mutableStateOf<AskState>(AskState.Idle)
        private set
    var smartOn by mutableStateOf(false)
        private set
    var smartInfo by mutableStateOf<String?>(null)
        private set

    private var llm: LlmClient? = null
    private var llmLoaded = false
    private var classifier: Classifier? = null
    private var initError: String? = null
    private val ready = CompletableDeferred<Boolean>()
    val s get() = S(lang)

    init {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                Kb.load(app)
                classifier = Classifier(app)
                ready.complete(true)
            } catch (e: Throwable) {
                Log.e("CropAdvisor", "start-up failed", e)
                initError = e.toString().take(400)
                ready.complete(false)
            }
        }
    }

    fun analyse(uri: Uri) {
        ask = AskState.Idle
        screen = Screen.Busy
        viewModelScope.launch(Dispatchers.Default) {
            val ok = ready.await()
            if (!ok) { screen = Screen.Error(s.loadError + "\n\n" + initError); return@launch }
            val bmp = ImageUtil.load(getApplication(), uri)
            if (bmp == null) { screen = Screen.Error(s.photoError); return@launch }
            val pred = try { classifier!!.predict(bmp) } catch (e: Throwable) {
                Log.e("CropAdvisor", "predict failed", e)
                screen = Screen.Error(s.loadError + "\n\n" + e.toString().take(400)); return@launch
            }
            val sev = try { Severity.estimate(bmp) } catch (e: Throwable) { Severity.Result(false) }
            screen = Screen.Result(ResultState(bmp, pred, sev, pred.top[0].classId, fromPossibility = false))
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

    fun setSmart(on: Boolean) {
        if (!on) { smartOn = false; smartInfo = null; unloadLlm(); return }
        if (lang != Lang.EN) { smartInfo = s.smartEnglishOnly; return }
        val gate = MemoryGate.check(getApplication())
        when {
            LlmFiles.find(getApplication()) == null -> smartInfo = s.smartNoFiles
            !gate.enough -> smartInfo = s.smartLowRam + " (free ${gate.availMb} MB, need ${MemoryGate.NEED_MB} MB)"
            else -> { smartOn = true; smartInfo = null }
        }
    }

    fun askQuestion(question: String) {
        val q = question.trim()
        val cur = (screen as? Screen.Result)?.state ?: return
        if (q.isEmpty() || ask is AskState.Thinking) return
        val entryL = Kb.get(cur.shownClass, lang) ?: return
        val entryEn = Kb.get(cur.shownClass, Lang.EN) ?: return
        val sev = cur.severity
        val pct = if (sev.ok && entryEn.hasLesions) Math.round(sev.percent) else null
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
                val files = LlmFiles.find(getApplication())
                if (files == null) {
                    smartOn = false
                    ask = AskState.Done(plain, AskSource.PLAIN, if (files == null) s.smartNoFiles else s.smartLowRam)
                    return@launch
                }
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
