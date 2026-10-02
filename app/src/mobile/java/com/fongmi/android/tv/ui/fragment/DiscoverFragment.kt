package com.fongmi.android.tv.ui.fragment

import android.os.Bundle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fongmi.android.tv.ui.custom.AiPageContainer
import com.fongmi.android.tv.ui.custom.MainPageHeaderStyle
import com.fongmi.android.tv.ui.custom.ProgressiveGlassSurface
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import kotlin.math.roundToInt
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.viewinterop.AndroidView
import androidx.viewbinding.ViewBinding
import com.fongmi.android.tv.App
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ai.*
import com.fongmi.android.tv.api.Douban
import com.fongmi.android.tv.ui.activity.AiSettingsActivity
import com.fongmi.android.tv.ui.activity.HomeActivity
import com.fongmi.android.tv.ui.activity.VideoActivity
import com.fongmi.android.tv.search.VodGroup
import com.github.catvod.utils.Logger
import com.fongmi.android.tv.ui.base.BaseFragment
import com.fongmi.android.tv.utils.ImgUtil
import com.fongmi.android.tv.utils.LocalProfile
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.Executors

/** The former discovery placeholder is now the mobile AI tab. */
class DiscoverFragment : BaseFragment() {
    private data class Turn(val question: String, val reply: AiReply? = null, val id: String = java.util.UUID.randomUUID().toString(), val askedAt: Long = System.currentTimeMillis())
    private var turns by mutableStateOf(listOf<Turn>())
    private var query by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var progress by mutableStateOf("正在思考…")
    private var routing by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var configured by mutableStateOf(false)
    private var pageVisible by mutableStateOf(true)
    private var glassNavigation by mutableStateOf(true)
    private var details by mutableStateOf(mapOf<String, AiMetadata>())
    private var profile = ""
    private var history = JSONArray()
    private var generation = 0
    private var service: Qwen? = null
    private var voiceTurnId: String? = null
    private var voiceCancel: Runnable? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val metadata = Executors.newFixedThreadPool(2)
    private var drawer by mutableStateOf(0f)
    private var historyQuery by mutableStateOf("")
    private var historyMenu by mutableStateOf<AiConversation?>(null)
    private var historyMenuPosition = IntOffset.Zero
    private var renameTarget by mutableStateOf<AiConversation?>(null)
    private var deleteTarget by mutableStateOf<AiConversation?>(null)
    private var renameText by mutableStateOf("")
    private var historyError by mutableStateOf("")
    private var conversations by mutableStateOf(listOf<AiConversation>())
    private var conversationId = java.util.UUID.randomUUID().toString()
    private var topInset by mutableIntStateOf(0)
    private var bottomInset by mutableIntStateOf(0)
    private var keyboardBottom by mutableIntStateOf(0)
    private var dragging by mutableStateOf(false)
    private var navigationHeight by mutableIntStateOf(0)
    private var entryGeneration by mutableIntStateOf(0)
    private var sourceSearch: AiVideoSearch? = null
    private var sourceGeneration = 0
    private var sourceTurnId by mutableStateOf("")
    private var sourceGroups by mutableStateOf(listOf<VodGroup>())
    private var sourceSearching by mutableStateOf(false)
    private var sourceTitle by mutableStateOf("")
    private var sourceError by mutableStateOf("")
    private val metadataSearches = mutableMapOf<String, AiVideoSearch>()
    private val metadataQueue = java.util.ArrayDeque<Pair<AiFilm, Int>>()

    fun updateNavigationHeight(height: Int) { navigationHeight = height }

    /** A tab click must restore a usable page immediately, even without a subsequent gesture. */
    fun onPageSelected() {
        drawer = 0f; dragging = false; historyMenu = null; entryGeneration++
        pageVisible = true
        syncNavigationHeight()
    }

    private fun syncNavigationHeight() {
        (activity as? HomeActivity)?.let { navigationHeight = it.bottomNavigationHeight }
    }

