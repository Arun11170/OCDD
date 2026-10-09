package com.cropadvisor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Yellow leaves: disease or nutrient?" Two questions, then a cautious pointer. It never names a fertiliser or an amount.
 * Rule used: mobile nutrients (nitrogen, phosphorus, potassium) show first on older lower leaves, immobile ones
 * (boron, sulphur, calcium, copper, iron) on younger upper leaves. Source: Cornell Soil Health, "How to diagnose plant
 * nutrient deficiencies using leaf symptoms" (blogs.cornell.edu/soilnow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutrientCheckCard(lang: Lang) {
    var spots by rememberSaveable { mutableStateOf(-1) }   // 1 yes, 0 no
    var where by rememberSaveable { mutableStateOf(-1) }   // 0 older lower leaves, 1 young upper leaves
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr(lang, "Yellow leaves: disease or nutrient?", "पीले पत्ते: रोग या पोषक तत्व की कमी?"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(tr(lang, "1. Do you see spots, rings, mould, powder or ooze on the leaves?", "1. क्या पत्तों पर धब्बे, छल्ले, फफूंद, पाउडर या रिसाव दिखता है?"), fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = spots == 1, onClick = { spots = 1 }, label = { Text(tr(lang, "Yes", "हाँ")) })
                FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = spots == 0, onClick = { spots = 0 }, label = { Text(tr(lang, "No", "नहीं")) })
            }
            if (spots == 0) {
                Text(tr(lang, "2. Where does the yellowing start?", "2. पीलापन कहाँ से शुरू होता है?"), fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = where == 0, onClick = { where = 0 }, label = { Text(tr(lang, "Old, lower leaves", "पुराने, निचले पत्ते")) })
                    FilterChip(modifier = Modifier.heightIn(min = 44.dp), selected = where == 1, onClick = { where = 1 }, label = { Text(tr(lang, "Young, top leaves", "नए, ऊपरी पत्ते")) })
                }
            }
            val result = when {
                spots == 1 -> tr(lang, "This points to a disease or pest. Take a photo of the leaf with this app.", "यह रोग या कीट की ओर संकेत करता है। इस ऐप से पत्ते की फोटो लें।")
                spots == 0 && where == 0 -> tr(lang, "This can be a shortage of a nutrient the plant moves around (nitrogen, phosphorus or potassium). Do not spray a fungicide for this.", "यह उस पोषक तत्व की कमी हो सकती है जिसे पौधा अपने अंदर ले जा सकता है (नाइट्रोजन, फॉस्फोरस या पोटाश)। इसके लिए फफूंदनाशक न छिड़कें।")
                spots == 0 && where == 1 -> tr(lang, "This can be a shortage of a nutrient the plant cannot move (iron, sulphur, calcium, boron or copper). Do not spray a fungicide for this.", "यह उस पोषक तत्व की कमी हो सकती है जिसे पौधा आगे नहीं ले जा सकता (लोहा, गंधक, कैल्शियम, बोरॉन या ताँबा)। इसके लिए फफूंदनाशक न छिड़कें।")
                else -> null
            }
            if (result != null && (spots == 1 || where != -1)) {
                Text(result, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (spots == 0) Text(tr(lang, "Get a soil test (ask your KVK) before adding any fertiliser. Mild shortages are hard to see, and water stress or roots damage can look the same.", "कोई भी खाद डालने से पहले मिट्टी की जाँच कराएँ (अपने KVK से पूछें)। हल्की कमी पहचानना कठिन है, और पानी की कमी या जड़ की चोट भी ऐसी ही दिख सकती है।"), fontSize = 13.sp)
            }
            Text(tr(lang, "General rule from Cornell Soil Health. A guide only; no fertiliser amounts are given.", "कॉर्नेल सॉइल हेल्थ का सामान्य नियम। सिर्फ़ मार्गदर्शन; खाद की मात्रा नहीं दी जाती।"), fontSize = 13.sp, color = Color(0xFF444444))
        }
    }
}
