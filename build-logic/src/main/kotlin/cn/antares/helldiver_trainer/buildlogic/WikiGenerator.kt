package cn.antares.helldiver_trainer.buildlogic

import kotlinx.serialization.json.Json
import java.io.File

object WikiGenerator {

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val compactJson = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * 基于已校验的 Wiki 内容清空并重建全部构建期输出产物。
     *
     * 每次执行会先删除旧的输出目录再生成以下 5 类产物：
     * 1. `manifest.json`：离线包元信息与分片路径清单；
     * 2. `index.json`：全量检索索引与双向展开后的关系边；
     * 3. `details/{stratagem,weapon,enemy}.json`：按类型分片的条目详情与最小来源子集；
     * 4. `GeneratedStratagemCatalog.kt`：按 `training.order` 排序的训练器战备目录；
     * 5. `WikiIconRegistry.kt`：`iconKey` 到 `MR.images` 的静态映射表。
     */
    fun generate(
        validated: ValidatedContent,
        appVersion: String,
        packVersion: String,
        outputKotlinDir: File,
        outputResourcesDir: File,
    ) {
        if (outputKotlinDir.exists()) outputKotlinDir.deleteRecursively()
        if (outputResourcesDir.exists()) outputResourcesDir.deleteRecursively()

        val kotlinPkgDir = File(outputKotlinDir, "cn/antares/helldiver_trainer/wiki/generated")
        kotlinPkgDir.mkdirs()
        outputResourcesDir.mkdirs()
        File(outputResourcesDir, "details").mkdirs()

        // 1. 生成离线包清单 manifest.json
        val manifest = WikiManifestOutput(
            schemaVersion = 1,
            packVersion = packVersion,
            generatedFromAppVersion = appVersion,
            entryCount = validated.entries.size,
            indexPath = "files/wiki/index.json",
            detailPaths = mapOf(
                "stratagem" to "files/wiki/details/stratagem.json",
                "weapon" to "files/wiki/details/weapon.json",
                "enemy" to "files/wiki/details/enemy.json",
            ),
        )
        File(outputResourcesDir, "manifest.json")
            .writeText(prettyJson.encodeToString(manifest), Charsets.UTF_8)

        // 2. 生成检索索引 index.json（含双向展开的关系边）
        val indexItems = validated.entries.map { loaded ->
            buildIndexItem(loaded.entry)
        }
        val relations = buildList {
            for ((_, _, _, _, _, _, e) in validated.entries) {
                for ((type, targetId, sourceId, condition) in e.relations) {
                    // 将每条有向关系同时展开为正向（isReverse = false）与反向（isReverse = true）两条记录，
                    // 便于客户端在任意一端条目详情页通过 ownerEntryId O(1) 查询关联条目
                    add(
                        WikiIndexRelationOutput(
                            ownerEntryId = e.entryId,
                            targetEntryId = targetId,
                            relationType = type,
                            isReverse = false,
                            condition = condition,
                            sourceId = sourceId,
                        ),
                    )
                    add(
                        WikiIndexRelationOutput(
                            ownerEntryId = targetId,
                            targetEntryId = e.entryId,
                            relationType = type,
                            isReverse = true,
                            condition = condition,
                            sourceId = sourceId,
                        ),
                    )
                }
            }
        }
        val indexOutput = WikiIndexOutput(entries = indexItems, relations = relations)
        File(outputResourcesDir, "index.json")
            .writeText(compactJson.encodeToString(indexOutput), Charsets.UTF_8)

        // 3. 按条目类型生成详情分片 details/{stratagem,weapon,enemy}.json
        for (kind in listOf("stratagem", "weapon", "enemy")) {
            val kindEntries = validated.entries.filter { it.entry.kind == kind }
            val detailMap = linkedMapOf<String, WikiEntryDetailOutput>()
            val usedSourceIds = linkedSetOf<String>()

            for ((_, _, _, _, _, _, e) in kindEntries) {
                usedSourceIds.add(e.sourceId)
                e.stratagem?.let { s ->
                    s.callInSeconds.sourceId?.let(usedSourceIds::add)
                    s.cooldownSeconds.sourceId?.let(usedSourceIds::add)
                    s.uses.sourceId?.let(usedSourceIds::add)
                }
                e.weapon?.let { w ->
                    for ((_, _, _, magazineCapacity, fireRateRpm, _, attacks) in w.configurations) {
                        magazineCapacity.sourceId?.let(usedSourceIds::add)
                        fireRateRpm.sourceId?.let(usedSourceIds::add)
                        for ((_, _, damage, armorPenetration) in attacks) {
                            damage.sourceId?.let(usedSourceIds::add)
                            armorPenetration.sourceId?.let(usedSourceIds::add)
                        }
                    }
                }
                val expandedParts = if (kind == "enemy") {
                    validated.expandedEnemyPartsById[e.entryId].orEmpty()
                } else {
                    emptyList()
                }
                for ((_, _, armor, _, sourceId) in expandedParts) {
                    sourceId?.let(usedSourceIds::add)
                    armor.sourceId?.let(usedSourceIds::add)
                }
                val enemyOut = if (kind == "enemy" && e.enemy != null) {
                    EnemyDetailOutput(
                        faction = e.enemy.faction,
                        sizeClass = e.enemy.sizeClass,
                        familyKey = e.enemy.familyKey,
                        parts = expandedParts,
                    )
                } else {
                    null
                }
                detailMap[e.entryId] = WikiEntryDetailOutput(
                    entryId = e.entryId,
                    kind = e.kind,
                    nameZh = e.nameZh,
                    nameEn = e.nameEn,
                    aliases = e.aliases,
                    summary = e.summary,
                    iconKey = e.iconKey,
                    sourceId = e.sourceId,
                    verifiedPatch = e.verifiedPatch,
                    stratagem = e.stratagem,
                    weapon = e.weapon,
                    enemy = enemyOut,
                )
            }

            val shardSources = usedSourceIds.associateWith { validated.sourcesById.getValue(it) }
            val shard = WikiDetailShardOutput(
                kind = kind,
                entries = detailMap,
                sources = shardSources,
            )
            File(outputResourcesDir, "details/$kind.json")
                .writeText(compactJson.encodeToString(shard), Charsets.UTF_8)
        }

        // 4. 生成训练器战备目录源码 GeneratedStratagemCatalog.kt
        val trainingEntries = validated.entries
            .map { it.entry }
            .filter { it.kind == "stratagem" && it.stratagem?.training != null }
            .sortedBy { it.stratagem!!.training!!.order }

        File(kotlinPkgDir, "GeneratedStratagemCatalog.kt")
            .writeText(renderStratagemCatalogKt(trainingEntries), Charsets.UTF_8)

        // 5. 生成图标资源映射表源码 WikiIconRegistry.kt
        File(kotlinPkgDir, "WikiIconRegistry.kt")
            .writeText(renderIconRegistryKt(validated.availableIconKeys), Charsets.UTF_8)
    }

