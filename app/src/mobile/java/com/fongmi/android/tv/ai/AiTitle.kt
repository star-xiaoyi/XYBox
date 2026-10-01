package com.fongmi.android.tv.ai

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/** Display punctuation must never become part of a provider's search query. */
object AiTitle {
    @JvmStatic fun clean(value: String): String = value.replace(Regex("[《》〈〉]"), "")
        .trim().trim('"', '“', '”', '「', '」', '*', '`').replace(Regex("\\s+"), " ").trim().take(100)
    private fun key(value: String) = Normalizer.normalize(clean(value), Normalizer.Form.NFKC)
        .lowercase().replace(Regex("[\\p{P}\\p{S}\\s]"), "")
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS).followRedirects(false).build()

    /** A model saying 'confirmed' is insufficient evidence that its invented season title exists. */
    fun exists(title: String, year: String): Boolean {
        val url = "https://movie.douban.com/j/subject_suggest".toHttpUrl().newBuilder()
            .addQueryParameter("q", clean(title)).build()
        return try {
            client.newCall(Request.Builder().url(url).header("Referer", "https://movie.douban.com/").build()).execute().use { response ->
                if (!response.isSuccessful) return false
                val items = JSONArray(response.body?.string() ?: return false)
                (0 until items.length()).any { i ->
                    val item = items.optJSONObject(i)
                    item != null && key(item.optString("title")) == key(title) &&
                        (year.isBlank() || item.optString("year").isBlank() || item.optString("year").take(4) == year.take(4))
                }
            }
        } catch (_: Exception) { false }
    }
}
