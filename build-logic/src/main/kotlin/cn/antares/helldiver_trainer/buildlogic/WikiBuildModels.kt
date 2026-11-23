package cn.antares.helldiver_trainer.buildlogic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 来源登记文件 `sources.json` 的根反序列化模型。
 */
@Serializable
data class SourcesFile(
    val sources: List<WikiSourceRecord> = emptyList(),
)

/**
 * 单条数据来源记录，用于追溯条目的版本与出处。
 */
@Serializable
data class WikiSourceRecord(
    val sourceId: String,
    val title: String,
    val sourceType: String,
    val location: String? = null,
    val url: String? = null,
    val verifiedDate: String? = null,
    val defaultPatch: String? = null,
)

@Serializable
data class WikiAliasRecord(
    val locale: String,
    val text: String,
)

@Serializable
data class WikiRelationInput(
    val type: String,
    val targetId: String,
    val sourceId: String? = null,
    val condition: String? = null,
)

@Serializable
enum class FactStatus {
    @SerialName("known")
    KNOWN,

    @SerialName("unknown")
    UNKNOWN,

    @SerialName("not_applicable")
    NOT_APPLICABLE,

    @SerialName("unlimited")
    UNLIMITED,
}

@Serializable
data class WikiFactRecord<T>(
    val status: FactStatus = FactStatus.UNKNOWN,
    val value: T? = null,
    val unit: String? = null,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
    val condition: String? = null,
) {
    companion object {
        fun <T> unknown(): WikiFactRecord<T> = WikiFactRecord(status = FactStatus.UNKNOWN)
    }
}

@Serializable
data class StratagemTrainingMeta(
    val legacyId: String,
    val order: Int,
)

@Serializable
data class StratagemInputData(
    val category: String,
    val directions: List<String> = emptyList(),
    val callInSeconds: WikiFactRecord<Double> = WikiFactRecord.unknown(),
    val cooldownSeconds: WikiFactRecord<Double> = WikiFactRecord.unknown(),
    val uses: WikiFactRecord<Int> = WikiFactRecord.unknown(),
    val unlockRequirement: String? = null,
    val training: StratagemTrainingMeta? = null,
)

@Serializable
data class WeaponAttackInput(
    val attackId: String,
    val nameZh: String,
    val damage: WikiFactRecord<Int> = WikiFactRecord.unknown(),
    val armorPenetration: WikiFactRecord<Int> = WikiFactRecord.unknown(),
)

@Serializable
data class WeaponConfigInput(
    val configId: String,
    val labelZh: String,
    val modelCode: String? = null,
    val magazineCapacity: WikiFactRecord<Int> = WikiFactRecord.unknown(),
    val fireRateRpm: WikiFactRecord<Int> = WikiFactRecord.unknown(),
    val fireModes: List<String> = emptyList(),
    val attacks: List<WeaponAttackInput> = emptyList(),
)

@Serializable
data class WeaponInputData(
    val slot: String,
    val weaponClass: String,
    val configurations: List<WeaponConfigInput> = emptyList(),
)

@Serializable
data class EnemyPartInput(
    val partId: String,
    val nameZh: String,
    val armor: WikiFactRecord<Int> = WikiFactRecord.unknown(),
    val notes: String? = null,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
)

/**
 * 敌人派生型对基础型部位的局部覆写或新增定义。
 *
 * 仅当所属敌人声明了 `extends` 时允许使用：
 * - `isNewPart = false`：按 `partId` 局部覆写基础型中已存在部位的非空字段；
 * - `isNewPart = true`：在基础型部位列表末尾追加全新部位（此时 `nameZh` 必填）。
 */
@Serializable
data class EnemyPartOverrideInput(
    val partId: String,
    val nameZh: String? = null,
    val armor: WikiFactRecord<Int>? = null,
    val notes: String? = null,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
    val isNewPart: Boolean = false,
)

@Serializable
data class EnemyInputData(
    val faction: String,
    val sizeClass: String,
    val familyKey: String,
    val extends: String? = null,
    val parts: List<EnemyPartInput> = emptyList(),
    val partOverrides: List<EnemyPartOverrideInput> = emptyList(),
)

/**
 * 单个 Wiki 条目的原始输入模型，对应 JSON 中的一条战备、武器或敌人定义。
 */
