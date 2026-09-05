package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidBottomTab
import com.fongmi.android.tv.ui.custom.liquid.LiquidBottomTabs
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.fongmi.android.tv.ui.custom.liquid.LiquidSlider
import com.fongmi.android.tv.ui.custom.liquid.LiquidToggle
import com.fongmi.android.tv.utils.ThemeUtil
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.sin

/**
 * Interactive component gallery based on the complete Backdrop catalog.
 * All glass surfaces share one animated layer backdrop, so this page demonstrates real refraction
 * instead of the rectangular simulated backdrop used by legacy View wrappers.
 */
class LiquidGlassShowcaseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val accent = Color(ContextCompat.getColor(context, ThemeUtil.getAccentColorResource()))
        val palette = ShowcasePalette(
            background = if (light) Color(0xFFF4F4F8) else Color(0xFF09090B),
            card = if (light) Color.White.copy(alpha = 0.76f) else Color(0xFF18181B).copy(alpha = 0.78f),
            glass = if (light) Color.White.copy(alpha = 0.36f) else Color(0xFF18181B).copy(alpha = 0.42f),
            text = if (light) Color(0xFF17171A) else Color.White,
            secondary = if (light) Color(0xFF67676E) else Color(0xFFA6A6AD),
            accent = accent
        )
        val frameNanos = remember { mutableLongStateOf(0L) }
        val backdrop = rememberLayerBackdrop()

        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        }

        Box(Modifier.fillMaxSize().background(palette.background)) {
            AnimatedBackdrop(Modifier.fillMaxSize().layerBackdrop(backdrop), frameNanos.longValue, light)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 102.dp, end = 16.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicText(
                    stringResource(R.string.glass_showcase_hint),
                    style = TextStyle(palette.secondary, 13.sp)
                )

                ButtonSamples(backdrop, frameNanos, palette)
                ToggleSample(backdrop, palette)
                SliderSample(backdrop, palette)
                BottomTabsSample(backdrop, frameNanos, palette, 3, R.string.glass_showcase_tabs_three)
                BottomTabsSample(backdrop, frameNanos, palette, 4, R.string.glass_showcase_tabs_four)
                DialogSample(backdrop, frameNanos, palette)
                LockScreenSample(backdrop, palette)
                ControlCenterSample(backdrop, frameNanos, palette)
                MagnifierSample(backdrop, palette)
                PlaygroundSample(backdrop, frameNanos, palette)
                AdaptiveLuminanceSample(backdrop, palette)
                ProgressiveBlurSample(backdrop, palette)
                ScrollContainerSample(backdrop, palette)
                LazyScrollContainerSample(backdrop, palette)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

private data class ShowcasePalette(
    val background: Color,
    val card: Color,
    val glass: Color,
    val text: Color,
    val secondary: Color,
    val accent: Color
)

@Composable
private fun AnimatedBackdrop(modifier: Modifier, frameNanos: Long, light: Boolean) {
    Canvas(modifier) {
        val seconds = frameNanos / 1_000_000_000f
        drawRect(
            Brush.linearGradient(
                listOf(
                    if (light) Color(0xFFEAF3FF) else Color(0xFF111827),
                    if (light) Color(0xFFFFF2DA) else Color(0xFF211827),
                    if (light) Color(0xFFE7FFF4) else Color(0xFF0F2725)
                )
            )
        )
        val drift = sin(seconds * 0.7f) * size.width * 0.08f
        drawCircle(
            Color(0xFF5EA7FF).copy(alpha = if (light) 0.56f else 0.38f),
            size.minDimension * 0.30f,
            Offset(size.width * 0.18f + drift, size.height * 0.18f)
        )
        drawCircle(
            Color(0xFFFFC247).copy(alpha = if (light) 0.48f else 0.30f),
            size.minDimension * 0.25f,
            Offset(size.width * 0.82f - drift, size.height * 0.48f)
        )
        drawCircle(
            Color(0xFF70E1B2).copy(alpha = if (light) 0.44f else 0.28f),
            size.minDimension * 0.34f,
            Offset(size.width * 0.28f - drift, size.height * 0.82f)
        )
    }
}

@Composable
private fun SampleCard(
    title: String,
    palette: ShowcasePalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BasicText(title, style = TextStyle(palette.text, 16.sp, FontWeight.SemiBold))
        content()
    }
}

@Composable
private fun ButtonSamples(backdrop: Backdrop, frameNanos: androidx.compose.runtime.LongState, palette: ShowcasePalette) {
    var pressCount by remember { mutableIntStateOf(0) }
    SampleCard(stringResource(R.string.glass_showcase_buttons), palette) {
        LiquidButton(
            onClick = { pressCount++ },
            backdrop = backdrop,
            frameNanos = frameNanos,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            SampleButtonText(stringResource(R.string.glass_showcase_button_transparent), palette.text)
        }
        LiquidButton(
            onClick = { pressCount++ },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = palette.glass,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            SampleButtonText(stringResource(R.string.glass_showcase_button_surface), palette.text)
        }
        LiquidButton(
            onClick = { pressCount++ },
            backdrop = backdrop,
            frameNanos = frameNanos,
            tint = Color(0xFF168BFF),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            SampleButtonText(stringResource(R.string.glass_showcase_button_tinted), Color.White)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LiquidButton(
                onClick = { pressCount++ },
                backdrop = backdrop,
                frameNanos = frameNanos,
                surfaceColor = palette.glass,
                modifier = Modifier.size(52.dp)
            ) { SampleButtonText("+", palette.text) }
            BasicText(
                stringResource(R.string.glass_showcase_button_circle, pressCount),
                style = TextStyle(palette.secondary, 13.sp)
            )
        }
    }
}

