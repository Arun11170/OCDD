package com.cropadvisor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File

private val Green = Ds.Brand
private val GreenDark = Ds.Forest
private val GreenSoft = Ds.Mint
private val Amber = Ds.Warn
private val AmberSoft = Ds.WarnSoft
private val Red = Ds.Danger
private val Ink = Ds.Ink
private val Page = Ds.Page

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()

    /** Gives Llama's memory back when the app leaves the screen (e.g. while the camera app is open). */
    override fun onStop() {
        super.onStop()
        model.unloadLlm()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiPrefs.bigText = getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).getBoolean("big_text", false)
        UiPrefs.simple = getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).getBoolean("simple_mode", false)
        Embedder.warmUp(this)      // loads the optional meaning-matcher in the background if it was downloaded
        setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * (if (UiPrefs.bigText) 1.25f else 1f))) {
                MaterialTheme(
                    typography = AppTypography,
                    colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, primaryContainer = GreenSoft, secondary = GreenDark, background = Page, surface = Color.White, surfaceVariant = GreenSoft, onBackground = Ink, onSurface = Ink),
                ) { App() }
            }
        }
    }
}

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides (if (vm.lang.rtl) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr),
    ) { AppBody(vm) }
}

@Composable
private fun AppBody(vm: MainViewModel) {
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
    var addMode by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingUri
        val add = addMode
        addMode = false
        if (ok && u != null) { if (add) vm.addPhoto(u) else vm.analyse(u) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) vm.analyse(u)
    }
    val plotPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(12)) { us ->
        if (us.isNotEmpty()) vm.analysePlot(us)
    }
    fun startCamera() {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }   // keep the cache small
        val f = File(dir, "leaf_${System.currentTimeMillis()}.jpg").also { it.createNewFile() }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        pendingUri = uri
        try { camera.launch(uri) } catch (e: Exception) { Log.w("CropAdvisor", "camera app could not start", e) }
    }
    // CAMERA is declared in the manifest, so Android refuses the camera intent (and the app closes) until it is granted
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
    }
    fun launchCamera() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) startCamera()
        else cameraPermission.launch(android.Manifest.permission.CAMERA)
    }

    val prefs = remember { ctx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE) }
    var welcomed by rememberSaveable { mutableStateOf(prefs.getBoolean("welcomed", false)) }
    if (!welcomed) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            WelcomeScreen(vm, s) { prefs.edit().putBoolean("welcomed", true).apply(); welcomed = true }
        }
        return
    }

    var setupDone by rememberSaveable { mutableStateOf(prefs.getBoolean("setup_done", false)) }
    if (!setupDone) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
            SetupScreen(vm, s) { prefs.edit().putBoolean("setup_done", true).apply(); setupDone = true }
        }
        return
    }

    BackHandler(enabled = vm.screen !is Screen.Home) { speech.value?.stop(); speaking = false; vm.reset() }

    if (CoachState.open) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            CoachCamera(vm.lang, { uri -> CoachState.open = false; vm.analyse(uri) }) { CoachState.open = false }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        when (val sc = vm.screen) {
            is Screen.Home -> HomeScreen(vm, s, ::launchCamera, {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                plotPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            is Screen.Plot -> PlotScreen(vm, s, sc.state)
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
                onRetake = { speech.value?.stop(); speaking = false; addMode = false; launchCamera() },
                onAddPhoto = { addMode = true; launchCamera() },
            )
        }
    }
}

// ───────────────────────── Welcome (first launch) ─────────────────────────

@Composable
private fun WelcomeScreen(vm: MainViewModel, s: S, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    var showNotices by rememberSaveable { mutableStateOf(false) }
    val notices = remember { try { ctx.assets.open("NOTICES.txt").bufferedReader().use { it.readText() } } catch (e: Exception) { "Built with Llama." } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LangRow(vm, s)
        Text(s.appName, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(s.welcomeBody, fontSize = 18.sp)
        Card(colors = CardDefaults.cardColors(containerColor = GreenSoft), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.builtWithLlama, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(s.welcomeAi, fontSize = 15.sp)
            }
        }
        Text(s.welcomeCaution, fontSize = 15.sp, color = Color(0xFF444444))
        TextButtonLike(s.welcomeLicences) { showNotices = !showNotices }
        if (showNotices) Text(notices, fontSize = 13.sp, color = Color(0xFF444444))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) { Text(s.welcomeContinue, fontSize = 18.sp) }
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
private fun ToolTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bg: Color, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(22.dp)).background(Color.White).clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon, bg, tint, 46.dp, 24.dp)
        Text(label, fontSize = 12.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = Ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center, minLines = 2, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** Section heading with a small green bar. */
@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Green))
        Text(text, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Ink)
    }
}

@Composable
private fun ToolDialog(title: String, closeText: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text(closeText) } },
    )
}

