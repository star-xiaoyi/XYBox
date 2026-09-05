package com.fongmi.android.tv.ui.custom.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

/**
 * Adapted from Kyant's Backdrop catalog LiquidSlider example (Apache-2.0).
 * The accent is supplied by XYBox so the same component can be used in the showcase and settings.
 */
@Composable
internal fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val light = !isSystemInDarkTheme()
    val trackColor = if (light) Color(0xFF787878).copy(alpha = 0.20f)
    else Color(0xFF787880).copy(alpha = 0.36f)
    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(value()) }
        val animation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {
                    didDrag = false
                    dragValue = value()
                },
                onDragStopped = { if (didDrag) onValueChange(targetValue) },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    // 累积原始位移，不能以外部已经吸附到 0.5/1.0 步长的值为基准。
                    // 否则每个 MotionEvent 的小位移都会被舍掉，看起来就像只能点击。
                    dragValue = if (isLtr) (dragValue + delta).coerceIn(valueRange)
                    else (dragValue - delta).coerceIn(valueRange)
                    onValueChange(dragValue)
                }
            )
        }

        LaunchedEffect(animation) {
            snapshotFlow { value() }.collectLatest { current ->
                if (animation.targetValue != current) animation.updateValue(current)
            }
        }

        Box(
            Modifier
                .then(animation.modifier)
                .pointerInput(animationScope) {
                    detectTapGestures { position ->
                        val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                        val target = (
                            if (isLtr) valueRange.start + delta
                            else valueRange.endInclusive - delta
                        ).coerceIn(valueRange)
                        animation.animateToValue(target)
                        onValueChange(target)
                    }
                }
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(Modifier.layerBackdrop(trackBackdrop)) {
                Box(
                    Modifier
                        .clip(Capsule())
                        .background(trackColor)
                        .height(6.dp)
                        .fillMaxWidth()
                )
                Box(
                    Modifier
                        .clip(Capsule())
                        .background(accentColor)
                        .height(6.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val progress = ((animation.value - valueRange.start) /
                                (valueRange.endInclusive - valueRange.start)).fastCoerceIn(0f, 1f)
                            val width = (constraints.maxWidth * progress).fastRoundToInt()
                            layout(width, placeable.height) { placeable.place(0, 0) }
                        }
                )
            }

            Box(
                Modifier
                    .graphicsLayer {
                        translationX = (
                            -size.width / 2f + trackWidth * (
                                (animation.value - valueRange.start) /
                                    (valueRange.endInclusive - valueRange.start)
                                ).fastCoerceIn(0f, 1f)
                        ).fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                            if (isLtr) 1f else -1f
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = animation.pressProgress
                                scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) {
                                    drawBackdrop()
                                }
                            }
                        ),
                        shape = { Capsule() },
                        effects = {
                            val progress = animation.pressProgress
                            blur(8.dp.toPx() * (1f - progress))
                            lens(
                                10.dp.toPx() * progress,
                                14.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.5f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                                alpha = animation.pressProgress
                            )
                        },
                        shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                        innerShadow = {
                            InnerShadow(
                                radius = 4.dp * animation.pressProgress,
                                alpha = animation.pressProgress
                            )
                        },
                        layerBlock = {
                            scaleX = animation.scaleX
                            scaleY = animation.scaleY
                            val velocity = animation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            drawRect(Color.White.copy(alpha = 1f - animation.pressProgress))
                        }
                    )
                    .size(40.dp, 24.dp)
            )
        }
    }
}
