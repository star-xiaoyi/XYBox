package com.fongmi.android.tv.ai

import com.github.catvod.utils.Logger
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AiMetadata(val id: String, val title: String, val year: String, val country: String,
    val kind: String, val actors: List<String>, val genres: List<String>, val pic: String, val rating: Double)

/** Full catalogue search; the five autocomplete suggestions are not a complete film catalogue. */
object AiCatalog {
    private val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, AiMetadata>()
    fun key(film: AiFilm) = listOf(film.title, film.year, film.country, film.kind, film.doubanId).joinToString("|")

    fun resolve(film: AiFilm): AiMetadata? {
        cache[key(film)]?.let { return it }
        return try {
            if (film.doubanId.matches(Regex("[0-9]{4,12}"))) {
                val type = if (AiIdentity.kind(film.kind) == "电影") "movie" else "tv"
                val detail = detail(film.doubanId, type)
                if (detail != null && matches(film, detail)) return detail.also { cache[key(film)] = it }
            }
            val url = "https://m.douban.com/rexxar/api/v2/search/movie".toHttpUrl().newBuilder()
                .addQueryParameter("q", AiIdentity.query(film.title)).addQueryParameter("count", "30").build().toString()
            val rows = get(url)?.optJSONArray("items") ?: return null
            val candidates = (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val type = row.optString("target_type")
                if (type !in setOf("tv", "movie")) return@mapNotNull null
                val item = row.optJSONObject("target") ?: return@mapNotNull null
                val subtitle = item.optString("card_subtitle").split('/').map { it.trim() }
                AiMetadata(item.optString("id"), item.optString("title"), item.optString("year"),
                    subtitle.firstOrNull().orEmpty(), if (subtitle.getOrNull(1).orEmpty().contains("动画")) "动漫" else if (type == "tv") "电视剧" else "电影",
                    subtitle.drop(3).flatMap { it.split(' ') }.filter { it.isNotBlank() },
                    subtitle.getOrNull(1).orEmpty().split(' ').filter { it.isNotBlank() },
                    picture(item.optString("cover_url")), item.optJSONObject("rating")?.optDouble("value", 0.0) ?: 0.0)
            }.filter { matches(film, it) }.distinctBy { it.id }
            if (candidates.size != 1) {
                Logger.d("AiMetadata phase=no_unique_match title=${film.title} year=${film.year} country=${film.country} matches=${candidates.size}")
                return null
            }
            val selected = candidates.single()
            val full = detail(selected.id, if (selected.kind == "电视剧") "tv" else "movie")
            val result = if (full != null && matches(film, full)) full.copy(pic = full.pic.ifBlank { selected.pic }) else selected
            cache[key(film)] = result
            Logger.d("AiMetadata phase=resolved title=${result.title} year=${result.year} country=${result.country} poster=${result.pic.isNotEmpty()}")
            result
        } catch (e: Exception) {
            Logger.d("AiMetadata phase=failed title=${film.title} error=${e.javaClass.simpleName}")
            null
        }
    }

    private fun matches(film: AiFilm, item: AiMetadata) = AiIdentity.matches(film, item.title, item.year, item.country, item.kind, item.actors.joinToString(" "))
    private fun detail(id: String, type: String): AiMetadata? { return try {
        val data = get("https://m.douban.com/rexxar/api/v2/$type/$id") ?: return null
        if (data.optString("title").isBlank()) return null
        val casts = data.optJSONArray("actors") ?: data.optJSONArray("casts")
        val actors = if (casts == null) emptyList() else (0 until casts.length()).mapNotNull { casts.optJSONObject(it)?.optString("name") }
        AiMetadata(id, data.optString("title"), data.optString("year"), jsonStrings(data.optJSONArray("countries")).joinToString(" / "),
            if (jsonStrings(data.optJSONArray("genres")).contains("动画")) "动漫" else if (type == "tv") "电视剧" else "电影", actors, jsonStrings(data.optJSONArray("genres")),
            picture(data.optJSONObject("pic")?.optString("large").orEmpty()), data.optJSONObject("rating")?.optDouble("value", 0.0) ?: 0.0)
    } catch (_: Exception) { null } }
    private fun get(url: String): JSONObject? = client.newCall(Request.Builder().url(url)
        .header("Referer", "https://m.douban.com/").build()).execute().use {
        if (!it.isSuccessful) null else JSONObject(it.body?.string() ?: "{}")
    }
    private fun picture(url: String) = if (url.isBlank()) "" else "$url@Referer=https://m.douban.com/"
}
