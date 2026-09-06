package com.pient.app.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pient.app.ui.theme.MonoFont
import com.pient.app.ui.theme.PientPanel

/**
 * 系统提示词只读面板（2026-09-01，pi-web system 面板同款）：
 * - 输入栏上下文指示器右侧文件图标点开；浮层家族同款规格（268.8dp 宽、16dp 圆角、
 *   右距 6dp、bottomOffset 锚定输入栏上缘、点外关闭无 scrim）。
 * - 内容 = agent.state.systemPrompt 全文只读展示：等宽字体、11sp、最多 ~280dp 高可滚动；
 *   空值显示空态斜体（pi-web system.empty 同款语义）。
 */
@Composable
fun SystemPromptPanel(
    prompt: String,
    bottomOffset: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    PientPanel(
        modifier = modifier
            .padding(end = 6.dp, bottom = bottomOffset)
            .width(268.8.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            // 标题行：「系统提示词」+ 右侧「只读」标注
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "系统提示词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "只读",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 全文只读展示（pi-web：等宽 + pre-wrap 语义）
            if (prompt.isNotEmpty()) {
                Text(
                    prompt,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = MonoFont,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                Text(
                    "暂无系统提示词",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}
