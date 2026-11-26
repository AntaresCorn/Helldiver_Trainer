package cn.antares.helldiver_trainer.data.wiki.matchup

import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.ComponentMatchupResult
import cn.antares.helldiver_trainer.data.wiki.model.ConflictStatus
import cn.antares.helldiver_trainer.data.wiki.model.CuratedRecommendationItem
import cn.antares.helldiver_trainer.data.wiki.model.DamageComponentKind
import cn.antares.helldiver_trainer.data.wiki.model.DamageRuleSet
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.MatchEvidence
import cn.antares.helldiver_trainer.data.wiki.model.MatchVerdict
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
import cn.antares.helldiver_trainer.data.wiki.model.PartBreakEffectType
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendation
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.RuleMatchupResult
import cn.antares.helldiver_trainer.data.wiki.model.ViableWeaponRecommendation
import cn.antares.helldiver_trainer.data.wiki.model.WeaponAttackDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponDamageComponentDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiFact
import cn.antares.helldiver_trainer.data.wiki.model.WikiFactStatus
import cn.antares.helldiver_trainer.data.wiki.model.WikiSource
import kotlin.math.abs
import kotlin.math.ceil

/**
 * 敌我部位对位计算引擎：基于伤害规则与部位参数推算穿透、单发伤害及击破发数，
 * 并结合同版本实测记录执行冲突仲裁与武器推荐排序。
 */
object WikiMatchupEngine {

    private const val RULE_ASSUMPTION = "规则推算：未计距离衰减与命中角度"

    data class AttackCatalogItem(
        val option: AttackOption,
        val ownerEntry: WikiEntryDetail,
        val attack: WeaponAttackDetail,
    )

    fun evaluateMatchup(
        enemyDetail: WikiEntryDetail,
        partId: String,
        partState: EnemyPartState,
        ownerEntry: WikiEntryDetail,
        configId: String,
        attackId: String,
        attack: WeaponAttackDetail,
        ruleSet: DamageRuleSet,
        evidences: List<MatchEvidence>,
        targetPatch: String,
        hitCondition: String? = null,
        sources: Map<String, WikiSource> = emptyMap(),
    ): MatchupEvaluation {
        val attackRef = AttackRef(
            entryId = ownerEntry.entryId,
            configId = configId,
            attackId = attackId,
        )
        val partRef = EnemyPartRef(
            enemyId = enemyDetail.entryId,
            partId = partId,
        )
        val enemy = enemyDetail.enemy
        val part = enemy?.parts?.firstOrNull { it.partId == partId }

        val ruleResult = if (part == null) {
            RuleMatchupResult(
                overallVerdict = MatchVerdict.UNKNOWN,
                missingReasons = listOf("未找到部位：$partId"),
                assumptions = listOf(RULE_ASSUMPTION),
                rulePatchVersion = ruleSet.patchVersion,
            )
        } else if (partState == EnemyPartState.INTACT && part.protectedByPartIds.isNotEmpty()) {
            val partsById = enemy.parts.associateBy { it.partId }
            val blockerNames = part.protectedByPartIds.map { id -> partsById[id]?.nameZh ?: id }
            RuleMatchupResult(
                overallVerdict = MatchVerdict.BLOCKED,
                blockedByPartIds = part.protectedByPartIds,
                blockedByPartNamesZh = blockerNames,
                assumptions = listOf(
                    "当前状态无法直接命中此部位，需先破坏：${blockerNames.joinToString("、")}",
                ),
                rulePatchVersion = ruleSet.patchVersion,
            )
        } else {
            calculateRuleResult(
                enemyDetail = enemyDetail,
                part = part,
                attack = attack,
                ruleSet = ruleSet,
            )
        }

        val relevantEvidences = evidences.filter { ev ->
            ev.enemyId == enemyDetail.entryId &&
                    ev.partId == partId &&
                    ev.partState == partState &&
                    ev.attackRef == attackRef
        }

        val (matchedEvidences, historicalEvidences) = relevantEvidences.partition { ev ->
            ev.patchVersion == targetPatch &&
                    (hitCondition == null || ev.hitCondition == null || ev.hitCondition == hitCondition)
        }

        val (conflictStatus, conflictReasonZh) = detectConflicts(
            ruleResult = ruleResult,
            matchedEvidences = matchedEvidences,
        )

        return MatchupEvaluation(
            partRef = partRef,
            partState = partState,
            attackRef = attackRef,
            targetPatch = targetPatch,
            ruleResult = ruleResult,
            matchedEvidences = matchedEvidences,
            historicalEvidences = historicalEvidences,
            conflictStatus = conflictStatus,
            conflictReasonZh = conflictReasonZh,
            sources = sources,
        )
    }

