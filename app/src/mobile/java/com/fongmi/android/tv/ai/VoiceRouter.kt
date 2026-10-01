package com.fongmi.android.tv.ai

import com.fongmi.android.tv.search.TitleKey
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Local intent rules first; a free catalogue check is used only for bare/ambiguous titles. */
object VoiceRouter {
    enum class Route { SEARCH, AI, LOOKUP }
    data class Plan(val route: Route, val keyword: String)
    private val prefix = Regex("^(?:请)?(?:帮我|给我)?(?:搜索|搜一下|搜|查找|找一下|播放|放一下|我想看|我想要看|我要看|想看|要看|看一下)(?:一下)?")
    private val recommendation = Regex("推荐|换一批|类似|像.+一样|适合|高分|评分|近[几一二三四五六七八九十0-9]+年|最近|[几一二三四五六七八九十0-9]+部|类型|题材|剧情|讲的是|讲述|记不清|叫什么|有没有|好看|悬疑|喜剧|恐怖|动作片|科幻片|爱情片|国产片|下饭")
    private val needsTitleResolution = Regex("第[零一二两三四五六七八九十百0-9]+[部季篇]|续集|续作|前传|后传|外传|别名|俗称|原名")
    private val client = OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()
    fun plan(text: String): Plan {
        val cleaned = text.trim().trim('。', '！', '？', '.', '!', '?')
        val match = prefix.find(cleaned)
        var title = if (match == null) cleaned else cleaned.substring(match.range.last + 1).trim()
        title = title.replace(Regex("^(?:一部|一个)?(?:名字叫|名叫|叫)"), "")
            .replace(Regex("(?:这部电影|这部电视剧|的电影|的电视剧)$"), "").trim().trim('《', '》', '“', '”', '"')
        if (title.isBlank()) return Plan(Route.AI, cleaned)
        if (needsTitleResolution.containsMatchIn(title)) return Plan(Route.AI, title)
        if (recommendation.containsMatchIn(cleaned)) return Plan(Route.LOOKUP, title)
        if (match != null && title.length <= 40 && title !in setOf("电影", "电视剧", "电视", "动画", "综艺")) return Plan(Route.SEARCH, title)
        if (match == null && title.length in 2..20 && !Regex("什么|怎么|为什么|哪|吗|我").containsMatchIn(title)) return Plan(Route.SEARCH, title)
        return Plan(Route.LOOKUP, title)
    }
    /** A failed catalogue request is not a signal to spend AI quota. Caller offers manual choices. */
    fun isFilm(title: String): Boolean {
        val url = "https://movie.douban.com/j/subject_suggest".toHttpUrl().newBuilder().addQueryParameter("q", title).build()
        client.newCall(Request.Builder().url(url).header("Referer", "https://movie.douban.com/").build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("影片资料暂时不可用")
            val list = JSONArray(response.body?.string() ?: throw IOException("影片资料为空"))
            val expected = TitleKey.normalize(title)
            return (0 until list.length()).any { index ->
                val item = list.optJSONObject(index)
                item != null && expected.isNotEmpty() && expected == TitleKey.normalize(item.optString("title"))
            }
        }
    }
}
