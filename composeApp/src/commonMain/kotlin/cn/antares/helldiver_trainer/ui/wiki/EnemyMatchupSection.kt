package cn.antares.helldiver_trainer.ui.wiki

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.ComponentMatchupResult
import cn.antares.helldiver_trainer.data.wiki.model.ConflictStatus
import cn.antares.helldiver_trainer.data.wiki.model.CuratedRecommendationItem
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartDetail
import cn.antares.helldiver_trainer.data.wiki.model.MatchVerdict
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.ViableWeaponRecommendation
import cn.antares.helldiver_trainer.data.wiki.model.formatDisplay
import cn.antares.helldiver_trainer.viewmodel.WikiLoadState
import kotlin.math.roundToInt

/**
 * 部位武器推荐区块：支持切换推荐目的、展示外甲遮挡提示，并分“编辑实证推荐”与“其他可用武器”两栏展示可点击跳转卡片。
 */
@Composable
fun PartRecommendationSection(
    selectedPart: EnemyPartDetail?,
    goal: RecommendationGoal,
    recommendationState: WikiLoadState<PartRecommendationResult>?,
    onSelectGoal: (RecommendationGoal) -> Unit,
    onOpenRecommendedAttack: (AttackRef) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    WikiSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "部位武器推荐${selectedPart?.let { " · ${it.nameZh}" } ?: ""}",
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RecommendationGoal.entries.forEach { item ->
                    WikiFilterChip(
                        label = item.labelZh,
                        selected = goal == item,
                        onClick = { onSelectGoal(item) },
                    )
                }
            }

            when (recommendationState) {
                null -> {
                    Text("请先选择部位", color = Color.LightGray, fontSize = 13.sp)
                }

                is WikiLoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = primary, modifier = Modifier.size(28.dp))
                    }
                }

                is WikiLoadState.Error -> {
                    WikiErrorBox(
                        message = recommendationState.message,
                        showRetry = !recommendationState.isUnsupportedVersion,
                        onRetry = onRetry,
                    )
                }

                is WikiLoadState.Content -> {
                    val result = recommendationState.value
                    if (!result.blockedNoticeZh.isNullOrBlank()) {
                        WarningBanner(text = result.blockedNoticeZh)
                    }

                    Text(
                        text = "编辑实证推荐",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.curatedRecommendations.isEmpty()) {
                        Text(
                            text = "暂无编辑实证推荐",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                        )
                    } else {
                        result.curatedRecommendations.forEach { item ->
                            CuratedRecommendationCard(
                                item = item,
                                onClick = { onOpenRecommendedAttack(item.recommendation.attackRef) },
                            )
                        }
                    }

                    HorizontalDivider(color = WikiBorderColor)

                    Text(
                        text = "其他可用武器",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (result.otherViableWeapons.isEmpty()) {
                        Text(
                            text = "当前条件下暂无其他匹配武器",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                        )
                    } else {
                        result.otherViableWeapons.forEach { item ->
                            ViableWeaponCard(
                                item = item,
                                onClick = { onOpenRecommendedAttack(item.attackOption.ref) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 敌人与武器/战备对位分析器：支持选择任意可用攻击方式（含战备武器配置），展示分伤害组件推算、实测记录、待复核冲突提示与历史补丁记录。
 */
@Composable
fun EnemyMatchupAnalyzerSection(
    availableAttacks: List<AttackOption>,
    selectedAttackRef: AttackRef?,
    matchupState: WikiLoadState<MatchupEvaluation>?,
    onSelectAttack: (AttackRef) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    var dropdownExpanded by remember { mutableStateOf(false) }
    val selectedOption = remember(availableAttacks, selectedAttackRef) {
        availableAttacks.firstOrNull { it.ref == selectedAttackRef }
            ?: availableAttacks.firstOrNull()
    }

    WikiSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "武器对位分析",
                color = primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )

            if (availableAttacks.isEmpty()) {
                Text("暂无可对比的武器攻击方式", color = Color.LightGray, fontSize = 13.sp)
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(WikiPanelBg)
                            .border(1.dp, WikiBorderColor, RoundedCornerShape(8.dp))
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedOption?.let {
                                    "${it.ownerNameZh} · ${it.attackNameZh}"
                                } ?: "选择武器或战备攻击方式",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (selectedOption != null) {
                                Text(
                                    text = "${selectedOption.ownerKind.labelZh} · ${selectedOption.configLabelZh}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "展开武器列表",
                            tint = Color.LightGray,
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(WikiPanelBg),
                    ) {
                        availableAttacks.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "${option.ownerNameZh} · ${option.attackNameZh}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                        )
                                        Text(
                                            text = "${option.ownerKind.labelZh} · ${option.configLabelZh}",
                                            color = Color.LightGray,
                                            fontSize = 11.sp,
                                        )
                                    }
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    onSelectAttack(option.ref)
                                },
                            )
                        }
                    }
                }
            }

            when (matchupState) {
                null -> Unit

                is WikiLoadState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = primary, modifier = Modifier.size(28.dp))
                    }
                }

                is WikiLoadState.Error -> {
                    WikiErrorBox(
                        message = matchupState.message,
                        showRetry = !matchupState.isUnsupportedVersion,
                        onRetry = onRetry,
                    )
                }

                is WikiLoadState.Content -> {
                    val eval = matchupState.value
                    val rule = eval.ruleResult

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusTag(
                            text = rule.overallVerdict.labelZh,
                            tint = verdictColor(rule.overallVerdict, primary),
                        )
                        StatusTag(text = "规则推算", tint = Color(0xFF64B5F6))
                        if (eval.matchedEvidences.isNotEmpty()) {
                            StatusTag(text = "实测记录", tint = Color(0xFF81C784))
                        }
                        if (rule.overallVerdict == MatchVerdict.UNKNOWN || rule.missingReasons.isNotEmpty()) {
                            StatusTag(text = "待核验", tint = Color(0xFFFFB74D))
                        }
                    }

                    if (eval.conflictStatus == ConflictStatus.NEEDS_REVIEW) {
                        val evidenceVerdict =
                            eval.matchedEvidences.firstOrNull()?.verdict?.labelZh ?: "未知"
                        WarningBanner(
                            text = buildString {
                                append("待复核冲突：规则推算为「${rule.overallVerdict.labelZh}」，")
                                append("实测记录为「$evidenceVerdict」")
                                if (!eval.conflictReasonZh.isNullOrBlank()) {
                                    append("（${eval.conflictReasonZh}）")
                                }
                            },
                        )
                    }

                    if (rule.blockedByPartNamesZh.isNotEmpty()) {
                        Text(
                            text = "受保护部位阻挡：${rule.blockedByPartNamesZh.joinToString("、")}",
                            color = Color(0xFFFFB74D),
                            fontSize = 12.sp,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(WikiPanelBg)
                            .border(1.dp, WikiBorderColor, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MatchupMetricRow(
                            "单发部位伤害估算",
                            rule.totalPartDamagePerShot?.let { formatNumber(it) } ?: "待核验",
                        )
                        MatchupMetricRow(
                            "单发主生命伤害估算",
                            rule.totalMainDamagePerShot?.let { formatNumber(it) } ?: "待核验",
                        )
                        MatchupMetricRow(
                            "预计破甲发数",
                            rule.estimatedShotsToBreak?.let { "$it 发" } ?: "待核验",
                        )
                        MatchupMetricRow(
                            "预计击杀发数",
                            rule.estimatedShotsToKill?.let { "$it 发" } ?: "待核验",
                        )
                    }

                    if (rule.componentResults.isNotEmpty()) {
                        Text(
                            text = "伤害组件拆解",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        rule.componentResults.forEach { comp ->
                            ComponentResultCard(comp, primary)
                        }
                    }

                    if (eval.matchedEvidences.isNotEmpty()) {
                        HorizontalDivider(color = WikiBorderColor)
                        Text(
                            text = "当前版本实测记录（${eval.targetPatch}）",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        eval.matchedEvidences.forEach { ev ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WikiPanelBg)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "${ev.verdict.labelZh} · ${ev.summaryZh}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = "破甲: ${ev.shotsToBreak.formatDisplay()} | 击杀: ${ev.shotsToKill.formatDisplay()}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    if (eval.historicalEvidences.isNotEmpty()) {
                        HorizontalDivider(color = WikiBorderColor)
                        Text(
                            text = "历史版本记录",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        eval.historicalEvidences.forEach { ev ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WikiPanelBg.copy(alpha = 0.65f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "补丁 ${ev.patchVersion} · ${ev.verdict.labelZh}",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = ev.summaryZh,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CuratedRecommendationCard(
    item: CuratedRecommendationItem,
    onClick: () -> Unit,
) {
    val option = item.attackOption
    val rec = item.recommendation
    WikiSurfaceCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${option.ownerNameZh} · ${option.attackNameZh}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusTag(text = "实测推荐", tint = Color(0xFF81C784))
            }
            Text(
                text = rec.reasonZh,
                color = Color.LightGray,
                fontSize = 12.sp,
            )
            if (!rec.condition.isNullOrBlank()) {
                Text(
                    text = "条件：${rec.condition}",
                    color = Color(0xFFFFB74D),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ViableWeaponCard(
    item: ViableWeaponRecommendation,
    onClick: () -> Unit,
) {
    val option = item.attackOption
    val rule = item.evaluation.ruleResult
    val primary = MaterialTheme.colorScheme.primary
    WikiSurfaceCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${option.ownerNameZh} · ${option.attackNameZh}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                val shotsHint = listOfNotNull(
                    rule.estimatedShotsToBreak?.let { "破甲约 $it 发" },
                    rule.estimatedShotsToKill?.let { "击杀约 $it 发" },
                ).joinToString(" | ")
                if (shotsHint.isNotEmpty()) {
                    Text(shotsHint, color = Color.LightGray, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusTag(
                text = rule.overallVerdict.labelZh,
                tint = verdictColor(rule.overallVerdict, primary),
            )
        }
    }
}

@Composable
private fun ComponentResultCard(
    comp: ComponentMatchupResult,
    primary: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(WikiPanelBg)
            .border(1.dp, WikiBorderColor, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${comp.labelZh}（${comp.componentKind.labelZh}）",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            StatusTag(
                text = comp.verdict.labelZh,
                tint = verdictColor(comp.verdict, primary),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "穿甲倍率: ${comp.armorMultiplier?.let { formatNumber(it) } ?: "待核验"}",
                color = Color.LightGray,
                fontSize = 11.sp,
            )
            comp.explosiveMultiplier?.let {
                Text(
                    text = "爆炸倍率: ${formatNumber(it)}",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = "部位伤害: ${comp.estimatedPartDamage?.let { formatNumber(it) } ?: "待核验"}",
                color = Color.LightGray,
                fontSize = 11.sp,
            )
            Text(
                text = "主伤: ${comp.estimatedMainDamage?.let { formatNumber(it) } ?: "待核验"}",
                color = Color.LightGray,
                fontSize = 11.sp,
            )
        }
        if (comp.missingReasons.isNotEmpty()) {
            Text(
                text = "缺失项：${comp.missingReasons.joinToString("、")}",
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    val warnColor = Color(0xFFFFB74D)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(warnColor.copy(alpha = 0.14f))
            .border(1.dp, warnColor.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = warnColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun StatusTag(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.18f))
            .border(1.dp, tint.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MatchupMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun verdictColor(verdict: MatchVerdict, primary: Color): Color = when (verdict) {
    MatchVerdict.PENETRATES,
    MatchVerdict.STRIPS_ARMOR,
    MatchVerdict.KILLS,
        -> Color(0xFF81C784)

    MatchVerdict.REDUCED -> Color(0xFFFFB74D)
    MatchVerdict.RICOCHET,
    MatchVerdict.BLOCKED,
        -> Color(0xFFFF8A80)

    MatchVerdict.UNKNOWN -> primary
}

private fun formatNumber(value: Double): String {
    val rounded = (value * 100.0).roundToInt() / 100.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
