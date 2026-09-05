package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.R
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.fongmi.android.tv.ui.custom.liquid.LiquidToggle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import java.util.Locale

/**
 * 设置页主体使用一张完整 Compose 画布，所有玻璃控件共享同一个 LayerBackdrop。
 * 这样既与展示台的结构一致，也不会再暴露每个小 ComposeView 的矩形渲染边界。
 */
class SettingsGlassContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    fun interface OnActionListener {
        fun onAction(action: Int)
    }

    fun interface OnLongActionListener {
        fun onLongAction(action: Int)
    }

    fun interface OnToggleListener {
        fun onToggle(action: Int, checked: Boolean)
    }

    fun interface OnOptionListener {
        fun onOption(action: Int, index: Int)
    }

    fun interface OnEditorSaveListener {
        fun onEditorSave(action: Int, name: String, value: String)
    }

    fun interface OnWebDavActionListener {
        fun onWebDavAction(action: Int, url: String, username: String, password: String)
    }

    private data class State(
        val vodDescription: String = "",
        val vodName: String = "",
        val vodUrl: String = "",
        val liveDescription: String = "",
        val liveName: String = "",
        val liveUrl: String = "",
        val liveVisible: Boolean = true,
        val themeOptions: List<String> = emptyList(),
        val themeIndex: Int = 0,
        val sizeOptions: List<String> = emptyList(),
        val sizeIndex: Int = 0,
        val accentOptions: List<String> = emptyList(),
        val accentIndex: Int = 0,
        val dohOptions: List<String> = emptyList(),
        val dohIndex: Int = 0,
        @ColorInt val accentColor: Int = 0xFFFFCC00.toInt(),
        val historyVisible: Boolean = true,
        val liveTabVisible: Boolean = false,
        val incognito: Boolean = false,
        val doh: String = "",
        val proxy: String = "",
        val proxyRaw: String = "",
        val webDavProvider: Int = 0,
        val webDavUrl: String = "",
        val webDavUsername: String = "",
        val webDavPassword: String = "",
        val webDavStatus: String = "",
        val cache: String = "",
        val version: String = "",
        val query: String = ""
    )

    private var state by mutableStateOf(State())
    private var actionListener: OnActionListener? = null
    private var longActionListener: OnLongActionListener? = null
    private var toggleListener: OnToggleListener? = null
    private var optionListener: OnOptionListener? = null
    private var editorSaveListener: OnEditorSaveListener? = null
    private var webDavActionListener: OnWebDavActionListener? = null
    private var expandedAction by mutableIntStateOf(0)
    private var webDavProviderExpanded by mutableStateOf(false)
    private var aboutVisible by mutableStateOf(false)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        clipToPadding = false
    }

    fun setOnActionListener(listener: OnActionListener?) {
        actionListener = listener
    }

    fun setOnLongActionListener(listener: OnLongActionListener?) {
        longActionListener = listener
    }

    fun setOnToggleListener(listener: OnToggleListener?) {
        toggleListener = listener
    }

    fun setOnOptionListener(listener: OnOptionListener?) {
        optionListener = listener
    }

    fun setOnEditorSaveListener(listener: OnEditorSaveListener?) {
        editorSaveListener = listener
    }

    fun setOnWebDavActionListener(listener: OnWebDavActionListener?) {
        webDavActionListener = listener
    }

    fun setSourceDescriptions(vod: String, live: String) {
        state = state.copy(vodDescription = vod, liveDescription = live)
    }

    fun setSourceEditors(vodName: String?, vodUrl: String?, liveName: String?, liveUrl: String?) {
        state = state.copy(
            vodName = vodName.orEmpty(),
            vodUrl = vodUrl.orEmpty(),
            liveName = liveName.orEmpty(),
            liveUrl = liveUrl.orEmpty()
        )
    }

    fun setLiveVisible(visible: Boolean) {
        state = state.copy(liveVisible = visible)
    }

    fun setThemeOptions(options: Array<String>, selectedIndex: Int) {
        state = state.copy(themeOptions = options.toList(), themeIndex = selectedIndex)
    }

    fun setSizeOptions(options: Array<String>, selectedIndex: Int) {
        state = state.copy(sizeOptions = options.toList(), sizeIndex = selectedIndex)
    }

    fun setAccentOptions(options: Array<String>, selectedIndex: Int, @ColorInt color: Int) {
        state = state.copy(accentOptions = options.toList(), accentIndex = selectedIndex, accentColor = color)
    }

    fun setDohOptions(options: Array<String>, selectedIndex: Int) {
        state = state.copy(
            dohOptions = options.toList(),
            dohIndex = selectedIndex,
            doh = options.getOrNull(selectedIndex).orEmpty()
        )
    }

    fun setHistoryVisibleChecked(checked: Boolean) {
        state = state.copy(historyVisible = checked)
    }

    fun setLiveTabVisibleChecked(checked: Boolean) {
        state = state.copy(liveTabVisible = checked)
    }

    fun setIncognitoChecked(checked: Boolean) {
        state = state.copy(incognito = checked)
    }

    fun setDoh(value: String) {
        state = state.copy(doh = value)
    }

    fun setProxy(value: String) {
        state = state.copy(proxy = value)
    }

    fun setProxyEditor(value: String?) {
        state = state.copy(proxyRaw = value.orEmpty())
    }

    fun setWebDavEditor(url: String?, username: String?, password: String?) {
        val safeUrl = url.orEmpty()
        val provider = when {
            safeUrl.contains("jianguoyun.com", true) || safeUrl.isEmpty() -> 0
            safeUrl.contains("nextcloud", true) -> 1
            safeUrl.contains("owncloud", true) -> 2
            else -> 3
        }
        state = state.copy(
            webDavProvider = provider,
            webDavUrl = safeUrl,
            webDavUsername = username.orEmpty(),
            webDavPassword = password.orEmpty()
        )
    }

    fun setWebDavStatus(value: String) {
        state = state.copy(webDavStatus = value)
    }

    fun setCache(value: String) {
        state = state.copy(cache = value)
    }

    fun setVersion(value: String) {
        state = state.copy(version = value)
    }

    fun setQuery(value: String) {
        state = state.copy(query = value.trim().lowercase(Locale.getDefault()))
    }

    private fun selectOption(action: Int, index: Int) {
        state = when (action) {
            ACTION_THEME -> state.copy(themeIndex = index)
            ACTION_SIZE -> state.copy(sizeIndex = index)
            ACTION_ACCENT -> state.copy(accentIndex = index)
            ACTION_DOH -> state.copy(dohIndex = index)
            else -> state
        }
        expandedAction = 0
        optionListener?.onOption(action, index)
    }

    private fun toggleDialog(action: Int) {
        expandedAction = if (expandedAction == action) 0 else action
    }

    private fun setToggle(action: Int, checked: Boolean) {
        state = when (action) {
            ACTION_HISTORY_VISIBLE -> state.copy(historyVisible = checked)
            ACTION_LIVE_TAB_VISIBLE -> state.copy(liveTabVisible = checked)
            ACTION_INCOGNITO -> state.copy(incognito = checked)
            else -> state
        }
        toggleListener?.onToggle(action, checked)
    }

    @Composable
    override fun Content() {
        val light = !isSystemInDarkTheme()
        val palette = SettingsPalette(
            background = if (light) Color(0xFFF2F2F7) else Color.Black,
            backdropStart = if (light) Color(0xFFF0F5FB) else Color(0xFF070A10),
            backdropEnd = if (light) Color(0xFFF7F3EE) else Color(0xFF100D12),
            card = if (light) Color.White.copy(alpha = 0.76f) else Color(0xFF18181B).copy(alpha = 0.78f),
            glass = if (light) Color.White.copy(alpha = 0.36f) else Color(0xFF18181B).copy(alpha = 0.42f),
            text = if (light) Color(0xFF1C1C1E) else Color.White,
            secondary = if (light) Color(0xFF6C6C70) else Color(0xFF98989D),
            separator = if (light) Color(0x263C3C43) else Color(0x40545458),
            accent = Color(state.accentColor)
        )
        val backdrop = rememberLayerBackdrop()
        val frameNanos = remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        }

        Box(Modifier.fillMaxSize().background(palette.background)) {
            Canvas(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                drawRect(Brush.linearGradient(listOf(palette.backdropStart, palette.backdropEnd)))
            }
            SettingsList(backdrop, frameNanos, palette, state)
            if (aboutVisible) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.14f))
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = { aboutVisible = false }
                        )
                )
            }
            AboutGlassSheet(
                visible = aboutVisible,
                backdrop = backdrop,
                frameNanos = frameNanos,
                palette = palette,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    @Composable
    private fun AboutGlassSheet(
        visible: Boolean,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette,
        modifier: Modifier
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = modifier.padding(start = 12.dp, end = 12.dp, bottom = 104.dp),
            enter = expandVertically(tween(240), expandFrom = Alignment.Bottom) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(190), shrinkTowards = Alignment.Bottom) + fadeOut(tween(120))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28.dp) },
                        effects = {
                            colorControls(brightness = 0.08f, saturation = 1.28f)
                            blur(12.dp.toPx())
                            lens(18.dp.toPx(), 36.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(palette.glass) }
                    )
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onClick = {}
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = stringResource(R.string.setting_about),
                        modifier = Modifier.weight(1f),
                        style = TextStyle(palette.text, 20.sp, FontWeight.Bold)
                    )
                    LiquidButton(
                        onClick = { aboutVisible = false },
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        surfaceColor = palette.glass,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_action_close),
                            contentDescription = stringResource(R.string.action_close_search),
                            colorFilter = ColorFilter.tint(palette.text),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                BasicText(
                    stringResource(R.string.about_dev_title),
                    style = TextStyle(palette.text, 14.sp, FontWeight.Bold)
                )
                BasicText(
                    stringResource(R.string.about_dev_body),
                    style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium, lineHeight = 19.sp)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    stringResource(R.string.about_disclaimer_title),
                    style = TextStyle(palette.text, 14.sp, FontWeight.Bold)
                )
                BasicText(
                    stringResource(R.string.about_disclaimer_body),
                    style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium, lineHeight = 19.sp)
                )
                Spacer(Modifier.height(2.dp))
                EditorButton(
                    text = stringResource(R.string.about_github),
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)))
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingsList(
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette,
        current: State
    ) {
        val groups = listOf(
            listOf(ACTION_VOD, ACTION_LIVE, ACTION_WEBDAV),
            listOf(ACTION_THEME, ACTION_ACCENT, ACTION_SIZE, ACTION_HISTORY_VISIBLE, ACTION_LIVE_TAB_VISIBLE),
            listOf(ACTION_PLAYER, ACTION_OPERATION),
            listOf(ACTION_INCOGNITO, ACTION_CACHE, ACTION_BACKUP, ACTION_RESTORE),
            listOf(ACTION_LABORATORY),
            listOf(ACTION_VERSION, ACTION_ABOUT)
        )
        var resultCount = 0

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            groups.forEach { ids ->
                val visible = ArrayList<Int>()
                ids.forEach { id ->
                    if (id != ACTION_LIVE || current.liveVisible) {
                        if (matches(id, current, current.query)) visible.add(id)
                    }
                }
                if (visible.isNotEmpty()) {
                    resultCount += visible.size
                    GlassCard(palette) {
                        visible.forEachIndexed { index, id ->
                            SettingsItem(id, backdrop, frameNanos, palette, current)
                            if (index < visible.lastIndex) Divider(palette)
                        }
                    }
                }
            }
            if (current.query.isNotEmpty() && resultCount == 0) {
                BasicText(
                    text = stringResource(R.string.setting_search_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    style = TextStyle(palette.secondary, 14.sp)
                )
            }
        }
    }

    @Composable
    private fun matches(id: Int, current: State, query: String): Boolean {
        if (query.isEmpty()) return true
        val text = when (id) {
            ACTION_VOD -> stringResource(R.string.setting_vod) + " " + current.vodDescription
            ACTION_LIVE -> stringResource(R.string.setting_live) + " " + current.liveDescription
            ACTION_THEME -> stringResource(R.string.setting_theme) + " " + current.themeOptions.joinToString(" ")
            ACTION_ACCENT -> stringResource(R.string.setting_accent) + " " + current.accentOptions.joinToString(" ")
            ACTION_SIZE -> stringResource(R.string.setting_size) + " " + current.sizeOptions.joinToString(" ")
            ACTION_HISTORY_VISIBLE -> stringResource(R.string.setting_history_visible)
            ACTION_LIVE_TAB_VISIBLE -> stringResource(R.string.setting_live_tab_visible)
            ACTION_PLAYER -> stringResource(R.string.setting_player) + " " + stringResource(R.string.setting_player_summary)
            ACTION_OPERATION -> stringResource(R.string.setting_operation) + " " + stringResource(R.string.setting_operation_summary)
            ACTION_WEBDAV -> stringResource(R.string.setting_webdav) + " " + stringResource(R.string.setting_webdav_summary)
            ACTION_SYNC -> stringResource(R.string.setting_lan_sync) + " " + stringResource(R.string.setting_lan_sync_summary)
            ACTION_DOH -> stringResource(R.string.setting_doh) + " " + current.doh
            ACTION_PROXY -> stringResource(R.string.setting_proxy) + " " + current.proxy
            ACTION_INCOGNITO -> stringResource(R.string.setting_incognito)
            ACTION_CACHE -> stringResource(R.string.setting_cache) + " " + current.cache
            ACTION_BACKUP -> stringResource(R.string.setting_backup)
            ACTION_RESTORE -> stringResource(R.string.setting_restore)
            ACTION_LABORATORY -> stringResource(R.string.setting_laboratory) + " " + stringResource(R.string.setting_laboratory_summary)
            ACTION_VERSION -> current.version
            ACTION_ABOUT -> stringResource(R.string.setting_about)
            else -> ""
        }
        return text.lowercase(Locale.getDefault()).contains(query)
    }

    @Composable
    private fun SettingsItem(
        id: Int,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette,
        current: State
    ) {
        when (id) {
            ACTION_VOD -> SourceRow(
                id, R.drawable.ic_nav_vod, R.string.setting_vod, current.vodDescription,
                backdrop, frameNanos, palette
            )
            ACTION_LIVE -> SourceRow(
                id, R.drawable.ic_nav_live, R.string.setting_live, current.liveDescription,
                backdrop, frameNanos, palette
            )
            ACTION_THEME -> DialogOptionRow(
                id, R.drawable.ic_settings_appearance, R.string.setting_theme,
                current.themeOptions, current.themeIndex, backdrop, frameNanos, palette
            )
            ACTION_ACCENT -> DialogAccentRow(current, backdrop, frameNanos, palette)
            ACTION_SIZE -> DialogOptionRow(
                id, R.drawable.ic_setting_size, R.string.setting_size,
                current.sizeOptions, current.sizeIndex, backdrop, frameNanos, palette
            )
            ACTION_HISTORY_VISIBLE -> ToggleRow(
                id, R.drawable.ic_nav_history, R.string.setting_history_visible,
                current.historyVisible, backdrop, palette
            )
            ACTION_LIVE_TAB_VISIBLE -> ToggleRow(
                id, R.drawable.ic_nav_live, R.string.setting_live_tab_visible,
                current.liveTabVisible, backdrop, palette
            )
            ACTION_PLAYER -> ActionRow(id, R.drawable.ic_settings_playback, R.string.setting_player, R.string.setting_player_summary, palette)
            ACTION_OPERATION -> ActionRow(id, R.drawable.ic_settings_gesture, R.string.setting_operation, R.string.setting_operation_summary, palette)
            ACTION_WEBDAV -> WebDavEditorRow(backdrop, frameNanos, palette, current)
            ACTION_SYNC -> ActionRow(id, R.drawable.ic_settings_devices, R.string.setting_lan_sync, R.string.setting_lan_sync_summary, palette)
            ACTION_DOH -> DialogOptionRow(
                id, R.drawable.ic_setting_doh, R.string.setting_doh,
                current.dohOptions, current.dohIndex, backdrop, frameNanos, palette
            )
            ACTION_PROXY -> ProxyEditorRow(backdrop, frameNanos, palette, current)
            ACTION_INCOGNITO -> ToggleRow(id, R.drawable.ic_setting_incognito, R.string.setting_incognito, current.incognito, backdrop, palette)
            ACTION_CACHE -> ActionRow(id, R.drawable.ic_settings_clean, R.string.setting_cache, R.string.setting_cache_summary, palette, current.cache)
            ACTION_BACKUP -> ActionRow(id, R.drawable.ic_settings_backup, R.string.setting_backup, null, palette)
            ACTION_RESTORE -> ActionRow(id, R.drawable.ic_settings_restore, R.string.setting_restore, null, palette)
            ACTION_LABORATORY -> ActionRow(id, R.drawable.ic_setting_laboratory, R.string.setting_laboratory, R.string.setting_laboratory_summary, palette)
            ACTION_VERSION -> ActionRow(id, R.drawable.ic_settings_update, null, null, palette, current.version, true)
            ACTION_ABOUT -> ActionRow(
                id, R.drawable.ic_settings_info, R.string.setting_about, null, palette,
                customOnClick = { aboutVisible = true }
            )
        }
    }

    @Composable
    private fun SourceRow(
        id: Int,
        icon: Int,
        title: Int,
        summary: String,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette
    ) {
        Column {
            SettingsRow(
                icon, stringResource(title), summary, palette,
                onClick = { toggleDialog(id) },
                onLongClick = { toggleDialog(id) }
            ) {
                GlassIconButton(R.drawable.ic_setting_home, backdrop, frameNanos, palette) {
                    actionListener?.onAction(if (id == ACTION_VOD) ACTION_VOD_HOME else ACTION_LIVE_HOME)
                }
                Spacer(Modifier.width(6.dp))
                GlassIconButton(R.drawable.ic_setting_history, backdrop, frameNanos, palette) {
                    actionListener?.onAction(if (id == ACTION_VOD) ACTION_VOD_HISTORY else ACTION_LIVE_HISTORY)
                }
            }
            SourceEditorPanel(id, backdrop, frameNanos, palette)
        }
    }

    @Composable
    private fun SourceEditorPanel(
        id: Int,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette
    ) {
        val clipboard = LocalClipboardManager.current
        val isVod = id == ACTION_VOD
        val name = if (isVod) state.vodName else state.liveName
        val url = if (isVod) state.vodUrl else state.liveUrl
        EditorPanel(expandedAction == id, backdrop, palette) {
            EditorTextField(
                value = name,
                hint = stringResource(R.string.dialog_config_name),
                backdrop = backdrop,
                palette = palette
            ) { value ->
                state = if (isVod) state.copy(vodName = value.take(10)) else state.copy(liveName = value.take(10))
            }
            EditorTextField(
                value = url,
                hint = stringResource(R.string.dialog_config_hint),
                backdrop = backdrop,
                palette = palette
            ) { raw ->
                val value = when {
                    url.isEmpty() && raw.equals("h", true) -> "http://"
                    url.isEmpty() && raw.equals("f", true) -> "file://"
                    url.isEmpty() && raw.equals("a", true) -> "assets://"
                    else -> raw
                }
                state = if (isVod) state.copy(vodUrl = value) else state.copy(liveUrl = value)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorButton(
                    text = stringResource(R.string.dialog_paste),
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                ) {
                    clipboard.getText()?.text?.let { pasted ->
                        state = if (isVod) state.copy(vodUrl = pasted) else state.copy(liveUrl = pasted)
                    }
                }
                EditorButton(
                    text = stringResource(R.string.dialog_positive),
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    palette = palette,
                    modifier = Modifier.weight(1f)
                ) {
                    editorSaveListener?.onEditorSave(id, name.trim(), url.trim())
                    expandedAction = 0
                }
            }
        }
    }

    @Composable
    private fun ProxyEditorRow(
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette,
        current: State
    ) {
        Column {
            SettingsRow(
                icon = R.drawable.ic_m3_private_connectivity,
                title = stringResource(R.string.setting_proxy),
                summary = null,
                palette = palette,
                onClick = { toggleDialog(ACTION_PROXY) }
            ) {
                BasicText(current.proxy, style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium), maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Arrow(palette)
            }
            EditorPanel(expandedAction == ACTION_PROXY, backdrop, palette) {
                EditorTextField(
                    value = current.proxyRaw,
                    hint = "socks5://127.0.0.1:9978",
                    backdrop = backdrop,
                    palette = palette
                ) { raw ->
                    val value = when {
                        current.proxyRaw.isEmpty() && raw.equals("h", true) -> "http://"
                        current.proxyRaw.isEmpty() && raw.equals("s", true) -> "socks5://"
                        current.proxyRaw.isEmpty() && raw.length == 1 -> "socks5://$raw"
                        else -> raw
                    }
                    state = state.copy(proxyRaw = value)
                }
                EditorButton(
                    text = stringResource(R.string.dialog_positive),
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    editorSaveListener?.onEditorSave(ACTION_PROXY, "", current.proxyRaw.trim())
                    expandedAction = 0
                }
            }
        }
    }

    @Composable
    private fun WebDavEditorRow(
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette,
        current: State
    ) {
        val providers = listOf("坚果云", "Nextcloud", "ownCloud", "自定义")
        Column {
            SettingsRow(
                icon = R.drawable.ic_settings_cloud_sync,
                title = stringResource(R.string.setting_webdav),
                summary = stringResource(R.string.setting_webdav_summary),
                palette = palette,
                onClick = { toggleDialog(ACTION_WEBDAV) }
            ) { Arrow(palette) }
            EditorPanel(expandedAction == ACTION_WEBDAV, backdrop, palette) {
                SecondaryEditorLabel("服务提供商", palette)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = { webDavProviderExpanded = !webDavProviderExpanded }
                        )
                        .height(42.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        providers.getOrElse(current.webDavProvider) { providers.last() },
                        modifier = Modifier.weight(1f),
                        style = TextStyle(palette.text, 14.sp, FontWeight.SemiBold)
                    )
                    Arrow(palette)
                }
                AnimatedVisibility(webDavProviderExpanded) {
                    Column {
                        providers.forEachIndexed { index, provider ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        interactionSource = null,
                                        indication = null,
                                        onClick = {
                                            state = state.copy(
                                                webDavProvider = index,
                                                webDavUrl = if (index == 0) JIANGUOYUN_URL else current.webDavUrl
                                            )
                                            webDavProviderExpanded = false
                                        }
                                    )
                                    .height(38.dp)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(provider, Modifier.weight(1f), style = TextStyle(palette.text, 13.sp, FontWeight.Medium))
                                if (index == current.webDavProvider) {
                                    BasicText("✓", style = TextStyle(palette.accent, 15.sp, FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
                if (current.webDavProvider != 0) {
                    EditorTextField(current.webDavUrl, "WebDAV 服务器地址", backdrop, palette) {
                        state = state.copy(webDavUrl = it)
                    }
                }
                EditorTextField(current.webDavUsername, "用户名", backdrop, palette) {
                    state = state.copy(webDavUsername = it)
                }
                EditorTextField(current.webDavPassword, "密码", backdrop, palette, password = true) {
                    state = state.copy(webDavPassword = it)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditorButton("测试连接", backdrop, frameNanos, palette, Modifier.weight(1f)) {
                        sendWebDavAction(WEBDAV_TEST)
                    }
                    EditorButton("保存并同步", backdrop, frameNanos, palette, Modifier.weight(1f)) {
                        sendWebDavAction(WEBDAV_SAVE)
                    }
                }
                if (current.webDavStatus.isNotEmpty()) {
                    BasicText(
                        current.webDavStatus,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        style = TextStyle(palette.secondary, 12.sp, FontWeight.Medium)
                    )
                }
            }
        }
    }

    private fun sendWebDavAction(action: Int) {
        val url = if (state.webDavProvider == 0) JIANGUOYUN_URL else state.webDavUrl.trim()
        webDavActionListener?.onWebDavAction(
            action,
            url,
            state.webDavUsername.trim(),
            state.webDavPassword
        )
    }

    @Composable
    private fun SecondaryEditorLabel(text: String, palette: SettingsPalette) {
        BasicText(text, style = TextStyle(palette.secondary, 12.sp, FontWeight.Medium))
    }

    @Composable
    private fun EditorPanel(
        visible: Boolean,
        backdrop: Backdrop,
        palette: SettingsPalette,
        content: @Composable ColumnScope.() -> Unit
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(tween(220)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(24.dp) },
                        effects = {
                            colorControls(brightness = 0.08f, saturation = 1.28f)
                            blur(12.dp.toPx())
                            lens(18.dp.toPx(), 36.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(palette.glass) }
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                content = content
            )
        }
    }

    @Composable
    private fun EditorTextField(
        value: String,
        hint: String,
        backdrop: Backdrop,
        palette: SettingsPalette,
        password: Boolean = false,
        onValueChange: (String) -> Unit
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { com.kyant.shapes.Capsule() },
                    effects = { blur(2.dp.toPx()); lens(10.dp.toPx(), 20.dp.toPx()) },
                    onDrawSurface = { drawRect(palette.glass) }
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(palette.text, 13.sp, FontWeight.Medium),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.accent),
                singleLine = true,
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) BasicText(hint, style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium))
                        inner()
                    }
                }
            )
        }
    }

    @Composable
    private fun EditorButton(
        text: String,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette,
        modifier: Modifier,
        onClick: () -> Unit
    ) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = palette.glass,
            modifier = modifier.height(42.dp)
        ) {
            BasicText(text, style = TextStyle(palette.text, 13.sp, FontWeight.SemiBold))
        }
    }

    @Composable
    private fun DialogOptionRow(
        id: Int,
        icon: Int,
        title: Int,
        options: List<String>,
        selected: Int,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette
    ) {
        Column {
            SettingsRow(
                icon = icon,
                title = stringResource(title),
                summary = null,
                palette = palette,
                onClick = { toggleDialog(id) }
            ) {
                BasicText(
                    options.getOrNull(selected).orEmpty(),
                    style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium),
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Arrow(palette)
            }
            DialogOptionsPanel(
                visible = expandedAction == id,
                options = options,
                selected = selected,
                backdrop = backdrop,
                palette = palette,
                onSelect = { selectOption(id, it) }
            )
        }
    }

    @Composable
    private fun DialogAccentRow(
        current: State,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette
    ) {
        val colors = listOf(Color(0xFFFFCC00), Color(0xFF0A84FF), Color(0xFF30D158), Color(0xFFBF5AF2))
        Column {
            SettingsRow(
                icon = R.drawable.ic_settings_palette,
                title = stringResource(R.string.setting_accent),
                summary = null,
                palette = palette,
                onClick = { toggleDialog(ACTION_ACCENT) }
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(colors.getOrElse(current.accentIndex) { palette.accent })
                )
                Spacer(Modifier.width(8.dp))
                BasicText(
                    current.accentOptions.getOrNull(current.accentIndex).orEmpty(),
                    style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium)
                )
                Spacer(Modifier.width(6.dp))
                Arrow(palette)
            }
            DialogOptionsPanel(
                visible = expandedAction == ACTION_ACCENT,
                options = current.accentOptions,
                selected = current.accentIndex,
                backdrop = backdrop,
                palette = palette,
                colors = colors,
                onSelect = { selectOption(ACTION_ACCENT, it) }
            )
        }
    }

    @Composable
    private fun DialogOptionsPanel(
        visible: Boolean,
        options: List<String>,
        selected: Int,
        backdrop: Backdrop,
        palette: SettingsPalette,
        colors: List<Color>? = null,
        onSelect: (Int) -> Unit
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(tween(220)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(24.dp) },
                        effects = {
                            colorControls(brightness = 0.08f, saturation = 1.28f)
                            blur(12.dp.toPx())
                            lens(18.dp.toPx(), 36.dp.toPx(), depthEffect = true)
                        },
                        highlight = { Highlight.Plain },
                        onDrawSurface = { drawRect(palette.glass) }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                options.forEachIndexed { index, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = null,
                                indication = null,
                                onClick = { onSelect(index) }
                            )
                            .height(42.dp)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colors?.getOrNull(index)?.let { color ->
                            Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(color))
                            Spacer(Modifier.width(10.dp))
                        }
                        BasicText(
                            label,
                            modifier = Modifier.weight(1f),
                            style = TextStyle(palette.text, 14.sp, FontWeight.SemiBold)
                        )
                        if (index == selected) {
                            BasicText("✓", style = TextStyle(palette.accent, 16.sp, FontWeight.Bold))
                        }
                    }
                    if (index < options.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .height(1.dp)
                                .background(palette.separator)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ToggleRow(
        id: Int,
        icon: Int,
        title: Int,
        checked: Boolean,
        backdrop: Backdrop,
        palette: SettingsPalette
    ) {
        SettingsRow(
            icon, stringResource(title), null, palette,
            onClick = { setToggle(id, !checked) }
        ) {
            LiquidToggle(
                selected = { checked },
                onSelect = { setToggle(id, it) },
                backdrop = backdrop,
                accentColor = palette.accent,
                modifier = Modifier.size(64.dp, 40.dp)
            )
        }
    }

    @Composable
    private fun ActionRow(
        id: Int,
        icon: Int,
        title: Int?,
        summary: Int?,
        palette: SettingsPalette,
        value: String? = null,
        longClickable: Boolean = false,
        customOnClick: (() -> Unit)? = null
    ) {
        SettingsRow(
            icon = icon,
            title = title?.let { stringResource(it) } ?: value.orEmpty(),
            summary = summary?.let { stringResource(it) },
            palette = palette,
            onClick = customOnClick ?: {
                actionListener?.onAction(id)
                Unit
            },
            onLongClick = if (longClickable) ({ longActionListener?.onLongAction(id) }) else null
        ) {
            if (!value.isNullOrEmpty() && title != null) {
                BasicText(value, style = TextStyle(palette.secondary, 13.sp), maxLines = 1)
                Spacer(Modifier.width(8.dp))
            }
            Arrow(palette)
        }
    }

    @Composable
    private fun ValueRow(id: Int, icon: Int, title: Int, value: String, palette: SettingsPalette) {
        SettingsRow(
            icon, stringResource(title), null, palette,
            onClick = { actionListener?.onAction(id) }
        ) {
            BasicText(
                value,
                style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Arrow(palette)
        }
    }

    @Composable
    private fun GlassOptions(
        id: Int,
        options: List<String>,
        selected: Int,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette
    ) {
        Row(Modifier.width(180.dp).height(48.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                LiquidButton(
                    onClick = { selectOption(id, index) },
                    backdrop = backdrop,
                    frameNanos = frameNanos,
                    tint = if (active) palette.accent else Color.Unspecified,
                    surfaceColor = if (active) Color.Unspecified else palette.glass,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    BasicText(
                        label,
                        style = TextStyle(
                            color = if (active && palette.accent.luminance() <= 0.55f) Color.White else palette.text,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    private fun GlassIconButton(
        icon: Int,
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SettingsPalette,
        onClick: () -> Unit
    ) {
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            frameNanos = frameNanos,
            surfaceColor = palette.glass,
            modifier = Modifier.size(42.dp)
        ) {
            Image(
                painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.secondary),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    @Composable
    private fun GlassCard(palette: SettingsPalette, content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier
                .fillMaxWidth()
                // 只绘制圆角背景，不裁剪子项；液态按钮拖出卡片时仍保持完整。
                .background(palette.card, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
            content = content
        )
    }

    @Composable
    private fun SettingsRow(
        icon: Int,
        title: String,
        summary: String?,
        palette: SettingsPalette,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        trailing: @Composable RowScope.() -> Unit
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = onClick != null || onLongClick != null,
                    interactionSource = null,
                    indication = null,
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 15.dp, vertical = 8.dp)
                .height(if (summary == null) 42.dp else 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.secondary),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                BasicText(
                    title,
                    style = TextStyle(palette.text, 15.sp, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!summary.isNullOrEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        summary,
                        style = TextStyle(palette.secondary, 12.sp, FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }

    @Composable
    private fun Divider(palette: SettingsPalette) {
        Box(Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp).height(1.dp).background(palette.separator))
    }

    @Composable
    private fun Arrow(palette: SettingsPalette) {
        Image(
            painterResource(R.drawable.ic_arrow_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.secondary),
            modifier = Modifier.size(20.dp)
        )
    }

    companion object {
        const val ACTION_VOD = 1
        const val ACTION_LIVE = 2
        const val ACTION_VOD_HOME = 3
        const val ACTION_LIVE_HOME = 4
        const val ACTION_VOD_HISTORY = 5
        const val ACTION_LIVE_HISTORY = 6
        const val ACTION_THEME = 7
        const val ACTION_ACCENT = 8
        const val ACTION_SIZE = 9
        const val ACTION_HISTORY_VISIBLE = 10
        const val ACTION_LIVE_TAB_VISIBLE = 11
        const val ACTION_PLAYER = 12
        const val ACTION_OPERATION = 13
        const val ACTION_WEBDAV = 14
        const val ACTION_SYNC = 15
        const val ACTION_DOH = 16
        const val ACTION_PROXY = 17
        const val ACTION_INCOGNITO = 18
        const val ACTION_CACHE = 19
        const val ACTION_BACKUP = 20
        const val ACTION_RESTORE = 21
        const val ACTION_LABORATORY = 22
        const val ACTION_VERSION = 23
        const val ACTION_ABOUT = 24
        const val WEBDAV_TEST = 25
        const val WEBDAV_SAVE = 26
        private const val PROJECT_URL = "https://github.com/star-xiaoyi/XYBox"
        private const val JIANGUOYUN_URL = "https://dav.jianguoyun.com/dav/XYBox/"
    }
}

private data class SettingsPalette(
    val background: Color,
    val backdropStart: Color,
    val backdropEnd: Color,
    val card: Color,
    val glass: Color,
    val text: Color,
    val secondary: Color,
    val separator: Color,
    val accent: Color
)
