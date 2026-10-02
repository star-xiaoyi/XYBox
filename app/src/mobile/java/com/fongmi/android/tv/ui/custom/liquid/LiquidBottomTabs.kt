package com.fongmi.android.tv.ui.custom.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Direct Android adaptation of the Backdrop catalog's LiquidBottomTabs component.
 *
 * Only sizing and the supplied idle container color differ from the catalog: XYBox keeps the
 * requested compact 52dp height, while the caller can make the idle glass opaque enough that
 * poster text cannot show through.
 */
@Composable
internal fun LiquidBottomTabs(
    selectedTabIndex: Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    frameNanos: LongState,
    tabsCount: Int,
    accentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    interactionEnabled: Boolean = true,
    selectionAlpha: Float = 1f,
    processingProgress: Float = 0f,
    processingTabIndex: Int = 1,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val tabsBackdrop = rememberLayerBackdrop()
    val voiceBlue = Color(LocalContext.current.getColor(com.fongmi.android.tv.R.color.voice_recording_blue))

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount
        }
        val initialIndex = selectedTabIndex.fastCoerceIn(0, tabsCount - 1)
        val offsetAnimation = remember(tabsCount) { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(tabsCount) { mutableIntStateOf(initialIndex) }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = initialIndex.toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 62f / 44f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        LaunchedEffect(selectedTabIndex, tabsCount) {
            currentIndex = selectedTabIndex.fastCoerceIn(0, tabsCount - 1)
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope, tabsCount) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    shadow = null,
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 12.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawBehind = { frameNanos.longValue },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .fillMaxSize()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics { }
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        shadow = null,
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                        },
                        onDrawBehind = { frameNanos.longValue },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(44.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    alpha = selectionAlpha
                    translationX = if (isLtr) {
                        dampedDragAnimation.value * tabWidth + panelOffset
                    } else {
                        size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    }
                }
                .then(if (interactionEnabled) interactiveHighlight.gestureModifier else Modifier)
                .then(if (interactionEnabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress)
                    },
                    shadow = null,
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 8.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawBehind = { frameNanos.longValue },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(44.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        if (processingProgress > 0f) {
            val motion = remember { com.fongmi.android.tv.ai.VoiceGlowMotion() }
            val slot = processingTabIndex * (1f - processingProgress) + .5f
            val iconX = with(density) { 4.dp.toPx() } + slot * tabWidth
            val physicalIconX = if (isLtr) iconX else constraints.maxWidth - iconX
            // Decorative only: hit targets and content stay in the existing navigation.
            Box(Modifier.matchParentSize()
                .graphicsLayer { translationX = panelOffset }
                .processingOutline(processingProgress, frameNanos, voiceBlue, physicalIconX, motion))
        }
    }
}

private fun Modifier.processingOutline(progress: Float, frameNanos: LongState, blue: Color,
    iconX: Float, motion: com.fongmi.android.tv.ai.VoiceGlowMotion) = drawWithCache {
    val width = 1.6.dp.toPx()
    val radius = minOf(size.height, size.width) / 2f
    val originX = motion.originX(iconX).coerceIn(radius, maxOf(radius, size.width - radius))
    val outline = Path().apply {
        // Explicit origin on the upper edge directly above the AI icon, never the default path start.
        moveTo(originX, 0f)
        lineTo(size.width - radius, 0f)
        arcTo(Rect(size.width - 2 * radius, 0f, size.width, size.height), -90f, 180f, false)
        lineTo(radius, size.height)
        arcTo(Rect(0f, 0f, radius * 2, size.height), 90f, 180f, false)
        lineTo(originX, 0f)
        close()
    }
    val measure = PathMeasure().apply { setPath(outline, true) }
    val segment = Path()
    val length = measure.length
    val stroke = Stroke(width, cap = StrokeCap.Round)
    val glow = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round)
    onDrawBehind {
        if (length <= 0f) return@onDrawBehind
        val opacity = progress.coerceIn(0f, 1f)
        val animated = android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()
        val phase = if (animated) motion.phase(frameNanos.longValue, progress >= .999f, iconX) else 0f
        val head = length * phase
        val tail = length * .23f
        val step = tail / 24f
        // The path runs clockwise from the icon's top edge; the fading tail follows behind.
        for (index in 0 until 24) {
            val start = (head - (24 - index) * step + length) % length
            val end = start + step
            segment.reset()
            measure.getSegment(start, minOf(end, length), segment, true)
            if (end > length) measure.getSegment(0f, end - length, segment, true)
            val strength = (index + 1) / 24f
            drawPath(segment, blue.copy(alpha = .12f * strength * opacity), style = glow)
            drawPath(segment, blue.copy(alpha = (.06f + .84f * strength * strength) * opacity), style = stroke)
        }
    }
}
