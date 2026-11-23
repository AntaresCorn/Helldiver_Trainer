package cn.antares.helldiver_trainer.ui.wiki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind

/**
 * 资料库首页分类浏览面板，展示战备、武器、敌人三大类入口及对应条目总数。
 */
@Composable
fun WikiHomePane(
    kindCounts: Map<WikiKind, Int>,
    onSelectKind: (WikiKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "按分类浏览",
            color = Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        CategoryEntryCard(
            title = "战备",
            subtitle = "呼叫指令、分类筛选与训练器同源数据",
            count = kindCounts[WikiKind.STRATAGEM] ?: 0,
            icon = Icons.Rounded.MilitaryTech,
            onClick = { onSelectKind(WikiKind.STRATAGEM) },
        )
        CategoryEntryCard(
            title = "武器",
            subtitle = "主武器、副武器、投掷物分类参数与改型",
            count = kindCounts[WikiKind.WEAPON] ?: 0,
            icon = Icons.Rounded.Shield,
            onClick = { onSelectKind(WikiKind.WEAPON) },
        )
        CategoryEntryCard(
            title = "敌人",
            subtitle = "按阵营查看敌人家族、变种与部位护甲",
            count = kindCounts[WikiKind.ENEMY] ?: 0,
            icon = Icons.Rounded.BugReport,
            onClick = { onSelectKind(WikiKind.ENEMY) },
        )
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * 首页大类入口卡片，展示分类图标、名称、条目计数与简介说明。
 */
@Composable
private fun CategoryEntryCard(
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    WikiSurfaceCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$count 条",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = Color.LightGray,
            )
        }
    }
}
