package com.cropadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Is spraying worth it?" Pure arithmetic on the farmer's own two numbers. The app knows no prices and guesses no yield loss:
 * it only shows how much of the crop's value a spray must save before it pays for itself.
 */
@Composable
fun CostCard(lang: Lang) {
    var value by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFBE9E7))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Is spraying worth the cost?", "क्या छिड़काव का खर्च वसूल होगा?"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedTextField(
                value = value, onValueChange = { value = it.filter { c -> c.isDigit() }.take(9) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                label = { Text(tr(lang, "Expected sale value of this crop (₹)", "इस फसल की अपेक्षित बिक्री कीमत (₹)")) },
            )
            OutlinedTextField(
                value = cost, onValueChange = { cost = it.filter { c -> c.isDigit() }.take(9) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                label = { Text(tr(lang, "Cost of spraying: product + labour (₹)", "छिड़काव का खर्च: उत्पाद + मज़दूरी (₹)")) },
            )
            val v = value.toLongOrNull(); val c = cost.toLongOrNull()
            if (v != null && c != null && v > 0) {
                val pct = "%.1f".format(c * 100.0 / v)
                Text(
                    tr(lang, "The spray pays for itself only if it saves at least $pct% of the crop's value.", "छिड़काव तभी वसूल होगा जब वह फसल की कीमत का कम से कम $pct% बचाए।"),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(tr(lang, "Ask your KVK how much loss this problem usually causes, and compare.", "अपने KVK से पूछें कि यह समस्या आम तौर पर कितना नुकसान करती है, और तुलना करें।"), fontSize = 13.sp)
            }
            Text(tr(lang, "Your own numbers only. The app does not know prices or yield loss.", "सिर्फ़ आपके अपने आँकड़े। ऐप को भाव या उपज-नुकसान की जानकारी नहीं है।"), fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}
