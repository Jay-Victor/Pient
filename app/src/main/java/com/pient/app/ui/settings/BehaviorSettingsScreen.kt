package com.pient.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.pient.app.data.i18n.L
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.pient.app.data.DrawerMode
import com.pient.app.data.SettingsStore
import com.pient.app.runtime.PiKeepAlive
import com.pient.app.ui.components.DividerLine
import com.pient.app.ui.components.SectionHeader
import com.pient.app.ui.components.isTabletLayout

/**
 * 行为设置（2026-08-28；2026-09-10 改为三选一）：
 * 手机端 = 侧边栏展出方式三选一 —— 水平滑出（默认，浮层 + 遮罩）/ 3D 透视展开 / 推动展开；
 * 平板端 = 固定压缩滑出（不支持 3D 透视展开与推动展开），故只呈现一种方式（用户决策 2026-09-10）。
 * 切换即时生效并持久化（prefs `drawer_mode`）；平板端不写 prefs，手机端所选方式得以保留。
 */
@Composable
fun BehaviorSettingsScreen(nav: NavController) {
    val isTablet = isTabletLayout()
    val context = LocalContext.current
    // 通知权限（Android 13+）：常驻通知要真显示，得在开档时申请一次（没有它服务照跑、通知不显示）
    val notifPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(context, L.settings.residentNotificationNoPermission, Toast.LENGTH_LONG).show()
            }
        }

    /** 开/关「后台常驻通知」：改状态（落盘见 PientApp 的持久化 effect）+ 当场挂/撤常驻档 */
    fun applyResidentNotification(enabled: Boolean) {
        SettingsStore.residentNotification = enabled
        PiKeepAlive.setResident(context, enabled)
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 消息通知权限（Android 13+）：缺它通知不显示 —— 开总开关时申请一次（同上，不拦开关本身）
    val replyNotifPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(context, L.settings.messageNotifyNoPermission, Toast.LENGTH_LONG).show()
            }
        }

    /** 开/关「消息通知」：改状态（落盘见 PientApp 的持久化 effect）；打开时补一次通知权限 */
    fun applyReplyNotify(enabled: Boolean) {
        SettingsStore.replyNotify = enabled
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            replyNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Icon(
                Icons.Outlined.ArrowBack, L.common.back,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = { nav.popBackStack() }),
            )
            Text(
                L.settings.behavior,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { SectionHeader(L.settings.behaviorSubtitle) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    if (isTablet) {
                        // 平板端固定压缩滑出（不支持 3D 透视展开 / 推动展开）：只有一种方式，
                        // 因此整行不可点、也不写 prefs —— 手机端所选展出方式在平板仍被保留。
                        DrawerModeRow(
                            icon = Icons.Outlined.Compress,
                            title = L.theme.compressSlide,
                            desc = L.theme.compressSlideDesc,
                            selected = true,
                            onClick = null,
                        )
                    } else {
                        DrawerModeOptions.forEachIndexed { i, option ->
                            if (i > 0) DividerLine()
                            DrawerModeRow(
                                icon = option.icon,
                                title = option.title,
                                desc = option.desc,
                                selected = SettingsStore.drawerMode == option.mode,
                                onClick = { SettingsStore.drawerMode = option.mode },
                            )
                        }
                    }
                }
            }
            item { SectionHeader(L.theme.filePreview) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    SettingToggleRow(
                        icon = Icons.Outlined.SwapHoriz,
                        title = L.theme.noWrap,
                        desc = L.theme.noWrapDesc,
                        checked = SettingsStore.filePreviewNoWrap,
                        onCheckedChange = { SettingsStore.filePreviewNoWrap = it },
                    )
                }
            }
            item { SectionHeader(L.theme.startup) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    SettingToggleRow(
                        icon = Icons.Outlined.Animation,
                        title = L.theme.startupAnimation,
                        desc = L.theme.startupAnimationDesc,
                        checked = SettingsStore.startupAnimation,
                        onCheckedChange = { SettingsStore.startupAnimation = it },
                    )
                }
            }
            item { SectionHeader(L.settings.backgroundKeepAlive) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    SettingToggleRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = L.settings.residentNotification,
                        desc = L.settings.residentNotificationDesc,
                        checked = SettingsStore.residentNotification,
                        onCheckedChange = { applyResidentNotification(it) },
                    )
                }
            }
            // 消息通知（2026-09-16，行为设置）：AI 回复完成且应用不在前台时发系统通知；
            // 提示音 / 震动是通知渠道属性，各自独立开关（见 runtime/ReplyNotify.kt）
            item { SectionHeader(L.settings.messageNotify) }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                ) {
                    SettingToggleRow(
                        icon = Icons.Outlined.NotificationsNone,
                        title = L.settings.messageNotify,
                        desc = L.settings.messageNotifyDesc,
                        checked = SettingsStore.replyNotify,
                        onCheckedChange = { applyReplyNotify(it) },
                    )
                    DividerLine()
                    SettingToggleRow(
                        icon = Icons.AutoMirrored.Outlined.VolumeUp,
                        title = L.settings.messageNotifySound,
                        desc = L.settings.messageNotifySoundDesc,
                        checked = SettingsStore.replyNotifySound,
                        onCheckedChange = { SettingsStore.replyNotifySound = it },
                    )
                    DividerLine()
                    SettingToggleRow(
                        icon = Icons.Outlined.Vibration,
                        title = L.settings.messageNotifyVibrate,
                        desc = L.settings.messageNotifyVibrateDesc,
                        checked = SettingsStore.replyNotifyVibrate,
                        onCheckedChange = { SettingsStore.replyNotifyVibrate = it },
                    )
                }
            }
        }
    }
}

/** 开关型设置行（图标 + 标题 + 说明 + 开关；整行可点；文件预览页设置 / 开屏设置共用） */
@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** 展出方式选项（顺序即卡片内的排列顺序） */
private data class DrawerModeOption(
    val mode: DrawerMode,
    val icon: ImageVector,
    val title: String,
    val desc: String,
)

private val DrawerModeOptions: List<DrawerModeOption>
    get() = listOf(
    DrawerModeOption(
        mode = DrawerMode.SLIDE,
        icon = Icons.AutoMirrored.Outlined.ViewSidebar,
        title = L.theme.drawerSlide,
        desc = L.theme.drawerSlideDesc,
    ),
    DrawerModeOption(
        mode = DrawerMode.PERSPECTIVE,
        icon = Icons.Outlined.ViewInAr,
        title = L.theme.drawerPerspective,
        desc = L.theme.drawerPerspectiveDesc,
    ),
    DrawerModeOption(
        mode = DrawerMode.PUSH,
        icon = Icons.AutoMirrored.Outlined.FormatIndentIncrease,
        title = L.theme.drawerPush,
        desc = L.theme.drawerPushDesc,
    ),
)

/** 展出方式选项行：图标 + 标题 + 说明 + 选中对勾（整行可点，选中态变色加勾）；onClick = null 时行不可点（平板固定项） */
@Composable
private fun DrawerModeRow(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Icon(
            icon, null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.Check, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}
