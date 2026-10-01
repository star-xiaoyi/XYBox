package com.fongmi.android.tv.ai

import okhttp3.*
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Qwen duplex ASR, using the same Bailian key as text and web search. */
class CloudSpeech(key: String, factory: WebSocket.Factory = client, private val partial: (String) -> Unit) : AutoCloseable {
    companion object {
        const val MODEL = "qwen-audio-3.1-asr-flash-streaming"
        private val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS).pingInterval(15, TimeUnit.SECONDS)
            .followRedirects(false).retryOnConnectionFailure(false).build()
    }
    private val id = UUID.randomUUID().toString()
    private val started = CountDownLatch(1)
    private val finished = CountDownLatch(1)
    private val closed = AtomicBoolean(false)
    private val sentences = sortedMapOf<Int, String>()
    @Volatile private var failure: String? = null
    @Volatile private var complete = false
    private val socket: WebSocket = factory.newWebSocket(Request.Builder()
        .url("wss://dashscope.aliyuncs.com/api-ws/v1/inference").header("Authorization", "Bearer $key").build(), object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (closed.get()) { webSocket.cancel(); return }
            val parameters = JSONObject().put("format", "pcm").put("sample_rate", 16000)
                .put("vad_model", "near_meeting_16k").put("language_hints", JSONArray().put("zh").put("en"))
            val input = JSONObject().put("context", JSONArray().put(JSONObject().put("role", "user").put("content", JSONArray()
                .put(JSONObject().put("type", "input_text").put("text", "这是影视应用中的语音找片，用户会说电影名、电视剧名、演员名或观看要求。")))))
            val payload = JSONObject().put("task_group", "audio").put("task", "asr").put("function", "recognition")
                .put("model", MODEL).put("parameters", parameters).put("input", input)
            if (!webSocket.send(event("run-task", payload).toString())) fail("语音连接已断开")
        }
        override fun onMessage(webSocket: WebSocket, text: String) {
            if (closed.get()) return
            try {
                val data = JSONObject(text); val header = data.getJSONObject("header")
                if (header.optString("task_id") != id) return
                when (header.optString("event")) {
                    "task-started" -> started.countDown()
                    "result-generated" -> {
                        val sentence = data.optJSONObject("payload")?.optJSONObject("output")?.optJSONObject("sentence") ?: return
                        if (sentence.optBoolean("heartbeat")) return
                        val index = sentence.optInt("sentence_id", sentence.optInt("begin_time", 0))
                        synchronized(sentences) { sentences[index] = sentence.optString("text") }
                        partial(transcript())
                    }
                    "task-finished" -> { complete = true; finished.countDown() }
                    "task-failed" -> fail("千问语音服务暂不可用，请检查百炼密钥、模型权限和余额")
                }
            } catch (_: Exception) { fail("无法读取千问语音结果，请重试") }
        }
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(when (response?.code) { 401, 403 -> "百炼密钥无效或未开通语音模型，请检查 AI 设置"; 429 -> "语音服务限流，请稍后重试"; else -> "语音连接失败，请检查网络" })
        }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { if (!complete && !closed.get()) fail("语音连接提前结束，请重试") }
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (!complete && !closed.get()) fail("语音连接已断开，请重试") }
    })
    private fun event(action: String, payload: JSONObject) = JSONObject().put("header", JSONObject()
        .put("action", action).put("task_id", id).put("streaming", "duplex")).put("payload", payload)
    private fun fail(reason: String) { if (failure == null) failure = reason; started.countDown(); finished.countDown() }
    private fun checkFailure() { failure?.let { throw IOException(it) }; if (closed.get()) throw IOException("已取消") }
    fun awaitReady() { if (!started.await(10, TimeUnit.SECONDS)) throw IOException("语音服务连接超时"); checkFailure() }
    fun send(pcm: ByteArray) {
        checkFailure()
        if (started.count != 0L || complete) throw IOException("语音服务尚未就绪或已结束")
        if (socket.queueSize() > 320000 || !socket.send(pcm.toByteString())) throw IOException("网络过慢，语音发送已停止，请重试")
    }
    fun finish(): String {
        checkFailure()
        if (!socket.send(event("finish-task", JSONObject().put("input", JSONObject())).toString())) throw IOException("语音连接已断开")
        if (!finished.await(15, TimeUnit.SECONDS)) throw IOException("等待语音结果超时，请重试")
        checkFailure()
        if (!complete) throw IOException("语音识别未完成")
        return transcript()
    }
    private fun transcript() = synchronized(sentences) { sentences.values.joinToString("").trim() }
    override fun close() { if (closed.compareAndSet(false, true)) { started.countDown(); finished.countDown(); socket.cancel() } }
}
