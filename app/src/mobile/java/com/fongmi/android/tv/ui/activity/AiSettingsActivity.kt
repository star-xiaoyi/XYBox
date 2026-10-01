package com.fongmi.android.tv.ui.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.viewbinding.ViewBinding
import com.fongmi.android.tv.App
import com.fongmi.android.tv.ai.*
import com.fongmi.android.tv.ui.base.BaseActivity
import java.util.concurrent.Executors

class AiSettingsActivity : BaseActivity() {
    companion object { @JvmStatic fun start(activity: Activity) { activity.startActivity(Intent(activity, AiSettingsActivity::class.java)) } }
    private var key by mutableStateOf("")
    private var status by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var speechStatus by mutableStateOf("")
    private var speechBusy by mutableStateOf(false)
    private var speechTask: CloudSpeech? = null
    private var speechGeneration = 0
    private var request: Qwen? = null
    private var generation = 0
    private val worker = Executors.newFixedThreadPool(2)
    private var toolbar: com.fongmi.android.tv.ui.custom.SecondaryGlassToolbarView? = null
    private var topInset by mutableIntStateOf(0)
    override fun getBinding(): ViewBinding {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        key = AiCredentials.read()
        val view = ComposeView(this).apply {
            isSaveEnabled = false
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val top = with(androidx.compose.ui.platform.LocalDensity.current) { topInset.toDp() }
                val background = if (isSystemInDarkTheme()) Color(0xFF161719) else Color(0xFFF3F4F6)
                Column(Modifier.fillMaxSize().background(background).navigationBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                        .padding(top = top + 84.dp, bottom = 24.dp).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ConfigCard("千问服务", "一份密钥，找片、联网与语音都能用") {
                            AiText("百炼 API Key", 13, true)
                            AiField(key, "填写北京地域的百炼密钥", true) {
                                key = it.take(512); status = ""; speechStatus = ""; cancelTest()
                                speechGeneration++; speechTask?.close(); speechBusy = false
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Action("保存", !busy && !speechBusy, true) {
                                    try { AiCredentials.save(key.trim()); key = key.trim(); status = "已保存，找片与语音共用此密钥" }
                                    catch (_: Exception) { status = "保存失败，请检查密钥格式" }
                                }
                                Action(if (busy) "连接中…" else "测试找片", !busy && key.isNotBlank()) { test() }
                                Action("清除", !busy && !speechBusy) { try { AiCredentials.save(""); key = ""; status = "密钥已清除" } catch (_: Exception) { status = "清除失败" } }
                            }
                            if (status.isNotBlank()) AiText(status, 12, true)
                            AiText("已预设千问 Plus 与联网搜索。对话、搜索和语音按百炼账户实际用量计费，无需填写模型 ID。", 12, true)
                            Link("前往百炼获取密钥 ↗", "https://bailian.console.aliyun.com/")
                        }
                        ConfigCard("实时语音", "按住说话，松手发送") {
                            AiText("使用上方同一份百炼密钥。语音会发送至千问识别，取消后停止发送。", 13, true)
                            Action(if (speechBusy) "连接中…" else "测试语音", !speechBusy && key.isNotBlank()) { testSpeech() }
                            if (speechStatus.isNotBlank()) AiText(speechStatus, 12, true)
                        }
                        AiText("密钥加密保存在本机当前账号，不参与云同步。测试连接会产生少量服务用量。", 12, true, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
        val root = android.widget.FrameLayout(this)
        root.addView(view, android.widget.FrameLayout.LayoutParams(-1, -1))
        toolbar = com.fongmi.android.tv.ui.custom.SecondaryGlassToolbarView(this).apply {
            setTitle("AI 设置"); setPrimaryActionVisible(false); setBackClickListener { finish() }
            setBackdropView(view); setRenderingEnabled(true)
        }
        root.addView(toolbar, android.widget.FrameLayout.LayoutParams(-1, -2))
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            topInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars()).top
            toolbar?.setTopInsetPixels(topInset); insets
        }
        return ViewBinding { root }
    }
    @Composable private fun ConfigCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(aiBackground()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AiText(title, 20, bold = true); AiText(subtitle, 13, true)
            Spacer(Modifier.height(2.dp)); content()
        }
    }
    @Composable private fun Action(text: String, enabled: Boolean = true, primary: Boolean = false, action: () -> Unit) {
        Box(Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (primary) Color(0xFF1677FF).copy(alpha = if (enabled) 1f else .4f) else aiSurface())
            .clickable(enabled = enabled, onClick = action).padding(horizontal = 15.dp, vertical = 12.dp)) {
            androidx.compose.foundation.text.BasicText(text, style = androidx.compose.ui.text.TextStyle(
                color = if (primary) Color.White else aiTextColor().copy(alpha = if (enabled) 1f else .4f), fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)))
        }
    }
    @Composable private fun Link(label: String, url: String) { AiText(label, 12, true, modifier = Modifier.clickable { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.padding(vertical = 4.dp)) }
    private fun cancelTest() { generation++; request?.cancel(); busy = false }
    private fun testSpeech() {
        val value = key.trim()
        if (value.length !in 10..512 || value.any { it.isWhitespace() || it.code !in 33..126 }) { speechStatus = "语音密钥格式不正确"; return }
        val token = ++speechGeneration; speechBusy = true; speechStatus = ""
        worker.execute {
            val result = try {
                CloudSpeech(value) {}.use { task ->
                    speechTask = task
                    if (token != speechGeneration) return@execute
                    task.awaitReady()
                }
                "语音连接成功"
            } catch (e: Exception) { e.message ?: "语音连接失败" }
            App.post { if (token == speechGeneration && !isDestroyed) { speechBusy = false; speechStatus = result } }
        }
    }
    private fun test() {
        val value = key.trim(); val token = ++generation
        val api = Qwen().also { request = it }; busy = true; status = ""
        worker.execute {
            val result = try { api.test(value); "模型连接成功，请保存配置" } catch (e: Exception) { e.message ?: "连接失败，请检查配置" }
            App.post { if (token == generation && !isDestroyed) { busy = false; status = result } }
        }
    }
    override fun onDestroy() { generation++; speechGeneration++; request?.cancel(); speechTask?.close(); worker.shutdownNow(); toolbar?.setRenderingEnabled(false); super.onDestroy() }
}
