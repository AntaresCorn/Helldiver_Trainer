package cn.antares.helldiver_trainer.ui.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.bridge.openWebPage
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.StratagemDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponConfigDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
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
    isWideScreen: Boolean = false,
    selectedViewId: String? = null,
    selectedPartId: String? = null,
    previewState: EnemyPartState = EnemyPartState.INTACT,
    goal: RecommendationGoal = RecommendationGoal.KILL,
    selectedAttackRef: AttackRef? = null,
    onSelectView: (String) -> Unit = {},
    onSelectPart: (String) -> Unit = {},
    onSelectGoal: (RecommendationGoal) -> Unit = {},
    onSelectAttack: (AttackRef) -> Unit = {},
    onOpenRecommendedAttack: (AttackRef) -> Unit = {},
    onRetryAnalysis: () -> Unit = {},
    onLoadAnatomyImageBytes: suspend (String) -> WikiResult<ByteArray> = { WikiResult.Missing(it) },
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
            val bundle = detailState.value
            val detail = bundle.entry.detail
            val related = bundle.related
            val enemy = detail.enemy
            val showInteractiveAnatomy = detail.kind == WikiKind.ENEMY &&
                    enemy != null &&
                    (bundle.anatomy != null || (!bundle.anatomyMissing && bundle.anatomyError != null))

            if (showInteractiveAnatomy) {
                InteractiveEnemyDetailPane(
                    bundle = bundle,
                    detail = detail,
                    enemy = enemy,
                    related = related,
                    isWideScreen = isWideScreen,
                    selectedViewId = selectedViewId,
                    selectedPartId = selectedPartId,
                    previewState = previewState,
                    goal = goal,
                    selectedAttackRef = selectedAttackRef,
                    onSelectView = onSelectView,
                    onSelectPart = onSelectPart,
                    onSelectGoal = onSelectGoal,
                    onSelectAttack = onSelectAttack,
                    onOpenRelated = onOpenRelated,
                    onOpenRecommendedAttack = onOpenRecommendedAttack,
                    onRetryAnalysis = onRetryAnalysis,
                    onLoadAnatomyImageBytes = onLoadAnatomyImageBytes,
                    modifier = modifier,
                )
            } else {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IdentitySection(detail)
                    if (detail.kind == WikiKind.ENEMY && enemy != null) {
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
                                EnemyStats(enemy)
                            }
                        }
                        if (enemy.parts.isNotEmpty()) {
                            val selectedPart =
                                enemy.parts.firstOrNull { it.partId == selectedPartId }
                            PartRecommendationSection(
                                selectedPart = selectedPart,
                                goal = goal,
                                recommendationState = bundle.recommendationState,
                                onSelectGoal = onSelectGoal,
                                onOpenRecommendedAttack = onOpenRecommendedAttack,
                                onRetry = onRetryAnalysis,
                            )
                            EnemyMatchupAnalyzerSection(
                                availableAttacks = bundle.availableAttacks,
                                selectedAttackRef = selectedAttackRef,
                                matchupState = bundle.matchupState,
                                onSelectAttack = onSelectAttack,
                                onRetry = onRetryAnalysis,
                            )
                        }
                    } else {
                        StatsSection(detail)
                    }
                    SummaryAndRelationsSection(detail, related, onOpenRelated)
                    SourcesSection(bundle)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun InteractiveEnemyDetailPane(
    bundle: WikiDetailBundle,
    detail: WikiEntryDetail,
    enemy: EnemyDetail,
    related: List<WikiRelatedItem>,
    isWideScreen: Boolean,
    selectedViewId: String?,
    selectedPartId: String?,
    previewState: EnemyPartState,
    goal: RecommendationGoal,
    selectedAttackRef: AttackRef?,
    onSelectView: (String) -> Unit,
    onSelectPart: (String) -> Unit,
    onSelectGoal: (RecommendationGoal) -> Unit,
    onSelectAttack: (AttackRef) -> Unit,
    onOpenRelated: (String) -> Unit,
    onOpenRecommendedAttack: (AttackRef) -> Unit,
    onRetryAnalysis: () -> Unit,
    onLoadAnatomyImageBytes: suspend (String) -> WikiResult<ByteArray>,
    modifier: Modifier = Modifier,
) {
    val partsById = remember(enemy.parts) { enemy.parts.associateBy { it.partId } }
    val selectedPart = remember(enemy.parts, selectedPartId) {
        enemy.parts.firstOrNull { it.partId == selectedPartId }
    }
    val listState = remember(detail.entryId) { LazyListState() }
    var stickyHeaderHeightPx by remember { mutableIntStateOf(0) }
    var pendingScrollPartId by remember { mutableStateOf<String?>(null) }
    var scrollNonce by remember { mutableIntStateOf(0) }

    val handleTapFromImage: (String) -> Unit = { partId ->
        onSelectPart(partId)
        pendingScrollPartId = partId
        scrollNonce++
    }

    LaunchedEffect(scrollNonce, isWideScreen) {
        val targetPartId = pendingScrollPartId ?: return@LaunchedEffect
        val partIdx = enemy.parts.indexOfFirst { it.partId == targetPartId }
        if (partIdx < 0) return@LaunchedEffect

        if (isWideScreen) {
            // 宽屏右侧列表项顺序: 0=parts_header, 1..N=parts
            listState.animateScrollToItem(index = 1 + partIdx, scrollOffset = 0)
        } else {
            // 窄屏列表项顺序: 0=identity, 1=sticky_viewer, 2=parts_header, 3..N=parts
            listState.animateScrollToItem(
                index = 3 + partIdx,
                scrollOffset = -stickyHeaderHeightPx,
            )
        }
        pendingScrollPartId = null
    }

    if (isWideScreen) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IdentitySection(detail)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EnemyAnatomyViewer(
                    anatomy = bundle.anatomy,
                    anatomyError = bundle.anatomyError,
                    selectedViewId = selectedViewId,
                    selectedPartId = selectedPartId,
                    previewState = previewState,
                    onSelectView = onSelectView,
                    onTapPartFromImage = handleTapFromImage,
                    onLoadImageBytes = onLoadAnatomyImageBytes,
                    onRetryAnatomy = onRetryAnalysis,
                    modifier = Modifier.weight(0.46f),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "parts_header") {
                        Text(
                            text = "部位列表（点击图片或列表切换部位）",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    itemsIndexed(
                        items = enemy.parts,
                        key = { _, part -> "part_${part.partId}" },
                    ) { _, part ->
                        EnemyPartRow(
                            part = part,
                            isSelected = part.partId == selectedPartId,
                            partsById = partsById,
                            onClick = { onSelectPart(part.partId) },
                        )
                    }
                    if (enemy.parts.isNotEmpty()) {
                        item(key = "recommendations") {
                            PartRecommendationSection(
                                selectedPart = selectedPart,
                                goal = goal,
                                recommendationState = bundle.recommendationState,
                                onSelectGoal = onSelectGoal,
                                onOpenRecommendedAttack = onOpenRecommendedAttack,
                                onRetry = onRetryAnalysis,
                            )
                        }
                        item(key = "matchup") {
                            EnemyMatchupAnalyzerSection(
                                availableAttacks = bundle.availableAttacks,
                                selectedAttackRef = selectedAttackRef,
                                matchupState = bundle.matchupState,
                                onSelectAttack = onSelectAttack,
                                onRetry = onRetryAnalysis,
                            )
                        }
                    }
                    item(key = "summary") {
                        SummaryAndRelationsSection(detail, related, onOpenRelated)
                    }
                    item(key = "sources") {
                        SourcesSection(bundle)
                    }
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "identity") {
                IdentitySection(detail)
            }
            // 图鉴图片容器作为 stickyHeader，并在部位列表结束后放置哨兵 stickyHeader，实现仅在浏览部位列表区间吸顶
            stickyHeader(key = "anatomy_viewer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(bottom = 4.dp)
                        .onSizeChanged { stickyHeaderHeightPx = it.height },
                ) {
                    EnemyAnatomyViewer(
                        anatomy = bundle.anatomy,
                        anatomyError = bundle.anatomyError,
                        selectedViewId = selectedViewId,
                        selectedPartId = selectedPartId,
                        previewState = previewState,
                        onSelectView = onSelectView,
                        onTapPartFromImage = handleTapFromImage,
                        onLoadImageBytes = onLoadAnatomyImageBytes,
                        onRetryAnatomy = onRetryAnalysis,
                        maxImageHeight = 220.dp,
                    )
                }
            }
            item(key = "parts_header") {
                Text(
                    text = "部位列表（点击图片或列表切换部位）",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            itemsIndexed(
                items = enemy.parts,
                key = { _, part -> "part_${part.partId}" },
            ) { _, part ->
                EnemyPartRow(
                    part = part,
                    isSelected = part.partId == selectedPartId,
                    partsById = partsById,
                    onClick = { onSelectPart(part.partId) },
                )
            }
            // 哨兵吸顶头：进入下方推荐/对位区时把上方的 anatomy_viewer 顶出屏幕
            stickyHeader(key = "anatomy_sticky_end") {
                Spacer(Modifier.height(1.dp))
            }
            if (enemy.parts.isNotEmpty()) {
                item(key = "recommendations") {
                    PartRecommendationSection(
                        selectedPart = selectedPart,
                        goal = goal,
                        recommendationState = bundle.recommendationState,
                        onSelectGoal = onSelectGoal,
                        onOpenRecommendedAttack = onOpenRecommendedAttack,
                        onRetry = onRetryAnalysis,
                    )
                }
                item(key = "matchup") {
                    EnemyMatchupAnalyzerSection(
                        availableAttacks = bundle.availableAttacks,
                        selectedAttackRef = selectedAttackRef,
                        matchupState = bundle.matchupState,
                        onSelectAttack = onSelectAttack,
                        onRetry = onRetryAnalysis,
                    )
                }
            }
            item(key = "summary") {
                SummaryAndRelationsSection(detail, related, onOpenRelated)
            }
            item(key = "sources") {
                SourcesSection(bundle)
            }
            item(key = "bottom_spacer") {
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
 * 战备核心属性区：渲染呼叫方向指令、分类、呼叫延迟、基础冷却、使用次数、解锁条件及可选武器配置剖面。
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
    s.weaponProfile?.let { profile ->
        WeaponConfigurationsBlock(profile.configurations)
    }
}

/**
 * 武器核心属性区：渲染槽位、子类以及各配置/改型的弹匣容量、射速、射击模式和攻击伤害穿甲参数。
 */
@Composable
private fun WeaponStats(w: WeaponDetail) {
    FieldRow("槽位", w.slot.labelZh)
    FieldRow("武器子类", w.weaponClass.labelZh)
    WeaponConfigurationsBlock(w.configurations)
}

@Composable
private fun WeaponConfigurationsBlock(configurations: List<WeaponConfigDetail>) {
    configurations.forEachIndexed { idx, cfg ->
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
            atk.components.forEach { comp ->
                val radiusText = listOfNotNull(
                    comp.innerRadiusMeters?.let { "内径 ${it}m" },
                    comp.outerRadiusMeters?.let { "外径 ${it}m" },
                ).joinToString(" / ")
                val detailText = buildString {
                    append("${comp.componentKind.labelZh} · 常规 ${comp.normalDamage.formatDisplay()}")
                    append(" | 耐久 ${comp.durableDamage.formatDisplay()}")
                    append(" | 穿甲 ${comp.armorPenetration.formatDisplay()}")
                    if (radiusText.isNotEmpty()) append(" | $radiusText")
                }
                FieldRow(label = "  └ ${comp.labelZh}", value = detailText)
            }
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
 * 资料来源与版本溯源区：汇总条目级与各字段级 `sourceId`（含武器配置、伤害组件、血池、破甲效果及解剖图鉴），展示核验版本、授权许可与外部参考链接。
 */
@Composable
private fun SourcesSection(bundle: WikiDetailBundle) {
    val detail = bundle.entry.detail
    val allSources = buildMap {
        putAll(bundle.entry.sources)
        bundle.anatomy?.sources?.let(::putAll)
        (bundle.recommendationState as? WikiLoadState.Content)?.value?.sources?.let(::putAll)
        (bundle.matchupState as? WikiLoadState.Content)?.value?.sources?.let(::putAll)
    }

    val referencedIds = linkedSetOf(detail.sourceId).apply {
        detail.stratagem?.let { s ->
            s.callInSeconds.sourceId?.let(::add)
            s.cooldownSeconds.sourceId?.let(::add)
            s.uses.sourceId?.let(::add)
            s.weaponProfile?.configurations?.collectConfigurationSourceIds(::add)
        }
        detail.weapon?.configurations?.collectConfigurationSourceIds(::add)
        detail.enemy?.let { e ->
            e.pools.forEach { pool ->
                pool.sourceId?.let(::add)
                pool.maxHealth.sourceId?.let(::add)
            }
            e.parts.forEach { p ->
                p.sourceId?.let(::add)
                p.armor.sourceId?.let(::add)
                p.durability.sourceId?.let(::add)
                p.toMainRatio.sourceId?.let(::add)
                p.explosionResistance.sourceId?.let(::add)
                p.onBreak.forEach { eff -> eff.sourceId?.let(::add) }
            }
        }
        bundle.anatomy?.views?.forEach { view ->
            add(view.sourceId)
        }
        (bundle.recommendationState as? WikiLoadState.Content)?.value?.let { rec ->
            rec.curatedRecommendations.forEach { item ->
                add(item.recommendation.sourceId)
                item.evidences.forEach { ev -> add(ev.sourceId) }
            }
        }
        (bundle.matchupState as? WikiLoadState.Content)?.value?.let { eval ->
            eval.matchedEvidences.forEach { ev -> add(ev.sourceId) }
            eval.historicalEvidences.forEach { ev -> add(ev.sourceId) }
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
                val src = allSources[sid]
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
                    val licenseMeta = listOfNotNull(
                        src.license?.takeIf { it.isNotBlank() }?.let { "许可: $it" },
                        src.attribution?.takeIf { it.isNotBlank() }?.let { "署名: $it" },
                    ).joinToString(" · ")
                    if (licenseMeta.isNotEmpty()) {
                        Text(licenseMeta, color = Color.LightGray, fontSize = 11.sp)
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

private fun List<WeaponConfigDetail>.collectConfigurationSourceIds(add: (String) -> Unit) {
    forEach { cfg ->
        cfg.magazineCapacity.sourceId?.let(add)
        cfg.fireRateRpm.sourceId?.let(add)
        cfg.attacks.forEach { atk ->
            atk.damage.sourceId?.let(add)
            atk.armorPenetration.sourceId?.let(add)
            atk.components.forEach { comp ->
                comp.sourceId?.let(add)
                comp.normalDamage.sourceId?.let(add)
                comp.durableDamage.sourceId?.let(add)
                comp.armorPenetration.sourceId?.let(add)
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