@Composable
private fun HomeScreen(vm: MainViewModel, s: S, onCamera: () -> Unit, onGallery: () -> Unit, onPlot: () -> Unit) {
    val l = vm.lang
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    val hctx = LocalContext.current
    // the crop of the latest check that was a crop (not a pest): the weather and stage tools use it
    val lastCrop = vm.history.firstNotNullOfOrNull { h -> Kb.get(h.classId, Lang.EN)?.crop?.takeIf { it != "pest" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    // top bar: logo, name, language
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Ds.BrandLight, GreenDark))), contentAlignment = Alignment.Center) {
            Image(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(54.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(s.appName, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ink, modifier = Modifier.weight(1f))
        VoiceButton(s, vm.lang, { l2 -> vm.lang = l2; hctx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).edit().putString("lang", l2.code).apply() }) { c ->
            when (c) {
                Cmd.CAMERA, Cmd.NEW_CHECK -> onCamera()
                Cmd.GALLERY -> onGallery()
                Cmd.PLOT -> onPlot()
                Cmd.LIBRARY -> tool = "lib"
                Cmd.DIARY -> tool = "diary"
                Cmd.WEATHER -> tool = "weather"
                Cmd.STAGES -> tool = "stage"
                Cmd.YELLOW -> tool = "nutrient"
                Cmd.HELP -> tool = "help"
                Cmd.SETTINGS -> tool = "settings"
                Cmd.CALL -> dial(hctx)
                Cmd.LISTEN, Cmd.BACK -> Unit
            }
        }
        Spacer(Modifier.width(8.dp))
        LangRow(vm, s)
    }
    // hero: the one thing to do
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(30.dp))
        .background(Brush.linearGradient(listOf(GreenDark, Green)))) {
        Image(painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, alpha = 0.10f,
            modifier = Modifier.size(210.dp).align(Alignment.TopEnd).offset(x = 50.dp, y = (-30).dp))
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(tr(l, "Check your crop", "अपनी फसल जाँचें"), fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(s.tagline, fontSize = 14.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(2.dp))
            DsButton(s.takePhoto, Icons.Rounded.CameraAlt, onCamera, Modifier.fillMaxWidth(), BtnStyle.OnDark, 58.dp)
            DsButton(s.pickPhoto, Icons.Rounded.PhotoLibrary, onGallery, Modifier.fillMaxWidth(), BtnStyle.Soft, 50.dp)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RecheckBanner(l, onCamera)
        val now = System.currentTimeMillis()
        val due = vm.history.firstOrNull { (now - it.ts) / 86_400_000L in 6L..30L && Kb.get(it.classId, l)?.isHealthy == false && !Rechecks.isSet(hctx, it.classId) && Rechecks.due(hctx, now).none { r -> r.cls == it.classId } }
        if (due != null) {
            val de = Kb.get(due.classId, l)
            Card(colors = CardDefaults.cardColors(containerColor = AmberSoft), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.recheckTitle(if (de != null) s.cropName(de.crop) else due.classId, ((now - due.ts) / 86_400_000L).toInt()), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(s.recheckBody, fontSize = 14.sp)
                    Button(onClick = onCamera) { Text(s.takePhoto) }
                }
            }
        }

        SectionTitle(tr(l, "Tools", "साधन"))
        val tiles = listOf(
            listOf(Icons.Rounded.PhotoCamera, tr(l, "Live tips camera", "लाइव सुझाव कैमरा"), "coach", Ds.Sky, Color(0xFF1C6FA8)),
            listOf(Icons.Rounded.Agriculture, s.plotButton.substringBefore("(").trim(), "plot", Ds.Sand, Color(0xFF9A6A00)),
            listOf(Icons.AutoMirrored.Rounded.MenuBook, s.libraryButton.substringBefore("(").trim(), "lib", Ds.Lilac, Color(0xFF5B3FA6)),
            listOf(Icons.Rounded.EditNote, tr(l, "Season diary", "मौसम की डायरी"), "diary", Color(0xFFFFE9DA), Color(0xFFC25B14)),
            listOf(Icons.Rounded.WbSunny, tr(l, "Weather watch", "मौसम की चेतावनी"), "weather", Ds.Sky, Color(0xFF1C6FA8)),
            listOf(Icons.Rounded.Grass, tr(l, "Crop stages", "फसल अवस्था"), "stage", Ds.Mint, Ds.Brand),
            listOf(Icons.Rounded.Eco, tr(l, "Yellow leaves", "पीले पत्ते"), "nutrient", Ds.WarnSoft, Ds.WarnInk),
            listOf(Icons.Rounded.SupportAgent, tr(l, "Help", "मदद"), "help", Ds.Rose, Ds.Danger),
            listOf(Icons.Rounded.Settings, tr(l, "Settings", "सेटिंग"), "settings", Color(0xFFECEFF1), Color(0xFF50606A)),
        )
        tiles.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    ToolTile(t[0] as androidx.compose.ui.graphics.vector.ImageVector, t[1] as String, t[3] as Color, t[4] as Color, Modifier.weight(1f).fillMaxHeight()) {
                        when (val id = t[2] as String) { "coach" -> CoachState.open = true; "plot" -> onPlot(); else -> tool = id }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        val close = s.closeText
        when (tool) {
            "lib" -> LibraryDialogs(vm, s) { tool = null }
            "diary" -> DiaryDialog(l, vm.history, { csv -> shareText(hctx, s.exportHeader, csv) }) { tool = null }
            "weather" -> ToolDialog(tr(l, "Weather watch", "मौसम की चेतावनी"), close, { tool = null }) { WeatherWatchCard(l, lastCrop) { c -> s.cropName(c) } }
            "stage" -> ToolDialog(tr(l, "Crop stage guide", "फसल अवस्था गाइड"), close, { tool = null }) { CropCalendarCard(l, lastCrop) }
            "nutrient" -> ToolDialog(tr(l, "Yellow leaves check", "पीले पत्तों की जाँच"), close, { tool = null }) { NutrientCheckCard(l) }
            "help" -> ToolDialog(tr(l, "Help and helplines", "मदद और हेल्पलाइन"), close, { tool = null }) { HelpCard(l) }
            "settings" -> SettingsDialog(vm, s) { tool = null }
        }

        if (vm.history.isNotEmpty()) {
            SectionTitle(s.historyTitle)
            vm.history.take(3).forEach { h ->
                val e = Kb.get(h.classId, l)
                val thumb = remember(h.file) {
                    try {
                        val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                        android.graphics.BitmapFactory.decodeFile(h.file, o)?.asImageBitmap()
                    } catch (t: Throwable) { null }
                }
                Row(
                    Modifier.fillMaxWidth().shadow(2.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .clickable { vm.analyse(Uri.fromFile(File(h.file)), save = false) }.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (thumb != null) Image(bitmap = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.height(56.dp).width(56.dp).clip(RoundedCornerShape(8.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(if (e != null) "${s.cropName(e.crop)} — ${e.name}" else h.classId, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(History.dateText(h.ts, l) + (if (h.damage != null) "  ·  ${s.band(Severity.bandOf(h.damage.toFloat()))}" else ""), fontSize = 13.sp, color = Color(0xFF444444))
                    }
                    Box(Modifier.size(48.dp).clickable { vm.deleteHistory(h.ts) }, contentAlignment = Alignment.Center) {
                        Text("✕", fontSize = 18.sp, color = Color(0xFF444444))
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = GreenSoft), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.tipsTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                s.tips.forEach { Text("•  $it", fontSize = 16.sp) }
            }
        }
        Text(s.footer, fontSize = 13.sp, color = Color(0xFF5A6B5F))
    }
    }
}

@Composable
private fun SettingsDialog(vm: MainViewModel, s: S, onClose: () -> Unit) {
    val l = vm.lang
    val prefCtx = LocalContext.current
    var showStates by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    ToolDialog(tr(l, "Settings", "सेटिंग"), s.closeText, onClose) {
        Text(s.stateLabel + ": " + (vm.state?.let { Region.label(it, l) } ?: s.stateNone), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Green,
            modifier = Modifier.fillMaxWidth().clickable { showStates = true }.padding(vertical = 8.dp))
        Text(s.stateHint, fontSize = 13.sp, color = Color(0xFF444444))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = UiPrefs.bigText, onCheckedChange = {
                UiPrefs.bigText = it
                prefCtx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).edit().putBoolean("big_text", it).apply()
            })
            Text(s.bigTextLabel, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = UiPrefs.simple, onCheckedChange = {
                UiPrefs.simple = it
                prefCtx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).edit().putBoolean("simple_mode", it).apply()
            })
            Text(tr(l, "Simple mode: big sign and voice first", "सरल मोड: पहले बड़ा चिह्न और आवाज़"), fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
        }
        if (vm.history.isNotEmpty()) {
            TextButtonLike(s.historyClear) { vm.clearHistory() }
            TextButtonLike(s.exportHistory) { shareText(prefCtx, s.exportHeader, History.csv(prefCtx)) }
        }
        SmartSetupCard(vm, s)
        VoiceAiCard(vm, l)
        var checks by remember { mutableStateOf<List<MainViewModel.CheckLine>?>(null) }
        var checking by remember { mutableStateOf(false) }
        DsButton(if (checking) tr(l, "Checking…", "जाँच हो रही है…") else tr(l, "Run self-check of all features", "सभी सुविधाओं की स्व-जाँच चलाएँ"), Icons.Rounded.Check,
            { if (!checking) { checking = true; checks = null; vm.selfCheck { r -> checks = r; checking = false } } }, Modifier.fillMaxWidth(), BtnStyle.Soft, 48.dp)
        checks?.forEach { c ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (c.ok) "✓" else "✗", color = if (c.ok) Green else Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Column { Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(c.note, fontSize = 12.sp, color = Ds.Muted) }
            }
        }
        TextButtonLike(s.aboutLink) { showAbout = true }
        Text(s.builtWithLlama, fontSize = 13.sp, color = Color(0xFF444444))
    }
    if (showStates) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStates = false },
            title = { Text(s.stateLabel) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Region.states.forEach { st ->
                        Text(Region.label(st.id, l), fontSize = 18.sp,
                            fontWeight = if (vm.state == st.id) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { vm.chooseState(st.id); showStates = false }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.chooseState(null); showStates = false }) { Text(s.stateClear) } },
        )
    }
    if (showAbout) {
        val notices = remember { try { prefCtx.assets.open("NOTICES.txt").bufferedReader().use { it.readText() } } catch (e: Exception) { "Built with Llama." } }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(s.aboutTitle) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(s.builtWithLlama, fontWeight = FontWeight.Bold); Text(notices, fontSize = 13.sp) } },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { showAbout = false }) { Text(s.closeText) } },
        )
    }
}

