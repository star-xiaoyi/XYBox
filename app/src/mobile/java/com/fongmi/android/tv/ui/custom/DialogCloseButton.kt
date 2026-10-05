package com.fongmi.android.tv.ui.custom

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fongmi.android.tv.R

/** A visible 32 dp circle inside a 44 dp touch target, shared by popup headers. */
@Composable
internal fun DialogCloseButton(onClick: () -> Unit, background: Color, foreground: Color) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = "关闭弹窗" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_action_close), null, Modifier.size(16.dp),
                colorFilter = ColorFilter.tint(foreground))
        }
    }
}