    fun updateSystemInsets(top: Int, bottom: Int) { topInset = top; bottomInset = bottom }

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): ViewBinding {
        pageVisible = !isHidden
        (activity as? HomeActivity)?.let {
            updateSystemInsets(it.systemBarTopInset, it.systemBarBottomInset)
            navigationHeight = it.bottomNavigationHeight
        }
        val view = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { Screen() }
        }
        val root = android.widget.FrameLayout(requireContext())
        root.addView(view, android.widget.FrameLayout.LayoutParams(-1, -1))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            topInset = bars.top; bottomInset = bars.bottom
            keyboardBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            insets
        }
        androidx.core.view.ViewCompat.setWindowInsetsAnimationCallback(root,
            object : androidx.core.view.WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(insets: androidx.core.view.WindowInsetsCompat,
                    runningAnimations: MutableList<androidx.core.view.WindowInsetsAnimationCompat>): androidx.core.view.WindowInsetsCompat {
                    keyboardBottom = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                    return insets
                }
            })
        return ViewBinding { root }
    }

    override fun onResume() { super.onResume(); pageVisible = !isHidden; syncNavigationHeight(); refreshIdentity() }
    override fun onPause() { pageVisible = false; cancelVoiceRequest(); cancelSources(); cancelMetadataSearches(); super.onPause() }
    override fun onHiddenChanged(hidden: Boolean) { super.onHiddenChanged(hidden); pageVisible = !hidden; if (!hidden) refreshIdentity() else { cancelVoiceRequest(); cancelSources(); cancelMetadataSearches() } }
    private fun refreshIdentity() {
        if (profile != LocalProfile.id()) { reset(); profile = LocalProfile.id() }
        conversations = AiConversations.list()
        configured = AiCredentials.read().isNotEmpty()
        glassNavigation = (activity as? HomeActivity)?.isGlassNavigationEnabled ?: true
    }
    private fun reset() {
        cancelVoiceRequest()
        cancelMetadataSearches()
        cancelSources(); sourceTurnId = ""; sourceGroups = emptyList(); sourceError = ""
        generation++; service?.cancel(); service = null; busy = false; routing = false
        history = JSONArray(); turns = emptyList(); details = emptyMap(); error = ""; query = ""
        conversationId = java.util.UUID.randomUUID().toString(); drawer = 0f
        historyMenu = null; renameTarget = null; deleteTarget = null; historyError = ""
    }
    fun acceptVoice(text: String) {
        refreshIdentity(); editQuery(text)
        if (!busy) send()
    }
    fun acceptResolvedVoice(text: String, raw: String?, reply: AiReply?, failure: String, askedAt: Long) {
        refreshIdentity()
        reset()
        if (reply == null || raw == null) { query = text; error = failure; return }
        val turn = Turn(text, reply, askedAt = askedAt)
        turns = listOf(turn)
        history.put(message("user", text).put("asked_at", turn.askedAt)).put(message("assistant", raw))
        try { AiConversations.save(conversationId, history); conversations = AiConversations.list() }
        catch (_: Exception) { error = "本次对话未能保存到本机" }
        if (failure.isNotBlank()) error = failure
        reply.films.forEach { enrich(it, generation, turn.id) }
    }

    /** Observe an already running request; no model call is made by these methods. */
    fun beginVoiceRequest(text: String, status: String, askedAt: Long, cancel: Runnable): String {
        refreshIdentity(); reset(); onPageSelected()
        val turn = Turn(text, askedAt = askedAt)
        turns = listOf(turn); progress = status; busy = true
        voiceTurnId = turn.id; voiceCancel = cancel
        return turn.id
    }

    fun updateVoiceProgress(id: String, status: String) {
        if (voiceTurnId == id && profile == LocalProfile.id()) progress = status
    }

    fun updateVoiceReply(id: String, raw: String, reply: AiReply) {
        if (voiceTurnId != id || profile != LocalProfile.id()) return
        val turn = turns.find { it.id == id } ?: return
        if (turn.reply != null) return
        turns = turns.map { if (it.id == id) it.copy(reply = reply) else it }
        history.put(message("user", turn.question).put("asked_at", turn.askedAt)).put(message("assistant", raw))
        try { AiConversations.save(conversationId, history); conversations = AiConversations.list() }
        catch (_: Exception) { error = "本次对话未能保存到本机" }
        reply.films.forEach { enrich(it, generation, id) }
    }

    fun finishVoiceRequest(id: String, raw: String?, reply: AiReply?, failure: String) {
        if (voiceTurnId != id || profile != LocalProfile.id()) return
        if (raw != null && reply != null) updateVoiceReply(id, raw, reply)
        else turns.find { it.id == id }?.let { query = it.question; turns = turns.filterNot { item -> item.id == id } }
        busy = false; voiceTurnId = null; voiceCancel = null
        if (failure.isNotBlank()) error = failure
    }

    fun abortVoiceRequest(id: String) {
        if (voiceTurnId != id) return
        busy = false; voiceTurnId = null; voiceCancel = null
        turns.find { it.id == id && it.reply == null }?.let {
            query = it.question; turns = turns.filterNot { item -> item.id == id }
        }
    }

    private fun cancelVoiceRequest() {
        val id = voiceTurnId ?: return
        val cancel = voiceCancel
        abortVoiceRequest(id)
        cancel?.run()
    }

    private fun editQuery(text: String) {
        if (sourceSearching) cancelSources()
        if (routing) {
            generation++
            routing = false
            error = ""
        }
        query = text.take(2000)
    }
    private fun send() {
        val input = query.trim()
        if (input.isEmpty() || busy || routing) return
        cancelSources()
        cancelMetadataSearches()
        val key = AiCredentials.read()
        if (key.isEmpty()) { AiSettingsActivity.start(requireActivity()); return }
        val token = ++generation
        val identity = LocalProfile.id()
        val request = Qwen().also { service = it }
        val context = JSONArray(history.toString())
        val pending = Turn(input)
        turns = (turns + pending).takeLast(20); query = ""; error = ""; progress = "正在思考…"; busy = true
        worker.execute {
            try {
                val (raw, reply) = request.chat(key, context, input) { status ->
                    App.post { if (token == generation && identity == LocalProfile.id()) progress = status }
                }
                App.post {
                    if (token != generation || identity != LocalProfile.id()) return@post
                    history.put(message("user", input).put("asked_at", pending.askedAt)).put(message("assistant", raw))
                    while (history.length() > 40) history.remove(0)
                    try { AiConversations.save(conversationId, history); conversations = AiConversations.list() } catch (_: Exception) { error = "本次对话未能保存到本机" }
                    val completed = pending.copy(reply = reply)
                    turns = turns.dropLast(1) + completed; busy = false
                    reply.films.forEach { enrich(it, token, completed.id) }
                    if (reply.search.isNotEmpty() && isResumed && !isHidden) search(reply.search, reply.searchYear, completed.id)
                }
            } catch (e: Exception) {
                App.post {
                    if (token != generation || identity != LocalProfile.id()) return@post
                    busy = false; turns = turns.dropLast(1); query = input
                    error = when (e) {
                        is java.net.SocketTimeoutException -> "连接超时，输入已保留，请重试"
                        is IOException -> e.message ?: "网络连接失败，请重试"
                        else -> "暂时无法读取 AI 回复，请重试"
                    }
                }
            }
        }
    }
    private fun contextualFilm(film: AiFilm, turnId: String): AiFilm {
        val turn = turns.find { it.id == turnId }
        return film.copy(country = AiIdentity.countryHint(turn?.question.orEmpty()).ifBlank { film.country.ifBlank { turn?.reply?.country.orEmpty() } },
            kind = film.kind.ifBlank { turn?.reply?.kind.orEmpty().ifBlank { AiIdentity.kind(turn?.question.orEmpty()) } },
            actors = film.actors.ifEmpty { turn?.reply?.actors.orEmpty() })
    }

    private fun enrich(film: AiFilm, token: Int, turnId: String) {
        val requested = contextualFilm(film, turnId)
        metadata.execute {
            if (token != generation) return@execute
            val detail = AiCatalog.resolve(requested)
            App.post {
                if (token != generation) return@post
                if (detail != null) details = details + (AiCatalog.key(requested) to detail)
                if ((detail?.pic.isNullOrBlank() || detail?.summary.isNullOrBlank()) && pageVisible) findFallbackMetadata(requested, token)
            }
        }
    }

    private fun findFallbackMetadata(film: AiFilm, token: Int) {
        val key = AiCatalog.key(film)
        if (key in metadataSearches) return
        if (metadataSearches.size >= 2) { metadataQueue.add(film to token); return }
        val request = AiVideoSearch(update = { _, running -> if (!running) {
            metadataSearches.remove(key)
            while (metadataQueue.isNotEmpty() && metadataSearches.size < 2) {
                val next = metadataQueue.removeFirst()
                if (next.second == generation) findFallbackMetadata(next.first, next.second)
            }
        } }, open = { group ->
            if (token == generation) {
                val vod = group.first().vod
                // This poster belongs to an edition-checked provider result; never label it as Douban data.
                val fallback = AiMetadata("", vod.vodName, vod.vodYear, vod.vodArea,
                    vod.typeName, vod.vodActor.split(Regex("[/、,，]")).filter { it.isNotBlank() }, emptyList(), vod.vodPic, 0.0, vod.vodContent.replace(Regex("<[^>]+>"), "").trim())
                val known = details[key]
                details = details + (key to (known?.copy(pic = known.pic.ifBlank { fallback.pic },
                    summary = known.summary.ifBlank { fallback.summary }) ?: fallback))
            }
        })
        metadataSearches[key] = request
        request.start(film)
    }

    private fun cancelMetadataSearches() {
        metadataSearches.values.toList().forEach { it.cancel() }
        metadataSearches.clear()
        metadataQueue.clear()
    }
    private fun cancelSources() {
        sourceGeneration++; sourceSearch?.cancel(); sourceSearch = null; sourceSearching = false
    }

    private fun search(title: String, year: String = "", turnId: String = turns.lastOrNull()?.id.orEmpty()) {
        val reply = turns.find { it.id == turnId }?.reply
        val film = reply?.films?.firstOrNull { AiIdentity.name(it.title) == AiIdentity.name(title) && (year.isBlank() || it.year == year) }
            ?: AiFilm(title, year, "", reply?.country.orEmpty(), reply?.kind.orEmpty(), reply?.actors.orEmpty())
        searchFilm(film, turnId)
    }

    private fun searchFilm(film: AiFilm, turnId: String) {
        cancelVoiceRequest()
        val requested = contextualFilm(film, turnId)
        val info = details[AiCatalog.key(requested)]
        val target = if (info == null) requested else requested.copy(title = info.title, year = requested.year.ifBlank { info.year },
            country = requested.country.ifBlank { info.country }, kind = requested.kind.ifBlank { info.kind }, actors = info.actors.ifEmpty { requested.actors })
        if (sourceSearching && sourceTurnId == turnId && sourceTitle == target.title) return
        metadataSearches.remove(AiCatalog.key(requested))?.cancel()
        cancelSources()
        val token = sourceGeneration; val identity = LocalProfile.id()
        sourceTitle = target.title; sourceTurnId = turnId; sourceGroups = emptyList(); sourceError = ""; sourceSearching = true
        sourceSearch = AiVideoSearch(update = { groups, running ->
            if (token == sourceGeneration && identity == LocalProfile.id()) {
                sourceGroups = groups; sourceSearching = running
                sourceError = if (!running && groups.isEmpty()) "暂未找到这个版本的可播放资源，请稍后重试。" else ""
            }
        }, open = { group ->
            if (token == sourceGeneration) sourceSearching = false
            if (token == sourceGeneration && identity == LocalProfile.id() && isResumed && !isHidden && drawer == 0f) {
                VideoActivity.group(requireActivity(), group)
            }
        }).also { it.start(target) }
    }
    private fun stop() {
        cancelVoiceRequest()
        cancelSources()
        generation++; service?.cancel(); busy = false
        turns.lastOrNull()?.takeIf { it.reply == null }?.let { query = it.question; turns = turns.dropLast(1) }
    }

    fun closeHistoryIfOpen(): Boolean {
        if (drawer <= 0f) return false
        drawer = 0f; return true
    }
    private fun openConversation(item: AiConversation) {
        cancelVoiceRequest()
        cancelMetadataSearches()
        cancelSources(); sourceGroups = emptyList(); sourceTurnId = ""
        generation++; service?.cancel(); busy = false; routing = false
        conversationId = item.id; history = JSONArray(item.messages)
        val restored = mutableListOf<Turn>()
        var question = ""
        var askedAt = 0L
        for (i in 0 until history.length()) {
            val message = history.getJSONObject(i)
            if (message.optString("role") == "user") {
                question = message.optString("content")
                askedAt = message.optLong("asked_at", 0L)
            }
            else if (message.optString("role") == "assistant") {
                try { restored += Turn(question, parse(message.optString("content")), askedAt = askedAt) } catch (_: Exception) { }
            }
        }
        turns = restored; query = ""; error = ""; drawer = 0f
        turns.takeLast(3).forEach { turn -> turn.reply?.films.orEmpty().forEach { enrich(it, generation, turn.id) } }
    }
    @Composable private fun Screen() {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val top = with(density) { topInset.toDp() }
        val bottom = with(density) { bottomInset.toDp() }
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val slide by key(entryGeneration) {
            androidx.compose.animation.core.animateFloatAsState(drawer,
                androidx.compose.animation.core.tween(if (dragging) 0 else 360), label = "history")
        }
        // pointerInput survives tab entries; never capture a replaced animation State in its coroutine.
        val latestSlide by rememberUpdatedState(slide)
        LaunchedEffect(busy, pageVisible) { if (pageVisible) (activity as? HomeActivity)?.setAiThinking(busy) }
        val historyBackground = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1D1E20) else Color(0xFFF1F2F4)
        val underlayShade = .42f * (1f - slide.coerceIn(0f, 1f))
        val underlay = Color.Black.copy(alpha = underlayShade).compositeOver(historyBackground)
        // Blend the whole stationary background once; the history column stays transparent.
        BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().background(underlay)) {
            val travel = with(density) { minOf(maxWidth * .84f, 380.dp).toPx() }
            val pageWidth = with(density) { maxWidth.roundToPx() }
            val pageHeight = with(density) { maxHeight.roundToPx() }
            val cardScale = 1f - .05f * slide
            val cardWidth = maxWidth * cardScale
            val cardHeight = maxHeight * cardScale
            val cardModifier = Modifier.offset {
                IntOffset((slide * travel).roundToInt(), ((pageHeight - pageHeight * cardScale) / 2f).roundToInt())
            }.requiredSize(cardWidth, cardHeight)
            LaunchedEffect((slide * 4).roundToInt(), pageVisible) {
                if (pageVisible) Logger.d("AiDrawer progress=$slide dragging=$dragging width=$maxWidth height=$maxHeight cardWidth=$cardWidth cardHeight=$cardHeight travelPx=$travel radiusDp=${28 * slide * cardScale} navPx=$navigationHeight imePx=$keyboardBottom renderer=layout_bounds_clip")
            }
            Box(Modifier.fillMaxSize().pointerInput(travel) {
                var openedAtStart = false
                var distance = 0f
                val velocity = VelocityTracker()
                // Observe from the stationary root, not the page moving under the finger.
                detectHorizontalDragGestures(
                    onDragStart = { position ->
                        historyMenu = null
                        val start = latestSlide
                        openedAtStart = start > .5f; distance = 0f
                        drawer = start; dragging = true; keyboard?.hide()
                        Logger.d("AiDrawer gesture=start progress=$start")
                        velocity.resetTracking()
                        velocity.addPosition(android.os.SystemClock.uptimeMillis(), position)
                    },
                    onDragEnd = {
                        val speed = velocity.calculateVelocity().x
                        val target = when {
                            kotlin.math.abs(speed) > 600.dp.toPx() -> if (speed > 0) 1f else 0f
                            kotlin.math.abs(distance) > 36.dp.toPx() -> if (distance > 0) 1f else 0f
                            else -> if (openedAtStart) 1f else 0f
                        }
                        dragging = false; drawer = target
                        Logger.d("AiDrawer gesture=end distancePx=$distance velocityPx=$speed target=$target")
                    },
                    onDragCancel = {
                        dragging = false; drawer = if (openedAtStart) 1f else 0f
                        Logger.d("AiDrawer gesture=cancel target=$drawer")
                    }
                ) { change, amount ->
                    velocity.addPosition(change.uptimeMillis, change.position)
                    distance += amount
                    if (amount > 0 || drawer > 0) {
                        change.consume(); drawer = (drawer + amount / travel).coerceIn(0f, 1f)
                    }
                    Logger.d("AiGesture move t=${change.uptimeMillis} x=${change.position.x} y=${change.position.y} " +
                        "dx=$amount distance=$distance requested=$drawer travel=$travel")
                }
            }) {
            // The stationary root owns the entire underlay. A scrim on only this sliding
            // column made the left side darker than the strips above and below the card.
            if (slide > .001f) Column(Modifier.width(with(density) { travel.toDp() }).fillMaxHeight().graphicsLayer {
                translationX = (slide - 1f) * travel
            }.padding(top = top + 16.dp, bottom = bottom + 12.dp).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AiText("历史对话", 22, bold = true)
                AiField(historyQuery, "搜索对话") { historyQuery = it }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(conversations.filter { it.title.contains(historyQuery, true) }, key = { it.id }) { item ->
                        HistoryRow(item)
                    }
                    if (conversations.isEmpty()) item { AiText("聊过的影片会留在这里", 14, true, modifier = Modifier.padding(top = 20.dp)) }
                }
            }
            val cardColor = aiBackground().toArgb()
            AndroidView(factory = { context ->
                AiPageContainer(context).apply {
                    page.setContent { PageContent() }
                    (activity as? HomeActivity)?.registerAiNavigationHost(navigation, page)
                }
            }, modifier = cardModifier, update = { host ->
                host.update(cardColor, pageWidth, pageHeight)
                host.updateDrawDiagnostics(slide, dragging, underlay.toArgb(), historyBackground.toArgb(), underlayShade)
            }, onRelease = { host ->
                (activity as? HomeActivity)?.releaseAiNavigationHost(host.navigation)
            })
            if (slide > .001f) Box(cardModifier.clip(RoundedCornerShape((28 * slide * cardScale).dp))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null) { drawer = 0f })
        }
    }
        HistoryMenu()
        HistoryDialogs()
    }
    @Composable private fun PageContent() {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val top = with(density) { topInset.toDp() }
        val inputBottom = with(density) { maxOf(navigationHeight, keyboardBottom).toDp() }
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val backdrop = rememberLayerBackdrop()
        val frame = remember { mutableLongStateOf(0L) }
        val list = rememberLazyListState()
        LaunchedEffect(pageVisible) { while (pageVisible) withFrameNanos { frame.longValue = it } }
        LaunchedEffect(turns.size, busy) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex) }
        Box(Modifier.fillMaxSize().background(aiBackground())) {
                Column(Modifier.fillMaxSize().layerBackdrop(backdrop).background(aiBackground()).padding(bottom = inputBottom), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                        if (turns.isEmpty()) {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(top = top + 72.dp).padding(24.dp),
                                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                AndroidView(factory = { ImageView(it).apply { setImageDrawable(AiOrbDrawable()) } }, modifier = Modifier.size(60.dp),
                                    update = { (it.drawable as AiOrbDrawable).apply { setTint(0xFF249DF2.toInt()); setRunning(pageVisible); setMode(if (busy) 2 else 0) } }, onReset = null,
                                    onRelease = { (it.drawable as AiOrbDrawable).setRunning(false) })
                                Spacer(Modifier.height(24.dp)); AiText("今天想看点什么？", 23, bold = true)
                            }
                        } else LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = top + 84.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            items(turns, key = { it.id }) { turn ->
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AiText(if (turn.askedAt > 0L) java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(turn.askedAt)) else "历史提问 · 时间未记录",
                                        11, true, modifier = Modifier.align(Alignment.End))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        Box(Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(22.dp)).background(aiSurface()).padding(16.dp)) { AiText(turn.question) }
                                    }
                                    val reply = turn.reply
                                    if (reply == null || (voiceTurnId == turn.id && busy)) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        AndroidView(factory = { ImageView(it).apply { setImageDrawable(AiOrbDrawable()) } }, modifier = Modifier.size(28.dp),
                                            update = { (it.drawable as AiOrbDrawable).apply { setTint(0xFF249DF2.toInt()); setMode(2); setRunning(pageVisible) } }, onReset = null,
                                            onRelease = { (it.drawable as AiOrbDrawable).setRunning(false) })
                                        AiText(progress, muted = true)
                                    }
                                    if (reply != null) {
                                        if (reply.message.isNotEmpty()) AiText(reply.message)
                                        reply.films.forEach { film -> FilmCard(film, turn.id) }
                                        if (turn.id == sourceTurnId) SourceResults()
                                        Sources(reply.sources)
                                    }
                                }
                            }
                        }
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (error.isNotEmpty()) AiText(error, 12, true)
                            AndroidView(factory = { context -> VoiceInputHost(context).apply {
                                addView(ComposeView(context).apply {
                                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                                    setContent { MessageInput() }
                                }, FrameLayout.LayoutParams(-1, -2))
                            } }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(top + 72.dp).align(Alignment.TopCenter).clipToBounds()) {
                    val base = aiBackground()
                    val glass = base.copy(alpha = if (androidx.compose.foundation.isSystemInDarkTheme()) .82f else .86f)
                    ProgressiveGlassSurface(Modifier.matchParentSize(), backdrop, base, glass, top + 28.dp, frame)
                    Row(Modifier.fillMaxWidth().padding(top = top).height(MainPageHeaderStyle.rowHeight)
                        .padding(horizontal = MainPageHeaderStyle.horizontalPadding),
                        verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.text.BasicText("AI 找片",
                            style = androidx.compose.ui.text.TextStyle(color = aiTextColor(), fontSize = MainPageHeaderStyle.titleSize,
                                fontWeight = MainPageHeaderStyle.titleWeight),
                            modifier = Modifier.clickable {
                                keyboard?.hide(); conversations = AiConversations.list(); drawer = 1f
                            })
                        Spacer(Modifier.weight(1f))
                        HeaderButton(R.drawable.ic_profile_settings, "AI 设置", backdrop, frame, glass) { AiSettingsActivity.start(requireActivity()) }
                        Spacer(Modifier.width(8.dp))
                        HeaderButton(R.drawable.ic_add, "新对话", backdrop, frame, glass) { reset() }
                    }
                }
        }
    }

    @Composable private fun HistoryRow(item: AiConversation) {
        var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
        fun showMenu(position: Offset) {
            val point = coordinates?.takeIf { it.isAttached }?.localToWindow(position) ?: return
            historyMenuPosition = IntOffset(point.x.roundToInt(), point.y.roundToInt())
            historyError = ""; historyMenu = item
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (item.id == conversationId) aiSurface() else Color.Transparent)
            .onGloballyPositioned { coordinates = it }
            .pointerInput(item) {
                detectTapGestures(onTap = { openConversation(item) }, onLongPress = { showMenu(it) })
            }.semantics {
                onClick("打开对话") { openConversation(item); true }
                onLongClick("对话操作") { showMenu(Offset.Zero); true }
            }.padding(14.dp)) {
            AiText(item.title.ifEmpty { "找片对话" }, 15)
            AiText(java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(item.updated)), 11, true)
        }
    }

    @Composable private fun HistoryMenu() {
        val item = historyMenu ?: return
        val density = androidx.compose.ui.platform.LocalDensity.current
        val margin = with(density) { 12.dp.roundToPx() }
        val gap = with(density) { 8.dp.roundToPx() }
        val point = historyMenuPosition
        val position = remember(point, margin, gap, topInset, bottomInset) {
            object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                    layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                    val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                    val minY = topInset + margin
                    val maxY = (windowSize.height - bottomInset - popupContentSize.height - margin).coerceAtLeast(minY)
                    val below = point.y + gap
                    val y = if (below <= maxY) below else point.y - gap - popupContentSize.height
                    return IntOffset(point.x.coerceIn(margin, maxX), y.coerceIn(minY, maxY))
                }
            }
        }
        Popup(popupPositionProvider = position, onDismissRequest = { historyMenu = null },
            properties = PopupProperties(focusable = true)) {
            Column(Modifier.width(200.dp)
                .background(aiBackground(), RoundedCornerShape(18.dp)).padding(6.dp)) {
                AiText("重命名", 15, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { renameText = item.title; renameTarget = item; historyMenu = null }.padding(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(aiSurface()))
                AiText("删除对话", 15, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { deleteTarget = item; historyMenu = null }.padding(14.dp))
            }
        }
    }

    @Composable private fun HistoryDialogs() {
        val rename = renameTarget
        val deletion = deleteTarget
        if (rename == null && deletion == null) return
        fun dismiss() { historyMenu = null; renameTarget = null; deleteTarget = null; historyError = "" }
        androidx.compose.ui.window.Dialog(onDismissRequest = { dismiss() }) {
            Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(aiBackground()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    rename != null -> {
                        AiText("重命名对话", 18, bold = true)
                        AiField(renameText, "对话名称") { renameText = it.take(60); historyError = "" }
                        AiButton("保存", renameText.isNotBlank(), true) {
                            if (profile != LocalProfile.id()) { dismiss(); refreshIdentity() }
                            else try { AiConversations.rename(rename.id, renameText); conversations = AiConversations.list(); dismiss() }
                            catch (e: Exception) { historyError = e.message ?: "保存失败，请重试" }
                        }
                    }
                    deletion != null -> {
                        AiText("删除这段对话？", 18, bold = true)
                        AiText(deletion.title, muted = true)
                        AiButton("删除", primary = true) {
                            if (profile != LocalProfile.id()) { dismiss(); refreshIdentity() }
                            else try {
                                AiConversations.delete(deletion.id)
                                // Invalidate in-flight replies so a deleted conversation cannot reappear.
                                if (deletion.id == conversationId) { reset(); drawer = 1f }
                                conversations = AiConversations.list(); dismiss()
                            } catch (_: Exception) { historyError = "删除失败，请重试" }
                        }
                    }
                }
                if (historyError.isNotEmpty()) AiText(historyError, 13, true)
                AiButton("取消") { dismiss() }
            }
        }
    }

    @Composable private fun HeaderButton(icon: Int, description: String, backdrop: Backdrop,
        frame: LongState, glass: Color, onClick: () -> Unit) {
        LiquidButton(onClick = onClick, backdrop = backdrop, frameNanos = frame,
            surfaceColor = glass, dragResponse = .42f, modifier = Modifier.size(MainPageHeaderStyle.buttonSize)) {
            Image(painterResource(icon), description, Modifier.size(MainPageHeaderStyle.iconSize), colorFilter = ColorFilter.tint(aiTextColor()))
        }
    }

    @Composable private fun MessageInput() {
        val actionFill = aiControlFill()
        val actionText = aiControlText()
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(aiSurface())
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicTextField(query, { editQuery(it) },
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(aiTextColor(), 16.sp), maxLines = 4,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
                decorationBox = { field -> Box { if (query.isBlank()) AiText("发消息，按住说话", 15, true); field() } })
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp)
                .semantics { contentDescription = if (busy) "停止回复" else "发送" }
                .clickable(enabled = busy || (query.isNotBlank() && !routing)) { if (busy) stop() else send() },
                contentAlignment = Alignment.Center) {
                Box(Modifier.size(30.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(actionFill.copy(alpha = if (busy || (query.isNotBlank() && !routing)) 1f else .28f)),
                    contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                        if (busy) {
                            val side = 8.dp.toPx()
                            drawRoundRect(actionText, topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
                                size = androidx.compose.ui.geometry.Size(side, side),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                        } else {
                            val stroke = 2.dp.toPx()
                            val tip = Offset(size.width / 2f, 3.dp.toPx())
                            drawLine(actionText, Offset(tip.x, size.height - 3.dp.toPx()), tip,
                                stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(actionText, Offset(3.dp.toPx(), 9.dp.toPx()), tip,
                                stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                            drawLine(actionText, tip, Offset(size.width - 3.dp.toPx(), 9.dp.toPx()),
                                stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                        }
                    }
                }
            }
        }
    }

    /** Observe the whole input including its text and send control, while retaining ordinary taps.
     * Cancel child gestures before their long-press timers can select text or click the button. */
    private inner class VoiceInputHost(context: Context) : FrameLayout(context) {
        init { isClickable = true }
        private val timer = Handler(Looper.getMainLooper())
        private var voice = false
        private var downX = 0f
        private var downY = 0f
        private val location = IntArray(2)
        private val hold = Runnable {
            val home = activity as? HomeActivity ?: return@Runnable
            voice = true
            val cancel = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            super.dispatchTouchEvent(cancel); cancel.recycle()
            parent?.requestDisallowInterceptTouchEvent(true)
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(windowToken, 0)
            home.onAiVoiceLongPress(this, downX, downY)
        }
        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            getLocationInWindow(location)
            val x = event.x + location[0]; val y = event.y + location[1]
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                voice = false; downX = x; downY = y
                timer.postDelayed(hold, ViewConfiguration.getLongPressTimeout().toLong())
            }
            if (voice) {
                // Window coordinates keep the cancel threshold stable when the input moves with IME.
                val translated = MotionEvent.obtain(event).apply { offsetLocation(location[0].toFloat(), location[1].toFloat()) }
                (activity as? HomeActivity)?.onAiVoiceTouch(this, translated); translated.recycle()
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    voice = false; parent?.requestDisallowInterceptTouchEvent(false)
                }
                return true
            }
            if (event.actionMasked != MotionEvent.ACTION_DOWN && (event.actionMasked != MotionEvent.ACTION_MOVE ||
                kotlin.math.hypot(x - downX, y - downY) > ViewConfiguration.get(context).scaledTouchSlop)) timer.removeCallbacks(hold)
            return super.dispatchTouchEvent(event)
        }
        override fun onDetachedFromWindow() {
            timer.removeCallbacks(hold)
            if (voice) {
                val cancel = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                (activity as? HomeActivity)?.onAiVoiceTouch(this, cancel); cancel.recycle(); voice = false
            }
            super.onDetachedFromWindow()
        }
    }

    @Composable private fun Sources(sources: List<AiSource>) {
        if (sources.isEmpty()) return
        var expanded by remember(sources) { mutableStateOf(false) }
        val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 90f else 0f,
            androidx.compose.animation.core.tween(160), label = "sources")
        val arrowColor = aiTextColor().copy(alpha = .64f)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.clickable { expanded = !expanded }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AiText("参考资料 · ${sources.size} 条", 12, true)
                androidx.compose.foundation.Canvas(Modifier.size(14.dp).graphicsLayer { rotationZ = rotation }) {
                    val stroke = 1.5.dp.toPx()
                    drawLine(arrowColor, Offset(size.width * .36f, size.height * .22f), Offset(size.width * .64f, size.height * .5f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(arrowColor, Offset(size.width * .64f, size.height * .5f), Offset(size.width * .36f, size.height * .78f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
                }
            }
            if (expanded) sources.forEach { source ->
                AiText(source.label + " ↗", 12, true, modifier = Modifier.clickable {
                    startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(source.url)))
                }.padding(vertical = 4.dp))
            }
        }
    }

    @Composable private fun SourceResults() {
        if (sourceError.isNotBlank()) AiText(sourceError, 13, true)
    }

    @Composable private fun FilmCard(film: AiFilm, turnId: String) {
        val requested = contextualFilm(film, turnId)
        val detail = details[AiCatalog.key(requested)]
        val title = detail?.title ?: film.title
        val opening = sourceSearching && sourceTurnId == turnId && (sourceTitle == title || sourceTitle == requested.title)
        Row(Modifier.fillMaxWidth().height(126.dp).clip(RoundedCornerShape(14.dp)).background(aiSurface())
            .clickable(enabled = !opening) { searchFilm(requested, turnId) }) {
            // A fixed 2:3 portrait keeps every result the same size while leaving more room for text.
            AndroidView(factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                modifier = Modifier.width(84.dp).fillMaxHeight().clipToBounds(), update = {
                    val pic = detail?.pic.orEmpty()
                    if (pic.isEmpty()) { it.scaleType = ImageView.ScaleType.CENTER; it.setImageResource(R.drawable.ic_img_empty) }
                    else ImgUtil.load("", pic, it, ImageView.ScaleType.CENTER_CROP, true)
                })
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                AiText(title, 15, bold = true, maxLines = 2)
                val meta = listOf(detail?.year ?: requested.year, detail?.country ?: requested.country,
                    detail?.kind ?: requested.kind, if ((detail?.rating ?: 0.0) > 0) "豆瓣 %.1f".format(detail!!.rating) else "")
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (opening || meta.isNotBlank()) AiText(if (opening) "正在打开…" else meta, 11, true, maxLines = 1)
                val summary = detail?.summary.orEmpty().ifBlank { film.reason }
                // Height and width determine the visible lines; AiText ellipsizes the final fitting line.
                if (summary.isNotBlank()) AiText(summary, 12, true, modifier = Modifier.fillMaxWidth().weight(1f))
            }
        }
    }

    override fun onDestroyView() { cancelVoiceRequest(); cancelSources(); cancelMetadataSearches(); super.onDestroyView() }
    override fun onDestroy() { cancelSources(); cancelMetadataSearches(); generation++; service?.cancel(); worker.shutdownNow(); metadata.shutdownNow(); super.onDestroy() }
}
