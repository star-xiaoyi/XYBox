package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.bean.Download
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle
import java.util.LinkedHashMap

/** 剧集删除弹窗的完整液态画布：面板、剧集和底部操作都共享同一个 Backdrop。 */
class LiquidGlassDownloadDeleteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface DeleteListener {
        fun onDelete(episodeKeys: Set<String>)
    }

    private var groupNameState by mutableStateOf("")
    private var episodesState by mutableStateOf<List<Download>>(emptyList())
    private var selectedState by mutableStateOf<Set<String>>(emptySet())
    private var cancelClickListener: View.OnClickListener? = null
    private var deleteListener: DeleteListener? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
        elevation = 20f * resources.displayMetrics.density
    }

    fun setGroup(group: Download.Group) {
        val unique = LinkedHashMap<String, Download>()
        group.items.forEach { item ->
            val key = Download.episodeKey(item.episodeName)
            val current = unique[key]
            if (current == null || (!current.isDone && item.isDone)) unique[key] = item
        }
        groupNameState = group.vodName
        episodesState = unique.values.toList()
        selectedState = emptySet()
    }

    fun setCancelClickListener(listener: View.OnClickListener?) {
        cancelClickListener = listener
    }

    fun setDeleteListener(listener: DeleteListener?) {
        deleteListener = listener
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val palette = DeletePalette(
            panel = if (light) Color(0xFFF8F8FA).copy(alpha = 0.91f) else Color(0xFF18181B).copy(alpha = 0.93f),
            glass = if (light) Color.White.copy(alpha = 0.62f) else Color(0xFF2C2C30).copy(alpha = 0.72f),
            episodeSurface = if (light) Color(0xFFEAEAED) else Color(0xFF303034),
            episodeBorder = if (light) Color(0xFFD1D1D6) else Color(0xFF48484A),
            text = if (light) Color(0xFF1C1C1E) else Color.White,
            secondary = if (light) Color(0xFF6C6C70) else Color(0xFF98989D),
            accent = Color(context.getColor(R.color.accent))
        )
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberCanvasBackdrop {
            drawRect(
                Brush.linearGradient(
                    if (light) listOf(Color.White, Color(0xFFDDE0E6), Color(0xFFF9F9FB))
                    else listOf(Color(0xFF303035), Color(0xFF101012), Color(0xFF27272B))
                )
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(5.dp)
                .heightIn(max = 580.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(28.dp) },
                    effects = {
                        colorControls(brightness = 0.10f, saturation = 0.66f)
                        blur(28.dp.toPx())
                        lens(12.dp.toPx(), 28.dp.toPx(), depthEffect = true)
                    },
                    onDrawSurface = { drawRect(palette.panel) }
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BasicText(
                text = context.getString(R.string.dialog_delete_download_item, groupNameState),
                style = TextStyle(palette.text, 16.sp, FontWeight.Bold, lineHeight = 22.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                LiquidButton(
                    onClick = ::toggleAll,
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    surfaceColor = palette.glass,
                    dragResponse = 0.30f,
                    modifier = Modifier.height(42.dp).padding(horizontal = 2.dp)
                ) {
                    BasicText(
                        text = context.getString(
                            if (episodesState.isNotEmpty() && selectedState.size == episodesState.size) R.string.download_deselect_all
                            else R.string.download_select_all
                        ),
                        modifier = Modifier.padding(horizontal = 18.dp),
                        style = TextStyle(palette.secondary, 13.sp, FontWeight.SemiBold)
                    )
                }
            }
            EpisodeGrid(palette)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton(
                    text = context.getString(R.string.dialog_negative),
                    textColor = palette.text,
                    enabled = true,
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    palette = palette,
                    onClick = { cancelClickListener?.onClick(this@LiquidGlassDownloadDeleteView) },
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = context.getString(R.string.download_delete_selected, selectedState.size),
                    textColor = palette.accent,
                    enabled = selectedState.isNotEmpty(),
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    palette = palette,
                    onClick = { deleteListener?.onDelete(selectedState) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    private fun ColumnScope.EpisodeGrid(
        palette: DeletePalette
    ) {
        val span = spanCount()
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            episodesState.chunked(span).forEach { episodes ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    episodes.forEach { item ->
                        val key = Download.episodeKey(item.episodeName)
                        val selected = selectedState.contains(key)
                        val selectedText = if (palette.accent.luminance() > 0.58f) Color(0xFF1C1C1E) else Color.White
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(shape)
                                .background(if (selected) palette.accent else palette.episodeSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (selected) palette.accent else palette.episodeBorder,
                                    shape = shape
                                )
                                .clickable { toggle(key) }
                        ) {
                            BasicText(
                                text = item.episodeName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = TextStyle(
                                    if (selected) selectedText else palette.text,
                                    14.sp,
                                    if (selected) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    repeat(span - episodes.size) { Spacer(Modifier.weight(1f).height(48.dp)) }
                }
            }
        }
    }

    @Composable
    private fun DialogButton(
        text: String,
        textColor: Color,
        enabled: Boolean,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: DeletePalette,
        onClick: () -> Unit,
        modifier: Modifier
    ) {
        LiquidButton(
            onClick = { if (enabled) onClick() },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = palette.glass,
            dragResponse = 0.34f,
            modifier = modifier.height(52.dp).alpha(if (enabled) 1f else 0.45f)
        ) {
            BasicText(text, style = TextStyle(textColor, 14.sp, FontWeight.SemiBold))
        }
    }

    private fun toggle(key: String) {
        selectedState = selectedState.toMutableSet().apply {
            if (!remove(key)) add(key)
        }
    }

    private fun toggleAll() {
        selectedState = if (selectedState.size == episodesState.size) emptySet()
        else episodesState.mapTo(LinkedHashSet()) { Download.episodeKey(it.episodeName) }
    }

    private fun spanCount(): Int {
        val longest = episodesState.maxOfOrNull { it.episodeName.length } ?: 1
        return when {
            longest >= 14 -> 1
            longest >= 8 -> 2
            else -> 3
        }
    }
}

private data class DeletePalette(
    val panel: Color,
    val glass: Color,
    val episodeSurface: Color,
    val episodeBorder: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color
)
