package cn.antares.helldiver_trainer.data.wiki.model

import kotlinx.serialization.Serializable

/**
 * 资料条目或字段所引用的原始出处元数据，用于详情页展示与核验溯源。
 */
@Serializable
data class WikiSource(
    val sourceId: String,
    val title: String,
    val sourceType: String,
    val location: String? = null,
    val url: String? = null,
    val verifiedDate: String? = null,
    val defaultPatch: String? = null,
    val license: String? = null,
    val attribution: String? = null,
)

/**
 * 资料条目的已核验别名（按语言区域区分），同时参与检索匹配与详情展示。
 */
@Serializable
data class WikiAlias(
    val locale: String,
    val text: String,
)

/**
 * 离线资料包根清单（`manifest.json`），描述数据协议版本、目录索引路径及各分类详情分片路径。
 */
@Serializable
data class WikiManifest(
    val schemaVersion: Int,
    val packVersion: String,
    val generatedFromAppVersion: String,
    val entryCount: Int,
    val indexPath: String,
    val detailPaths: Map<WikiKind, String>,
    val rulesPath: String = "files/wiki/rules.json",
    val anatomyPaths: Map<String, String> = emptyMap(),
    val anatomyAssetPaths: Map<String, String> = emptyMap(),
    val evidencePath: String = "files/wiki/matchups/evidence.json",
    val recommendationsPath: String = "files/wiki/matchups/recommendations.json",
)

/**
 * 规范化检索文本对：保留折叠空格后的 [text] 与去除全部空白的 [compact]，
 * 兼顾分词语境与无空格连写（如型号/中文简称）的精确及子串匹配。
 */
@Serializable
data class NormalizedTextPair(
    val text: String,
    val compact: String,
)

/**
 * 轻量级目录索引条目，在初始化时全量加载入内存。
 *
 * 仅携带列表筛选、排序与多档搜索所需的元数据及构建期预规范化文本，
 * 大体积数值与明细则下沉至按需加载的 [WikiDetailShard] 中。
 */
@Serializable
data class WikiIndexItem(
    val entryId: String,
    val kind: WikiKind,
    val nameZh: String,
    val nameEn: String? = null,
    val summary: String? = null,
    val iconKey: String? = null,
    val stratagemCategory: StratagemCategory? = null,
    val weaponSlot: WeaponSlot? = null,
    val weaponClass: WeaponClass? = null,
    val enemyFaction: EnemyFaction? = null,
    val enemySizeClass: EnemySizeClass? = null,
    val modelCodes: List<String> = emptyList(),
    val directions: List<WikiDirection> = emptyList(),
    val trainingOrder: Int? = null,
    val normalizedNameZh: String,
    val normalizedNameZhCompact: String,
    val normalizedNameEn: String? = null,
    val normalizedNameEnCompact: String? = null,
    val normalizedAliases: List<NormalizedTextPair> = emptyList(),
    val normalizedModelCodes: List<NormalizedTextPair> = emptyList(),
)

/**
 * 索引包中预展开的有向关联边，支持正反向快速查询而无需加载详情分片。
 */
@Serializable
data class WikiIndexRelation(
    val ownerEntryId: String,
    val targetEntryId: String,
    val relationType: WikiRelationType,
    val isReverse: Boolean,
    val condition: String? = null,
    val sourceId: String? = null,
)

/**
 * 离线资料库目录索引包（`index.json`），汇总全部索引条目与预展开关联边。
 */
@Serializable
data class WikiIndexPack(
    val entries: List<WikiIndexItem> = emptyList(),
    val relations: List<WikiIndexRelation> = emptyList(),
)

/**
 * 战备与练习模式（旧版战备 ID 与排序序号）的映射信息。
 */
@Serializable
data class StratagemTrainingInfo(
    val legacyId: String,
    val order: Int,
)

@Serializable
data class WeaponProfileDetail(
    val configurations: List<WeaponConfigDetail> = emptyList(),
)

/**
 * 战备类条目的专有详情数据。
 */
@Serializable
data class StratagemDetail(
    val category: StratagemCategory,
    val directions: List<WikiDirection> = emptyList(),
    val callInSeconds: WikiFact<Double> = WikiFact.unknown(),
    val cooldownSeconds: WikiFact<Double> = WikiFact.unknown(),
    val uses: WikiFact<Int> = WikiFact.unknown(),
    val unlockRequirement: String? = null,
    val training: StratagemTrainingInfo? = null,
    val weaponProfile: WeaponProfileDetail? = null,
)

@Serializable
data class WeaponDamageComponentDetail(
    val componentId: String,
    val labelZh: String,
    val componentKind: DamageComponentKind,
    val normalDamage: WikiFact<Int> = WikiFact.unknown(),
    val durableDamage: WikiFact<Int> = WikiFact.unknown(),
    val armorPenetration: WikiFact<Int> = WikiFact.unknown(),
    val innerRadiusMeters: Double? = null,
    val outerRadiusMeters: Double? = null,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
    val condition: String? = null,
)

/**
 * 武器单种攻击模式的伤害与穿甲数值事实。
 */
@Serializable
data class WeaponAttackDetail(
    val attackId: String,
    val nameZh: String,
    val damage: WikiFact<Int> = WikiFact.unknown(),
    val armorPenetration: WikiFact<Int> = WikiFact.unknown(),
    val components: List<WeaponDamageComponentDetail> = emptyList(),
)

