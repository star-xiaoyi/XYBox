package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/** One native drawing boundary for the AI page, its glass layers and the original navigation. */
class AiPageContainer(context: Context) : FrameLayout(context) {
    private val content = FrameLayout(context)
    val page = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
    val navigation = FrameLayout(context)
    private val roundedBackground = GradientDrawable()
    private val dim = GradientDrawable()
    private val boundary = Path()
    private var radius = 0f
    private var progress = 0f
    private var requestedProgress = 0f
    private var tracingDrag = false
    private var underlayColor = 0
    private var underlayBaseColor = 0
    private var underlayShade = 0f
    private var cardColor = 0
    private var hierarchyPending = true
    private var lastFrameState = ""
    private var lastFrameTime = -1L
    private var frameIndex = 0L
    private val traceId = Integer.toHexString(System.identityHashCode(this))
    private val windowLocation = IntArray(2)
    private val frameClip = Rect()

    init {
        clipChildren = true
        clipToPadding = true
        clipToOutline = true
        background = roundedBackground
        foreground = dim
        navigation.clipChildren = false
        navigation.clipToPadding = false
        content.clipChildren = false
        content.clipToPadding = false
        content.pivotX = 0f
        content.pivotY = 0f
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radius)
            }
        }
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(page, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(navigation, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    fun update(color: Int, pageWidth: Int, pageHeight: Int) {
        cardColor = color
        if (content.layoutParams.width != pageWidth || content.layoutParams.height != pageHeight)
            content.layoutParams = LayoutParams(pageWidth, pageHeight)
        roundedBackground.setColor(color)
        dim.setColor(color)
    }

    fun updateDrawDiagnostics(requested: Float, dragging: Boolean, underlay: Int, baseColor: Int, shade: Float) {
        if (dragging && !tracingDrag) hierarchyPending = true
        requestedProgress = requested
        tracingDrag = dragging
        underlayColor = underlay
        underlayBaseColor = baseColor
        underlayShade = shade
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (width <= 0 || height <= 0 || content.width <= 0 || content.height <= 0) return
        // AndroidView.update and Compose measurement may observe different animation frames.
        // Fit the content to THIS layout's bounds, then derive the mask and tint from that fit.
        // Separate X/Y factors account for integer rounding without exposing a 1px backing.
        val scaleX = width.toFloat() / content.width
        val scaleY = height.toFloat() / content.height
        content.scaleX = scaleX
        content.scaleY = scaleY
        val scale = minOf(scaleX, scaleY)
        progress = ((1f - scale) / .05f).coerceIn(0f, 1f)
        radius = 28f * resources.displayMetrics.density * progress * scale
        elevation = 24f * resources.displayMetrics.density * progress * scale
        roundedBackground.cornerRadius = radius
        dim.cornerRadius = radius
        dim.alpha = (.64f * 255f * progress).toInt()
        updateBoundary()
    }

    private fun updateBoundary() {
        boundary.reset()
        boundary.addRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Path.Direction.CW)
        invalidateOutline()
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        // Clip background, child Compose render nodes, glass sampling and foreground together.
        canvas.clipPath(boundary)
        try {
            traceFrame(canvas)
            super.draw(canvas)
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    private fun traceFrame(canvas: Canvas) {
        getLocationInWindow(windowLocation)
        val state = "${windowLocation[0]}:${windowLocation[1]}:$width:$height:$progress:$tracingDrag:$underlayColor"
        val moving = tracingDrag || requestedProgress > 0f && requestedProgress < 1f || progress > 0f && progress < 1f
        val now = android.os.SystemClock.uptimeMillis()
        // Log every rendered drag/settle frame, including a finger held still mid-drag.
        // Idle pages log only their final state, and do not fill the persistent log continuously.
        if (now == lastFrameTime || !moving && state == lastFrameState) return
        lastFrameTime = now
        lastFrameState = state
        frameIndex++
        canvas.getClipBounds(frameClip)
        val key = "id=$traceId frame=$frameIndex t=$now"
        com.github.catvod.utils.Logger.d("AiFrame $key drag=$tracingDrag requested=$requestedProgress layout=$progress " +
            "window=${windowLocation[0]},${windowLocation[1]} size=${width}x$height page=${page.width}x${page.height} " +
            "scale=${content.scaleX},${content.scaleY} gap=${width - content.width * content.scaleX},${height - content.height * content.scaleY} " +
            "radius=$radius elevation=$elevation clip=${frameClip.toShortString()} navHeight=${navigation.height} hardware=${canvas.isHardwareAccelerated}")
        com.github.catvod.utils.Logger.d("AiFrameColor $key source=config underlay=${hex(underlayColor)} history=inherit historyScrim=0 " +
            "base=${hex(underlayBaseColor)} wholeUnderlayShade=$underlayShade " +
            "topUnderlay=${hex(underlayColor)} bottomUnderlay=${hex(underlayColor)} card=${hex(cardColor)} " +
            "cardDim=${hex(cardColor)} dimAlpha=${dim.alpha} cardAlpha=$alpha navAlpha=${navigation.alpha} renderer=single_fading_underlay")
        if (hierarchyPending) {
            hierarchyPending = false
            traceLayer("content", content)
            traceLayer("page", page)
            traceLayer("navigation", navigation)
            var layer: View? = this
            var index = 0
            while (layer != null && index < 12) {
                traceLayer("ancestor${index++}", layer)
                layer = layer.parent as? View
            }
        }
    }

    private fun traceLayer(role: String, view: View) {
        val drawable = view.background
        val backgroundColor = when (drawable) {
            is ColorDrawable -> hex(drawable.color)
            is GradientDrawable -> drawable.color?.defaultColor?.let(::hex) ?: "gradient"
            null -> "none"
            else -> drawable.javaClass.simpleName
        }
        com.github.catvod.utils.Logger.d("AiLayer id=$traceId frame=$frameIndex role=$role type=${view.javaClass.simpleName} " +
            "bounds=${view.left},${view.top},${view.right},${view.bottom} translation=${view.translationX},${view.translationY} " +
            "scale=${view.scaleX},${view.scaleY} alpha=${view.alpha} elevation=${view.elevation} background=$backgroundColor " +
            "clipChildren=${(view as? ViewGroup)?.clipChildren} clipOutline=${view.clipToOutline}")
    }

    private fun hex(color: Int) = "#" + Integer.toHexString(color).padStart(8, '0')
}