/** Offline browser of every disease and pest in the advice database: pick a crop (or pests), then a problem. */
@Composable
private fun LibraryDialogs(vm: MainViewModel, s: S, onClose: () -> Unit) {
    var group by rememberSaveable { mutableStateOf(vm.cropList.firstOrNull() ?: "pest") }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val groups: List<String> = vm.cropList + "pest"
    val ids = Kb.allEnglish().filter { it.classId.substringBefore("__") == group && !it.isHealthy }.map { it.classId }.sorted()
    val shown = open?.let { Kb.get(it, vm.lang) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s.libraryTitle) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groups.forEach { g ->
                        FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = group == g, onClick = { group = g }, label = { Text(if (g == "pest") s.libPests else s.cropName(g), fontSize = 14.sp) })
                    }
                }
                Spacer(Modifier.height(6.dp))
                ids.forEach { id ->
                    Text(Kb.get(id, vm.lang)?.name ?: id, fontSize = 17.sp, modifier = Modifier.fillMaxWidth().clickable { open = id }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onClose) { Text(s.closeText) } },
    )

    if (shown != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(shown.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.typeName(shown.type), color = Green, fontWeight = FontWeight.SemiBold)
                    if (shown.cause.isNotBlank()) { Text(s.cause, fontWeight = FontWeight.SemiBold); Text(shown.cause, fontSize = 14.sp) }
                    if (shown.symptoms.isNotEmpty()) { Text(s.whatYouSee, fontWeight = FontWeight.SemiBold); shown.symptoms.forEach { Text("•  $it", fontSize = 14.sp) } }
                    if (shown.actions.isNotEmpty()) { Text(s.whatToDo, fontWeight = FontWeight.SemiBold); shown.actions.forEach { Text("•  $it", fontSize = 14.sp) } }
                    shown.lookAlikes?.let { Text(s.lookAlike, fontWeight = FontWeight.SemiBold); Text(it, fontSize = 14.sp) }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = null }) { Text(s.closeText) } },
        )
    }
}

/** Name of each voice action, in the screen language, for the "pick an action" list. */
private fun cmdLabel(s: S, l: Lang, c: Cmd): String = when (c) {
    Cmd.CAMERA -> s.takePhoto
    Cmd.GALLERY -> s.pickPhoto
    Cmd.LISTEN -> s.listen
    Cmd.NEW_CHECK -> s.newCheck
    Cmd.BACK -> tr(l, "Back to home", "होम पर वापस")
    Cmd.HELP -> tr(l, "Help", "मदद")
    Cmd.CALL -> s.callExpert
    Cmd.DIARY -> tr(l, "Season diary", "मौसम की डायरी")
    Cmd.WEATHER -> tr(l, "Weather watch", "मौसम की चेतावनी")
    Cmd.STAGES -> tr(l, "Crop stages", "फसल अवस्था")
    Cmd.SETTINGS -> tr(l, "Settings", "सेटिंग")
    Cmd.LIBRARY -> s.libraryButton.substringBefore("(").trim()
    Cmd.PLOT -> s.plotButton.substringBefore("(").trim()
    Cmd.YELLOW -> tr(l, "Yellow leaves", "पीले पत्ते")
}

/**
 * Microphone button: say what you want ("take photo", "help", "फोटो लें") in the chosen language.
 * If the phone's voice typing does not work, a list of the same actions opens, so nothing is ever a dead end.
 */
@Composable
private fun VoiceButton(s: S, lang: Lang, onLang: (Lang) -> Unit, onCmd: (Cmd) -> Unit) {
    var fallback by remember { mutableStateOf<String?>(null) }   // non-null = show the action list, with this note
    var typed by remember { mutableStateOf("") }
    // Used for both what is heard and what is typed: exact words first, then the language switch, then the meaning-matcher.
    val handle: (List<String>) -> Unit = { guesses ->
        val newLang = guesses.firstNotNullOfOrNull { VoiceNav.matchLang(it) }
        val c = VoiceNav.matchAny(lang, guesses) ?: Embedder.matchCommand(guesses)
        if (c != null) { fallback = null; onCmd(c) }
        else if (newLang != null && newLang != lang) { fallback = null; onLang(newLang) }
        else fallback = tr(lang, "I heard: ", "मैंने सुना: ") + guesses.first() + "\n" + tr(lang, "I did not understand. Pick an action:", "समझ नहीं आया। कोई काम चुनें:")
    }
    val start = rememberVoiceTrigger(lang, VoiceNav.examples(lang), onHeard = { guesses -> handle(guesses) }, onProblem = { fallback = it })
    Box(Modifier.size(44.dp).clip(CircleShape).background(Ds.Brand).clickable { start() }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Mic, contentDescription = tr(lang, "Voice command", "आवाज़ से कमांड"), tint = Color.White, modifier = Modifier.size(22.dp))
    }
    val note = fallback
    if (note != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { fallback = null },
            title = { Text(tr(lang, "What do you want to do?", "आप क्या करना चाहते हैं?")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(note, fontSize = 13.sp, color = Ds.Muted, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr(lang, "Or type what you want, in any language", "या लिखें कि आप क्या चाहते हैं, किसी भी भाषा में"), fontSize = 12.sp) },
                        trailingIcon = { androidx.compose.material3.IconButton(onClick = { if (typed.isNotBlank()) { val t = typed; typed = ""; handle(listOf(t)) } }) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Ds.Brand) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    listOf(Cmd.CAMERA, Cmd.GALLERY, Cmd.LISTEN, Cmd.NEW_CHECK, Cmd.PLOT, Cmd.LIBRARY, Cmd.DIARY, Cmd.WEATHER, Cmd.STAGES, Cmd.YELLOW, Cmd.HELP, Cmd.CALL, Cmd.SETTINGS, Cmd.BACK).forEach { c ->
                        Text(cmdLabel(s, lang, c), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ds.Forest,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { fallback = null; onCmd(c) }.padding(horizontal = 8.dp, vertical = 12.dp))
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { fallback = null }) { Text(s.closeText) } },
        )
    }
}