@Serializable
data class WikiEntryInput(
    val entryId: String,
    val kind: String,
    val nameZh: String,
    val nameEn: String? = null,
    val aliases: List<WikiAliasRecord> = emptyList(),
    val summary: String? = null,
    val iconKey: String? = null,
    val sourceId: String,
    val verifiedPatch: String? = null,
    val stratagem: StratagemInputData? = null,
    val weapon: WeaponInputData? = null,
    val enemy: EnemyInputData? = null,
    val relations: List<WikiRelationInput> = emptyList(),
)

@Serializable
data class WikiEntryListFile(
    val entries: List<WikiEntryInput> = emptyList(),
)

/**
 * 携带源文件路径与目录预期分类信息的已加载条目包装类。
 *
 * `expected*` 字段由文件所处的目录及文件名推导，用于校验条目的 `category` / `slot` / `weaponClass` /
 * `faction` / `sizeClass` 是否与其磁盘存放路径严格一致。
 */
data class LoadedEntry(
    val relativePath: String,
    val expectedStratagemCategory: String? = null,
    val expectedWeaponSlot: String? = null,
    val expectedWeaponClass: String? = null,
    val expectedEnemyFaction: String? = null,
    val expectedEnemySizeClass: String? = null,
    val entry: WikiEntryInput,
)

/**
 * 通过 [WikiValidator.loadAndValidate] 全量校验后的只读构建中间产物，直接供 [WikiGenerator] 消费。
 */
data class ValidatedContent(
    val sourcesById: Map<String, WikiSourceRecord>,
    val entries: List<LoadedEntry>,
    val expandedEnemyPartsById: Map<String, List<EnemyPartInput>>,
    val availableIconKeys: Set<String>,
)

// --- 输出 JSON 模型（写入 composeResources/files/wiki/） ---

/**
 * 离线包清单文件 `manifest.json` 的输出模型，记录版本、条目总数以及索引与分片资源路径。
 */
@Serializable
data class WikiManifestOutput(
    val schemaVersion: Int,
    val packVersion: String,
    val generatedFromAppVersion: String,
    val entryCount: Int,
    val indexPath: String,
    val detailPaths: Map<String, String>,
)

@Serializable
data class NormalizedTextPairOutput(
    val text: String,
    val compact: String,
)

@Serializable
data class WikiIndexItemOutput(
    val entryId: String,
    val kind: String,
    val nameZh: String,
    val nameEn: String? = null,
    val summary: String? = null,
    val iconKey: String? = null,
    val stratagemCategory: String? = null,
    val weaponSlot: String? = null,
    val weaponClass: String? = null,
    val enemyFaction: String? = null,
    val enemySizeClass: String? = null,
    val modelCodes: List<String> = emptyList(),
    val directions: List<String> = emptyList(),
    val trainingOrder: Int? = null,
    val normalizedNameZh: String,
    val normalizedNameZhCompact: String,
    val normalizedNameEn: String? = null,
    val normalizedNameEnCompact: String? = null,
    val normalizedAliases: List<NormalizedTextPairOutput> = emptyList(),
    val normalizedModelCodes: List<NormalizedTextPairOutput> = emptyList(),
)

@Serializable
data class WikiIndexRelationOutput(
    val ownerEntryId: String,
    val targetEntryId: String,
    val relationType: String,
    val isReverse: Boolean,
    val condition: String? = null,
    val sourceId: String? = null,
)

/**
 * 全量搜索索引文件 `index.json` 的输出模型，包含轻量条目摘要与双向展开后的关系列表。
 */
@Serializable
data class WikiIndexOutput(
    val entries: List<WikiIndexItemOutput>,
    val relations: List<WikiIndexRelationOutput>,
)

@Serializable
data class EnemyDetailOutput(
    val faction: String,
    val sizeClass: String,
    val familyKey: String,
    val parts: List<EnemyPartInput>,
)

@Serializable
data class WikiEntryDetailOutput(
    val entryId: String,
    val kind: String,
    val nameZh: String,
    val nameEn: String? = null,
    val aliases: List<WikiAliasRecord> = emptyList(),
    val summary: String? = null,
    val iconKey: String? = null,
    val sourceId: String,
    val verifiedPatch: String? = null,
    val stratagem: StratagemInputData? = null,
    val weapon: WeaponInputData? = null,
    val enemy: EnemyDetailOutput? = null,
)

/**
 * 按条目类型（`stratagem` / `weapon` / `enemy`）拆分的详情分片文件 `details/<kind>.json` 输出模型，
 * 仅打包该分片实际引用的来源记录以控制运行时加载体积。
 */
@Serializable
data class WikiDetailShardOutput(
    val kind: String,
    val entries: Map<String, WikiEntryDetailOutput>,
    val sources: Map<String, WikiSourceRecord>,
)
