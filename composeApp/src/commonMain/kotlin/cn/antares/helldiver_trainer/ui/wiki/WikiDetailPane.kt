package cn.antares.helldiver_trainer.ui.wiki

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.bridge.openWebPage
import cn.antares.helldiver_trainer.data.wiki.model.EnemyDetail
import cn.antares.helldiver_trainer.data.wiki.model.StratagemDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiSource
import cn.antares.helldiver_trainer.data.wiki.model.formatDisplay
import cn.antares.helldiver_trainer.viewmodel.WikiDetailBundle
import cn.antares.helldiver_trainer.viewmodel.WikiLoadState

/**
 * 资料库条目详情面板，负责展示未选择空态、加载态、错误态及条目完整结构化信息。
 */
@Composable
fun WikiDetailPane(
    detailState: WikiLoadState<WikiDetailBundle>?,
    onRetryDetail: () -> Unit,
    onOpenRelated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (detailState) {
        null -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("从左侧选择条目查看详情", color = Color.LightGray, fontSize = 14.sp)
            }
        }

        is WikiLoadState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is WikiLoadState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                WikiErrorBox(
                    message = detailState.message,
                    showRetry = !detailState.isUnsupportedVersion,
                    onRetry = onRetryDetail,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        is WikiLoadState.Content -> {
            val detail = detailState.value.entry.detail
            val sources = detailState.value.entry.sources
            val related = detailState.value.related
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IdentitySection(detail)
                StatsSection(detail)
                SummaryAndRelationsSection(detail, related, onOpenRelated)
                SourcesSection(detail, sources)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 详情页头部身份标识区，展示条目图标、中英文名称、分类标签及常用别名。
 */
@Composable
private fun IdentitySection(detail: WikiEntryDetail) {
    val subTag = when (detail.kind) {
        WikiKind.STRATAGEM -> detail.stratagem?.category?.labelZh
        WikiKind.WEAPON -> detail.weapon?.let { "${it.slot.labelZh} · ${it.weaponClass.labelZh}" }
        WikiKind.ENEMY -> detail.enemy?.let { "${it.faction.labelZh} · ${it.sizeClass.labelZh}" }
    }
    WikiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WikiIconBadge(iconKey = detail.iconKey, kind = detail.kind, size = 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.nameZh,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!detail.nameEn.isNullOrBlank()) {
                        Text(
                            text = detail.nameEn,
                            color = Color.LightGray,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    WikiKindBadge(kind = detail.kind, extraLabel = subTag)
                }
            }
            if (detail.aliases.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detail.aliases.forEach { alias ->
                        Text(
                            text = "别名: ${alias.text}",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 常查数值分区容器，按条目大类分发渲染对应的核心属性块。
 */
@Composable
private fun StatsSection(detail: WikiEntryDetail) {
    WikiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "常查字段",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            detail.stratagem?.let { StratagemStats(it) }
            detail.weapon?.let { WeaponStats(it) }
            detail.enemy?.let { EnemyStats(it) }
        }
    }
}

/**
 * 战备核心属性区：渲染呼叫方向指令、分类、呼叫延迟、基础冷却、使用次数与解锁条件。
 */
@Composable
private fun StratagemStats(s: StratagemDetail) {
    Text("呼叫方向", color = Color.LightGray, fontSize = 12.sp)
    DirectionArrowRow(directions = s.directions, arrowSize = 26.dp)
    HorizontalDivider(color = WikiBorderColor)
    FieldRow("分类", s.category.labelZh)
    FieldRow("呼叫延迟", s.callInSeconds.formatDisplay())
    FieldRow("基础冷却", s.cooldownSeconds.formatDisplay())
    FieldRow("使用次数", s.uses.formatDisplay())
    FieldRow("解锁条件", s.unlockRequirement ?: "待核验")
}

/**
 * 武器核心属性区：渲染槽位、子类以及各配置/改型的弹匣容量、射速、射击模式和攻击伤害穿甲参数。
 */
@Composable
private fun WeaponStats(w: WeaponDetail) {
    FieldRow("槽位", w.slot.labelZh)
    FieldRow("武器子类", w.weaponClass.labelZh)
    w.configurations.forEachIndexed { idx, cfg ->
        if (idx > 0) Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = WikiBorderColor)
        Text(
            text = cfg.labelZh,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        cfg.modelCode?.let { FieldRow("型号", it) }
        FieldRow("弹匣容量", cfg.magazineCapacity.formatDisplay())
        FieldRow("射速", cfg.fireRateRpm.formatDisplay())
        if (cfg.fireModes.isNotEmpty()) {
            FieldRow("射击模式", cfg.fireModes.joinToString(" / "))
        }
        cfg.attacks.forEach { atk ->
            FieldRow(
                label = "攻击 · ${atk.nameZh}",
                value = "伤害 ${atk.damage.formatDisplay()} | 穿甲 ${atk.armorPenetration.formatDisplay()}",
            )
        }
    }
}

/**
 * 敌人核心属性区：渲染阵营、体型等级、所属家族及各部位护甲与备注。
 */
@Composable
private fun EnemyStats(e: EnemyDetail) {
    FieldRow("阵营", e.faction.labelZh)
    FieldRow("体型等级", e.sizeClass.labelZh)
    FieldRow("家族", e.familyKey)
    if (e.parts.isEmpty()) {
        FieldRow("部位数据", "待核验")
    } else {
        HorizontalDivider(color = WikiBorderColor)
        Text("部位与护甲", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        e.parts.forEach { part ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        part.nameZh,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "护甲 ${part.armor.formatDisplay()}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (!part.notes.isNullOrBlank()) {
                    Text(part.notes, color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * 说明与关联跳转区：展示条目补充说明及关联条目卡片，点击可经 `onOpenRelated` 压栈跳转。
 */
@Composable
private fun SummaryAndRelationsSection(
    detail: WikiEntryDetail,
    related: List<WikiRelatedItem>,
    onOpenRelated: (String) -> Unit,
) {
    if (detail.summary.isNullOrBlank() && related.isEmpty()) return
    WikiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "说明与关联",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!detail.summary.isNullOrBlank()) {
                Text(detail.summary, color = Color.White, fontSize = 13.sp)
            }
            if (related.isNotEmpty()) {
                HorizontalDivider(color = WikiBorderColor)
                related.forEach { rel ->
                    WikiSurfaceCard(
                        onClick = { onOpenRelated(rel.target.entryId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WikiIconBadge(
                                iconKey = rel.target.iconKey,
                                kind = rel.target.kind,
                                size = 36.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rel.labelZh,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = rel.target.nameZh,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (rel.target.directions.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    DirectionArrowRow(
                                        directions = rel.target.directions,
                                        arrowSize = 14.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 资料来源与版本溯源区：汇总条目级 `detail.sourceId` 与战备/武器/敌人各字段级的 `sourceId`（使用有序集合去重），展示对应核验版本与外部参考链接。
 */
@Composable
private fun SourcesSection(
    detail: WikiEntryDetail,
    sources: Map<String, WikiSource>,
) {
    val referencedIds = linkedSetOf(detail.sourceId).apply {
        detail.stratagem?.let { s ->
            s.callInSeconds.sourceId?.let(::add)
            s.cooldownSeconds.sourceId?.let(::add)
            s.uses.sourceId?.let(::add)
        }
        detail.weapon?.configurations?.forEach { cfg ->
            cfg.magazineCapacity.sourceId?.let(::add)
            cfg.fireRateRpm.sourceId?.let(::add)
            cfg.attacks.forEach { atk ->
                atk.damage.sourceId?.let(::add)
                atk.armorPenetration.sourceId?.let(::add)
            }
        }
        detail.enemy?.parts?.forEach { p ->
            p.sourceId?.let(::add)
            p.armor.sourceId?.let(::add)
        }
    }

    WikiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "资料来源与版本",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            FieldRow("条目 ID", detail.entryId)
            FieldRow("已核验游戏补丁", detail.verifiedPatch ?: "待核验")
            referencedIds.forEach { sid ->
                val src = sources[sid]
                if (src != null) {
                    HorizontalDivider(color = WikiBorderColor)
                    Text(
                        src.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    val meta = listOfNotNull(
                        src.sourceType,
                        src.verifiedDate?.let { "核验: $it" },
                        src.defaultPatch?.let { "补丁: $it" },
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, color = Color.LightGray, fontSize = 11.sp)
                    }
                    if (!src.url.isNullOrBlank()) {
                        Text(
                            text = src.url,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clickable { openWebPage(src.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
