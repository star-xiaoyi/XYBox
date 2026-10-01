package com.fongmi.android.tv.ai

import android.util.AtomicFile
import com.fongmi.android.tv.App
import com.fongmi.android.tv.utils.LocalProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AiConversation(val id: String, val title: String, val updated: Long, val messages: String, val renamed: Boolean = false)
/** Local account-scoped conversations; no credentials, recordings or WebDAV export. */
object AiConversations {
    private fun file() = AtomicFile(File(App.get().filesDir, "ai-chat-${LocalProfile.id()}.json"))
    @Synchronized fun list(): List<AiConversation> = try {
        val items = JSONArray(String(file().readFully(), Charsets.UTF_8))
        (0 until items.length()).map { i -> items.getJSONObject(i).let {
            AiConversation(it.getString("id"), it.getString("title"), it.getLong("updated"), it.getJSONArray("messages").toString(), it.optBoolean("renamed"))
        } }.sortedByDescending { it.updated }
    } catch (_: Exception) { emptyList() }
    @Synchronized fun save(id: String, messages: JSONArray) {
        if (messages.length() == 0) return
        val existing = list()
        val previous = existing.find { it.id == id }
        val title = if (previous?.renamed == true) previous.title else messages.optJSONObject(0)?.optString("content").orEmpty().take(36)
        write((existing.filterNot { it.id == id } + AiConversation(id, title, System.currentTimeMillis(), messages.toString(), previous?.renamed == true))
            .sortedByDescending { it.updated }.take(60))
    }
    @Synchronized fun delete(id: String) { write(list().filterNot { it.id == id }) }
    @Synchronized fun rename(id: String, title: String) {
        val name = title.trim().replace('\n', ' ').take(60)
        require(name.isNotBlank()) { "请输入对话名称" }
        val existing = list()
        require(existing.any { it.id == id }) { "对话已不存在" }
        write(existing.map { if (it.id == id) it.copy(title = name, renamed = true) else it })
    }
    private fun write(items: List<AiConversation>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("updated", it.updated).put("messages", JSONArray(it.messages)).put("renamed", it.renamed)) }
        val target = file(); val output = target.startWrite()
        try { output.write(array.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(output) }
        catch (e: Exception) { target.failWrite(output); throw e }
    }
}