@Composable
private fun LangRow(vm: MainViewModel, s: S) {
    val langCtx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { open = true }.padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Language, contentDescription = s.language, tint = Green, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(vm.lang.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = Ds.Muted, modifier = Modifier.size(18.dp))
    }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(s.language) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Lang.entries.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                vm.lang = l
                                langCtx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).edit().putString("lang", l.code).apply()
                                open = false
                            }.padding(horizontal = 8.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (l.beta) l.label + " (beta)" else l.label, fontSize = 17.sp, fontWeight = if (vm.lang == l) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (vm.lang == l) Icon(Icons.Rounded.Check, contentDescription = null, tint = Green)
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text(s.closeText) } },
        )
    }
}

// ───────────────────────── Result ─────────────────────────

@Composable
private fun ResultScreen(
    vm: MainViewModel, s: S, st: ResultState,
    speaking: Boolean, voiceMissing: Boolean,
    onListen: (String) -> Unit, onRetake: () -> Unit, onAddPhoto: () -> Unit,
) {
    val ctx = LocalContext.current
    val top = st.prediction.top
    // When the model is not sure, show no advice until the farmer picks a possibility or answers a question.
    val hold = !st.prediction.sure && !st.revealed
    val entry = if (hold) null else Kb.get(st.shownClass, vm.lang)
    val sev = st.severity
    val answered = st.answers.any { it.second != null }
    val post: Map<String, Double> = st.engine?.posterior(st.answers) ?: emptyMap()
    val photoConf = top.firstOrNull { it.classId == st.shownClass }?.prob ?: 0f
    val conf = if (answered) (post[st.shownClass] ?: 0.0).toFloat() else photoConf
    val showSeverity = entry != null && entry.hasLesions && sev.ok
    // diseased leaf but almost nothing marked: the finder cannot see this kind of spot, so never claim "0% damage"
    val cantMeasure = showSeverity && entry?.isHealthy == false && sev.percent < Severity.MIN_REAL_PCT
    val measured = showSeverity && !cantMeasure

    // folded sections: the first screen shows only the answer, damage and what to do now
    var oSee by rememberSaveable { mutableStateOf(false) }
    var oDo by rememberSaveable { mutableStateOf(false) }
    var oSpray by rememberSaveable { mutableStateOf(false) }
    var oCheck by rememberSaveable { mutableStateOf(hold || answered) }
    var oMore by rememberSaveable { mutableStateOf(false) }
    var oAsk by rememberSaveable { mutableStateOf(false) }
    var oInfo by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White).clickable { vm.reset() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.newCheck, tint = Ink)
                }
                Spacer(Modifier.weight(1f))
                VoiceButton(s, vm.lang, { l2 -> vm.lang = l2; ctx.getSharedPreferences("cropadvisor", Context.MODE_PRIVATE).edit().putString("lang", l2.code).apply() }) { c ->
                    when (c) {
                        Cmd.LISTEN -> if (entry != null) onListen(spokenText(s, entry, sev, measured))
                        Cmd.NEW_CHECK, Cmd.BACK -> vm.reset()
                        Cmd.CAMERA -> onRetake()
                        Cmd.CALL, Cmd.HELP -> dial(ctx)
                        Cmd.GALLERY, Cmd.PLOT, Cmd.LIBRARY, Cmd.DIARY, Cmd.WEATHER, Cmd.STAGES, Cmd.SETTINGS, Cmd.YELLOW -> {
                            Toast.makeText(ctx, tr(vm.lang, "Go back to the home screen first.", "पहले होम स्क्रीन पर जाएँ।"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                LangRow(vm, s)
            }
        }

        // photo (original or damage overlay)
        item {
            val bmp = if (st.showMarked && sev.overlay != null) sev.overlay else st.photo
            Box {
                Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(28.dp)),
                )
                if (entry != null) Box(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    if (entry.isHealthy) Pill(tr(vm.lang, "Looks healthy", "स्वस्थ दिखता है"), Color.White, Ds.Forest, Icons.Rounded.Check)
                    else Pill(tr(vm.lang, "Problem found", "समस्या मिली"), Color.White, Ds.Danger, Icons.Rounded.Warning)
                }
            }
            if (measured && sev.overlay != null) {
                TextButtonLike(if (st.showMarked) s.showOriginal else s.showMarked) { vm.toggleMarked() }
            }
        }
        if (UiPrefs.simple && entry != null) {
            item { SimpleSummary(vm.lang, entry, if (measured) sev.band else null, { onListen(spokenText(s, entry, sev, measured)) }, speaking) }
        }

        if (st.quality.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = AmberSoft), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.qualityTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF7A5900))
                    st.quality.forEach { Text("•  " + s.quality(it), fontSize = 14.sp) }
                    Text(s.qualityNote, fontSize = 13.sp, color = Color(0xFF444444))
                }
            }
        }

        // headline
        item {
            val sure = if (answered) conf >= 0.60f else st.prediction.sure
            // green only for a healthy leaf; a problem goes from yellow (unsure) to red (very sure) with the confidence
            val healthyOk = entry?.isHealthy == true && !hold
            val tConf = ((conf - 0.5f) / 0.5f).coerceIn(0f, 1f)
            val cardColor = if (healthyOk) GreenSoft else androidx.compose.ui.graphics.lerp(AmberSoft, Color(0xFFFFCDD2), tConf)
            val barColor = if (healthyOk) Green else androidx.compose.ui.graphics.lerp(Amber, Red, tConf)
            Card(
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!hold) {
                        Text(if (sure && !st.fromPossibility) s.sureTitle else s.otherPossibilities, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ds.Muted)
                    } else {
                        Text(if (st.wide) s.wideTitle else s.notSureTitle, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Ds.WarnInk)
                        Text(if (st.wide) s.wideBody else s.notSureBody, fontSize = 15.sp)
                        val fam = if (st.wide) null else Family.shared(top)
                        if (fam != null) Text(s.familyNote(fam), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (entry != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(s.cropName(entry.crop), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ds.Muted)
                                Text(entry.name, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                                Text(s.typeName(entry.type), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (healthyOk) Green else barColor)
                            }
                            ScoreRing(conf, barColor, "${(conf * 100).toInt()}%")
                        }
                        Text(
                            "${s.confidence}: ${s.confLabel(conf)}" + if (answered) "   (${s.photoAlone((photoConf * 100).toInt())})" else "",
                            fontSize = 13.sp, color = Ds.Muted,
                        )
                        if (!entry.isHealthy && st.engine != null) Text(s.verdict(conf), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (!entry.isHealthy) {
                            val mates = Confusable.partners(st.shownClass).mapNotNull { Kb.get(it, vm.lang)?.name }
                            if (mates.isNotEmpty()) Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Ds.WarnInk, modifier = Modifier.size(18.dp))
                                Text(s.mixedUp(mates.joinToString(", ")), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Ds.WarnInk)
                            }
                        }
                    }
                    if (st.fromPossibility) Text(s.possibilityNote, fontSize = 13.sp, color = Ds.WarnInk)
                    if (st.answerNote != null) Text(st.answerNote, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── the short answer: damage + what to do now, then the main buttons ──
        if (entry != null) {
            // severity + spray-or-wait
            if (entry.isHealthy) {
                item { InfoCard(s.healthyNote) }
            } else {
                item {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(s.severityTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                            if (cantMeasure) {
                                Text(s.cantMeasureTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A5900))
                                Text(s.cantMeasureBody, fontSize = 15.sp)
                                Text(s.decisionTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
                                Text(s.cantMeasureAct, fontSize = 16.sp)
                            } else if (measured) {
                                Text(s.band(sev.band), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = bandColor(sev.band))
                                Text(s.bandRange(sev.band), fontSize = 14.sp, color = Color(0xFF444444))
                                val order = listOf(Band.VERY_LIGHT, Band.MILD, Band.MODERATE, Band.SEVERE)
                                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    order.forEach { b ->
                                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                                            .background(if (order.indexOf(b) <= order.indexOf(sev.band)) bandColor(b) else Color(0xFFE0E0E0)))
                                    }
                                }
                                Text(s.severityNote, fontSize = 13.sp, color = Color(0xFF444444))
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
        }
        // not-sure actions + possibilities
        if (!st.prediction.sure) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DsButton(s.retake, Icons.Rounded.CameraAlt, onRetake, Modifier.fillMaxWidth(), BtnStyle.Primary, 54.dp)
                    DsButton(s.callExpert, Icons.Rounded.Call, { dial(ctx) }, Modifier.fillMaxWidth(), BtnStyle.Outline, 50.dp)
                }
            }
        }
        if (top.count { it.prob >= 0.02f } > 1) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.otherPossibilities, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                val chips: List<Guess> = if (answered)
                    post.entries.sortedByDescending { it.value }.take(3).map { Guess(it.key, it.value.toFloat()) }
                else top
                chips.filterIndexed { i, g -> i == 0 || g.prob >= 0.02f }.forEach { g ->
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


        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry != null) {
                    DsButton(if (speaking) s.stop else s.listen, Icons.AutoMirrored.Rounded.VolumeUp, { onListen(spokenText(s, entry, sev, measured)) }, Modifier.fillMaxWidth(), BtnStyle.Primary, 58.dp)
                    if (voiceMissing) Text(s.ttsMissing, fontSize = 13.sp, color = Red)
                }
                DsButton(s.newCheck, Icons.Rounded.Refresh, { vm.reset() }, Modifier.fillMaxWidth(), BtnStyle.Soft, 52.dp)
                DsButton(s.callExpert, Icons.Rounded.Call, { dial(ctx) }, Modifier.fillMaxWidth(), BtnStyle.Outline, 50.dp)
                Text(s.footer, fontSize = 12.sp, color = Ds.Muted, modifier = Modifier.padding(top = 4.dp))
            }
        }

        // ── everything else is folded away: tap a heading to read it ──
        item { Spacer(Modifier.height(4.dp)); SectionTitle(tr(vm.lang, "More details", "और जानकारी")) }

        if (entry != null && (entry.symptoms.isNotEmpty() || entry.cause.isNotBlank() || entry.lookAlikes != null)) {
            accHeader(Icons.Rounded.Search, s.whatYouSee, oSee) { oSee = !oSee }
            if (oSee) {
                section(s.whatYouSee, entry.symptoms)
                if (entry.cause.isNotBlank() && entry.type != "healthy" && !entry.cause.lowercase().contains("not stated")) item { Labeled(s.cause, entry.cause) }
                entry.lookAlikes?.let { la -> item { Labeled(s.lookAlike, la) } }
                if (!entry.isHealthy) item { LookalikeCard(vm.lang, entry, st.prediction.ranked) }
            }
        }

        if (entry != null) {
            accHeader(Icons.Rounded.Build, s.whatToDo, oDo) { oDo = !oDo }
            if (oDo) {
                section(s.whatToDo, entry.actions)
                if (!entry.isHealthy) {
                    entry.whenToAct?.let { w -> item { Labeled(s.whenTitle, w) } }
                    item { FriendsCard(vm.lang, entry.classId) }
                    item { LadderCard(vm.lang, entry.treatments.isNotEmpty(), Organic.of(st.shownClass, Lang.EN) != null) }
                    Organic.of(st.shownClass, vm.lang)?.let { oi -> item { OrganicCard(s, oi) } }
                    item { TreatmentCard(s, entry) }
                    item { SafetyCard(s) }
                }
            }
        }

        if (entry != null && !entry.isHealthy) {
            accHeader(Icons.Rounded.CalendarMonth, tr(vm.lang, "Spray or wait, and cost", "छिड़काव या इंतज़ार, और खर्च"), oSpray) { oSpray = !oSpray }
            if (oSpray) {
                item { SprayPlanCard(vm.lang, entry, if (measured) sev.band else null) }
                item { CostCard(vm.lang) }
                item { ShareCardButton(vm.lang, entry, st.photo, if (measured) sev.band else null) }
            }
        }

        if (st.engine != null) {
            accHeader(Icons.Rounded.QuestionAnswer, s.askTitle, oCheck) { oCheck = !oCheck }
            if (oCheck) { item { CheckSection(vm, s, st, post) } }
        }

        accHeader(Icons.Rounded.PhotoCamera, s.morePhotoTitle, oMore) { oMore = !oMore }
        if (oMore) {
        val prevItem = st.prev
        if (entry != null && prevItem != null && !entry.isHealthy && st.shownClass == prevItem.classId) item {
            val days = ((System.currentTimeMillis() - prevItem.ts) / 86_400_000L).toInt()
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.followTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(if (measured && prevItem.damage != null) s.followBody(days, prevItem.damage, Math.round(sev.percent)) else s.followNoDamage, fontSize = 15.sp)
                    if (measured && prevItem.damage != null && days >= 2) {
                        val perWeek = (Math.round(sev.percent) - prevItem.damage) * 7f / days
                        if (Math.abs(perWeek) >= 2f) Text(s.followRate(perWeek), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (perWeek > 0) Red else Green)
                    }
                    Text(s.followNote, fontSize = 13.sp, color = Color(0xFF444444))
                }
            }
        }

        if (entry != null && !entry.isHealthy && Outcomes.betterShare(Outcomes.list(ctx), entry.classId) != null) item { OutcomeStat(vm.lang, entry.classId) }

        item {
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(s.morePhotoTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(s.morePhotoBody, fontSize = 13.sp, color = Color(0xFF444444))
                    if (st.extraPhotos > 0) Text(s.morePhotoCount(st.extraPhotos), fontSize = 13.sp, color = Color(0xFF2E5E31))
                    OutlinedButton(onClick = onAddPhoto, modifier = Modifier.fillMaxWidth()) { Text(s.morePhotoButton, fontSize = 15.sp) }
                    val fp = st.foundPest
                    if (fp != null) {
                        val pe = Kb.get(fp, vm.lang)
                        Text(s.pestFoundTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Green)
                        Text(pe?.name ?: fp, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { vm.viewPossibility(fp) }) { Text(s.seePest) }
                    }
                }
            }
        }
            if (entry != null) {
                val causes = Causes.of(st.shownClass)
                if (causes.isNotEmpty()) item { CauseCard(vm, s, causes) }
            }
        }

        if (entry != null && !entry.isHealthy) {
            accHeader(Icons.Rounded.Forum, s.askHeading, oAsk) { oAsk = !oAsk }
            if (oAsk) item { AskSection(vm, s) }
        }

        if (entry != null) {
            accHeader(Icons.Rounded.Info, s.sourcesTitle, oInfo) { oInfo = !oInfo }
            if (oInfo) {
                if (!Kb.hasHindi(entry.classId) && vm.lang == Lang.HI) item { InfoCard(s.hindiMissing) }
                if (!entry.isHealthy) item { Labeled(s.regionNote, s.regionText(entry)) }
            item {
                OutlinedButton(onClick = { shareSummary(ctx, s, entry, conf, if (measured) sev else null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(s.shareReport, fontSize = 15.sp)
                }
            }
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
                if (st.histTs != null) item { FeedbackCard(vm, s, st.feedback) }
            }
        }

    }
}