    fun recommendForPart(
        enemyEntryId: String,
        enemyDetail: WikiEntryDetail,
        partId: String,
        partState: EnemyPartState,
        goal: RecommendationGoal,
        targetPatch: String,
        ruleSet: DamageRuleSet,
        evidences: List<MatchEvidence>,
        recommendations: List<PartRecommendation>,
        attackCatalog: List<AttackCatalogItem>,
        sources: Map<String, WikiSource> = emptyMap(),
    ): PartRecommendationResult {
        val partRef = EnemyPartRef(enemyId = enemyEntryId, partId = partId)
        val enemy = enemyDetail.enemy
        val part = enemy?.parts?.firstOrNull { it.partId == partId }

        if (partState == EnemyPartState.INTACT && part != null && part.protectedByPartIds.isNotEmpty()) {
            val partsById = enemy.parts.associateBy { it.partId }
            val blockerNames = part.protectedByPartIds.map { id -> partsById[id]?.nameZh ?: id }
            return PartRecommendationResult(
                partRef = partRef,
                partState = partState,
                goal = goal,
                targetPatch = targetPatch,
                blockedByPartIds = part.protectedByPartIds,
                blockedByPartNamesZh = blockerNames,
                blockedNoticeZh = "需先破坏外甲（${blockerNames.joinToString("、")}）后才可有效攻击此部位",
                curatedRecommendations = emptyList(),
                otherViableWeapons = emptyList(),
                sources = sources,
            )
        }

        val catalogByRef = attackCatalog.associateBy { it.option.ref }
        val evidencesById = evidences.associateBy { it.evidenceId }

        val curatedRecommendations = recommendations
            .filter { rec ->
                rec.enemyId == enemyEntryId &&
                        rec.partId == partId &&
                        rec.partState == partState &&
                        rec.goal == goal &&
                        rec.patchVersion == targetPatch
            }
            .sortedBy { it.priorityOrder }
            .mapNotNull { rec ->
                val catalogItem = catalogByRef[rec.attackRef] ?: return@mapNotNull null
                val eval = evaluateMatchup(
                    enemyDetail = enemyDetail,
                    partId = partId,
                    partState = partState,
                    ownerEntry = catalogItem.ownerEntry,
                    configId = rec.attackRef.configId,
                    attackId = rec.attackRef.attackId,
                    attack = catalogItem.attack,
                    ruleSet = ruleSet,
                    evidences = evidences,
                    targetPatch = targetPatch,
                    hitCondition = rec.condition,
                    sources = sources,
                )
                if (eval.conflictStatus == ConflictStatus.NEEDS_REVIEW) {
                    return@mapNotNull null
                }
                val itemEvidences = rec.evidenceIds
                    .mapNotNull { evidencesById[it] }
                    .ifEmpty { eval.matchedEvidences }
                CuratedRecommendationItem(
                    recommendation = rec,
                    attackOption = catalogItem.option,
                    evidences = itemEvidences,
                )
            }

        val curatedRefs = curatedRecommendations.mapTo(HashSet()) { it.recommendation.attackRef }

        val otherViableWeapons = attackCatalog
            .asSequence()
            .filter { item -> item.option.ref !in curatedRefs }
            .mapNotNull { item ->
                val eval = evaluateMatchup(
                    enemyDetail = enemyDetail,
                    partId = partId,
                    partState = partState,
                    ownerEntry = item.ownerEntry,
                    configId = item.option.ref.configId,
                    attackId = item.option.ref.attackId,
                    attack = item.attack,
                    ruleSet = ruleSet,
                    evidences = evidences,
                    targetPatch = targetPatch,
                    hitCondition = null,
                    sources = sources,
                )
                if (eval.conflictStatus == ConflictStatus.NEEDS_REVIEW) {
                    return@mapNotNull null
                }
                val verdict = eval.ruleResult.overallVerdict
                if (verdict != MatchVerdict.PENETRATES && verdict != MatchVerdict.REDUCED) {
                    return@mapNotNull null
                }
                ViableWeaponRecommendation(
                    attackOption = item.option,
                    evaluation = eval,
                )
            }
            .sortedWith(
                compareBy(
                    { if (it.evaluation.ruleResult.overallVerdict == MatchVerdict.PENETRATES) 0 else 1 },
                    { -(it.evaluation.ruleResult.totalPartDamagePerShot ?: -1.0) },
                    { it.attackOption.ownerNameZh },
                    { it.attackOption.ref.entryId },
                    { it.attackOption.ref.configId },
                    { it.attackOption.ref.attackId },
                ),
            )
            .toList()

        return PartRecommendationResult(
            partRef = partRef,
            partState = partState,
            goal = goal,
            targetPatch = targetPatch,
            curatedRecommendations = curatedRecommendations,
            otherViableWeapons = otherViableWeapons,
            sources = sources,
        )
    }

