package com.fongmi.android.tv.ai

import java.text.Normalizer

/** Version constraints are carried from the conversation through catalogue and provider lookup. */
object AiIdentity {
    private val season = Regex("第([0-9零一二两三四五六七八九十]{1,3})季")
    private val edition = Regex("[（(]?(?:美|英|日|韩|俄)版[）)]?")
    private fun number(text: String): Int = text.toIntOrNull() ?: run {
        val digits = "零一二三四五六七八九"; val t = text.replace('两', '二'); val at = t.indexOf('十')
        if (at < 0) digits.indexOf(t.first()) else (if (at == 0) 1 else digits.indexOf(t.first())) * 10 +
            (if (at == t.lastIndex) 0 else digits.indexOf(t.last()))
    }
    fun name(value: String): String = season.replace(edition.replace(Normalizer.normalize(AiTitle.clean(value), Normalizer.Form.NFKC), "")) {
        number(it.groupValues[1]).let { n -> if (n == 1) "" else n.toString() }
    }.lowercase().replace(Regex("[\\s\\p{P}\\p{S}]+"), "")
    fun query(value: String): String = edition.replace(AiTitle.clean(value), "").replace(Regex("第[一1]季"), "").trim()
    fun countryHint(text: String): String = when {
        Regex("美剧|美版|美国|\\bUSA?\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "美国"
        Regex("英剧|英版|英国|\\bUK\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "英国"
        Regex("韩剧|韩版|韩国").containsMatchIn(text) -> "韩国"
        Regex("日剧|日版|日本").containsMatchIn(text) -> "日本"
        Regex("国产|大陆|中国|内地").containsMatchIn(text) -> "中国"
        else -> ""
    }
    fun kind(text: String): String = when {
        Regex("动漫|动画|番剧").containsMatchIn(text) -> "动漫"
        Regex("综艺|真人秀").containsMatchIn(text) -> "综艺"
        Regex("电影|影片|剧情片|喜剧片").containsMatchIn(text) -> "电影"
        Regex("剧|连续").containsMatchIn(text) -> "电视剧"
        else -> ""
    }
    fun matches(film: AiFilm, title: String, year: String, area: String, type: String, actors: String): Boolean {
        if (name(title) != name(film.title) && (film.originalTitle.isBlank() || name(title) != name(film.originalTitle))) return false
        val expectedYear = film.year.toIntOrNull() ?: 0
        val actualYear = Regex("(?:19|20)[0-9]{2}").find(year)?.value?.toIntOrNull() ?: 0
        if (expectedYear > 0 && actualYear > 0 && expectedYear != actualYear) return false
        val expectedCountry = countryHint(film.country)
        val actualCountry = countryHint(area).ifBlank { countryHint(title) }
        if (expectedCountry.isNotBlank() && actualCountry.isNotBlank() && expectedCountry != actualCountry) return false
        val expectedKind = kind(film.kind); val actualKind = kind(type)
        if (expectedKind.isNotBlank() && actualKind.isNotBlank() && expectedKind != actualKind) return false
        val actorMatch = film.actors.any { name(it).isNotBlank() && name(actors).contains(name(it)) }
        if (expectedYear > 0 && actualYear == 0 && !actorMatch) return false
        if (expectedCountry.isNotBlank() && actualCountry.isBlank() && !(actorMatch || (expectedYear > 0 && expectedYear == actualYear && actualKind == expectedKind && actualKind.isNotBlank()))) return false
        if (expectedKind.isNotBlank() && actualKind.isBlank() && !actorMatch && expectedYear == 0) return false
        return true
    }
}