/** A tappable heading that opens or folds the section below it. */
private fun androidx.compose.foundation.lazy.LazyListScope.accHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, open: Boolean, onToggle: () -> Unit) {
    item {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { onToggle() }.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(icon, GreenSoft, Green, 40.dp, 22.dp)
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = Ds.Muted)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    item {
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.askHeading, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

            // quick questions answered straight from the verified advice (work on every phone)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = false, onClick = { vm.quick(Topic.SYMPTOMS) }, label = { Text(s.quickSymptoms, fontSize = 13.sp) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = false, onClick = { vm.quick(Topic.ACTIONS) }, label = { Text(s.quickActions, fontSize = 13.sp) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = false, onClick = { vm.quick(Topic.TREATMENT) }, label = { Text(s.quickTreat, fontSize = 13.sp) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = false, onClick = { vm.quick(Topic.TIMING) }, label = { Text(s.quickWhen, fontSize = 13.sp) })
            }

            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(s.askHint) }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
            )
            var micNote by remember { mutableStateOf<String?>(null) }
            val startMic = rememberVoiceTrigger(vm.lang, tr(vm.lang, "your question", "अपना सवाल"), onHeard = { g -> text = g.first(); micNote = null }, onProblem = { micNote = it })
            OutlinedButton(onClick = { startMic() }, modifier = Modifier.fillMaxWidth()) { Text(s.micButton, fontSize = 15.sp) }
            micNote?.let { Text(it, fontSize = 13.sp, color = Red) }
            Text(s.micNote, fontSize = 13.sp, color = Color(0xFF444444))
            Button(onClick = { vm.askQuestion(text) }, modifier = Modifier.fillMaxWidth()) { Text(s.askButton, fontSize = 16.sp) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = vm.smartOn, onCheckedChange = { vm.switchSmart(it) })
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
                            fontSize = 13.sp, color = Color(0xFF2E5E31), fontWeight = FontWeight.SemiBold,
                        )
                        Text(a.text, fontSize = 16.sp)
                        a.note?.let { Text(it, fontSize = 13.sp, color = Color(0xFF444444)) }
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
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AmberSoft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(s.treatTitle, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (shown.isEmpty()) {
                Text(s.noTreat, fontSize = 16.sp)
            } else {
                shown.distinctBy { it.activeIngredient }.forEach { t ->
                    Text("•  ${t.activeIngredient}", fontSize = 16.sp)
                    val tag = when {
                        t.cibrcStatus.startsWith("name_on_cibrc") -> s.cibrcOk
                        t.cibrcStatus.startsWith("not_found") || t.cibrcStatus.startsWith("group_name") -> s.cibrcAsk
                        else -> null
                    }
                    tag?.let { Text(it, fontSize = 13.sp, color = Color(0xFF5D4300), modifier = Modifier.padding(start = 14.dp)) }
                }
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


/** "Check to be sure": one adaptive question at a time; every answer re-ranks the diagnosis. */
@Composable
private fun CheckSection(vm: MainViewModel, s: S, st: ResultState, post: Map<String, Double>) {
    val eng = st.engine ?: return
    val next = eng.next(st.answers)
    if (next == null && st.answers.isEmpty()) return
    val answered = st.answers.any { it.second != null }
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(s.askTitle, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            if (answered) {
                val tr = eng.trace(st.answers)
                val te = tr?.let { Kb.get(it.classId, vm.lang) }
                if (tr != null && te != null && !te.isHealthy) {
                    Text(s.traceLine(s.cropName(te.crop) + " — " + te.name, tr.confirmed, tr.total), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Green)
                }
            }
            if (next != null) {
                Text(s.checkProgress(st.answers.size + 1), fontSize = 13.sp, color = Color(0xFF444444))
                Text(next.text(vm.lang), fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.answerCheck(next.id, true) }) { Text(s.answerYes) }
                    Button(onClick = { vm.answerCheck(next.id, false) }) { Text(s.answerNo) }
                    OutlinedButton(onClick = { vm.answerCheck(next.id, null) }) { Text(s.answerSkip) }
                }
            } else {
                val topId = post.maxByOrNull { it.value }?.key
                val e = topId?.let { Kb.get(it, vm.lang) }
                val pct = ((post[topId] ?: 0.0) * 100).toInt()
                val msg = when {
                    !answered || e == null -> s.checkUnsure
                    e.isHealthy && pct >= 70 -> s.checkNoDisease(s.cropName(e.crop))
                    pct >= 80 -> s.checkLikely("${s.cropName(e.crop)} — ${e.name}", pct)
                    else -> s.checkUnsure
                }
                Text(msg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            if (answered) {
                val stillLeft = post.entries.count { it.value >= 0.03 }
                Text(s.treeLeft(stillLeft, eng.candidateCount), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Green)
                post.entries.filter { it.value >= 0.03 }.sortedByDescending { it.value }.take(5).forEach { (id, v) ->
                    val e = Kb.get(id, vm.lang)
                    val name = if (e != null) s.cropName(e.crop) + " — " + e.name else id
                    Column {
                        Text("$name  ${(v * 100).toInt()}%", fontSize = 14.sp)
                        LinearProgressIndicator(progress = { v.toFloat() }, modifier = Modifier.fillMaxWidth(), color = if (v >= 0.6) Green else Amber)
                    }
                }
            }
            st.answers.forEach { (fid, a) ->
                val f = Checks.feature(fid)
                if (f != null) Text(
                    "• ${f.text(vm.lang)}  →  " + (when (a) { true -> s.answerYes; false -> s.answerNo; null -> s.answerSkip }),
                    fontSize = 13.sp, color = Color(0xFF444444),
                )
            }
            if (st.answers.isNotEmpty()) OutlinedButton(onClick = { vm.undoCheck() }) { Text(s.undoLast) }
            Text(s.evidenceNote, fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}


/** Pests that cause or spread the problem on screen; tapping opens that pest's own advice when we have it. */
@Composable
private fun CauseCard(vm: MainViewModel, s: S, causes: List<CauseLink>) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.causeTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            causes.forEach { c ->
                Text(s.causeRel(c.rel), fontSize = 13.sp, color = Color(0xFF444444))
                Text(if (vm.lang == Lang.HI && c.nameHi.isNotBlank()) c.nameHi else I18n.t(vm.lang, c.nameEn), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (c.pestClass != null) {
                    OutlinedButton(onClick = { vm.viewPossibility(c.pestClass) }) { Text(s.seePest) }
                }
            }
            Text(s.causeNote, fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}


private fun shareSummary(ctx: Context, s: S, e: Entry, conf: Float, sev: Severity.Result?) {
    val sb = StringBuilder()
    sb.append(s.shareHeader).append("\n")
    sb.append("${s.cropName(e.crop)} — ${e.name} (${(conf * 100).toInt()}%)\n")
    if (sev != null) sb.append("${s.severityTitle}: ~${Math.round(sev.percent)}%\n")
    e.actions.take(3).forEach { sb.append("• ").append(it).append("\n") }
    sb.append("\n").append(s.shareFooter)
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, sb.toString()) }
    try { ctx.startActivity(Intent.createChooser(send, s.shareReport)) } catch (_: Exception) {}
}

@Composable
private fun OrganicCard(s: S, info: OrganicInfo) {
    val ctx = LocalContext.current
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (info.options.isNotEmpty()) {
                Text(s.organicTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                info.options.forEach { Text("•  $it", fontSize = 15.sp) }
            }
            if (info.favoured.isNotBlank()) {
                Text(s.favouredTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                Text(info.favoured, fontSize = 15.sp)
            }
            Text(s.organicNote, fontSize = 13.sp, color = Color(0xFF444444))
            info.sources.forEach { u ->
                Text(s.organicSource + ": " + u.removePrefix("https://").removePrefix("http://").substringBefore('/'), fontSize = 13.sp, color = Color(0xFF1565C0),
                    textDecoration = TextDecoration.Underline, modifier = Modifier.clickable { openLink(ctx, u) })
            }
        }
    }
}

@Composable
private fun SafetyCard(s: S) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AmberSoft)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(s.safetyTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            s.safetyLines.forEach { Text("•  $it", fontSize = 14.sp) }
        }
    }
}

