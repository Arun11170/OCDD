package com.cropadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shows the order in which to try things: field steps first, low-cost and bio options next, registered chemicals last. */
@Composable
fun LadderCard(lang: Lang, hasChem: Boolean, hasOrganic: Boolean) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F8F1))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr(lang, "Try in this order", "इस क्रम में आज़माएँ"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text("1.  " + tr(lang, "Field steps above (cost nothing)", "ऊपर के खेत के उपाय (कोई खर्च नहीं)"), fontSize = 14.sp)
            if (hasOrganic) Text("2.  " + tr(lang, "Low-cost and biological options", "कम खर्च और जैविक विकल्प"), fontSize = 14.sp)
            if (hasChem) Text((if (hasOrganic) "3.  " else "2.  ") + tr(lang, "Registered chemicals, only if the problem keeps growing", "पंजीकृत रसायन, तभी जब समस्या बढ़ती रहे"), fontSize = 14.sp)
        }
    }
}
