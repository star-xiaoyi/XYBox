package com.fongmi.android.tv.ai

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.fongmi.android.tv.App
import com.fongmi.android.tv.utils.LocalProfile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate private storage: credentials never enter WebDAV or the legacy backup preferences. */
object AiCredentials {
    private const val ALIAS = "xybox_deepseek_v1"
    private val prefs get() = App.get().getSharedPreferences("ai_credentials", 0)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun slot(provider: String) = if (provider == "deepseek") LocalProfile.id() else LocalProfile.id() + ":" + provider
    @Synchronized fun read(provider: String = "speech"): String {
        val stored = prefs.getString(slot(provider), "").orEmpty()
        if (stored.isEmpty()) return ""
        return try {
            val parts = stored.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { "" }
    }
    @Synchronized fun save(value: String, provider: String = "speech") {
        if (value.isBlank()) { check(prefs.edit().remove(slot(provider)).commit()); return }
        require(value.length in 10..512 && value.none { it.isWhitespace() || it.code !in 33..126 }) { "密钥格式不正确" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString(slot(provider), encrypted).commit()) { "密钥保存失败" }
    }
}

data class AiFilm(val title: String, val year: String, val reason: String,
    val country: String = "", val kind: String = "", val actors: List<String> = emptyList(),
    val originalTitle: String = "", val doubanId: String = "")
data class AiSource(val label: String, val url: String, val readAt: String)
data class AiReply(val message: String, val films: List<AiFilm>, val search: String = "", val sources: List<AiSource> = emptyList(),
    val searchYear: String = "", val country: String = "", val kind: String = "", val actors: List<String> = emptyList())

internal fun jsonStrings(array: JSONArray?): List<String> = if (array == null) emptyList() else
    (0 until array.length()).map { array.optString(it).trim() }.filter { it.isNotEmpty() }.take(8)

internal fun message(role: String, content: String) = JSONObject().put("role", role).put("content", content)
internal fun parse(raw: String): AiReply {
    val obj = JSONObject(raw)
    val films = obj.optJSONArray("films") ?: JSONArray()
    val items = (0 until films.length()).mapNotNull { index ->
        val film = films.optJSONObject(index) ?: return@mapNotNull null
        val title = AiTitle.clean(film.optString("title"))
        val requiredCountry = AiIdentity.countryHint(obj.optString("country"))
        val filmCountry = AiIdentity.countryHint(film.optString("country"))
        if (title.isBlank() || (requiredCountry.isNotBlank() && filmCountry.isNotBlank() && requiredCountry != filmCountry)) null else AiFilm(title, film.optString("year").take(4), film.optString("reason"),
            film.optString("country").ifBlank { obj.optString("country") }, film.optString("type").ifBlank { obj.optString("type") },
            jsonStrings(film.optJSONArray("actors") ?: obj.optJSONArray("actors")),
            film.optString("original_title"), film.optString("douban_id"))
    }.distinctBy { AiIdentity.name(it.title) + it.year + it.country }.take(5)
    val text = obj.optString("message").trim()
    val search = AiTitle.clean(obj.optString("search"))
    val sources = obj.optJSONArray("sources") ?: JSONArray()
    val links = (0 until sources.length()).mapNotNull { index ->
        val source = sources.optJSONObject(index) ?: return@mapNotNull null
        val url = source.optString("url")
        if (!safeSourceUrl(url)) null
        else AiSource(source.optString("label"), url, source.optString("read_at"))
    }
    if (text.isEmpty() && items.isEmpty() && search.isEmpty()) throw IOException("AI 回复格式不完整，请重试")
    val cards = if (items.isEmpty() && search.isNotBlank()) listOf(AiFilm(search, obj.optString("search_year").take(4), "",
        obj.optString("country"), obj.optString("type"), jsonStrings(obj.optJSONArray("actors")))) else items
    return AiReply(text, cards, search, links, obj.optString("search_year").take(4), obj.optString("country"),
        obj.optString("type"), jsonStrings(obj.optJSONArray("actors")))
}

internal fun safeSourceUrl(value: String): Boolean = try {
    val uri = java.net.URI(value)
    uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null
} catch (_: Exception) { false }
