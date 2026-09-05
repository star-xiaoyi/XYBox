package com.fongmi.android.tv.ui.custom

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.AttributeSet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fongmi.android.tv.R
import com.fongmi.android.tv.Setting
import com.fongmi.android.tv.api.config.VodConfig
import com.fongmi.android.tv.ui.custom.liquid.LiquidButton
import com.fongmi.android.tv.ui.custom.liquid.LiquidSlider
import com.fongmi.android.tv.ui.custom.liquid.LiquidToggle
import com.fongmi.android.tv.utils.ResUtil
import com.fongmi.android.tv.utils.ThemeUtil
import com.fongmi.android.tv.utils.UrlUtil
import com.github.catvod.bean.Doh
import com.github.catvod.utils.Util
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import java.text.DecimalFormat
import kotlin.math.round

class SettingsPlayerGlassContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    interface OnNetworkSettingListener {
        fun onDohSelected(index: Int)
        fun onProxySaved(proxy: String)
    }

    private data class PlayerState(
        val render: Int = 0,
        val engine: Int = 0,
        val scale: Int = 0,
        val buffer: Int = 1,
        val speed: Float = 3f,
        val tunnel: Boolean = false,
        val audioDecode: Boolean = false,
        val aac: Boolean = false,
        val caption: Boolean = false,
        val danmaku: Boolean = true,
        val background: Boolean = false,
        val ua: String = "",
        val dohOptions: List<String> = emptyList(),
        val dohIndex: Int = 0,
        val proxy: String = "",
        val captionAvailable: Boolean = true
    )

    private var state by mutableStateOf(PlayerState())
    private var expanded by mutableIntStateOf(0)
    private var networkSettingListener: OnNetworkSettingListener? = null

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        refresh()
    }

    fun refresh() {
        val dohItems = VodConfig.get().doh
        state = PlayerState(
            render = Setting.getRender(),
            engine = Setting.getPlayerEngine(),
            scale = Setting.getScale(),
            buffer = Setting.getBuffer(),
            speed = Setting.getSpeed(),
            tunnel = Setting.isTunnel(),
            audioDecode = Setting.isAudioPrefer(),
            aac = Setting.isPreferAAC(),
            caption = Setting.isCaption(),
            danmaku = Setting.isDanmakuLoad(),
            background = Setting.getBackground() == 1,
            ua = Setting.getUa(),
            dohOptions = dohItems.map { it.name },
            dohIndex = dohItems.indexOf(Doh.objectFrom(Setting.getDoh())).coerceAtLeast(0),
            proxy = Setting.getProxy().orEmpty(),
            captionAvailable = Setting.hasCaption()
        )
    }

    fun setOnNetworkSettingListener(listener: OnNetworkSettingListener?) {
        networkSettingListener = listener
    }

    private fun togglePanel(action: Int) {
        expanded = if (expanded == action) 0 else action
    }

    private fun select(action: Int, index: Int) {
        state = when (action) {
            RENDER -> {
                Setting.putRender(index)
                if (index == 1 && state.tunnel) Setting.putTunnel(false)
                state.copy(render = index, tunnel = if (index == 1) false else state.tunnel)
            }
            ENGINE -> state.copy(engine = index).also { Setting.putPlayerEngine(index) }
            SCALE -> state.copy(scale = index).also { Setting.putScale(index) }
            CAPTION -> state.copy(caption = index == 1).also { Setting.putCaption(index == 1) }
            DOH -> state.copy(dohIndex = index).also { networkSettingListener?.onDohSelected(index) }
            else -> state
        }
        expanded = 0
    }

    private fun toggle(action: Int, checked: Boolean) {
        state = when (action) {
            TUNNEL -> {
                Setting.putTunnel(checked)
                if (checked && state.render == 1) Setting.putRender(0)
                state.copy(tunnel = checked, render = if (checked && state.render == 1) 0 else state.render)
            }
            AUDIO -> state.copy(audioDecode = checked).also { Setting.putAudioPrefer(checked) }
            AAC -> state.copy(aac = checked).also { Setting.putPreferAAC(checked) }
            DANMAKU -> state.copy(danmaku = checked).also { Setting.putDanmakuLoad(checked) }
            BACKGROUND -> state.copy(background = checked).also { Setting.putBackground(if (checked) 1 else 0) }
            else -> state
        }
    }

    @Composable
    override fun Content() {
        val palette = secondaryPalette()
        val backdrop = rememberLayerBackdrop()
        val frameNanos = remember { mutableLongStateOf(0L) }
        val renderOptions = remember { ResUtil.getStringArray(R.array.select_render).toList() }
        val engineOptions = remember { ResUtil.getStringArray(R.array.select_player_engine).toList() }
        val scaleOptions = remember { ResUtil.getStringArray(R.array.select_scale).toList() }
        val captionOptions = remember { ResUtil.getStringArray(R.array.select_caption).toList() }
        LaunchedEffect(Unit) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        }

        SecondaryRoot(Modifier.layerBackdrop(backdrop), palette) {
            GlassGroup(palette) {
                ChoiceSettingRow(RENDER, R.string.player_render, renderOptions, state.render, backdrop, palette)
                SecondaryDivider(palette)
                ChoiceSettingRow(ENGINE, R.string.player_engine, engineOptions, state.engine, backdrop, palette)
                SecondaryDivider(palette)
                ChoiceSettingRow(SCALE, R.string.player_scale, scaleOptions, state.scale, backdrop, palette)
            }
            GlassGroup(palette) {
                SliderSettingRow(
                    action = BUFFER,
                    title = R.string.player_buffer,
                    valueText = state.buffer.toString(),
                    value = state.buffer.toFloat(),
                    range = 1f..10f,
                    step = 1f,
                    backdrop = backdrop,
                    palette = palette
                ) { value ->
                    val snapped = value.toInt().coerceIn(1, 10)
                    state = state.copy(buffer = snapped)
                    Setting.putBuffer(snapped)
                }
                SecondaryDivider(palette)
                SliderSettingRow(
                    action = SPEED,
                    title = R.string.player_speed,
                    valueText = DecimalFormat("0.#").format(state.speed) + "×",
                    value = state.speed,
                    range = 2f..5f,
                    step = 0.5f,
                    backdrop = backdrop,
                    palette = palette
                ) { value ->
                    val snapped = (round(value * 2f) / 2f).coerceIn(2f, 5f)
                    state = state.copy(speed = snapped)
                    Setting.putSpeed(snapped)
                }
                SecondaryDivider(palette)
                ToggleSettingRow(R.string.player_tunnel, state.tunnel, backdrop, palette) { toggle(TUNNEL, it) }
            }
            GlassGroup(palette) {
                ToggleSettingRow(R.string.player_audio_decode, state.audioDecode, backdrop, palette) { toggle(AUDIO, it) }
                SecondaryDivider(palette)
                ToggleSettingRow(R.string.player_aac, state.aac, backdrop, palette) { toggle(AAC, it) }
            }
            GlassGroup(palette) {
                if (state.captionAvailable) {
                    ChoiceSettingRow(
                        CAPTION,
                        R.string.player_caption,
                        captionOptions,
                        if (state.caption) 1 else 0,
                        backdrop,
                        palette,
                        onLongClick = {
                            if (state.caption) context.startActivity(Intent(Settings.ACTION_CAPTIONING_SETTINGS))
                        }
                    )
                    SecondaryDivider(palette)
                }
                ToggleSettingRow(R.string.player_danmaku_load, state.danmaku, backdrop, palette) { toggle(DANMAKU, it) }
            }
            GlassGroup(palette) {
                ToggleSettingRow(R.string.player_background, state.background, backdrop, palette) { toggle(BACKGROUND, it) }
                SecondaryDivider(palette)
                UaSettingRow(backdrop, palette)
            }
            GlassGroup(palette) {
                ChoiceSettingRow(
                    DOH,
                    R.string.setting_doh,
                    state.dohOptions,
                    state.dohIndex,
                    backdrop,
                    palette
                )
                SecondaryDivider(palette)
                ProxySettingRow(backdrop, frameNanos, palette)
            }
        }
    }

    @Composable
    private fun ChoiceSettingRow(
        action: Int,
        title: Int,
        options: List<String>,
        selected: Int,
        backdrop: Backdrop,
        palette: SecondaryPalette,
        onLongClick: (() -> Unit)? = null
    ) {
        Column {
            SecondaryRow(
                title = stringResource(title),
                value = options.getOrNull(selected).orEmpty(),
                palette = palette,
                onClick = { togglePanel(action) },
                onLongClick = onLongClick
            ) { SecondaryArrow(palette) }
            ChoicePanel(expanded == action, options, selected, backdrop, palette) { select(action, it) }
        }
    }

    @Composable
    private fun SliderSettingRow(
        action: Int,
        title: Int,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        step: Float,
        backdrop: Backdrop,
        palette: SecondaryPalette,
        onChange: (Float) -> Unit
    ) {
        Column {
            SecondaryRow(stringResource(title), valueText, palette, onClick = { togglePanel(action) }) {
                SecondaryArrow(palette)
            }
            GlassPanel(expanded == action, backdrop, palette) {
                BasicText(valueText, style = TextStyle(palette.secondary, 13.sp, FontWeight.SemiBold))
                LiquidSlider(
                    value = { value },
                    onValueChange = { raw ->
                        val snapped = range.start + round((raw - range.start) / step) * step
                        onChange(snapped.coerceIn(range))
                    },
                    valueRange = range,
                    visibilityThreshold = step / 10f,
                    backdrop = backdrop,
                    accentColor = palette.accent,
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                )
            }
        }
    }

    @Composable
    private fun UaSettingRow(backdrop: Backdrop, palette: SecondaryPalette) {
        val keyboard = LocalSoftwareKeyboardController.current
        Column {
            SecondaryRow(stringResource(R.string.player_ua), state.ua, palette, onClick = { togglePanel(UA) }) {
                SecondaryArrow(palette)
            }
            GlassPanel(expanded == UA, backdrop, palette) {
                BasicTextField(
                    value = state.ua,
                    onValueChange = { raw ->
                        val value = when {
                            raw.equals("c", true) -> Util.CHROME
                            raw.equals("o", true) -> Util.OKHTTP
                            else -> raw
                        }
                        state = state.copy(ua = value)
                        Setting.putUa(value.trim())
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    textStyle = TextStyle(palette.text, 14.sp, FontWeight.Medium),
                    cursorBrush = SolidColor(palette.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        expanded = 0
                    })
                )
            }
        }
    }

    @Composable
    private fun ProxySettingRow(
        backdrop: Backdrop,
        frameNanos: androidx.compose.runtime.LongState,
        palette: SecondaryPalette
    ) {
        val keyboard = LocalSoftwareKeyboardController.current
        val display = if (state.proxy.isEmpty()) stringResource(R.string.none) else UrlUtil.scheme(state.proxy)
        Column {
            SecondaryRow(
                title = stringResource(R.string.setting_proxy),
                value = display,
                palette = palette,
                onClick = { togglePanel(PROXY) }
            ) { SecondaryArrow(palette) }
            GlassPanel(expanded == PROXY, backdrop, palette) {
                BasicTextField(
                    value = state.proxy,
                    onValueChange = { raw ->
                        val value = when {
                            state.proxy.isEmpty() && raw.equals("h", true) -> "http://"
                            state.proxy.isEmpty() && raw.equals("s", true) -> "socks5://"
                            state.proxy.isEmpty() && raw.length == 1 -> "socks5://$raw"
                            else -> raw
                        }
                        state = state.copy(proxy = value)
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    textStyle = TextStyle(palette.text, 14.sp, FontWeight.Medium),
                    cursorBrush = SolidColor(palette.accent),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        networkSettingListener?.onProxySaved(state.proxy.trim())
                        expanded = 0
                    }),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (state.proxy.isEmpty()) {
                                BasicText(
                                    "socks5://127.0.0.1:9978",
                                    style = TextStyle(palette.secondary, 14.sp, FontWeight.Medium)
                                )
                            }
                            inner()
                        }
                    }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    LiquidButton(
                        onClick = {
                            keyboard?.hide()
                            networkSettingListener?.onProxySaved(state.proxy.trim())
                            expanded = 0
                        },
                        backdrop = backdrop,
                        frameNanos = frameNanos,
                        surfaceColor = palette.glass,
                        modifier = Modifier.size(76.dp, 38.dp)
                    ) {
                        BasicText(
                            stringResource(R.string.dialog_positive),
                            style = TextStyle(palette.text, 14.sp, FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val RENDER = 1
        private const val ENGINE = 2
        private const val SCALE = 3
        private const val BUFFER = 4
        private const val SPEED = 5
        private const val TUNNEL = 6
        private const val AUDIO = 7
        private const val AAC = 8
        private const val CAPTION = 9
        private const val DANMAKU = 10
        private const val BACKGROUND = 11
        private const val UA = 12
        private const val DOH = 13
        private const val PROXY = 14
    }
}

class SettingsOperationGlassContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    private data class OperationState(
        val doubleTapPlay: Boolean = true,
        val doubleTapSeek: Boolean = false,
        val seekSeconds: Int = 10,
        val brightness: Boolean = true,
        val volume: Boolean = true,
        val progress: Boolean = true,
        val episodePort: Boolean = true,
        val episodeLand: Boolean = true
    )

    private var state by mutableStateOf(OperationState())
    private var expanded by mutableIntStateOf(0)
    private val seekValues = listOf(5, 10, 15, 30)

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        refresh()
    }

    fun refresh() {
        state = OperationState(
            doubleTapPlay = Setting.isGestureDoubleTapPlay(),
            doubleTapSeek = Setting.isGestureDoubleTapSeek(),
            seekSeconds = Setting.getGestureSeekSeconds(),
            brightness = Setting.isGestureBrightness(),
            volume = Setting.isGestureVolume(),
            progress = Setting.isGestureProgress(),
            episodePort = Setting.isGestureEpisodePort(),
            episodeLand = Setting.isGestureEpisodeLand()
        )
    }

    @Composable
    override fun Content() {
        val palette = secondaryPalette()
        val backdrop = rememberLayerBackdrop()
        SecondaryRoot(Modifier.layerBackdrop(backdrop), palette) {
            GlassGroup(palette) {
                ToggleSettingRow(R.string.player_gesture_double_tap_play, state.doubleTapPlay, backdrop, palette) {
                    state = state.copy(doubleTapPlay = it)
                    Setting.putGestureDoubleTapPlay(it)
                }
                SecondaryDivider(palette)
                ToggleSettingRow(R.string.player_gesture_double_tap_seek, state.doubleTapSeek, backdrop, palette) {
                    state = state.copy(doubleTapSeek = it)
                    Setting.putGestureDoubleTapSeek(it)
                    if (!it) expanded = 0
                }
                if (state.doubleTapSeek) {
                    SecondaryDivider(palette)
                    Column {
                        SecondaryRow(
                            title = stringResource(R.string.player_gesture_seek_seconds),
                            value = stringResource(R.string.player_gesture_seek_seconds_value, state.seekSeconds),
                            palette = palette,
                            onClick = { expanded = if (expanded == SEEK) 0 else SEEK }
                        ) { SecondaryArrow(palette) }
                        ChoicePanel(
                            visible = expanded == SEEK,
                            options = seekValues.map { stringResource(R.string.player_gesture_seek_seconds_value, it) },
                            selected = seekValues.indexOf(state.seekSeconds).coerceAtLeast(0),
                            backdrop = backdrop,
                            palette = palette
                        ) { index ->
                            val seconds = seekValues[index]
                            state = state.copy(seekSeconds = seconds)
                            Setting.putGestureSeekSeconds(seconds)
                            expanded = 0
                        }
                    }
                }
            }
            GlassGroup(palette) {
                ToggleSettingRow(R.string.player_gesture_brightness, state.brightness, backdrop, palette) {
                    state = state.copy(brightness = it); Setting.putGestureBrightness(it)
                }
                SecondaryDivider(palette)
                ToggleSettingRow(R.string.player_gesture_volume, state.volume, backdrop, palette) {
                    state = state.copy(volume = it); Setting.putGestureVolume(it)
                }
                SecondaryDivider(palette)
                ToggleSettingRow(R.string.player_gesture_progress, state.progress, backdrop, palette) {
                    state = state.copy(progress = it); Setting.putGestureProgress(it)
                }
            }
            GlassGroup(palette) {
                ToggleSettingRow(R.string.player_gesture_episode_port, state.episodePort, backdrop, palette) {
                    state = state.copy(episodePort = it); Setting.putGestureEpisodePort(it)
                }
                SecondaryDivider(palette)
                ToggleSettingRow(R.string.player_gesture_episode_land, state.episodeLand, backdrop, palette) {
                    state = state.copy(episodeLand = it); Setting.putGestureEpisodeLand(it)
                }
            }
        }
    }

    companion object {
        private const val SEEK = 1
    }
}

