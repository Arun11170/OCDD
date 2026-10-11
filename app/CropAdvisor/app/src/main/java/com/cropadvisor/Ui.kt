package com.cropadvisor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small reusable pieces so every screen looks like one product. */

enum class BtnStyle { Primary, Soft, Outline, OnDark }

@Composable
fun DsButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, style: BtnStyle = BtnStyle.Primary, height: Dp = 56.dp) {
    val shape = RoundedCornerShape(18.dp)
    val (bg, fg) = when (style) {
        BtnStyle.Primary -> Ds.Brand to Color.White
        BtnStyle.Soft -> Ds.Mint to Ds.Forest
        BtnStyle.Outline -> Color.Transparent to Ds.Forest
        BtnStyle.OnDark -> Color.White to Ds.Forest
    }
    val inner: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
    if (style == BtnStyle.Outline) {
        OutlinedButton(onClick = onClick, modifier = modifier.height(height), shape = shape, border = BorderStroke(1.5.dp, Ds.Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = fg)) { inner() }
    } else {
        Button(onClick = onClick, modifier = modifier.height(height), shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)) { inner() }
    }
}

/** A round coloured badge holding an icon. */
@Composable
fun IconBadge(icon: ImageVector, bg: Color, tint: Color, badge: Dp = 44.dp, iconSize: Dp = 22.dp) {
    Box(Modifier.size(badge).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** A small rounded label such as "Problem found". */
@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Circular confidence meter with the percentage in the middle. */
@Composable
fun ScoreRing(progress: Float, color: Color, label: String, ringSize: Dp = 78.dp) {
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2f
            val box = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(color.copy(alpha = 0.18f), -90f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Ds.Ink)
    }
}
