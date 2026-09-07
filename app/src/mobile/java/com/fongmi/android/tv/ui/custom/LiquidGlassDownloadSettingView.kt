package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.Setting
import com.fongmi.android.tv.download.DownloadManager
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.fongmi.android.tv.ui.custom.liquid.LiquidSlider
import com.fongmi.android.tv.utils.ThemeUtil
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt

/** 缓存设置使用与主页筛选相同的页面内玻璃面板，从顶栏下方展开。 */
class LiquidGlassDownloadSettingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private var visibleState by mutableStateOf(false)
    private var backdropViewState by mutableStateOf<View?>(null)
    private var modeState by mutableIntStateOf(Setting.getDownloadMode())
    private var taskState by mutableIntStateOf(Setting.getDownloadTask())
    private var initialMode = modeState
    private var initialTask = taskState

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipChildren = false
        clipToPadding = false
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun show(backdropView: View?) {
        initialMode = Setting.getDownloadMode()
        initialTask = Setting.getDownloadTask()
        modeState = initialMode
        taskState = initialTask
        backdropViewState = backdropView
        visibleState = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun dismiss() {
        visibleState = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    fun isPanelVisible(): Boolean = visibleState

    private fun confirm() {
        Setting.putDownloadMode(modeState)
        Setting.putDownloadTask(taskState)
        if (modeState != initialMode || taskState != initialTask) {
            DownloadManager.get().applySettings()
        }
        dismiss()
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val palette = DownloadSettingPalette(
            panel = if (light) Color(0xFFF8F8FA).copy(alpha = 0.91f)
            else Color(0xFF18181B).copy(alpha = 0.93f),
            glass = if (light) Color.White.copy(alpha = 0.86f)
            else Color(0xFF242428).copy(alpha = 0.88f),
            text = if (light) Color(0xFF1C1C1E) else Color.White,
            secondary = if (light) Color(0xFF6C6C70) else Color(0xFF98989D),
            accent = Color(context.getColor(ThemeUtil.getAccentColorResource()))
        )
        val backdrop = rememberLayerBackdrop()
        val frameNanos = remember { mutableLongStateOf(0L) }
        val panelLocation = remember { IntArray(2) }
        val sourceLocation = remember { IntArray(2) }
        val sourceView = backdropViewState
        LaunchedEffect(visibleState, sourceView) {
            while (visibleState && sourceView != null) {
                withFrameNanos { frameNanos.longValue = it }
            }
        }

        Box(Modifier.fillMaxSize()) {
            if (visibleState) {
                Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    frameNanos.longValue
                    if (sourceView != null && sourceView.isAttachedToWindow) {
                        this@LiquidGlassDownloadSettingView.getLocationInWindow(panelLocation)
                        sourceView.getLocationInWindow(sourceLocation)
                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            val saveCount = nativeCanvas.save()
                            nativeCanvas.translate(
                                (sourceLocation[0] - panelLocation[0]).toFloat(),
                                (sourceLocation[1] - panelLocation[1]).toFloat()
                            )
                            sourceView.draw(nativeCanvas)
                            nativeCanvas.restoreToCount(saveCount)
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (light) 0.12f else 0.22f))
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = ::dismiss
                        )
                )
            }

            SettingPanel(
                visible = visibleState,
                backdrop = backdrop,
                frameNanos = frameNanos,
                palette = palette,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }

    @Composable
    private fun SettingPanel(
        visible: Boolean,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: DownloadSettingPalette,
        modifier: Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 64.dp, end = 12.dp),
            enter = expandVertically(tween(240), expandFrom = Alignment.Top) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(190), shrinkTowards = Alignment.Top) + fadeOut(tween(120))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28.dp) },
                        effects = {
                            colorControls(brightness = 0.10f, saturation = 0.52f)
                            blur(36.dp.toPx())
                            lens(8.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                        },
                        onDrawSurface = { drawRect(palette.panel) }
                    )
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(
                    text = stringResource(R.string.download_mode),
                    style = TextStyle(palette.text, 16.sp, FontWeight.Bold)
                )
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ModeButton(
                        text = stringResource(R.string.download_mode_smart),
                        mode = Setting.DOWNLOAD_MODE_SMART,
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        palette = palette,
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                    ModeButton(
                        text = stringResource(R.string.download_mode_fast),
                        mode = Setting.DOWNLOAD_MODE_FAST,
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        palette = palette,
                        modifier = Modifier.weight(1f).height(48.dp)
                    )
                }
                BasicText(
                    text = stringResource(
                        if (modeState == Setting.DOWNLOAD_MODE_FAST) R.string.download_mode_fast_hint
                        else R.string.download_mode_smart_hint
                    ),
                    style = TextStyle(palette.secondary, 13.sp, FontWeight.Normal, lineHeight = 19.sp)
                )

                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = stringResource(R.string.download_concurrent_task),
                        modifier = Modifier.weight(1f),
                        style = TextStyle(palette.text, 16.sp, FontWeight.Bold)
                    )
                    BasicText(
                        text = context.getString(R.string.download_concurrent_task_value, taskState),
                        style = TextStyle(palette.accent, 15.sp, FontWeight.Bold)
                    )
                }
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                    LiquidSlider(
                        value = { taskState.toFloat() },
                        onValueChange = {
                            taskState = it.roundToInt().coerceIn(1, Setting.DOWNLOAD_TASK_MAX)
                        },
                        valueRange = 1f..Setting.DOWNLOAD_TASK_MAX.toFloat(),
                        visibilityThreshold = 0.05f,
                        backdrop = backdrop,
                        accentColor = palette.accent,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp)
                    )
                }
                BasicText(
                    text = stringResource(R.string.download_concurrent_task_hint),
                    style = TextStyle(palette.secondary, 13.sp, FontWeight.Normal, lineHeight = 19.sp)
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LiquidButton(
                        onClick = ::dismiss,
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        surfaceColor = palette.glass,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        BasicText(
                            stringResource(R.string.dialog_negative),
                            style = TextStyle(palette.text, 14.sp, FontWeight.SemiBold)
                        )
                    }
                    LiquidButton(
                        onClick = ::confirm,
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        surfaceColor = palette.accent.copy(alpha = 0.88f),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        BasicText(
                            stringResource(R.string.dialog_positive),
                            style = TextStyle(palette.selectedText, 14.sp, FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ModeButton(
        text: String,
        mode: Int,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: DownloadSettingPalette,
        modifier: Modifier
    ) {
        val selected = modeState == mode
        LiquidButton(
            onClick = { modeState = mode },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = if (selected) palette.accent.copy(alpha = 0.88f) else palette.glass,
            modifier = modifier
        ) {
            BasicText(
                text = text,
                style = TextStyle(
                    color = if (selected) palette.selectedText else palette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}

private data class DownloadSettingPalette(
    val panel: Color,
    val glass: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color
) {
    val selectedText: Color
        get() = if (accent.luminance() > 0.58f) Color(0xFF1C1C1E) else Color.White
}
