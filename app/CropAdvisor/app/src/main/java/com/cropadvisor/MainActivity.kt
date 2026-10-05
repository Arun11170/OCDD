package com.cropadvisor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

private val Green = Color(0xFF2E7D32)
private val GreenSoft = Color(0xFFE8F5E9)
private val Amber = Color(0xFFF9A825)
private val AmberSoft = Color(0xFFFFF8E1)
private val Red = Color(0xFFC62828)
private val Ink = Color(0xFF1B1B1B)

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    /** Gives Llama's memory back when the app leaves the screen (e.g. while the camera app is open). */
    override fun onStop() {
        super.onStop()
        model.unloadLlm()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, background = Color(0xFFFAFAF5), surface = Color.White),
            ) { App() }
        }
    }
}

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val s = vm.s
    val speech = remember { mutableStateOf<Speech?>(null) }
    var speaking by remember { mutableStateOf(false) }
    var voiceMissing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val sp = Speech(ctx) { speaking = false }
        speech.value = sp
        onDispose { sp.shutdown() }
    }

    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingUri
        if (ok && u != null) vm.analyse(u)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.analyse(u)
    }
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // keep the cache small
        val f = File(dir, "leaf_${System.currentTimeMillis()}.jpg").also { it.createNewFile() }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        pendingUri = uri
        camera.launch(uri)
    }

    BackHandler(enabled = vm.screen !is Screen.Home) { speech.value?.stop(); speaking = false; vm.reset() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        when (val sc = vm.screen) {
            is Screen.Home -> HomeScreen(vm, s, ::launchCamera) {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            is Screen.Busy -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Green)
                Spacer(Modifier.height(16.dp))
                Text(s.analysing, fontSize = 18.sp)
            }
            is Screen.Error -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(sc.message, fontSize = 18.sp, color = Red)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.reset() }) { Text(s.newCheck) }
            }
            is Screen.Result -> ResultScreen(
                vm = vm, s = s, st = sc.state,
                speaking = speaking, voiceMissing = voiceMissing,
                onListen = { text ->
                    if (speaking) { speech.value?.stop(); speaking = false }
                    else {
                        val ok = speech.value?.speak(text, vm.lang.locale) ?: false
                        voiceMissing = !ok
                        speaking = ok
                    }
                },
                onRetake = { speech.value?.stop(); speaking = false; launchCamera() },
            )
        }
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
private fun HomeScreen(vm: MainViewModel, s: S, onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LangRow(vm, s)
        Text(s.appName, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(s.tagline, fontSize = 18.sp, color = Ink)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
            Text(s.takePhoto, fontSize = 20.sp)
        }
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text(s.pickPhoto, fontSize = 18.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = GreenSoft), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.tipsTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                s.tips.forEach { Text("•  $it", fontSize = 16.sp) }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(s.footer, fontSize = 12.sp, color = Color(0xFF555555))
    }
}

