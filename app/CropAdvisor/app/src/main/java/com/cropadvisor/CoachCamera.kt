package com.cropadvisor

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

object CoachState { var open by mutableStateOf(false) }

/** Live camera with plain tips while you aim ("too dark", "hold steady"). Only brightness and sharpness are measured, on this phone. */
@Composable
fun CoachCamera(lang: Lang, onPhoto: (Uri) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    BackHandler { onClose() }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) req.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text(tr(lang, "The camera needs permission. You can also use Take photo, which opens your phone camera.", "कैमरे को अनुमति चाहिए। आप 'फोटो लें' भी इस्तेमाल कर सकते हैं, जो फोन का कैमरा खोलता है।"), color = Color.White, fontSize = 16.sp)
            Button(onClick = { req.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 16.dp)) { Text(tr(lang, "Allow camera", "कैमरा अनुमति दें")) }
            OutlinedButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) { Text(tr(lang, "Back", "वापस")) }
        }
        return
    }

    // 0 none yet, 1 good, 2 dark, 3 bright, 4 blurry
    var hint by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    val exec = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { exec.shutdown() } }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val pv = PreviewView(c)
                val fut = ProcessCameraProvider.getInstance(c)
                fut.addListener({
                    try {
                        val provider = fut.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        var last = 0L
                        var prev = 0
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(exec) { img ->
                            try {
                                val now = System.currentTimeMillis()
                                if (now - last >= 400) {
                                    last = now
                                    val p = img.planes[0]; val buf = p.buffer; val rs = p.rowStride; val ps = p.pixelStride
                                    val w = img.width; val h = img.height
                                    var sum = 0L; var n = 0
                                    for (j in 0 until 40) for (i in 0 until 40) {
                                        sum += buf.get((j * h / 40) * rs + (i * w / 40) * ps).toInt() and 0xFF; n++
                                    }
                                    val mean = sum.toDouble() / n
                                    // sharpness: Laplacian variance on a central window at full resolution
                                    val cw = minOf(160, w - 4); val ch = minOf(160, h - 4)
                                    val x0 = (w - cw) / 2; val y0 = (h - ch) / 2
                                    var s = 0.0; var s2 = 0.0; var m = 0
                                    for (y in y0 until y0 + ch step 2) for (x in x0 until x0 + cw step 2) {
                                        val c0 = buf.get(y * rs + x * ps).toInt() and 0xFF
                                        val l = 4.0 * c0 - (buf.get(y * rs + (x - 1) * ps).toInt() and 0xFF) - (buf.get(y * rs + (x + 1) * ps).toInt() and 0xFF) -
                                            (buf.get((y - 1) * rs + x * ps).toInt() and 0xFF) - (buf.get((y + 1) * rs + x * ps).toInt() and 0xFF)
                                        s += l; s2 += l * l; m++
                                    }
                                    val variance = s2 / m - (s / m) * (s / m)
                                    val now2 = when { mean < 55 -> 2; mean > 225 -> 3; variance < 30.0 -> 4; else -> 1 }
                                    if (now2 == prev) hint = now2   // show a tip only when two checks agree
                                    prev = now2
                                }
                            } catch (t: Throwable) { /* tips are optional */ } finally { img.close() }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                    } catch (t: Throwable) { /* camera not available: the user can go back and use Take photo */ }
                }, ContextCompat.getMainExecutor(c))
                pv
            },
        )
        val (msg, col) = when (hint) {
            1 -> tr(lang, "Good. Fill the frame with one leaf, then tap the button.", "बढ़िया। फ्रेम में एक पत्ता रखें, फिर बटन दबाएँ।") to Color(0xFF2E7D32)
            2 -> tr(lang, "Too dark. Move to daylight.", "बहुत अँधेरा है। दिन की रोशनी में जाएँ।") to Color(0xFFB71C1C)
            3 -> tr(lang, "Too bright. Move out of the strong sun or shade the leaf.", "बहुत तेज़ रोशनी है। तेज़ धूप से हटें या पत्ते पर छाया करें।") to Color(0xFFB71C1C)
            4 -> tr(lang, "Not sharp. Hold steady, move a little closer and wait for focus.", "साफ नहीं है। फोन स्थिर रखें, थोड़ा पास जाएँ और फोकस का इंतज़ार करें।") to Color(0xFF8A6D00)
            else -> tr(lang, "Point at one leaf.", "एक पत्ते की ओर करें।") to Color(0xFF444444)
        }
        Text(msg, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.statusBarsPadding().padding(16.dp).fillMaxWidth().background(col, RoundedCornerShape(12.dp)).padding(12.dp))
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text(tr(lang, "Back", "वापस"), color = Color.White) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
                    val f = File(dir, "leaf_${System.currentTimeMillis()}.jpg")
                    capture.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(r: ImageCapture.OutputFileResults) { onPhoto(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)) }
                        override fun onError(e: ImageCaptureException) {
                            busy = false
                            android.widget.Toast.makeText(ctx, tr(lang, "Could not take the photo.", "फोटो नहीं ली जा सकी।"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                },
                modifier = Modifier.weight(2f),
            ) { Text(tr(lang, "Take photo", "फोटो लें"), fontSize = 18.sp) }
        }
    }
}
