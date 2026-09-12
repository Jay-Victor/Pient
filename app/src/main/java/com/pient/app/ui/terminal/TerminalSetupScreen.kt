package com.pient.app.ui.terminal

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.pient.app.runtime.PiRuntime
import com.pient.app.runtime.PiTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.pient.app.data.ChatState
import com.pient.app.ui.components.PientButton
import com.pient.app.ui.theme.PientPanel

/**
 * 环境配置页（设计计划 5.4；Operit SetupScreen 导航形态参考）：
 * 终端页快捷按键栏右端「环境配置」进入。rootfs 就绪检测 → 一键配置 → 完成返回。
 * 检测与就绪判定都是真实的（文件系统判定 + 一条真跑出来的探针命令）；尚未实现的是
 * 「缺 rootfs 时的一键解包」——ROOTFS 目前由 runtime/scripts 部署（首启解包见开发计划）。
 */
/** 环境自检探针：一条命令同时验证 rootfs 能跑、GNU bash、发行版与内核 */
private const val PROBE_CMD =
    """ . /etc/os-release; echo "${'$'}PRETTY_NAME · ${'$'}(uname -sr) · ${'$'}(bash --version | head -1)""""

@Composable
fun TerminalSetupScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var configuring by remember { mutableStateOf(false) }
    // 真实检测：文件系统判定（rootfs / bash / PRoot / 包装脚本）+ 一条真跑出来的探针输出
    var checks by remember { mutableStateOf(PiRuntime.terminalChecks(context)) }
    var probe by remember { mutableStateOf("") }
    var envReady by remember { mutableStateOf(false) }

    suspend fun refresh() {
        checks = withContext(Dispatchers.IO) { PiRuntime.terminalChecks(context) }
        probe = withContext(Dispatchers.IO) { PiTerminal.execOnce(context, PROBE_CMD) }
        envReady = checks.all { it.second } && probe.isNotEmpty()
    }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：返回 + 标题
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
                "环境配置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 配置项卡片
            PientPanel(modifier = Modifier.fillMaxWidth()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp),
                ) {
                    checks.forEachIndexed { i, (label, ok) ->
                        EnvRow(
                            label = label,
                            done = ok,
                            configuring = configuring && !ok && i == 0,
                        )
                    }
                }
            }

            // 状态说明
            Text(
                if (envReady) "环境就绪 · $probe\n终端页与 Agent 的 bash 工具共用同一 rootfs"
                else "缺少：" + checks.filterNot { it.second }.joinToString("、") { it.first } +
                    "\n（开发形态用 runtime/scripts 部署；首启解包待实现）",
                style = MaterialTheme.typography.labelSmall,
                color = if (envReady) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 一键配置 / 完成
            PientButton(
                text = if (envReady) "完成" else "重新检测",
                onClick = {
                    if (envReady) {
                        nav.popBackStack()
                    } else {
                        scope.launch {
                            configuring = true
                            refresh()
                            configuring = false
                            if (!envReady) {
                                Toast.makeText(context, "仍缺少组件：见上方清单", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )
        }
    }
}

@Composable
private fun EnvRow(label: String, done: Boolean, configuring: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Outlined.Check else Icons.Outlined.Terminal,
            null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (done) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        if (configuring && !done) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
