package cn.antares.helldiver_trainer.ui.wiki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.MR
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.ic_arrow
import cn.antares.helldiver_trainer.util.widget.SuperButton
import cn.antares.helldiver_trainer.wiki.generated.WikiIconRegistry
import dev.icerock.moko.resources.compose.painterResource

/** 资料库卡片默认背景色。 */
val WikiCardBg = Color(0xFF2B2B2B)

/** 资料库次级面板与筛选标签默认背景色。 */
val WikiPanelBg = Color(0xFF323232)

/** 资料库通用浅色半透明边框颜色。 */
val WikiBorderColor = Color.White.copy(alpha = 0.14f)

/**
 * 资料库统一样式的表面卡片容器，支持选中高亮态与可选点击事件。
 */
@Composable
fun WikiSurfaceCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val bg = if (selected) primary.copy(alpha = 0.16f) else WikiCardBg
    val border =
        if (selected) BorderStroke(1.5.dp, primary) else BorderStroke(1.dp, WikiBorderColor)
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = bg,
        border = border,
    ) {
        content()
    }
}

@Composable
fun WikiKindBadge(kind: WikiKind, extraLabel: String? = null) {
    val label = if (extraLabel.isNullOrBlank()) kind.labelZh else "${kind.labelZh} · $extraLabel"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 资料库条目图标徽章：优先渲染本地注册的矢量图标，缺失时按大类回退为默认占位图标。
 */
@Composable
fun WikiIconBadge(
    iconKey: String?,
    kind: WikiKind,
    size: Dp = 44.dp,
) {
    val imageRes = WikiIconRegistry.resolve(iconKey)
    if (imageRes != null) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(WikiPanelBg)
                .padding(4.dp),
        )
    } else {
        val fallbackIcon = when (kind) {
            WikiKind.STRATAGEM -> Icons.Rounded.MilitaryTech
            WikiKind.WEAPON -> Icons.Rounded.Shield
            WikiKind.ENEMY -> Icons.Rounded.BugReport
        }
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(WikiPanelBg)
                .border(1.dp, WikiBorderColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/**
 * 战备呼叫方向箭头序列组件，按方向枚举旋转共用箭头图标并自动换行排列。
 */
@Composable
fun DirectionArrowRow(
    directions: List<WikiDirection>,
    arrowSize: Dp = 18.dp,
) {
    if (directions.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        directions.forEach { dir ->
            Image(
                painter = painterResource(MR.images.ic_arrow),
                contentDescription = dir.name,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(arrowSize)
                    .rotate(
                        when (dir) {
                            WikiDirection.UP -> -90f
                            WikiDirection.DOWN -> 90f
                            WikiDirection.LEFT -> 180f
                            WikiDirection.RIGHT -> 0f
                        },
                    ),
            )
        }
    }
}

/**
 * 资料库搜索栏组件，支持名称/型号/俗称文本检索与底部快捷方向符号追加（用于战备指令反查）。
 */
@Composable
fun WikiSearchBar(
    queryText: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = queryText,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    "搜索名称、型号（AR-23）、俗称（500kg）或方向",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingIcon = {
                if (queryText.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Rounded.Clear,
                            contentDescription = "清空",
                            tint = Color.LightGray,
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = WikiCardBg,
                unfocusedContainerColor = WikiCardBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = WikiBorderColor,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("方向反查：", color = Color.LightGray, fontSize = 12.sp)
            listOf("↑", "↓", "←", "→").forEach { symbol ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(WikiPanelBg)
                        .border(1.dp, WikiBorderColor, RoundedCornerShape(6.dp))
                        .clickable { onQueryChange(queryText + symbol) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        symbol,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun WikiFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) primary else WikiPanelBg)
            .border(1.dp, if (selected) primary else WikiBorderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun WikiErrorBox(
    message: String,
    showRetry: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WikiSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message, color = Color(0xFFFF8A80), fontSize = 14.sp)
            if (showRetry) {
                Spacer(Modifier.height(10.dp))
                SuperButton(text = "重试", icon = Icons.Rounded.Refresh, onClick = onRetry)
            }
        }
    }
}

/**
 * 资料库检索结果列表项卡片，展示图标、名称、大类细分标签以及战备方向序列或摘要。
 */
@Composable
fun WikiIndexItemCard(
    item: WikiIndexItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val subTag = when (item.kind) {
        WikiKind.STRATAGEM -> item.stratagemCategory?.labelZh
        WikiKind.WEAPON -> listOfNotNull(item.weaponSlot?.labelZh, item.weaponClass?.labelZh)
            .joinToString(" · ")
            .ifBlank { null }

        WikiKind.ENEMY -> listOfNotNull(item.enemyFaction?.labelZh, item.enemySizeClass?.labelZh)
            .joinToString(" · ")
            .ifBlank { null }
    }
    WikiSurfaceCard(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WikiIconBadge(iconKey = item.iconKey, kind = item.kind, size = 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.nameZh,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    WikiKindBadge(kind = item.kind, extraLabel = subTag)
                }
                if (!item.nameEn.isNullOrBlank()) {
                    Text(
                        text = item.nameEn,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.kind == WikiKind.STRATAGEM && item.directions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    DirectionArrowRow(directions = item.directions, arrowSize = 16.dp)
                } else if (!item.summary.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.summary,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