@Composable
private fun RowScope.SampleButtonText(text: String, color: Color) {
    BasicText(text, Modifier.padding(horizontal = 14.dp), style = TextStyle(color, 14.sp, FontWeight.Medium))
}

@Composable
private fun ToggleSample(backdrop: Backdrop, palette: ShowcasePalette) {
    var selected by remember { mutableStateOf(true) }
    SampleCard(stringResource(R.string.glass_showcase_toggle), palette) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                stringResource(if (selected) R.string.setting_on else R.string.setting_off),
                Modifier.weight(1f),
                style = TextStyle(palette.secondary, 14.sp)
            )
            LiquidToggle(
                selected = { selected },
                onSelect = { selected = it },
                backdrop = backdrop,
                accentColor = palette.accent,
                modifier = Modifier.size(64.dp, 40.dp)
            )
        }
    }
}

@Composable
private fun SliderSample(backdrop: Backdrop, palette: ShowcasePalette) {
    var value by remember { mutableFloatStateOf(0.48f) }
    SampleCard(stringResource(R.string.glass_showcase_slider), palette) {
        BasicText("${(value * 100).toInt()}%", style = TextStyle(palette.secondary, 13.sp))
        LiquidSlider(
            value = { value },
            onValueChange = { value = it },
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            backdrop = backdrop,
            accentColor = palette.accent,
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )
    }
}

@Composable
private fun BottomTabsSample(
    backdrop: Backdrop,
    frameNanos: androidx.compose.runtime.LongState,
    palette: ShowcasePalette,
    count: Int,
    title: Int
) {
    var selected by remember(count) { mutableIntStateOf(0) }
    SampleCard(stringResource(title), palette) {
        LiquidBottomTabs(
            selectedTabIndex = selected,
            onTabSelected = { selected = it },
            backdrop = backdrop,
            frameNanos = frameNanos,
            tabsCount = count,
            accentColor = palette.accent,
            containerColor = palette.glass,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            repeat(count) { index ->
                LiquidBottomTab(onClick = { selected = index }) {
                    BasicText(
                        stringResource(R.string.glass_showcase_tab, index + 1),
                        style = TextStyle(palette.text, 12.sp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogSample(backdrop: Backdrop, frameNanos: androidx.compose.runtime.LongState, palette: ShowcasePalette) {
    var expanded by remember { mutableStateOf(false) }
    SampleCard(stringResource(R.string.glass_showcase_dialog), palette) {
        LiquidButton(
            onClick = { expanded = !expanded },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = palette.glass,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) { SampleButtonText(stringResource(R.string.glass_showcase_dialog_toggle), palette.text) }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28.dp) },
                        effects = {
                            colorControls(brightness = 0.08f, saturation = 1.35f)
                            blur(12.dp.toPx())
                            lens(20.dp.toPx(), 40.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(palette.glass) }
                    )
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicText(stringResource(R.string.glass_showcase_dialog_title), style = TextStyle(palette.text, 18.sp, FontWeight.Medium))
                BasicText(stringResource(R.string.glass_showcase_dialog_body), style = TextStyle(palette.secondary, 13.sp))
            }
        }
    }
}

@Composable
private fun LockScreenSample(backdrop: Backdrop, palette: ShowcasePalette) {
    SampleCard(stringResource(R.string.glass_showcase_lock_screen), palette) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(116.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(28.dp) },
                    effects = { vibrancy(); blur(6.dp.toPx()); lens(18.dp.toPx(), 32.dp.toPx()) },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(palette.glass) }
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText("09:41", style = TextStyle(palette.text, 42.sp, FontWeight.Light, textAlign = TextAlign.Center))
        }
    }
}

@Composable
private fun ControlCenterSample(backdrop: Backdrop, frameNanos: androidx.compose.runtime.LongState, palette: ShowcasePalette) {
    var enabled by remember { mutableStateOf(true) }
    SampleCard(stringResource(R.string.glass_showcase_control_center), palette) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { index ->
                LiquidButton(
                    onClick = { enabled = !enabled },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    tint = if (enabled && index == 0) Color(0xFF168BFF) else Color.Unspecified,
                    surfaceColor = if (enabled && index == 0) Color.Unspecified else palette.glass,
                    modifier = Modifier.weight(1f).height(58.dp)
                ) { SampleButtonText(listOf("Wi-Fi", "BT", "VPN")[index], if (enabled && index == 0) Color.White else palette.text) }
            }
        }
    }
}

