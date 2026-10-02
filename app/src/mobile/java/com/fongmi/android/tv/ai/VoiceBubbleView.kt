package com.fongmi.android.tv.ai

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

/** Fades recording in place; the existing navigation owns all thinking content. */
class VoiceBubbleView(
    context: Context,
    source: View,
    val usesNavigation: Boolean,
    private val onNavigation: (Boolean, Float, String) -> Unit
) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private var target = 0f
    private var level = 0f
    private var words = ""
    private var cancelled = false
    private var ending = false
    private var thinking = false
    private var morph = 0f
    private var navigationProgress = 0f
    private var navigationActive = false
    private var status = "正在思考…"
    private var finishing = false
    private var afterEntrance: (() -> Unit)? = null
    private var morphAnimator: ValueAnimator? = null
    private var finishAnimator: ValueAnimator? = null
    private val animated get() = Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
    private val bottomInset = ViewCompat.getRootWindowInsets(source)
        ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0

    // Capture only the original page, before adding this overlay.
    private val scene: RenderNode? = if (Build.VERSION.SDK_INT >= 31 && source.isHardwareAccelerated && source.width > 0) {
        try {
            RenderNode("voice-backdrop").apply {
                setPosition(0, 0, source.width, source.height)
                val recording = beginRecording(source.width, source.height)
                try { source.draw(recording) } finally { endRecording() }
                setRenderEffect(RenderEffect.createBlurEffect(18 * density, 18 * density, Shader.TileMode.CLAMP))
            }
        } catch (_: Exception) { null }
    } else null

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun updateNavigation(progress: Float) {
        if (!usesNavigation) return
        navigationActive = true
        navigationProgress = progress
        onNavigation(true, progress, status)
    }

    private fun resetNavigation() {
        if (!navigationActive) return
        navigationActive = false
        navigationProgress = 0f
        onNavigation(false, 0f, "")
    }

    fun showThinking(text: String) {
        if (finishing) return
        status = text
        if (!thinking) {
            thinking = true
            if (usesNavigation) {
                // One clock drives the blue fade and the robot's move inside the bar.
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                updateNavigation(0f)
                morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 320
                    interpolator = PathInterpolator(.22f, 0f, .18f, 1f)
                    addUpdateListener {
                        morph = it.animatedValue as Float
                        updateNavigation(morph)
                        invalidate()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            morph = 1f
                            updateNavigation(1f)
                            val pending = afterEntrance
                            afterEntrance = null
                            pending?.invoke()
                        }
                    })
                    start()
                }
            }
        } else updateNavigation(navigationProgress)
        if (!usesNavigation) contentDescription = listOf(words, text).filter { it.isNotBlank() }.joinToString("，")
        invalidate()
    }

    fun update(text: String, cancel: Boolean, finalizing: Boolean = false) {
        words = text; cancelled = cancel; ending = finalizing; invalidate()
    }
    fun setLevel(value: Float) { target = value.coerceIn(0f, 1f) }

    /** Finish the entrance before a fast result, but cancel queued navigation immediately. */
    fun dismiss(success: Boolean, awaitEntrance: Boolean, after: () -> Unit) {
        if (finishing && awaitEntrance) return
        if (!awaitEntrance) {
            afterEntrance = null
            finishAnimator?.removeAllListeners()
            finishAnimator?.cancel()
        }
        finishing = true
        val exit: () -> Unit = {
            val found = success && thinking
            if (found) {
                status = "找到了"
                if (!usesNavigation) contentDescription = status
            }
            animate().cancel()
            val initialAlpha = alpha
            val initialProgress = navigationProgress
            finishAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = if (thinking && usesNavigation) 260 else 180
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    val fade = if (found) smooth((fraction - .18f) / .82f) else smooth(fraction)
                    alpha = initialAlpha * (1f - fade)
                    if (navigationActive) updateNavigation(initialProgress * (1f - fade))
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        resetNavigation()
                        after()
                    }
                })
                start()
            }
        }
        if (awaitEntrance && usesNavigation && thinking && morph < 1f) afterEntrance = exit
        else {
            morphAnimator?.removeAllListeners()
            morphAnimator?.cancel()
            exit()
        }
    }

    fun stopAnimations() {
        afterEntrance = null
        animate().cancel()
        morphAnimator?.removeAllListeners()
        morphAnimator?.cancel()
        finishAnimator?.removeAllListeners()
        finishAnimator?.cancel()
        resetNavigation()
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        if (Build.VERSION.SDK_INT >= 29) scene?.discardDisplayList()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.argb((89 - 57 * morph).roundToInt(), 0, 0, 0))
        if (morph < 1f) {
            val layer = canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(),
                (255 * (1f - morph)).roundToInt())
            drawRecording(canvas)
            canvas.restoreToCount(layer)
        }
        if (animated && !thinking && isAttachedToWindow && isShown) postInvalidateOnAnimation()
    }

    private fun drawRecording(canvas: Canvas) {
        level += (target - level) * if (target > level) .38f else .12f
        val surfaceHeight = min(360 * density + bottomInset, height * .72f)
        val top = height - surfaceHeight
        val opaqueY = top + surfaceHeight * .55f
        paint.style = Paint.Style.FILL; paint.alpha = 255
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated && scene != null) {
            val layer = canvas.saveLayer(0f, top, width.toFloat(), height.toFloat(), null)
            canvas.drawRenderNode(scene)
            canvas.drawColor(0x59000000)
            paint.shader = LinearGradient(0f, top, 0f, opaqueY,
                Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint)
            paint.shader = null; paint.xfermode = null
            canvas.restoreToCount(layer)
        }
        val surfaceColor = if (cancelled) 0xFFE34D59.toInt() else context.getColor(com.fongmi.android.tv.R.color.voice_recording_blue)
        paint.shader = LinearGradient(0f, top, 0f, opaqueY,
            intArrayOf(surfaceColor and 0x00FFFFFF, (surfaceColor and 0x00FFFFFF) or 0x55000000, surfaceColor),
            floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), paint); paint.shader = null

        val centerY = height - bottomInset - min(64 * density, surfaceHeight * .18f)
        val time = SystemClock.uptimeMillis() / 150.0
        paint.color = Color.WHITE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = 2.6f * density
        val count = 39; val step = min(width * .76f / count, 7f * density)
        for (i in 0 until count) {
            val envelope = sin(PI * (i + 1) / (count + 1)).toFloat()
            val activity = (.4 + .6 * abs(sin(time + i * .85))).toFloat()
            val amplitude = density * (2.5f + 35f * level * envelope * activity)
            val x = width / 2f + (i - (count - 1) / 2f) * step
            paint.alpha = (100 + 155 * envelope).toInt()
            canvas.drawLine(x, centerY - amplitude / 2, x, centerY + amplitude / 2, paint)
        }
        paint.alpha = 255
        val label = when {
            thinking -> status
            cancelled -> "上滑取消"
            words.isNotBlank() -> words
            ending -> "正在确认识别结果…"
            else -> "请说话，松手发送"
        }.replace('\n', ' ')
        textPaint.color = Color.WHITE; textPaint.alpha = 255
        textPaint.textSize = 17f * density; textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT
        val visibleText = TextUtils.ellipsize(label, textPaint, max(1f, width - 48 * density), TextUtils.TruncateAt.START)
        val textY = centerY - min(68 * density, surfaceHeight * .20f)
        canvas.drawText(visibleText.toString(), width / 2f, textY, textPaint)
    }

    private fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