    private fun calculateRuleResult(
        enemyDetail: WikiEntryDetail,
        part: EnemyPartDetail,
        attack: WeaponAttackDetail,
        ruleSet: DamageRuleSet,
    ): RuleMatchupResult {
        val components = if (attack.components.isNotEmpty()) {
            attack.components
        } else {
            listOf(
                WeaponDamageComponentDetail(
                    componentId = "${attack.attackId}_direct",
                    labelZh = attack.nameZh,
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = attack.damage,
                    durableDamage = WikiFact.unknown(),
                    armorPenetration = attack.armorPenetration,
                ),
            )
        }

        val componentResults = components.map { comp ->
            evaluateComponent(comp = comp, part = part, ruleSet = ruleSet)
        }

        val nonStatusResults = componentResults.filter {
            it.componentKind != DamageComponentKind.STATUS
        }

        val overallVerdict = when {
            nonStatusResults.any { it.verdict == MatchVerdict.PENETRATES } -> MatchVerdict.PENETRATES
            nonStatusResults.any { it.verdict == MatchVerdict.REDUCED } -> MatchVerdict.REDUCED
            nonStatusResults.any { it.verdict == MatchVerdict.UNKNOWN } -> MatchVerdict.UNKNOWN
            nonStatusResults.isEmpty() -> MatchVerdict.UNKNOWN
            else -> MatchVerdict.RICOCHET
        }

        val totalPartDamagePerShot = if (
            nonStatusResults.isNotEmpty() &&
            nonStatusResults.all { it.estimatedPartDamage != null }
        ) {
            nonStatusResults.sumOf { it.estimatedPartDamage!! }
        } else {
            null
        }

        val totalMainDamagePerShot = if (
            totalPartDamagePerShot != null &&
            nonStatusResults.all { it.estimatedMainDamage != null }
        ) {
            nonStatusResults.sumOf { it.estimatedMainDamage!! }
        } else {
            null
        }

        val missingReasons = componentResults
            .flatMap { it.missingReasons }
            .distinct()
            .toMutableList()

        var estimatedShotsToBreak: Int? = null
        var estimatedShotsToKill: Int? = null

        if (totalPartDamagePerShot != null && totalPartDamagePerShot > 0.0) {
            val pool = enemyDetail.enemy?.pools?.firstOrNull { it.poolId == part.healthPoolRef }
            if (
                pool?.maxHealth?.status == WikiFactStatus.KNOWN &&
                pool.maxHealth.value != null &&
                part.toMainRatio.status == WikiFactStatus.KNOWN
            ) {
                estimatedShotsToBreak = ceil(
                    pool.maxHealth.value.toDouble() / totalPartDamagePerShot,
                ).toInt()
                if (part.onBreak.any { it.effectType == PartBreakEffectType.FATAL }) {
                    estimatedShotsToKill = estimatedShotsToBreak
                }
            }
        }

        return RuleMatchupResult(
            overallVerdict = overallVerdict,
            componentResults = componentResults,
            totalPartDamagePerShot = totalPartDamagePerShot,
            totalMainDamagePerShot = totalMainDamagePerShot,
            estimatedShotsToBreak = estimatedShotsToBreak,
            estimatedShotsToKill = estimatedShotsToKill,
            missingReasons = missingReasons,
            assumptions = listOf(RULE_ASSUMPTION),
            rulePatchVersion = ruleSet.patchVersion,
        )
    }

