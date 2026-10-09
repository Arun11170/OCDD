package com.cropadvisor

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Numbers a farmer may need, kept on the phone. Checked: Kisan Call Centre (free), crop-loss reporting 14447 within
 * 72 hours (PMFBY; SBI General claim process page), AIIMS poison helpline 1800-116-117 (aiims.edu NPIC page).
 * The phone's dialler opens with the number filled in; nothing is called until the farmer presses call.
 */
private fun dial(ctx: Context, number: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
}

@Composable
fun HelpCard(lang: Lang) {
    val ctx = LocalContext.current
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Help and helplines", "मदद और हेल्पलाइन"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedButton(onClick = { dial(ctx, "1800-180-1551") }, modifier = Modifier.fillMaxWidth()) {
                Text(tr(lang, "Kisan Call Centre 1800-180-1551 (free)", "किसान कॉल सेंटर 1800-180-1551 (मुफ़्त)"), fontSize = 15.sp)
            }
            Text(tr(lang, "Ask a farm expert about crops, pests and schemes.", "फसल, कीट और योजनाओं के बारे में कृषि विशेषज्ञ से पूछें।"), fontSize = 13.sp, color = Color(0xFF444444))
            OutlinedButton(onClick = { dial(ctx, "14447") }, modifier = Modifier.fillMaxWidth()) {
                Text(tr(lang, "Crop loss report 14447", "फसल नुकसान की सूचना 14447"), fontSize = 15.sp)
            }
            Text(tr(lang, "If your crop is insured (PMFBY), report a loss within 72 hours of the event. You can also tell your insurance company, bank or agriculture office.", "अगर आपकी फसल का बीमा है (PMFBY), तो नुकसान होने के 72 घंटे के भीतर सूचना दें। आप बीमा कंपनी, बैंक या कृषि कार्यालय को भी बता सकते हैं।"), fontSize = 13.sp, color = Color(0xFF444444))
            OutlinedButton(onClick = { dial(ctx, "1800-116-117") }, modifier = Modifier.fillMaxWidth()) {
                Text(tr(lang, "Poison helpline 1800-116-117 (24 hours)", "ज़हर हेल्पलाइन 1800-116-117 (24 घंटे)"), fontSize = 15.sp)
            }
            Text(tr(lang, "Soil test and local advice: ask at your Krishi Vigyan Kendra (KVK) or block agriculture office.", "मिट्टी की जाँच और स्थानीय सलाह: अपने कृषि विज्ञान केंद्र (KVK) या ब्लॉक कृषि कार्यालय में पूछें।"), fontSize = 13.sp)
        }
    }
}
