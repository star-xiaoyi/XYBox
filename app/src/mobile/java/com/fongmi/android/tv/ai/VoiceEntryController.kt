package com.fongmi.android.tv.ai

import androidx.lifecycle.Lifecycle
import com.fongmi.android.tv.App
import com.fongmi.android.tv.search.VodGroup
import com.fongmi.android.tv.ui.activity.HomeActivity
import com.fongmi.android.tv.ui.activity.VideoActivity
import com.fongmi.android.tv.ui.activity.AiSettingsActivity
import com.fongmi.android.tv.ui.fragment.DiscoverFragment
import com.fongmi.android.tv.utils.LocalProfile
import org.json.JSONArray

/** The same request can stay in the bottom bar or be observed inside the AI conversation. */
class VoiceEntryController(private val host: HomeActivity) {
    private class Pending(val input: String, val profile: String, val askedAt: Long) {
        var status = "正在思考…"
        var raw: String? = null
        var reply: AiReply? = null
        var failure = ""
        var finished = false
        var page: DiscoverFragment? = null
        var turnId: String? = null
    }
    private var active: Pending? = null
    private var model: Qwen? = null
    private var search: AiVideoSearch? = null

    private fun current(pending: Pending) = active === pending && pending.profile == LocalProfile.id() &&
        host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun cancel() = cancelPending(true)

    private fun cancelPending(hide: Boolean) {
        val previous = active
        active = null
        model?.cancel(); model = null
        search?.cancel(); search = null
        previous?.turnId?.let { previous.page?.abortVoiceRequest(it) }
        host.setAiThinking(false)
        if (hide) host.finishVoiceThinking(null)
    }

    /** Returns true even for repeated taps: never append the same question or submit it again. */
    fun openConversation(): Boolean {
        val pending = active ?: return false
        if (!current(pending)) return false
        if (pending.page != null) return true
        val page = host.openVoiceConversation() ?: return false
        if (!current(pending)) return false
        val id = page.beginVoiceRequest(pending.input, pending.status, pending.askedAt, Runnable {
            if (active === pending) cancel()
        })
        pending.page = page
        pending.turnId = id
        pending.reply?.let { reply -> pending.raw?.let { page.updateVoiceReply(id, it, reply) } }
        if (pending.finished) deliverToPage(pending)
        return true
    }

    private fun status(pending: Pending, text: String) {
        if (!current(pending)) return
        pending.status = text
        val id = pending.turnId
        if (id != null) pending.page?.updateVoiceProgress(id, text)
        else host.showVoiceThinking(text)
    }

    private fun deliverToPage(pending: Pending) {
        val id = pending.turnId ?: return
        pending.page?.finishVoiceRequest(id, pending.raw, pending.reply, pending.failure)
        if (active === pending) cancelPending(false)
    }

    private fun finish(pending: Pending, failure: String = "", group: VodGroup? = null) {
        if (!current(pending)) return
        pending.finished = true; pending.failure = failure
        host.setAiThinking(false)
        if (pending.page != null) { deliverToPage(pending); return }
        host.finishVoiceThinking(group != null) {
            if (!current(pending)) return@finishVoiceThinking
            // Opening the conversation cancels this presentation callback, not the request.
            if (pending.page != null) { deliverToPage(pending); return@finishVoiceThinking }
            cancelPending(false)
            if (group != null) VideoActivity.group(host, group)
            else host.showAiVoiceResult(pending.input, pending.raw, pending.reply, failure, pending.askedAt)
        }
    }

    fun start(input: String) {
        cancelPending(false)
        val key = AiCredentials.read()
        if (key.isBlank()) { host.finishVoiceThinking(null); AiSettingsActivity.start(host); return }
        val pending = Pending(input, LocalProfile.id(), System.currentTimeMillis())
        active = pending
        host.setAiThinking(true)
        status(pending, "正在思考…")
        val request = Qwen().also { model = it }
        App.execute {
            try {
                val (raw, reply) = request.chat(key, JSONArray(), input) { text ->
                    App.post { status(pending, text) }
                }
                App.post {
                    if (!current(pending)) return@post
                    pending.raw = raw; pending.reply = reply
                    // A deliberate tap opts into reading the answer and cards on this page.
                    if (pending.page != null || reply.search.isBlank() || reply.films.size != 1) {
                        finish(pending)
                        return@post
                    }
                    val film = reply.films.single()
                    if (AiIdentity.name(film.title) != AiIdentity.name(reply.search)) {
                        finish(pending)
                        return@post
                    }
                    status(pending, "正在匹配片源…")
                    search = AiVideoSearch(update = { groups, running ->
                        if (!running && groups.isEmpty()) finish(pending, "暂未找到可播放片源，可点卡片重试。")
                    }, open = { group -> finish(pending, group = group) }).also { it.start(film) }
                }
            } catch (e: Exception) {
                App.post { finish(pending, e.message ?: "AI 暂时未能完成请求，请重试") }
            }
        }
    }
}