@Composable
private fun FeedbackCard(vm: MainViewModel, s: S, fb: Int) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.feedbackAsk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (fb == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.setFeedback(true) }) { Text(s.feedbackYes) }
                    OutlinedButton(onClick = { vm.setFeedback(false) }) { Text(s.feedbackNo) }
                }
            } else {
                Text(s.feedbackThanks, fontSize = 14.sp, color = Color(0xFF2E5E31))
            }
        }
    }
}

@Composable
private fun PlotScreen(vm: MainViewModel, s: S, st: PlotState) {
    val firstProblem = st.rows.indexOfFirst { Kb.get(it.classId, vm.lang)?.isHealthy == false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LangRow(vm, s)
        Text(s.plotTitle, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(s.plotSummary(st.total, st.clear), fontSize = 16.sp)
        if (st.rows.isEmpty()) InfoCard(s.plotNone)
        st.rows.forEachIndexed { i, r ->
            val e = Kb.get(r.classId, vm.lang)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (i == firstProblem) Text(s.plotMost, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Green)
                    Text(if (e != null) "${s.cropName(e.crop)} — ${e.name}" else r.classId, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(s.plotRow(r.count, st.clear) + (r.avgDamage?.let { ", " + s.plotAvg(it) } ?: ""), fontSize = 15.sp)
                    OutlinedButton(onClick = { vm.analyse(r.sample, save = false) }) { Text(s.plotSeeAdvice) }
                }
            }
        }
        if (st.rows.isNotEmpty()) PlotShareCard(vm.lang, st) { c -> s.cropName(c) }
        Text(s.plotNote, fontSize = 13.sp, color = Color(0xFF444444))
        Button(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(s.backHome, fontSize = 17.sp) }
        Text(s.footer, fontSize = 13.sp, color = Color(0xFF444444))
    }
}