    private fun evaluateComponent(
        comp: WeaponDamageComponentDetail,
        part: EnemyPartDetail,
        ruleSet: DamageRuleSet,
    ): ComponentMatchupResult {
        if (comp.componentKind == DamageComponentKind.STATUS) {
            return ComponentMatchupResult(
                componentId = comp.componentId,
                labelZh = comp.labelZh,
                componentKind = comp.componentKind,
                verdict = MatchVerdict.UNKNOWN,
                armorMultiplier = null,
                explosiveMultiplier = null,
                estimatedPartDamage = null,
                estimatedMainDamage = null,
            )
        }

        val apKnown = comp.armorPenetration.status == WikiFactStatus.KNOWN &&
                comp.armorPenetration.value != null
        val avKnown = part.armor.status == WikiFactStatus.KNOWN &&
                part.armor.value != null

        if (!apKnown || !avKnown) {
            return ComponentMatchupResult(
                componentId = comp.componentId,
                labelZh = comp.labelZh,
                componentKind = comp.componentKind,
                verdict = MatchVerdict.UNKNOWN,
                armorMultiplier = null,
                explosiveMultiplier = null,
                estimatedPartDamage = null,
                estimatedMainDamage = null,
                missingReasons = listOf("穿甲或护甲等级未知"),
            )
        }

        val ap = comp.armorPenetration.value
        val av = part.armor.value

        if (ap < av) {
            return ComponentMatchupResult(
                componentId = comp.componentId,
                labelZh = comp.labelZh,
                componentKind = comp.componentKind,
                verdict = MatchVerdict.RICOCHET,
                armorMultiplier = ruleSet.ricochetMultiplier,
                explosiveMultiplier = if (comp.componentKind == DamageComponentKind.EXPLOSIVE) {
                    if (
                        part.explosionResistance.status == WikiFactStatus.KNOWN &&
                        part.explosionResistance.value != null
                    ) {
                        (1.0 - part.explosionResistance.value).coerceAtLeast(0.0)
                    } else {
                        null
                    }
                } else {
                    1.0
                },
                estimatedPartDamage = 0.0,
                estimatedMainDamage = 0.0,
            )
        }

        val verdict = if (ap > av) MatchVerdict.PENETRATES else MatchVerdict.REDUCED
        val mArmor = if (ap > av) ruleSet.penetrateMultiplier else ruleSet.equalArmorMultiplier

        val missing = mutableListOf<String>()
        val normalDamage = if (
            comp.normalDamage.status == WikiFactStatus.KNOWN &&
            comp.normalDamage.value != null
        ) {
            comp.normalDamage.value.toDouble()
        } else {
            missing.add("常规伤害未知")
            null
        }

        val durableDamage = if (
            comp.durableDamage.status == WikiFactStatus.KNOWN &&
            comp.durableDamage.value != null
        ) {
            comp.durableDamage.value.toDouble()
        } else {
            missing.add("耐久伤害未知")
            null
        }

        val durability = if (
            part.durability.status == WikiFactStatus.KNOWN &&
            part.durability.value != null
        ) {
            part.durability.value
        } else {
            missing.add("部位耐久占比未知")
            null
        }

        val mExplosive = if (comp.componentKind == DamageComponentKind.EXPLOSIVE) {
            if (
                part.explosionResistance.status == WikiFactStatus.KNOWN &&
                part.explosionResistance.value != null
            ) {
                (1.0 - part.explosionResistance.value).coerceAtLeast(0.0)
            } else {
                missing.add("部位爆炸抗性未知")
                null
            }
        } else {
            1.0
        }

        if (normalDamage == null || durableDamage == null || durability == null || mExplosive == null) {
            return ComponentMatchupResult(
                componentId = comp.componentId,
                labelZh = comp.labelZh,
                componentKind = comp.componentKind,
                verdict = verdict,
                armorMultiplier = mArmor,
                explosiveMultiplier = mExplosive,
                estimatedPartDamage = null,
                estimatedMainDamage = null,
                missingReasons = missing,
            )
        }

        val partDamage = ((1.0 - durability) * normalDamage + durability * durableDamage) *
                mArmor * mExplosive
        val mainDamage = if (
            part.toMainRatio.status == WikiFactStatus.KNOWN &&
            part.toMainRatio.value != null
        ) {
            partDamage * part.toMainRatio.value
        } else {
            null
        }

        return ComponentMatchupResult(
            componentId = comp.componentId,
            labelZh = comp.labelZh,
            componentKind = comp.componentKind,
            verdict = verdict,
            armorMultiplier = mArmor,
            explosiveMultiplier = mExplosive,
            estimatedPartDamage = partDamage,
            estimatedMainDamage = mainDamage,
        )
    }

