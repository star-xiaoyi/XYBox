package com.fongmi.android.tv.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.fongmi.android.tv.R
import com.fongmi.android.tv.utils.ThemeUtil
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.Alignment

@Composable internal fun aiAccent() = Color(LocalContext.current.getColor(ThemeUtil.getAccentColorResource()))
@Composable internal fun aiControlFill() = Color(LocalContext.current.getColor(R.color.control_primary))
@Composable internal fun aiControlText() = Color(LocalContext.current.getColor(R.color.control_on_fill))
@Composable internal fun aiTextColor() = Color(LocalContext.current.getColor(R.color.text_primary))
@Composable internal fun aiSurface() = aiTextColor().copy(alpha = .055f)
@Composable internal fun aiBackground(): Color = Color(LocalContext.current.getColor(R.color.screen_background))
@Composable internal fun AiIconButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Image(painterResource(icon), description, Modifier.size(23.dp), colorFilter = ColorFilter.tint(aiTextColor()))
    }
}
@Composable internal fun AiText(text: String, size: Int = 15, muted: Boolean = false, bold: Boolean = false, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, maxLines = maxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        style = TextStyle(color = aiTextColor().copy(alpha = if (muted) .64f else 1f),
        fontSize = size.sp, lineHeight = (size * 1.5f).sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal))
}
@Composable internal fun AiButton(text: String, enabled: Boolean = true, primary: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(if (primary) aiControlFill().copy(alpha = if (enabled) 1f else .4f) else aiSurface())
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        BasicText(text, style = TextStyle(if (primary) aiControlText() else aiTextColor().copy(alpha = if (enabled) 1f else .4f), 14.sp, FontWeight.Medium))
    }
}
@Composable internal fun AiField(value: String, hint: String, secret: Boolean = false, onValue: (String) -> Unit) {
    BasicTextField(value, onValue, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(aiSurface()).padding(16.dp), textStyle = TextStyle(aiTextColor(), 16.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text),
        singleLine = secret, maxLines = if (secret) 1 else 4,
        decorationBox = { inner -> Box { if (value.isEmpty()) AiText(hint, muted = true); inner() } })
}
