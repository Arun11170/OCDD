package com.cropadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Picture-and-voice summary for people who read little: one big sign, the name, the plain answer and a big Listen button. */
@Composable
fun SimpleSummary(lang: Lang, e: Entry, band: Band?, onListen: () -> Unit, speaking: Boolean) {
    val plan = SprayPlan.plan(lang, e.type, band, null, 0, e.treatments.isNotEmpty())
    val (icon, col) = when {
        e.isHealthy -> "✅" to Color(0xFF2E7D32)
        plan.verdict == SprayPlan.Verdict.NO_ACTION -> "🟢" to Color(0xFF2E7D32)
        plan.verdict == SprayPlan.Verdict.WAIT_RECHECK -> "🟡" to Color(0xFF8A6D00)
        else -> "🔴" to Color(0xFFB71C1C)
    }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(icon, fontSize = 64.sp)
            Text(e.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(if (e.isHealthy) tr(lang, "The leaf looks healthy.", "पत्ता स्वस्थ दिखता है।") else SprayPlan.title(lang, plan.verdict),
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = col, textAlign = TextAlign.Center)
            Button(onClick = onListen, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
                Text((if (speaking) "⏹  " else "🔊  ") + (if (speaking) tr(lang, "Stop", "रोकें") else tr(lang, "Listen", "सुनें")), fontSize = 22.sp)
            }
        }
    }
}