    private fun detectConflicts(
        ruleResult: RuleMatchupResult,
        matchedEvidences: List<MatchEvidence>,
    ): Pair<ConflictStatus, String?> {
        if (matchedEvidences.isEmpty()) {
            return ConflictStatus.NONE to null
        }

        val ruleVerdict = ruleResult.overallVerdict
        val ruleShots = ruleResult.estimatedShotsToBreak
        val reasons = mutableListOf<String>()

        for (ev in matchedEvidences) {
            val directionalConflict = when (ruleVerdict) {
                MatchVerdict.RICOCHET, MatchVerdict.BLOCKED -> ev.verdict in setOf(
                    MatchVerdict.PENETRATES,
                    MatchVerdict.REDUCED,
                    MatchVerdict.STRIPS_ARMOR,
                    MatchVerdict.KILLS,
                )

                MatchVerdict.PENETRATES, MatchVerdict.REDUCED -> ev.verdict in setOf(
                    MatchVerdict.RICOCHET,
                    MatchVerdict.BLOCKED,
                )

                else -> false
            }

            if (directionalConflict) {
                reasons.add(
                    "规则结论（${ruleVerdict.labelZh}）与实测记录（${ev.verdict.labelZh}）方向冲突",
                )
            }

            if (
                ruleShots != null &&
                ev.shotsToBreak.status == WikiFactStatus.KNOWN &&
                ev.shotsToBreak.value != null
            ) {
                val evShots = ev.shotsToBreak.value
                val threshold = maxOf(1, ceil(evShots * 0.5).toInt())
                if (abs(ruleShots - evShots) > threshold) {
                    reasons.add(
                        "规则推算击破发数（$ruleShots 发）与实测记录（$evShots 发）偏差超过阈值",
                    )
                }
            }
        }

        return if (reasons.isNotEmpty()) {
            ConflictStatus.NEEDS_REVIEW to reasons.distinct().joinToString("；")
        } else {
            ConflictStatus.NONE to null
        }
    }
}
