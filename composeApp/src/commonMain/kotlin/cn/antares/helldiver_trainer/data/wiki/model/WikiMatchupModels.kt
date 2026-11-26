package cn.antares.helldiver_trainer.data.wiki.model

import kotlinx.serialization.Serializable

@Serializable
data class AttackRef(
    val entryId: String,
    val configId: String,
    val attackId: String,
)

@Serializable
data class EnemyPartRef(
    val enemyId: String,
    val partId: String,
)

@Serializable
data class DamageRuleSet(
    val patchVersion: String,
    val penetrateMultiplier: Double,
    val equalArmorMultiplier: Double,
    val ricochetMultiplier: Double,
    val notes: String? = null,
    val sourceId: String,
)

@Serializable
data class DamageRulesPack(
    val ruleSets: List<DamageRuleSet> = emptyList(),
    val sources: Map<String, WikiSource> = emptyMap(),
)

@Serializable
data class MatchEvidence(
    val evidenceId: String,
    val enemyId: String,
    val partId: String,
    val partState: EnemyPartState,
    val attackRef: AttackRef,
    val hitCondition: String? = null,
    val verdict: MatchVerdict,
    val shotsToBreak: WikiFact<Int> = WikiFact.unknown(),
    val shotsToKill: WikiFact<Int> = WikiFact.unknown(),
    val summaryZh: String,
    val patchVersion: String,
    val sourceId: String,
)

@Serializable
data class MatchEvidencePack(
    val evidences: List<MatchEvidence> = emptyList(),
    val byEnemyPart: Map<String, List<String>> = emptyMap(),
    val byAttackRef: Map<String, List<String>> = emptyMap(),
    val sources: Map<String, WikiSource> = emptyMap(),
)

@Serializable
data class PartRecommendation(
    val recId: String,
    val enemyId: String,
    val partId: String,
    val partState: EnemyPartState,
    val goal: RecommendationGoal,
    val attackRef: AttackRef,
    val priorityOrder: Int,
    val condition: String? = null,
    val reasonZh: String,
    val evidenceIds: List<String> = emptyList(),
    val patchVersion: String,
    val sourceId: String,
)

@Serializable
data class PartRecommendationsPack(
    val recommendations: List<PartRecommendation> = emptyList(),
    val byGroupKey: Map<String, List<String>> = emptyMap(),
    val sources: Map<String, WikiSource> = emptyMap(),
)

data class AttackOption(
    val ref: AttackRef,
    val ownerNameZh: String,
    val ownerKind: WikiKind,
    val configLabelZh: String,
    val attackNameZh: String,
)

data class MatchupEvaluateRequest(
    val partRef: EnemyPartRef,
    val partState: EnemyPartState,
    val attackRef: AttackRef,
    val targetPatch: String? = null,
    val hitCondition: String? = null,
)

data class ComponentMatchupResult(
    val componentId: String,
    val labelZh: String,
    val componentKind: DamageComponentKind,
    val verdict: MatchVerdict,
    val armorMultiplier: Double?,
    val explosiveMultiplier: Double?,
    val estimatedPartDamage: Double?,
    val estimatedMainDamage: Double?,
    val missingReasons: List<String> = emptyList(),
)

data class RuleMatchupResult(
    val overallVerdict: MatchVerdict,
    val blockedByPartIds: List<String> = emptyList(),
    val blockedByPartNamesZh: List<String> = emptyList(),
    val componentResults: List<ComponentMatchupResult> = emptyList(),
    val totalPartDamagePerShot: Double? = null,
    val totalMainDamagePerShot: Double? = null,
    val estimatedShotsToBreak: Int? = null,
    val estimatedShotsToKill: Int? = null,
    val missingReasons: List<String> = emptyList(),
    val assumptions: List<String> = emptyList(),
    val rulePatchVersion: String? = null,
)

data class MatchupEvaluation(
    val partRef: EnemyPartRef,
    val partState: EnemyPartState,
    val attackRef: AttackRef,
    val targetPatch: String,
    val ruleResult: RuleMatchupResult,
    val matchedEvidences: List<MatchEvidence> = emptyList(),
    val historicalEvidences: List<MatchEvidence> = emptyList(),
    val conflictStatus: ConflictStatus = ConflictStatus.NONE,
    val conflictReasonZh: String? = null,
    val sources: Map<String, WikiSource> = emptyMap(),
)

data class CuratedRecommendationItem(
    val recommendation: PartRecommendation,
    val attackOption: AttackOption,
    val evidences: List<MatchEvidence> = emptyList(),
)

data class ViableWeaponRecommendation(
    val attackOption: AttackOption,
    val evaluation: MatchupEvaluation,
)

data class PartRecommendationResult(
    val partRef: EnemyPartRef,
    val partState: EnemyPartState,
    val goal: RecommendationGoal,
    val targetPatch: String,
    val blockedByPartIds: List<String> = emptyList(),
    val blockedByPartNamesZh: List<String> = emptyList(),
    val blockedNoticeZh: String? = null,
    val curatedRecommendations: List<CuratedRecommendationItem> = emptyList(),
    val otherViableWeapons: List<ViableWeaponRecommendation> = emptyList(),
    val sources: Map<String, WikiSource> = emptyMap(),
)