    /**
     * 构建单条轻量索引项，并利用 [WikiTextNormalizerBuild] 预计算中文名、英文名、
     * 别名与武器型号代码的标准化搜索字段（含带单空格与去空白紧凑串两种形式）。
     */
    private fun buildIndexItem(entry: WikiEntryInput): WikiIndexItemOutput {
        val normZh = WikiTextNormalizerBuild.normalize(entry.nameZh)
        val normEn = entry.nameEn?.let { WikiTextNormalizerBuild.normalize(it) }
        val modelCodes = entry.weapon?.configurations
            ?.mapNotNull { it.modelCode?.takeIf(String::isNotBlank) }
            ?.distinct()
            ?: emptyList()
        val normAliases = entry.aliases.map { WikiTextNormalizerBuild.normalize(it.text) }
        val normModels = modelCodes.map { WikiTextNormalizerBuild.normalize(it) }

        return WikiIndexItemOutput(
            entryId = entry.entryId,
            kind = entry.kind,
            nameZh = entry.nameZh,
            nameEn = entry.nameEn,
            summary = entry.summary,
            iconKey = entry.iconKey,
            stratagemCategory = entry.stratagem?.category,
            weaponSlot = entry.weapon?.slot,
            weaponClass = entry.weapon?.weaponClass,
            enemyFaction = entry.enemy?.faction,
            enemySizeClass = entry.enemy?.sizeClass,
            modelCodes = modelCodes,
            directions = entry.stratagem?.directions ?: emptyList(),
            trainingOrder = entry.stratagem?.training?.order,
            normalizedNameZh = normZh.text,
            normalizedNameZhCompact = normZh.compact,
            normalizedNameEn = normEn?.text,
            normalizedNameEnCompact = normEn?.compact,
            normalizedAliases = normAliases,
            normalizedModelCodes = normModels,
        )
    }

