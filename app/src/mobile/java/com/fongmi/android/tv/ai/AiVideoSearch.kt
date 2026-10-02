package com.fongmi.android.tv.ai

import com.fongmi.android.tv.App
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.bean.Vod
import com.fongmi.android.tv.model.SiteViewModel
import com.fongmi.android.tv.search.SearchTask
import com.fongmi.android.tv.search.VodGroup
import com.fongmi.android.tv.search.VodGrouper
import com.github.catvod.utils.Logger
import java.util.concurrent.Executors

/** Resolve one requested edition in the background, without a second source-picker UI. */
class AiVideoSearch(private val update: (List<VodGroup>, Boolean) -> Unit,
    private val open: (VodGroup) -> Unit) : SearchTask.Callback {
    private var task: SearchTask? = null
    private lateinit var film: AiFilm
    @Volatile private var cancelled = false
    private val details = Executors.newFixedThreadPool(3)
    private val seen = mutableSetOf<String>()
    private var pending = 0
    private var finished = false
    private val timeout = Runnable { finishEmpty() }

    fun start(target: AiFilm) {
        film = target.copy(title = AiTitle.clean(target.title))
        val sites = VodConfig.get().sites.filter { it.isSearchable && !it.isCloudDrive }.toMutableList()
        val home = VodConfig.get().home
        if (sites.remove(home)) sites.add(0, home)
        Logger.d("AiPlayback phase=start title=${film.title} year=${film.year} country=${film.country} kind=${film.kind} sites=${sites.size}")
        App.post(timeout, 22000)
        task = SearchTask.start(sites, AiIdentity.query(film.title), false, this)
    }

    override fun onResult(items: List<Vod>, cost: Long) {
        if (cancelled) return
        for (item in items) {
            if (item.isFolder || item.isAction || AiIdentity.name(item.vodName) != AiIdentity.name(film.title)) continue
            val knownYear = Regex("(?:19|20)[0-9]{2}").find(item.vodYear)?.value.orEmpty()
            if (film.year.isNotBlank() && knownYear.isNotBlank() && film.year != knownYear) continue
            val country = AiIdentity.countryHint(item.vodArea).ifBlank { AiIdentity.countryHint(item.vodName) }
            val requestedCountry = AiIdentity.countryHint(film.country)
            if (country.isNotBlank() && requestedCountry.isNotBlank() && country != requestedCountry) continue
            val key = item.siteKey + ":" + item.vodId
            if (seen.size >= 12 || !seen.add(key)) continue
            pending++
            details.execute {
                val detail = try {
                    if (cancelled) null else SiteViewModel.detail(item.site, item.vodId, false).list.firstOrNull()?.apply {
                        site = item.site
                        if (vodId.isBlank()) vodId = item.vodId
                        if (vodName.isBlank()) vodName = item.vodName
                        if (vodPic.isBlank()) vodPic = item.vodPic
                        if (vodYear.isBlank()) vodYear = item.vodYear
                        if (vodArea.isBlank()) vodArea = item.vodArea
                        if (typeName.isBlank()) typeName = item.typeName
                    }
                } catch (_: Exception) { null }
                App.post {
                    pending--
                    if (cancelled) return@post
                    val matched = detail != null && AiIdentity.matches(film, detail.vodName, detail.vodYear,
                        detail.vodArea, detail.typeName, detail.vodActor)
                    val hasEpisodes = detail?.vodFlags?.any { flag -> !flag.isCloudDrive && flag.episodes.any { it.url.isNotBlank() } } == true
                    Logger.d("AiPlayback phase=detail title=${detail?.vodName ?: item.vodName} year=${detail?.vodYear.orEmpty()} area=${detail?.vodArea.orEmpty()} matched=$matched episodes=$hasEpisodes")
                    if (matched && hasEpisodes) {
                        val groups = VodGrouper(film.title)
                        groups.add(listOf(detail), cost, mutableListOf(), linkedSetOf())
                        val group = groups.sorted().firstOrNull() ?: return@post
                        cancel()
                        update(listOf(group), false)
                        open(group)
                    } else if (finished && pending == 0) finishEmpty()
                }
            }
        }
    }

    override fun onFinish() {
        if (cancelled) return
        finished = true
        if (pending == 0) finishEmpty()
    }

    private fun finishEmpty() {
        if (cancelled) return
        Logger.d("AiPlayback phase=not_found title=${film.title} checked=${seen.size}")
        cancel(); update(emptyList(), false)
    }
    fun cancel() { cancelled = true; App.removeCallbacks(timeout); task?.cancel(); task = null; details.shutdownNow() }
}
