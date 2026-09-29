package com.fongmi.android.tv.ui.custom

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.shapes.RoundedRectangle

/** 首页、分类栏与二级顶栏共用的平滑模糊表面，末端透明且斜率归零。 */
@Composable
internal fun ProgressiveGlassSurface(
    modifier: Modifier, backdrop: Backdrop, base: Color, glass: Color,
    fadeStart: Dp, frameNanos: LongState, enabled: Boolean = true
) {
    val shaderFade = enabled && isRuntimeShaderSupported()
            Box(
                modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (enabled && !shaderFade) {
                            val start = fadeStart.toPx() / size.height
                            val end = (size.height - 2.dp.toPx()) / size.height
                            val stops = (0..32).map { step ->
                                val y = step / 32f
                                val t = ((y - start) / (end - start)).coerceIn(0f, 1f)
                                y to Color.White.copy(alpha = 1f - t * t * (3f - 2f * t))
                            }.toTypedArray()
                            drawRect(Brush.verticalGradient(*stops), blendMode = BlendMode.DstIn)
                        }
                    }
                    .drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(0.dp) },
                        effects = {
                            blur(8.dp.toPx())
                            if (shaderFade) runtimeShaderEffect(
                                "HomeProgressiveBlur",
                                """
                                    uniform shader content;
                                    uniform float2 fade;
                                    uniform float4 surface;
                                    half4 main(float2 coord) {
                                        float alpha = 1.0 - smoothstep(fade.x, fade.y, coord.y);
                                        half4 sampled = content.eval(coord);
                                        half4 frosted = mix(sampled, half4(surface.rgb, 1.0), surface.a);
                                        return frosted * alpha;
                                    }
                                """.trimIndent(), "content"
                            ) {
                                setFloatUniform("fade", fadeStart.toPx(), size.height - 2.dp.toPx())
                                setFloatUniform("surface", base.red, base.green, base.blue, glass.alpha)
                            }
                        },
                        onDrawBehind = { frameNanos.longValue },
                        onDrawSurface = { if (!shaderFade) drawRect(glass) }
                    )
            )
}