private fun shareText(ctx: Context, title: String, body: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, body) }
    try { ctx.startActivity(Intent.createChooser(send, title)) } catch (_: Exception) {}
}

/** Optional download that lets voice and typed commands be understood by meaning in all 13 languages. */
@Composable
private fun VoiceAiCard(vm: MainViewModel, l: Lang) {
    val installed = vm.voiceAiInstalled() || vm.voiceAi is DlState.Done
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(l, "Understand any language (voice and typing)", "किसी भी भाषा को समझे (आवाज़ और लिखकर)"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(tr(l, "A small model (about 120 MB, one time, works offline afterwards). Voice and typed commands are understood by meaning, not only by exact words, in all 13 languages. Optional: the word lists work without it.",
                "एक छोटा मॉडल (लगभग 120 MB, एक बार, बाद में बिना इंटरनेट)। आवाज़ और लिखे कमांड सिर्फ़ सटीक शब्दों से नहीं, मतलब से समझे जाते हैं, सभी 13 भाषाओं में। वैकल्पिक है।"), fontSize = 13.sp, color = Color(0xFF444444))
            when (val st = vm.voiceAi) {
                is DlState.Idle -> if (installed) Text(tr(l, "Installed", "इंस्टॉल है"), fontSize = 14.sp, color = Green, fontWeight = FontWeight.SemiBold)
                is DlState.Checking -> Text(tr(l, "Checking the server…", "सर्वर जाँच रहे हैं…"), fontSize = 14.sp)
                is DlState.Running -> {
                    val frac = if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f
                    Text("${st.done / (1024 * 1024)} / ${st.total / (1024 * 1024)} MB", fontSize = 14.sp)
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(), color = Green)
                }
                is DlState.Done -> Text(tr(l, "Installed", "इंस्टॉल है"), fontSize = 14.sp, color = Green, fontWeight = FontWeight.SemiBold)
                is DlState.Failed -> Text(st.message, fontSize = 14.sp, color = Red)
            }
            val busy = vm.voiceAi is DlState.Running || vm.voiceAi is DlState.Checking
            if (busy) OutlinedButton(onClick = { vm.cancelVoiceAi() }) { Text(tr(l, "Cancel", "रद्द करें")) }
            else if (!installed) Button(onClick = { vm.downloadVoiceAi() }, modifier = Modifier.fillMaxWidth()) { Text(tr(l, "Download (Wi-Fi recommended)", "डाउनलोड करें (Wi-Fi बेहतर)")) }
        }
    }
}