@Composable
private fun MagnifierSample(backdrop: Backdrop, palette: ShowcasePalette) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    SampleCard(stringResource(R.string.glass_showcase_magnifier), palette) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            BasicText(
                stringResource(R.string.glass_showcase_magnifier_text),
                Modifier.align(Alignment.Center).padding(horizontal = 18.dp),
                style = TextStyle(palette.text, 18.sp, FontWeight.Medium, textAlign = TextAlign.Center)
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationX = offset.x; translationY = offset.y }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        }
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = { lens(10.dp.toPx(), 28.dp.toPx(), depthEffect = true, chromaticAberration = true) },
                        innerShadow = { InnerShadow(radius = 12.dp) },
                        onDrawSurface = { drawRect(Color.White.copy(alpha = 0.08f)) }
                    )
                    .size(118.dp, 82.dp)
            )
        }
    }
}

@Composable
private fun PlaygroundSample(backdrop: Backdrop, frameNanos: androidx.compose.runtime.LongState, palette: ShowcasePalette) {
    var amount by remember { mutableFloatStateOf(0.45f) }
    var rounded by remember { mutableStateOf(true) }
    SampleCard(stringResource(R.string.glass_showcase_playground), palette) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(150.dp, 92.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(if (rounded) 44.dp else 12.dp) },
                    effects = {
                        vibrancy()
                        blur((amount * 14f).dp.toPx())
                        lens((amount * 24f).dp.toPx(), (amount * 42f).dp.toPx(), chromaticAberration = amount > 0.65f)
                    },
                    highlight = { Highlight.Plain },
                    shadow = { Shadow(radius = 8.dp, alpha = 0.18f) },
                    onDrawSurface = { drawRect(palette.glass) }
                )
        )
        LiquidSlider(
            value = { amount },
            onValueChange = { amount = it },
            valueRange = 0.1f..1f,
            visibilityThreshold = 0.001f,
            backdrop = backdrop,
            accentColor = palette.accent,
            modifier = Modifier.fillMaxWidth().height(34.dp)
        )
        LiquidButton(
            onClick = { rounded = !rounded },
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = palette.glass,
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) { SampleButtonText(stringResource(R.string.glass_showcase_playground_shape), palette.text) }
    }
}

@Composable
private fun AdaptiveLuminanceSample(backdrop: Backdrop, palette: ShowcasePalette) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val darkSurface = offsetX > 30f
    SampleCard(stringResource(R.string.glass_showcase_adaptive), palette) {
        Box(Modifier.fillMaxWidth().height(112.dp)) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { translationX = offsetX }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount.x).coerceIn(-90f, 90f)
                        }
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(24.dp) },
                        effects = {
                            colorControls(brightness = if (darkSurface) -0.12f else 0.18f, saturation = 1.45f)
                            blur(if (darkSurface) 4.dp.toPx() else 12.dp.toPx())
                            lens(20.dp.toPx(), 36.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(palette.glass) }
                    )
                    .size(150.dp, 74.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    stringResource(R.string.glass_showcase_drag),
                    style = TextStyle(if (darkSurface) Color.White else palette.text, 14.sp, FontWeight.Medium)
                )
            }
        }
    }
}

@Composable
private fun ProgressiveBlurSample(backdrop: Backdrop, palette: ShowcasePalette) {
    SampleCard(stringResource(R.string.glass_showcase_progressive_blur), palette) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .drawPlainBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(22.dp) },
                    effects = {
                        blur(8.dp.toPx())
                        if (isRuntimeShaderSupported()) {
                            runtimeShaderEffect(
                                "ShowcaseAlphaMask",
                                """
                                    uniform shader content;
                                    uniform float2 size;
                                    half4 main(float2 coord) {
                                        float alpha = smoothstep(size.y, size.y * 0.15, coord.y);
                                        return content.eval(coord) * alpha;
                                    }
                                """.trimIndent(),
                                "content"
                            ) { setFloatUniform("size", size.width, size.height) }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(stringResource(R.string.glass_showcase_progressive_blur_hint), style = TextStyle(palette.text, 14.sp))
        }
    }
}

@Composable
private fun ScrollContainerSample(backdrop: Backdrop, palette: ShowcasePalette) {
    SampleCard(stringResource(R.string.glass_showcase_scroll_container), palette) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(6) { index -> GlassTile(backdrop, palette, index) }
        }
    }
}

@Composable
private fun LazyScrollContainerSample(backdrop: Backdrop, palette: ShowcasePalette) {
    SampleCard(stringResource(R.string.glass_showcase_lazy_container), palette) {
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items((1..20).toList()) { index -> GlassTile(backdrop, palette, index) }
        }
    }
}

@Composable
private fun GlassTile(backdrop: Backdrop, palette: ShowcasePalette, index: Int) {
    Box(
        Modifier
            .width(104.dp)
            .height(84.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(22.dp) },
                effects = { vibrancy(); lens(14.dp.toPx(), 28.dp.toPx()) },
                highlight = { Highlight.Ambient },
                onDrawSurface = { drawRect(palette.glass) }
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(index.toString(), style = TextStyle(palette.text, 16.sp, FontWeight.Medium))
    }
}
