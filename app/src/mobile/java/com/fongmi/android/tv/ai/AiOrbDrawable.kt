package com.fongmi.android.tv.ai

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.sin

/** Transparent outlined orb; the face keeps its blink, gaze, breathing and thinking motion. */
class AiOrbDrawable : Drawable(), Runnable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tint = ColorStateList.valueOf(Color.WHITE)
    private var running = false
    private var mode = 0
    private var opacity = 255
    private var filter: ColorFilter? = null
    fun setMode(value: Int) { mode = value; invalidateSelf() }
    fun setRunning(value: Boolean) {
        if (running == value) return
        running = value; unscheduleSelf(this); if (value) run()
    }
    override fun run() {
        invalidateSelf()
        if (running && ValueAnimator.areAnimatorsEnabled()) scheduleSelf(this, SystemClock.uptimeMillis() + 33)
    }
    override fun draw(canvas: Canvas) {
        if (bounds.width() == 0 || bounds.height() == 0) return
        val save = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width()/24f, bounds.height()/24f)
        val t = if (ValueAnimator.areAnimatorsEnabled()) SystemClock.uptimeMillis() else 1500L
        val phase = t % 12000
        val bob = sin(t / 550.0).toFloat() * if (mode == 1) .5f else .22f
        val breath = 1f + sin(t / if (mode == 1) 180.0 else 700.0).toFloat() * if (mode == 1) .045f else .018f
        canvas.translate(0f,bob); canvas.scale(breath, 2f-breath,12f,12f)
        val tilt = if (mode == 0 && phase in 5500..8500) sin((phase-5500)/3000.0*Math.PI).toFloat()*8f else if(mode==2) sin(t/450.0).toFloat()*4f else 0f
        canvas.rotate(tilt,12f,12f)
        val layer=canvas.saveLayer(0f,0f,24f,24f,null)
        val base=tint.getColorForState(state,tint.defaultColor)
        paint.style=Paint.Style.STROKE; paint.colorFilter=filter; paint.xfermode=null
        paint.color=base; paint.alpha=opacity; paint.strokeWidth=1.45f
        canvas.drawCircle(12f,12f,9.7f,paint)
        val lookX = if(mode==2) sin(t/240.0).toFloat()*1.1f else if(phase in 5500..8500) .8f else sin(t/1400.0).toFloat()*.35f
        val lookY = if(mode==2) -.6f else if(phase in 5500..8500) -.7f else 0f
        val blink = mode==0 && (t%4700<115 || t%4700 in 250..330)
        val smile = mode==0 && phase in 3000..4800
        for((i,x) in floatArrayOf(8.7f,15.3f).withIndex()) {
            val center=x+lookX
            if(smile) {
                paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f; paint.strokeCap=Paint.Cap.ROUND
                canvas.drawArc(center-1.7f,9.3f+lookY,center+1.7f,12.7f+lookY,195f,150f,false,paint)
            } else {
                paint.style=Paint.Style.FILL
                val wink=mode==0 && phase in 10000..10400 && i==0
                val h=if(blink||wink) .9f else if(mode==1) 3.5f+sin(t/150.0+i*.8).toFloat()*.7f else if(mode==2) 2.5f else 3.8f
                canvas.drawRoundRect(center-1.05f,10.6f+lookY-h/2,center+1.05f,10.6f+lookY+h/2,1.1f,1.1f,paint)
            }
        }
        paint.xfermode=null; canvas.restoreToCount(layer); canvas.restoreToCount(save)
    }
    override fun setTintList(value: ColorStateList?) { tint=value?:ColorStateList.valueOf(Color.WHITE);invalidateSelf() }
    override fun setTint(value: Int) { setTintList(ColorStateList.valueOf(value)) }
    override fun isStateful()=true
    override fun onStateChange(state: IntArray): Boolean { invalidateSelf();return true }
    override fun setAlpha(alpha: Int) { opacity=alpha;invalidateSelf() }
    override fun setColorFilter(value: ColorFilter?) { filter=value;invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth()=24
    override fun getIntrinsicHeight()=24
}