@Composable
private fun secondaryPalette(): SecondaryPalette {
    val light = !isSystemInDarkTheme()
    return SecondaryPalette(
        background = if (light) Color(0xFFF2F2F7) else Color.Black,
        backdropStart = if (light) Color(0xFFF0F5FB) else Color(0xFF070A10),
        backdropEnd = if (light) Color(0xFFF7F3EE) else Color(0xFF100D12),
        card = if (light) Color.White.copy(alpha = 0.74f) else Color(0xFF18181B).copy(alpha = 0.76f),
        glass = if (light) Color.White.copy(alpha = 0.34f) else Color(0xFF18181B).copy(alpha = 0.40f),
        text = if (light) Color(0xFF1C1C1E) else Color.White,
        secondary = if (light) Color(0xFF6C6C70) else Color(0xFF98989D),
        separator = if (light) Color(0x263C3C43) else Color(0x40545458),
        accent = Color(ContextCompat.getColor(androidx.compose.ui.platform.LocalContext.current, ThemeUtil.getAccentColorResource()))
    )
}

@Composable
private fun SecondaryRoot(
    backdropModifier: Modifier,
    palette: SecondaryPalette,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(palette.background)) {
        Canvas(Modifier.fillMaxSize().then(backdropModifier)) {
            drawRect(Brush.linearGradient(listOf(palette.backdropStart, palette.backdropEnd)))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
private fun GlassGroup(palette: SecondaryPalette, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.card, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
        content = content
    )
}

@Composable
private fun SecondaryRow(
    title: String,
    value: String? = null,
    palette: SecondaryPalette,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
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
            .height(58.dp)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            title,
            modifier = Modifier.weight(1f),
            style = TextStyle(palette.text, 15.sp, FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!value.isNullOrEmpty()) {
            BasicText(
                value,
                style = TextStyle(palette.secondary, 13.sp, FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(7.dp))
        }
        trailing()
    }
}

@Composable
private fun ToggleSettingRow(
    title: Int,
    checked: Boolean,
    backdrop: Backdrop,
    palette: SecondaryPalette,
    onToggle: (Boolean) -> Unit
) {
    SecondaryRow(stringResource(title), palette = palette, onClick = { onToggle(!checked) }) {
        LiquidToggle(
            selected = { checked },
            onSelect = onToggle,
            backdrop = backdrop,
            accentColor = palette.accent,
            modifier = Modifier.size(60.dp, 38.dp)
        )
    }
}

@Composable
private fun ChoicePanel(
    visible: Boolean,
    options: List<String>,
    selected: Int,
    backdrop: Backdrop,
    palette: SecondaryPalette,
    onSelect: (Int) -> Unit
) {
    GlassPanel(visible, backdrop, palette) {
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
                BasicText(label, Modifier.weight(1f), style = TextStyle(palette.text, 14.sp, FontWeight.SemiBold))
                if (index == selected) BasicText("✓", style = TextStyle(palette.accent, 16.sp, FontWeight.Bold))
            }
            if (index < options.lastIndex) SecondaryDivider(palette, 8.dp)
        }
    }
}

@Composable
private fun GlassPanel(
    visible: Boolean,
    backdrop: Backdrop,
    palette: SecondaryPalette,
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
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun SecondaryDivider(palette: SecondaryPalette, inset: androidx.compose.ui.unit.Dp = 15.dp) {
    Box(Modifier.fillMaxWidth().padding(horizontal = inset).height(1.dp).background(palette.separator))
}

@Composable
private fun SecondaryArrow(palette: SecondaryPalette) {
    Image(
        painterResource(R.drawable.ic_arrow_right),
        contentDescription = null,
        colorFilter = ColorFilter.tint(palette.secondary),
        modifier = Modifier.size(18.dp)
    )
}

private data class SecondaryPalette(
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