@Composable
private fun SmartSetupCard(vm: MainViewModel, s: S) {
    androidx.compose.runtime.LaunchedEffect(vm.dl) { vm.refreshDevice() }
    val d = vm.device
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(s.dlTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (d != null) {
                Text(s.dlPhone(d.totalMb, d.availMb, d.freeStorageMb), fontSize = 13.sp, color = Color(0xFF444444))
                Text(s.dlRecommended(DeviceProfile.wanted(d)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Green)
            }
            Text(s.dlInstalled(vm.installed(LlmTier.FULL), vm.installed(LlmTier.LITE)), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = vm.forceTier == null, onClick = { vm.forceTier = null }, label = { Text(s.dlAuto, fontSize = 13.sp) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = vm.forceTier == LlmTier.FULL, onClick = { vm.forceTier = LlmTier.FULL }, label = { Text(s.dlFull, fontSize = 13.sp) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = vm.forceTier == LlmTier.LITE, onClick = { vm.forceTier = LlmTier.LITE }, label = { Text(s.dlLite, fontSize = 13.sp) })
            }
            OutlinedTextField(
                value = vm.server, onValueChange = { vm.changeServer(it) }, singleLine = true,
                label = { Text(s.dlServerLabel, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.hub, onValueChange = { vm.changeHub(it) }, singleLine = true,
                label = { Text(s.hubLabel, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth(),
            )
            Text(s.dlWifi, fontSize = 13.sp, color = Color(0xFF444444))
            when (val st = vm.dl) {
                is DlState.Idle -> {}
                is DlState.Checking -> Text(s.dlChecking, fontSize = 14.sp)
                is DlState.Running -> {
                    val frac = if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f
                    Text(s.dlProgress(st.label, st.done / (1024 * 1024), st.total / (1024 * 1024)), fontSize = 14.sp)
                    LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(), color = Green)
                }
                is DlState.Done -> Text(s.dlDone(st.label), fontSize = 14.sp, color = Color(0xFF2E5E31), fontWeight = FontWeight.SemiBold)
                is DlState.Failed -> Text(s.dlFailed(st.message), fontSize = 14.sp, color = Red)
            }
            val busy = vm.dl is DlState.Running || vm.dl is DlState.Checking
            if (busy) OutlinedButton(onClick = { vm.cancelDownload() }) { Text(s.dlCancel) }
            else Button(onClick = { vm.startDownload() }, enabled = vm.server.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(s.dlStart) }
        }
    }
}

/** First launch after the welcome page: fetch the Llama build that suits this phone, like a game downloading extra data. */
@Composable
private fun SetupScreen(vm: MainViewModel, s: S, onDone: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshDevice() }
    val d = vm.device
    val want = if (d != null) DeviceProfile.wanted(d) else null
    val ready = vm.dl is DlState.Done
    val running = vm.dl is DlState.Running || vm.dl is DlState.Checking
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LangRow(vm, s)
        Text(s.setupTitle, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Green)
        Text(s.setupBody, fontSize = 17.sp)
        if (d != null) {
            Text(s.dlPhone(d.totalMb, d.availMb, d.freeStorageMb), fontSize = 14.sp, color = Color(0xFF444444))
            Text(s.dlRecommended(want), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Green)
        }
        OutlinedTextField(
            value = vm.server, onValueChange = { vm.changeServer(it) }, singleLine = true,
            label = { Text(s.dlServerLabel, fontSize = 13.sp) }, modifier = Modifier.fillMaxWidth(),
        )
        Text(s.dlWifi, fontSize = 13.sp, color = Color(0xFF444444))
        when (val st = vm.dl) {
            is DlState.Idle -> {}
            is DlState.Checking -> Text(s.dlChecking, fontSize = 15.sp)
            is DlState.Running -> {
                val frac = if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f
                Text(s.dlProgress(st.label, st.done / (1024 * 1024), st.total / (1024 * 1024)), fontSize = 15.sp)
                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth(), color = Green)
            }
            is DlState.Done -> Text(s.dlDone(st.label), fontSize = 15.sp, color = Color(0xFF2E5E31), fontWeight = FontWeight.SemiBold)
            is DlState.Failed -> Text(s.dlFailed(st.message), fontSize = 15.sp, color = Red)
        }
        if (ready) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) { Text(s.welcomeContinue, fontSize = 18.sp) }
        } else {
            if (want != null) {
                if (running) OutlinedButton(onClick = { vm.cancelDownload() }, modifier = Modifier.fillMaxWidth()) { Text(s.dlCancel) }
                else Button(onClick = { vm.startDownload() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(20.dp)) { Text(s.setupDownload, fontSize = 18.sp) }
            } else Text(s.smartHub, fontSize = 15.sp)
            if (!running) OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(20.dp)) { Text(s.setupSkip, fontSize = 16.sp) }
            Text(s.setupSkipNote, fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}