@Composable
private fun LangRow(vm: MainViewModel, s: S) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        Text(s.language + ": ", fontSize = 14.sp)
        Lang.entries.forEach { l ->
            FilterChip(
                selected = vm.lang == l,
                onClick = { vm.lang = l },
                label = { Text(l.label) },
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

// ───────────────────────── Result ─────────────────────────

@Composable
private fun ResultScreen(
    vm: MainViewModel, s: S, st: ResultState,
    speaking: Boolean, voiceMissing: Boolean,
    onListen: (String) -> Unit, onRetake: () -> Unit,
) {
    val ctx = LocalContext.current
    val top = st.prediction.top
    // When the model is not sure, show no advice until the farmer picks a possibility or answers a question.
    val hold = !st.prediction.sure && !st.revealed
    val entry = if (hold) null else Kb.get(st.shownClass, vm.lang)
    val sev = st.severity
    val showSeverity = entry != null && entry.hasLesions && sev.ok

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { LangRow(vm, s) }

        // photo (original or damage overlay)
        item {
            val bmp = if (st.showMarked && sev.overlay != null) sev.overlay else st.photo
            Image(
                bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)),
            )
            if (showSeverity && sev.overlay != null) {
                TextButtonLike(if (st.showMarked) s.showOriginal else s.showMarked) { vm.toggleMarked() }
            }
        }

        // headline
        item {
            val sure = st.prediction.sure
            Card(
                colors = CardDefaults.cardColors(containerColor = if (sure) GreenSoft else AmberSoft),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!hold) {
                        Text(if (sure && !st.fromPossibility) s.sureTitle else s.otherPossibilities, fontSize = 14.sp, color = Color(0xFF444444))
                    } else {
                        Text(s.notSureTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A5900))
                        Text(s.notSureBody, fontSize = 16.sp)
                    }
                    if (entry != null) {
                        Text("${s.cropName(entry.crop)} — ${entry.name}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(s.typeName(entry.type), fontSize = 16.sp, color = Green)
                    }
                    val p = top.firstOrNull { it.classId == st.shownClass }?.prob
                    if (p != null) {
                        Text("${s.confidence}: ${(p * 100).toInt()}%", fontSize = 14.sp)
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = if (st.prediction.sure) Green else Amber)
                    }
                    if (st.fromPossibility) Text(s.possibilityNote, fontSize = 13.sp, color = Color(0xFF7A5900))
                    if (st.answerNote != null) Text(st.answerNote, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // not-sure actions + possibilities
        if (!st.prediction.sure) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRetake, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(s.retake, fontSize = 17.sp) }
                    OutlinedButton(onClick = { dial(ctx) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(s.callExpert, fontSize = 16.sp) }
                }
            }
        }
        if (top.count { it.prob >= 0.02f } > 1) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.otherPossibilities, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                top.filterIndexed { i, g -> i == 0 || g.prob >= 0.02f }.forEach { g ->
                    val e = Kb.get(g.classId, vm.lang)
                    val label = if (e != null) "${s.cropName(e.crop)} — ${e.name}  (${(g.prob * 100).toInt()}%)" else g.classId
                    FilterChip(
                        selected = g.classId == st.shownClass,
                        onClick = { vm.viewPossibility(g.classId) },
                        label = { Text(label, fontSize = 14.sp) },
                    )
                }
            }
        }

        if (entry != null) {
            // severity + spray-or-wait
            if (entry.isHealthy) {
                item { InfoCard(s.healthyNote) }
            } else {
                item {
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(s.severityTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (showSeverity) {
                                Text("${s.band(sev.band)} — about ${Math.round(sev.percent)}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = bandColor(sev.band))
                                Text(s.severityNote, fontSize = 12.sp, color = Color(0xFF666666))
                                if (sev.tooSmall) Text(s.leafSmall, fontSize = 13.sp, color = Color(0xFF7A5900))
                                if (sev.tooBig) Text(s.leafFull, fontSize = 13.sp, color = Color(0xFF7A5900))
                                Text(s.decisionTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                                Text(s.decision(sev.band, entry.type), fontSize = 16.sp)
                            } else if (!entry.hasLesions) {
                                Text(s.severityNA, fontSize = 15.sp)
                                if (entry.type == "viral") {
                                    Text(s.decisionTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                                    Text(s.decision(Band.MILD, "viral"), fontSize = 16.sp)
                                }
                            } else {
                                Text(s.severityFail, fontSize = 15.sp)
                            }
                        }
                    }
                }
            }

            if (!Kb.hasHindi(entry.classId) && vm.lang == Lang.HI) item { InfoCard(s.hindiMissing) }

            // facts
            section(s.whatYouSee, entry.symptoms)
            if (entry.cause.isNotBlank() && entry.type != "healthy" && !entry.cause.lowercase().contains("not stated")) item { Labeled(s.cause, entry.cause) }
            entry.lookAlikes?.let { la -> item { Labeled(s.lookAlike, la) } }

            if (!entry.isHealthy) {
                section(s.whatToDo, entry.actions)
                entry.whenToAct?.let { w -> item { Labeled(s.whenTitle, w) } }
                item { TreatmentCard(s, entry) }
                item { AskSection(vm, s) }

                // ask-back questions
                if (entry.ask.isNotEmpty()) {
                    item { Text(s.askTitle, fontWeight = FontWeight.SemiBold, fontSize = 17.sp) }
                    entry.ask.forEach { q ->
                        item {
                            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(q.question, fontSize = 16.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedButton(onClick = { vm.answer(q.ifYes) }) { Text(s.yes) }
                                        OutlinedButton(onClick = { vm.answer(q.ifNo) }) { Text(s.no) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                section(s.whatToDo, entry.actions)
            }

            if (!entry.isHealthy) item { Labeled(s.regionNote, s.regionText(entry)) }

            // sources
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.sourcesTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    entry.sources.forEach { src ->
                        Text(
                            if (src.publisher == "other") src.title else "${src.publisher}: ${src.title}", fontSize = 13.sp, color = Color(0xFF1565C0),
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { openLink(ctx, src.url) },
                        )
                    }
                }
            }
        }

        // listen + actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry != null) {
                    Button(onClick = { onListen(spokenText(s, entry, sev, showSeverity)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(if (speaking) s.stop else s.listen, fontSize = 17.sp)
                    }
                    if (voiceMissing) Text(s.ttsMissing, fontSize = 13.sp, color = Red)
                }
                OutlinedButton(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(s.newCheck, fontSize = 17.sp) }
                OutlinedButton(onClick = { dial(ctx) }, modifier = Modifier.fillMaxWidth()) { Text(s.callExpert, fontSize = 15.sp) }
                Text(s.footer, fontSize = 12.sp, color = Color(0xFF555555), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    item {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                lines.forEach { Text("•  $it", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
private fun AskSection(vm: MainViewModel, s: S) {
    var text by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.askHeading, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

            // quick questions answered straight from the verified advice (work on every phone)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { vm.quick(Topic.SYMPTOMS) }, label = { Text(s.quickSymptoms, fontSize = 13.sp) })
                FilterChip(selected = false, onClick = { vm.quick(Topic.ACTIONS) }, label = { Text(s.quickActions, fontSize = 13.sp) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { vm.quick(Topic.TREATMENT) }, label = { Text(s.quickTreat, fontSize = 13.sp) })
                FilterChip(selected = false, onClick = { vm.quick(Topic.TIMING) }, label = { Text(s.quickWhen, fontSize = 13.sp) })
            }

            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(s.askHint) }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
            )
            Button(onClick = { vm.askQuestion(text) }, modifier = Modifier.fillMaxWidth()) { Text(s.askButton, fontSize = 16.sp) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.smartOn, onCheckedChange = { vm.setSmart(it) })
                Text(s.smartSwitch, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
            }
            vm.smartInfo?.let { Text(it, fontSize = 13.sp, color = Color(0xFF7A5900)) }

            when (val a = vm.ask) {
                is AskState.Idle -> {}
                is AskState.Thinking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(24.dp), color = Green, strokeWidth = 2.dp)
                    Text(s.thinking, Modifier.padding(start = 10.dp), fontSize = 15.sp)
                }
                is AskState.Done -> Card(colors = CardDefaults.cardColors(containerColor = GreenSoft), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            when (a.source) {
                                AskSource.LLAMA -> s.smartAnswer
                                AskSource.SAFETY -> s.safetyAnswer
                                AskSource.PLAIN -> s.fromAdvice
                            },
                            fontSize = 12.sp, color = Color(0xFF2E5E31), fontWeight = FontWeight.SemiBold,
                        )
                        Text(a.text, fontSize = 16.sp)
                        a.note?.let { Text(it, fontSize = 12.sp, color = Color(0xFF666666)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreatmentCard(s: S, e: Entry) {
    // Chemical names are shown only when an Indian official source (TNAU / ICAR) names them.
    val shown = e.treatments.filter { it.indianSource }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AmberSoft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.treatTitle, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (shown.isEmpty()) {
                Text(s.noTreat, fontSize = 16.sp)
            } else {
                shown.map { it.activeIngredient }.distinct().forEach { Text("•  $it", fontSize = 16.sp) }
                Text(s.treatNote, fontSize = 13.sp, color = Color(0xFF5D4300))
            }
        }
    }
}

@Composable
private fun Labeled(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(body, fontSize = 15.sp)
    }
}

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = GreenSoft), shape = RoundedCornerShape(14.dp)) {
        Text(text, Modifier.padding(14.dp), fontSize = 15.sp)
    }
}

@Composable
private fun TextButtonLike(text: String, onClick: () -> Unit) {
    Text(text, color = Green, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
        modifier = Modifier.padding(top = 8.dp).clickable { onClick() })
}

private fun bandColor(b: Band) = when (b) {
    Band.VERY_LIGHT -> Green
    Band.MILD -> Color(0xFF9E9D24)
    Band.MODERATE -> Color(0xFFEF6C00)
    Band.SEVERE -> Red
}

/** What the voice reads: name, damage and decision, what you see, what to do. No chemicals are read out. */
private fun spokenText(s: S, e: Entry, sev: Severity.Result, showSeverity: Boolean): String {
    val b = StringBuilder()
    b.append("${s.cropName(e.crop)}. ${e.name}. ")
    if (e.isHealthy) {
        b.append(s.healthyNote).append(' ')
    } else if (showSeverity) {
        b.append("${s.band(sev.band)}, ${Math.round(sev.percent)} %. ")
        b.append(s.decision(sev.band, e.type)).append(' ')
    } else if (e.type == "viral") {
        b.append(s.decision(Band.MILD, "viral")).append(' ')
    }
    e.symptoms.take(3).forEach { b.append(it).append(". ") }
    if (!e.isHealthy) {
        b.append(s.whatToDo).append(". ")
        e.actions.take(4).forEach { b.append(it).append(". ") }
    }
    b.append(s.footer)
    return b.toString()
}

private fun dial(ctx: Context) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:18001801551"))) } catch (_: Exception) {}
}

private fun openLink(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
}
