package com.fongmi.android.tv.ai

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import com.fongmi.android.tv.ui.activity.AiSettingsActivity
import com.fongmi.android.tv.ui.activity.HomeActivity
import com.fongmi.android.tv.utils.Notify
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class VoiceHoldController(private val activity: HomeActivity, private val onText: (String) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val capture = Executors.newSingleThreadExecutor()
    private val inference = Executors.newSingleThreadExecutor()
    private var downX = 0f; private var downY = 0f
    private var holding = false; private var longPress = false; private var moved = false
    private var cancelled = false; private var busy = false
    private var source: View? = null
    private var partial = ""
    @Volatile private var cloud: CloudSpeech? = null
    @Volatile private var recording = false
    @Volatile private var generation = 0
    private var overlay: VoiceBubbleView? = null
    private val permission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Notify.show(if (granted) "麦克风已允许，请重新长按说话" else "未允许麦克风，仍可输入文字找片")
    }
    private val start = Runnable { if (holding && !moved) { longPress = true; begin() } }
    fun startHold(view: View, x: Float, y: Float) {
        handler.removeCallbacks(start)
        source = view; downX = x; downY = y
        holding = true; longPress = true; moved = false
        begin()
    }
    fun touch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                source = view; downX = event.x; downY = event.y
                holding = true; longPress = false; moved = false
                handler.postDelayed(start, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (longPress && recording) {
                    cancelled = downY - event.y > 64 * view.resources.displayMetrics.density
                    overlay?.update(partial, cancelled)
                } else if (!longPress && (abs(event.x - downX) > ViewConfiguration.get(activity).scaledTouchSlop ||
                    abs(event.y - downY) > ViewConfiguration.get(activity).scaledTouchSlop)) { moved = true; handler.removeCallbacks(start) }
            }
            MotionEvent.ACTION_UP -> {
                holding = false; handler.removeCallbacks(start)
                if (longPress && recording) {
                    if (cancelled) cancel() else { recording = false; overlay?.update(partial, false, true) }
                } else if (!longPress && !moved) view.performClick()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { moved = true; cancel() }
        }
        return true
    }
    private fun begin() {
        if (busy) { Notify.show("正在识别，请稍候"); return }
        val speechKey = AiCredentials.read()
        if (speechKey.isEmpty()) { AiSettingsActivity.start(activity); Notify.show("请先填写百炼 API Key"); return }
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { permission.launch(Manifest.permission.RECORD_AUDIO); return }
        val token = ++generation
        recording = true; busy = true; cancelled = false; partial = ""
        activity.setAiRecording(true)
        source?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showOverlay()
        val chunks = LinkedBlockingQueue<ByteArray>(310) // bounded to the 30-second recording limit
        capture.execute {
            var recorder: AudioRecord? = null
            try {
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 2, 32000))
                check(recorder.state == AudioRecord.STATE_INITIALIZED)
                recorder.startRecording(); check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                val buffer = ShortArray(1600); var count = 0
                while (recording && token == generation && count < 480000) {
                    val n = recorder.read(buffer, 0, minOf(buffer.size, 480000 - count)); check(n > 0)
                    count += n
                    val samples = FloatArray(n) { buffer[it] / 32768f }
                    val pcm = ByteArray(n * 2)
                    for (i in 0 until n) { pcm[i * 2] = buffer[i].toByte(); pcm[i * 2 + 1] = (buffer[i].toInt() shr 8).toByte() }
                    check(chunks.offer(pcm))
                    val level = (samples.sumOf { (it * it).toDouble() } / n).let { ((20 * kotlin.math.log10(kotlin.math.sqrt(it).coerceAtLeast(0.00001)) + 60) / 45).toFloat().coerceIn(0f, 1f) }
                    handler.post { if (token == generation) overlay?.setLevel(level) }
                }
                recorder.stop()
                handler.post {
                    if (token == generation) {
                        if (cancelled) cancel()
                        else {
                            recording = false; overlay?.setLevel(0f); overlay?.update(partial, false, true)
                            chunks.offer(ByteArray(0))
                        }
                    }
                }
            } catch (_: Exception) { fail(token, "无法录音，请检查麦克风是否被占用") }
            finally { recorder?.release() }
        }
        inference.execute {
            try {
                CloudSpeech(speechKey) { text ->
                    handler.post { if (token == generation) { partial = text; overlay?.update(text, cancelled, !recording) } }
                }.use { session ->
                    cloud = session
                    if (token != generation) return@execute
                    session.awaitReady()
                    while (token == generation) {
                        val chunk = chunks.poll(150, TimeUnit.MILLISECONDS) ?: continue
                        if (chunk.isEmpty()) {
                            val result = session.finish()
                            handler.post {
                                if (token != generation) return@post
                                busy = false; recording = false; hideOverlay(false)
                                if (result.isBlank()) Notify.show("没有识别清楚，请重试或输入文字") else onText(result)
                            }
                            break
                        }
                        session.send(chunk)
                    }
                }
            } catch (e: Exception) { fail(token, if (e is java.io.IOException) e.message ?: "语音网络异常" else "识别未完成，请重试") }
            finally { cloud = null }
        }
    }
    private fun fail(token: Int, text: String) { handler.post { if (token == generation) { cancel(); Notify.show(text) } } }
    private fun showOverlay() {
        val root = activity.findViewById<FrameLayout>(android.R.id.content)
        val bubble = VoiceBubbleView(activity, root)
        root.addView(bubble, FrameLayout.LayoutParams(-1, -1))
        overlay = bubble
        bubble.alpha = 0f
        bubble.animate().alpha(1f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
    }
    private fun hideOverlay(cancelled: Boolean) {
        activity.setAiRecording(false)
        val bubble = overlay ?: return; overlay = null
        bubble.animate().cancel()
        bubble.animate().alpha(0f)
            .setDuration(if (cancelled) 160 else 180).withEndAction { (bubble.parent as? ViewGroup)?.removeView(bubble) }.start()
    }
    fun cancel() { generation++; holding = false; handler.removeCallbacks(start); cloud?.close(); cancelled = true; recording = false; busy = false; hideOverlay(true) }
    fun destroy() { cancel(); capture.shutdownNow(); inference.shutdownNow() }
}
