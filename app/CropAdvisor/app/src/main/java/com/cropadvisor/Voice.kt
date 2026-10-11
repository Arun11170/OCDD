package com.cropadvisor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Speech to text inside the app (the phone's own recogniser, asked directly), so the app controls the microphone permission and
 * can say exactly what went wrong instead of showing a system dialog such as "voice search not allowed".
 */
class VoiceInput(private val ctx: Context) {
    private var rec: SpeechRecognizer? = null

    fun available(): Boolean = try { SpeechRecognizer.isRecognitionAvailable(ctx) } catch (e: Throwable) { false }

    fun start(languageTag: String, onResult: (List<String>) -> Unit, fail: (Int) -> Unit) {
        destroy()
        try {
            val r = SpeechRecognizer.createSpeechRecognizer(ctx)
            rec = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onError(error: Int) { fail(error) }
                override fun onResults(results: Bundle?) {
                    val g = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    if (g.isEmpty()) fail(SpeechRecognizer.ERROR_NO_MATCH) else onResult(g)
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }
            r.startListening(i)
        } catch (e: Throwable) {
            fail(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    fun cancel() { try { rec?.cancel() } catch (_: Throwable) {} }
    fun destroy() { try { rec?.destroy() } catch (_: Throwable) {}; rec = null }
}

/** A plain sentence for each recogniser error, so the farmer knows what to do. */
fun voiceErrorText(lang: Lang, code: Int): String = when (code) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr(lang, "Microphone permission is off. Turn it on: Settings > Apps > Crop Advisor > Permissions > Microphone.", "माइक्रोफ़ोन की अनुमति बंद है। चालू करें: सेटिंग > ऐप्स > Crop Advisor > अनुमतियाँ > माइक्रोफ़ोन।")
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> tr(lang, "I did not hear anything clear. Tap the mic and try again, or use the buttons below.", "कुछ साफ़ सुनाई नहीं दिया। माइक दबाकर फिर बोलें, या नीचे के बटन इस्तेमाल करें।")
    12, 13 -> tr(lang, "Voice typing for this language is not installed on the phone. Install it: Settings > Google > Settings for Google apps > Search, Assistant & Voice > Voice > Languages. Meanwhile use the buttons below.", "इस भाषा की आवाज़ से लिखने की सुविधा फोन में नहीं है। इंस्टॉल करें: सेटिंग > Google > Google ऐप्स की सेटिंग > खोज, असिस्टेंट और आवाज़ > आवाज़ > भाषाएँ। तब तक नीचे के बटन इस्तेमाल करें।")
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> tr(lang, "Voice typing needs internet, or the offline language pack from Google settings. Use the buttons below.", "आवाज़ से लिखने के लिए इंटरनेट या Google सेटिंग का ऑफ़लाइन भाषा पैक चाहिए। नीचे के बटन इस्तेमाल करें।")
    SpeechRecognizer.ERROR_AUDIO -> tr(lang, "The microphone could not be used. Close other apps that use it and try again.", "माइक्रोफ़ोन इस्तेमाल नहीं हो सका। उसे इस्तेमाल करने वाले दूसरे ऐप बंद करके फिर कोशिश करें।")
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> tr(lang, "The voice service is busy. Wait a moment and try again.", "आवाज़ सेवा व्यस्त है। थोड़ी देर बाद फिर कोशिश करें।")
    else -> tr(lang, "Voice typing is not working on this phone right now. Use the buttons below.", "इस फोन पर अभी आवाज़ से लिखना काम नहीं कर रहा। नीचे के बटन इस्तेमाल करें।")
}

/**
 * Returns a function to call when the mic is tapped. It asks for the microphone permission when needed, shows a "Listening" box,
 * and calls [onHeard] with the recogniser's guesses or [onProblem] with a plain sentence.
 */
@Composable
fun rememberVoiceTrigger(lang: Lang, hint: String, onHeard: (List<String>) -> Unit, onProblem: (String) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val input = remember { VoiceInput(ctx) }
    var listening by remember { mutableStateOf(false) }
    val heard by rememberUpdatedState(onHeard)
    val problem by rememberUpdatedState(onProblem)
    DisposableEffect(Unit) { onDispose { input.destroy() } }

    fun begin() {
        if (!input.available()) {
            problem(tr(lang, "This phone has no voice typing service. Install or enable the Google app, then try again. Use the buttons below for now.", "इस फोन में आवाज़ से लिखने की सेवा नहीं है। Google ऐप इंस्टॉल या चालू करके फिर कोशिश करें। अभी नीचे के बटन इस्तेमाल करें।"))
            return
        }
        listening = true
        input.start(lang.locale.toLanguageTag(), { g -> listening = false; heard(g) }, { code -> listening = false; problem(voiceErrorText(lang, code)) })
    }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) begin() else problem(voiceErrorText(lang, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }

    if (listening) {
        AlertDialog(
            onDismissRequest = { input.cancel(); listening = false },
            title = { Text(tr(lang, "Listening…", "सुन रहा हूँ…")) },
            text = { Text(tr(lang, "Say: ", "बोलें: ") + hint) },
            confirmButton = { TextButton(onClick = { input.cancel(); listening = false }) { Text(tr(lang, "Cancel", "रद्द करें")) } },
        )
    }

    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
        else askPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
