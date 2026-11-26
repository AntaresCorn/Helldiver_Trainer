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
        File(outputResourcesDir, "anatomy").mkdirs()
        File(outputResourcesDir, "anatomy_assets").mkdirs()
        File(outputResourcesDir, "matchups").mkdirs()

        val anatomyPaths = linkedMapOf<String, String>()
        for (enemyId in validated.anatomiesByEnemyId.keys.sorted()) {
            anatomyPaths[enemyId] = "files/wiki/anatomy/$enemyId.json"
        }
        val anatomyAssetPaths = linkedMapOf<String, String>()
        for (assetKey in validated.anatomyAssetFilesByKey.keys.sorted()) {
            anatomyAssetPaths[assetKey] = "files/wiki/anatomy_assets/$assetKey.webp"
        }

        // 1. 生成离线包清单 manifest.json (schemaVersion = 2)
        val manifest = WikiManifestOutput(
            schemaVersion = 2,
            packVersion = packVersion,
            generatedFromAppVersion = appVersion,
            entryCount = validated.entries.size,
            indexPath = "files/wiki/index.json",
            detailPaths = mapOf(
                "stratagem" to "files/wiki/details/stratagem.json",
                "weapon" to "files/wiki/details/weapon.json",
                "enemy" to "files/wiki/details/enemy.json",
            ),
            rulesPath = "files/wiki/rules.json",
            anatomyPaths = anatomyPaths,
            anatomyAssetPaths = anatomyAssetPaths,
            evidencePath = "files/wiki/matchups/evidence.json",
            recommendationsPath = "files/wiki/matchups/recommendations.json",
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
                    s.weaponProfile?.let { profile ->
                        collectConfigSources(profile.configurations, usedSourceIds)
                    }
                }
                e.weapon?.let { w ->
                    collectConfigSources(w.configurations, usedSourceIds)
                }
                val expandedPools = if (kind == "enemy") {
                    validated.expandedEnemyPoolsById[e.entryId].orEmpty()
                } else {
                    emptyList()
                }
                for (pool in expandedPools) {
                    pool.sourceId?.let(usedSourceIds::add)
                    pool.maxHealth.sourceId?.let(usedSourceIds::add)
                }
                val expandedParts = if (kind == "enemy") {
                    validated.expandedEnemyPartsById[e.entryId].orEmpty()
                } else {
                    emptyList()
                }
                for (part in expandedParts) {
                    part.sourceId?.let(usedSourceIds::add)
                    part.armor.sourceId?.let(usedSourceIds::add)
                    part.durability.sourceId?.let(usedSourceIds::add)
                    part.toMainRatio.sourceId?.let(usedSourceIds::add)
                    part.explosionResistance.sourceId?.let(usedSourceIds::add)
                    for (eff in part.onBreak) {
                        eff.sourceId?.let(usedSourceIds::add)
                    }
                }
                val enemyOut = if (kind == "enemy" && e.enemy != null) {
                    EnemyDetailOutput(
                        faction = e.enemy.faction,
                        sizeClass = e.enemy.sizeClass,
                        familyKey = e.enemy.familyKey,
                        mainPoolId = validated.expandedEnemyMainPoolIdById[e.entryId],
                        pools = expandedPools,
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

        // 4. 生成二期补丁规则、敌人解剖分片、WebP 底图、实测表与推荐表
        val ruleSources = validated.damageRules
            .map { it.sourceId }
            .distinct()
            .associateWith { validated.sourcesById.getValue(it) }
        File(outputResourcesDir, "rules.json").writeText(
            compactJson.encodeToString(
                DamageRulesOutput(
                    ruleSets = validated.damageRules,
                    sources = ruleSources,
                ),
            ),
            Charsets.UTF_8,
        )

        for ((enemyId, anatomy) in validated.anatomiesByEnemyId) {
            val anatomySources = anatomy.views
                .map { it.sourceId }
                .distinct()
                .associateWith { validated.sourcesById.getValue(it) }
            File(outputResourcesDir, "anatomy/$enemyId.json").writeText(
                compactJson.encodeToString(
                    EnemyAnatomyOutput(
                        enemyId = anatomy.enemyId,
                        defaultViewId = anatomy.defaultViewId,
                        views = anatomy.views,
                        sources = anatomySources,
                    ),
                ),
                Charsets.UTF_8,
            )
        }

        for ((assetKey, srcFile) in validated.anatomyAssetFilesByKey) {
            srcFile.copyTo(
                File(outputResourcesDir, "anatomy_assets/$assetKey.webp"),
                overwrite = true,
            )
        }

        val evByEnemyPart = linkedMapOf<String, MutableList<String>>()
        val evByAttackRef = linkedMapOf<String, MutableList<String>>()
        val evSourceIds = linkedSetOf<String>()
        for (ev in validated.evidences) {
            evByEnemyPart.getOrPut("${ev.enemyId}::${ev.partId}") { mutableListOf() }
                .add(ev.evidenceId)
            val atkKey =
                "${ev.attackRef.entryId}::${ev.attackRef.configId}::${ev.attackRef.attackId}"
            evByAttackRef.getOrPut(atkKey) { mutableListOf() }.add(ev.evidenceId)
            evSourceIds.add(ev.sourceId)
            ev.shotsToBreak.sourceId?.let(evSourceIds::add)
            ev.shotsToKill.sourceId?.let(evSourceIds::add)
        }
        File(outputResourcesDir, "matchups/evidence.json").writeText(
            compactJson.encodeToString(
                MatchEvidencePackOutput(
                    evidences = validated.evidences,
                    byEnemyPart = evByEnemyPart,
                    byAttackRef = evByAttackRef,
                    sources = evSourceIds.associateWith { validated.sourcesById.getValue(it) },
                ),
            ),
            Charsets.UTF_8,
        )

        val sortedRecs = validated.recommendations.withIndex()
            .sortedWith(compareBy({ it.value.priorityOrder }, { it.index }))
            .map { it.value }
        val recByGroup = linkedMapOf<String, MutableList<String>>()
        val recSources = linkedSetOf<String>()
        for (rec in sortedRecs) {
            val groupKey = "${rec.enemyId}::${rec.partId}::${rec.partState}::${rec.goal}"
            recByGroup.getOrPut(groupKey) { mutableListOf() }.add(rec.recId)
            recSources.add(rec.sourceId)
        }
        File(outputResourcesDir, "matchups/recommendations.json").writeText(
            compactJson.encodeToString(
                PartRecommendationsPackOutput(
                    recommendations = sortedRecs,
                    byGroupKey = recByGroup,
                    sources = recSources.associateWith { validated.sourcesById.getValue(it) },
                ),
            ),
            Charsets.UTF_8,
        )

        // 5. 生成训练器战备目录源码 GeneratedStratagemCatalog.kt
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
        val configs = entry.weapon?.configurations ?: entry.stratagem?.weaponProfile?.configurations
        val modelCodes = configs
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

    private fun collectConfigSources(
        configurations: List<WeaponConfigInput>,
        usedSourceIds: MutableSet<String>,
    ) {
        for (cfg in configurations) {
            cfg.magazineCapacity.sourceId?.let(usedSourceIds::add)
            cfg.fireRateRpm.sourceId?.let(usedSourceIds::add)
            for (atk in cfg.attacks) {
                atk.damage.sourceId?.let(usedSourceIds::add)
                atk.armorPenetration.sourceId?.let(usedSourceIds::add)
                for (comp in atk.components) {
                    comp.sourceId?.let(usedSourceIds::add)
                    comp.normalDamage.sourceId?.let(usedSourceIds::add)
                    comp.durableDamage.sourceId?.let(usedSourceIds::add)
                    comp.armorPenetration.sourceId?.let(usedSourceIds::add)
                }
            }
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