/**
 * 武器单一形态或弹药配置（如不同开火模式/装填构型）的规格明细。
 */
@Serializable
data class WeaponConfigDetail(
    val configId: String,
    val labelZh: String,
    val modelCode: String? = null,
    val magazineCapacity: WikiFact<Int> = WikiFact.unknown(),
    val fireRateRpm: WikiFact<Int> = WikiFact.unknown(),
    val fireModes: List<String> = emptyList(),
    val attacks: List<WeaponAttackDetail> = emptyList(),
)

/**
 * 武器类条目的专有详情数据，包含槽位、子类及多配置列表。
 */
@Serializable
data class WeaponDetail(
    val slot: WeaponSlot,
    val weaponClass: WeaponClass,
    val configurations: List<WeaponConfigDetail> = emptyList(),
)

@Serializable
data class EnemyHealthPoolDetail(
    val poolId: String,
    val labelZh: String,
    val maxHealth: WikiFact<Int> = WikiFact.unknown(),
    val bleedHealth: Int? = null,
    val bleedRatePerSecond: Double? = null,
    val isMain: Boolean = false,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
)

@Serializable
data class PartBreakEffectDetail(
    val effectType: PartBreakEffectType,
    val targetPartId: String? = null,
    val descriptionZh: String,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
)

/**
 * 敌人单个部位的护甲等级、弱点说明及独立溯源信息。
 */
@Serializable
data class EnemyPartDetail(
    val partId: String,
    val nameZh: String,
    val armor: WikiFact<Int> = WikiFact.unknown(),
    val notes: String? = null,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
    val healthPoolRef: String? = null,
    val durability: WikiFact<Double> = WikiFact.unknown(),
    val toMainRatio: WikiFact<Double> = WikiFact.unknown(),
    val damageCapToMain: Boolean = false,
    val explosionResistance: WikiFact<Double> = WikiFact.unknown(),
    val protectedByPartIds: List<String> = emptyList(),
    val onBreak: List<PartBreakEffectDetail> = emptyList(),
)

/**
 * 敌人类条目的专有详情数据，包含阵营、体型、族系键及分部位数据。
 */
@Serializable
data class EnemyDetail(
    val faction: EnemyFaction,
    val sizeClass: EnemySizeClass,
    val familyKey: String,
    val mainPoolId: String? = null,
    val pools: List<EnemyHealthPoolDetail> = emptyList(),
    val parts: List<EnemyPartDetail> = emptyList(),
)

/**
 * 资料条目完整详情模型，按 [kind] 携带对应的 [stratagem]、[weapon] 或 [enemy] 专有载荷。
 */
@Serializable
data class WikiEntryDetail(
    val entryId: String,
    val kind: WikiKind,
    val nameZh: String,
    val nameEn: String? = null,
    val aliases: List<WikiAlias> = emptyList(),
    val summary: String? = null,
    val iconKey: String? = null,
    val sourceId: String,
    val verifiedPatch: String? = null,
    val stratagem: StratagemDetail? = null,
    val weapon: WeaponDetail? = null,
    val enemy: EnemyDetail? = null,
)

/**
 * 按 [WikiKind] 拆分的详情分片文件模型，在查看对应分类条目详情时按需懒加载。
 */
@Serializable
data class WikiDetailShard(
    val kind: WikiKind,
    val entries: Map<String, WikiEntryDetail> = emptyMap(),
    val sources: Map<String, WikiSource> = emptyMap(),
)

/**
 * 仓库层返回给 UI 的条目详情聚合包，同时附带当前分片内的来源字典以便解析溯源文案。
 */
data class WikiEntryBundle(
    val detail: WikiEntryDetail,
    val sources: Map<String, WikiSource>,
)

/**
 * 面向界面展示的关联条目模型，已将正反向关系映射为中文标签 [labelZh] 并关联目标索引项。
 */
data class WikiRelatedItem(
    val relationType: WikiRelationType,
    val isReverse: Boolean,
    val labelZh: String,
    val target: WikiIndexItem,
    val condition: String? = null,
    val sourceId: String? = null,
)

/**
 * 搜索命中原因分级枚举，用于界面展示命中依据并直观反映匹配权重。
 */
enum class WikiMatchReason(val labelZh: String) {
    CATALOG("目录"),
    EXACT_NAME("名称精确命中"),
    EXACT_MODEL("型号精确命中"),
    DIRECTION_EXACT("方向精确命中"),
    EXACT_ALIAS("别名精确命中"),
    PREFIX("前缀命中"),
    SUBSTRING("关键词命中"),
    DIRECTION_SUBSEQUENCE("方向序列命中"),
}

/**
 * 资料库查询请求参数，支持关键词/方向输入及按分类、槽位、阵营等维度的组合筛选。
 */
data class WikiSearchRequest(
    val text: String = "",
    val kind: WikiKind? = null,
    val stratagemCategory: StratagemCategory? = null,
    val weaponSlot: WeaponSlot? = null,
    val weaponClass: WeaponClass? = null,
    val enemyFaction: EnemyFaction? = null,
    val enemySizeClass: EnemySizeClass? = null,
)

/**
 * 单条搜索命中结果，捆绑索引条目与对应的 [matchReason]。
 */
data class WikiSearchHit(
    val item: WikiIndexItem,
    val matchReason: WikiMatchReason,
)
