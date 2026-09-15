package com.pient.app.ui.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.PluginItem
import com.pient.app.runtime.PiPackages
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.components.PientDialog
import com.pient.app.ui.components.PientSegmented
import com.pient.app.ui.theme.MonoFont
import kotlinx.coroutines.launch

/**
 * 插件管理（**真数据层**，2026-09-15 接回 pi）：
 *
 * 页面上的每个动作 = 一条 pi 官方命令（[PiPackages]，口径见那里的 KDoc）：
 * 列表 = `pi list`（全局段 = 用户设置里的包，项目段 = 项目设置里的包）；
 * 安装 = `pi install <source>`（项目分段带 `-l`）；更新 = `pi update <source>` / 全部 = `pi update --extensions`；
 * 移除 = `pi remove <source>`。装/删/更新都在终端会话「pi 包管理」里跑 —— 切到终端页就能看全过程。
 *
 * 行上的开关是**只读状态**（pi 里「配置了」即生效，没有包级开关）：保持 `enabled` 真值 +
 * `onCheckedChange = null`（不灰、不改），真正的动作在详情弹窗里。
 */
@Composable
fun PluginsScreen(nav: NavController) {
    var segment by remember { mutableStateOf(0) }
    var installOpen by remember { mutableStateOf(false) }
    var detailFor by remember { mutableStateOf<PluginItem?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── 真数据：pi list 的结果（装/删/更新后重拉） ──
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadedOnce by remember { mutableStateOf(false) }
    fun reload() {
        scope.launch {
            loadError = PiPackages.refresh(context)
            loadedOnce = true
        }
    }
    LaunchedEffect(Unit) { reload() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Outlined.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = { nav.popBackStack() }),
                )
                Text(
                    "插件管理",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            PientSegmented(
                labels = listOf("全局", "项目"),
                selected = segment,
                onSelect = { segment = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                if (segment == 0) "~/.pi/agent/settings.json" else "当前项目 .pi/settings.json（pi install -l）",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )

            // 插件列表（pi list 的真结果）
            val list = if (segment == 0) PiPackages.global else PiPackages.project
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 90.dp,
                ),
            ) {
                items(list.size, key = { i -> list[i].source }) { i ->
                    PluginRow(list[i], onClick = { detailFor = list[i] })
                }
                if (list.isEmpty()) {
                    item {
                        Text(
                            when {
                                loadError != null -> "读取失败：$loadError"
                                !loadedOnce -> "正在读取 pi 的包列表…"
                                segment == 0 -> "还没有配置任何插件。点右下 + 安装（npm: 包 / git: / 本地路径）。"
                                else -> "当前项目没有插件（pi install -l 装到项目里）。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (PiPackages.running) {
                    item {
                        Text(
                            "⏳ ${PiPackages.step}（终端页「${PiPackages.SESSION}」会话可看全过程）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        // 右下 FAB → 安装弹窗
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(onClick = { installOpen = true }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, "安装插件", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    if (installOpen) {
        InstallPluginDialog(
            global = segment == 0,
            onDismiss = { installOpen = false },
            onInstalled = { source ->
                // 真安装：pi install <source>（项目分段加 -l），跑完重拉 pi list
                val name = parsePluginName(source)
                PiPackages.install(context, source, local = segment == 1, onDone = { reload() })
                toast(context, "已开始安装 $name —— 终端页「${PiPackages.SESSION}」可看进度")
                installOpen = false
            },
        )
    }

    // 插件详情弹窗（2026-09-08：点插件卡片弹出；删除 = 从列表移除插件及全部文件）
    detailFor?.let { item ->
        PluginDetailDialog(
            item = item,
            onDismiss = { detailFor = null },
            onDelete = {
                PiPackages.remove(context, item.source, local = !item.global, onDone = { reload() })
                toast(context, "已开始移除 ${item.name}")
                detailFor = null
            },
            onUpdate = {
                PiPackages.update(context, item.source, onDone = { reload() })
                toast(context, "已开始更新 ${item.name}")
                detailFor = null
            },
        )
    }
}

/** 从安装源解析插件名（npm:@x/name、git:host/name、路径尾段） */
private fun parsePluginName(source: String): String {
    val s = source.removePrefix("pi install").trim()
    return when {
        s.startsWith("npm:") -> s.substringAfterLast('/').substringBefore('@')
        s.startsWith("git:") -> s.substringAfterLast('/')
        s.startsWith("https://") -> s.substringAfterLast('/')
        else -> s.substringAfterLast('/').ifBlank { s }
    }.ifBlank { "pi-plugin-${s.hashCode().and(0xFFFF)}" }
}

private fun toast(context: android.content.Context, msg: String) {
    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun PluginRow(item: PluginItem, onClick: () -> Unit) {
    // 只读状态：pi 没有包级开关，「配置了」即生效（保持真值 + onCheckedChange=null，不灰不改）
    val enabled = item.enabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onClick),
        ) {
            Text(
                item.name,
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = MonoFont),
                color = if (enabled) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "来源：${item.source}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // 注意：不能用 Modifier.size() 压缩 Switch——内部轨道仍按默认 52dp 绘制并居中
        // 溢出，会向左侵入内容文字造成视觉重叠（实测溢出 ~10dp）
        Switch(
            checked = enabled,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * 安装弹窗：安装源输入框（pi install npm:xxx 或 npm:/git:/https:/本地路径）
 * + "pi.dev/packages 为官方插件市场"说明；安装中进度、完成刷新列表。
 */
@Composable
private fun InstallPluginDialog(
    global: Boolean,
    onDismiss: () -> Unit,
    onInstalled: (source: String) -> Unit,
) {
    var source by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val valid = source.isNotBlank()

    Box(Modifier.fillMaxSize()) {
        PientDialog(
            title = "添加插件",
            onDismiss = onDismiss,
            confirmText = "安装",
            confirmEnabled = valid && !installing,
            showClose = false,
            onConfirm = {
                // 真安装立刻交给数据层（pi install 在终端会话里跑，页面这里只负责关弹窗 + 提示）
                installing = true
                error = null
                onInstalled(source.trim())
            },
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = source,
                        onValueChange = { source = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = MonoFont,
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (source.isEmpty()) {
                                Text(
                                    "pi install npm:xxx …",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = MonoFont),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                            inner()
                        },
                    )
                }
                Text(
                    "支持：npm: 包 / git: 仓库 / https 链接 / 本地路径（git 类需 Ubuntu 里已装 git）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "pi.dev/packages 为官方插件市场",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (global) "装到全局：写 ~/.pi/agent/settings.json"
                    else "装到项目：写 .pi/settings.json（等价 pi install -l）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    "安装过程在终端页「${PiPackages.SESSION}」会话里可见",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (installing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "安装中…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