    /**
     * 按 `training.order` 顺序生成 `GeneratedStratagemCatalog.kt`，供训练器页面直接使用内置战备列表。
     */
    private fun renderStratagemCatalogKt(entries: List<WikiEntryInput>): String {
        val iconImports = entries
            .mapNotNull { it.iconKey }
            .distinct()
            .sorted()

        return buildString {
            appendLine("package cn.antares.helldiver_trainer.wiki.generated")
            appendLine()
            appendLine("import cn.antares.helldiver_trainer.MR")
            for (icon in iconImports) {
                appendLine("import cn.antares.helldiver_trainer.$icon")
            }
            appendLine("import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.DOWN")
            appendLine("import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.LEFT")
            appendLine("import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.RIGHT")
            appendLine("import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemInput.UP")
            appendLine("import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemItem")
            appendLine()
            appendLine("object GeneratedStratagemCatalog {")
            appendLine("    val items: List<StratagemItem> = listOf(")
            for ((_, _, nameZh, _, _, _, iconKey1, _, _, stratagem1) in entries) {
                val stratagem = stratagem1!!
                val training = stratagem.training!!
                val iconKey = iconKey1!!
                val inputs = stratagem.directions.joinToString(", ")
                appendLine("        StratagemItem(")
                appendLine("            id = \"${escapeKt(training.legacyId)}\",")
                appendLine("            name = \"${escapeKt(nameZh)}\",")
                appendLine("            icon = MR.images.$iconKey,")
                appendLine("            inputs = listOf($inputs),")
                appendLine("        ),")
            }
            appendLine("    )")
            appendLine("}")
        }
    }

    /**
     * 生成 `WikiIconRegistry.kt`，将所有已扫描到的 SVG `iconKey` 静态映射至 `MR.images.<iconKey>`。
     */
    private fun renderIconRegistryKt(availableIconKeys: Set<String>): String {
        val sortedKeys = availableIconKeys.sorted()
        return buildString {
            appendLine("package cn.antares.helldiver_trainer.wiki.generated")
            appendLine()
            appendLine("import cn.antares.helldiver_trainer.MR")
            for (icon in sortedKeys) {
                appendLine("import cn.antares.helldiver_trainer.$icon")
            }
            appendLine("import dev.icerock.moko.resources.ImageResource")
            appendLine()
            appendLine("object WikiIconRegistry {")
            appendLine("    fun resolve(iconKey: String?): ImageResource? = when (iconKey) {")
            for (icon in sortedKeys) {
                appendLine("        \"${escapeKt(icon)}\" -> MR.images.$icon")
            }
            appendLine("        else -> null")
            appendLine("    }")
            appendLine("}")
        }
    }

    /**
     * 对生成的 Kotlin 字符串字面量中的反斜杠、双引号、美元符及空白控制符进行转义。
     */
    private fun escapeKt(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
