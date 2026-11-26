package cn.antares.helldiver_trainer.buildlogic

import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import java.io.File
import kotlin.math.abs

object WikiValidator {

    private val validKinds = setOf("stratagem", "weapon", "enemy")
    private val validSourceTypes = setOf("project_migration", "wiki_gg", "patch_notes", "official")
    private val validStratagemCategories = setOf(
        "eagle",
        "orbital",
        "support_weapon",
        "backpack",
        "vehicle",
        "defensive",
        "mission",
    )
    private val weaponProfileAllowedStratagemCategories = setOf(
        "support_weapon",
        "eagle",
        "orbital",
        "defensive",
        "vehicle",
    )
    private val validDirections = setOf("UP", "RIGHT", "DOWN", "LEFT")
    private val validWeaponSlots = setOf("primary", "secondary", "throwable")
    private val validDamageComponentKinds = setOf("direct", "explosive", "fire", "status")
    private val validPartStates = setOf("intact", "broken")
    private val validBreakEffectTypes =
        setOf("expose_part", "disable_ability", "bleed_out", "fatal")
    private val validMatchVerdicts = setOf(
        "penetrates",
        "reduced",
        "ricochet",
        "strips_armor",
        "kills",
        "blocked",
        "unknown",
    )
    private val validRecommendationGoals = setOf(
        "strip_armor",
        "kill",
        "disable_ability",
        "attack_exposed",
    )
    private val allowedWeaponClassesBySlot = mapOf(
        "primary" to setOf(
            "assault_rifle",
            "marksman_rifle",
            "submachine_gun",
            "shotgun",
            "explosive",
            "energy_based",
            "special",
        ),
        "secondary" to setOf("pistol", "melee", "special"),
        "throwable" to setOf("standard", "special"),
    )
    private val validFactions = setOf("terminid", "automaton", "illuminate")
    private val validEnemySizeClasses = setOf("small", "medium", "heavy", "massive")
    private val validRelationTypes = setOf("calls_in", "variant_of")

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    /**
     * 扫描、加载并全面校验 `wiki-content` 下的来源清单与全部条目数据。
     *
     * 核心校验流程：
     * 1. 加载 `sources.json` 与 `moko-resources/images` 下可用的 SVG `iconKey` 集合；
     * 2. 按三类目录结构扫描并解析条目文件：`stratagems/<category>.json`、
     *    `weapons/<slot>/<weaponClass>.json`、`enemies/<faction>/<sizeClass>.json`；
     * 3. 校验单条目必填字段、唯一性、图标存在性及按 `kind` 分派结构校验；
     * 4. 校验跨条目关系（`calls_in` / `variant_of`）与敌人继承关系（`enemy.extends`），并执行环检测；
     * 5. 递归展开敌人部位继承与局部覆写（`partOverrides`）；
     * 6. 校验可训练战备 `training.order` 从 1 到 N 的连续性。
     *
     * @param contentDir `wiki-content` 根目录。
     * @param mokoImagesDir `moko-resources/images` 图标目录。
     * @return 通过所有校验的只读内容快照 [ValidatedContent]。
     */
    fun loadAndValidate(contentDir: File, mokoImagesDir: File): ValidatedContent {
        if (!contentDir.isDirectory) {
            fail("wiki-content", "-", "root", "内容源目录不存在: ${contentDir.absolutePath}")
        }
        val sourcesFile = File(contentDir, "sources.json")
        if (!sourcesFile.isFile) {
            fail("sources.json", "-", "sources", "缺少来源登记文件 sources.json")
        }

        val parsedSources = runCatching {
            json.decodeFromString<SourcesFile>(sourcesFile.readText(Charsets.UTF_8))
        }.getOrElse { e ->
            fail("sources.json", "-", "json", "解析 sources.json 失败: ${e.message}")
        }

        val sourcesById = linkedMapOf<String, WikiSourceRecord>()
        for (src in parsedSources.sources) {
            if (src.sourceId.isBlank()) {
                fail("sources.json", "-", "sourceId", "sourceId 不能为空")
            }
            if (src.title.isBlank()) {
                fail("sources.json", src.sourceId, "title", "来源 title 不能为空")
            }
            if (src.sourceType !in validSourceTypes) {
                fail(
                    "sources.json",
                    src.sourceId,
                    "sourceType",
                    "非法 sourceType: ${src.sourceType}",
                )
            }
            if (src.sourceType == "wiki_gg") {
                if (src.url.isNullOrBlank()) {
                    fail("sources.json", src.sourceId, "url", "wiki_gg 来源必须填写原始链接 url")
                }
                if (src.license.isNullOrBlank()) {
                    fail(
                        "sources.json",
                        src.sourceId,
                        "license",
                        "wiki_gg 来源必须填写协议 license（如 CC BY-NC-SA 4.0）",
                    )
                }
            }
            if (sourcesById.put(src.sourceId, src) != null) {
                fail("sources.json", src.sourceId, "sourceId", "sourceId 重复: ${src.sourceId}")
            }
        }

        val availableIconKeys = if (mokoImagesDir.isDirectory) {
            mokoImagesDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("svg", ignoreCase = true) }
                ?.map { it.nameWithoutExtension }
                ?.toSet()
                ?: emptySet()
        } else {
            emptySet()
        }

        val loadedEntries = mutableListOf<LoadedEntry>()

        // 1. 战备（7 个分类列表文件）
        val stratagemDir = File(contentDir, "stratagems")
        if (stratagemDir.isDirectory) {
            val files = stratagemDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: emptyList()
            for (file in files) {
                val relPath = "stratagems/${file.name}"
                val categoryFromFile = file.nameWithoutExtension
                if (categoryFromFile !in validStratagemCategories) {
                    fail(relPath, "-", "fileName", "战备分类文件名不合法: ${file.name}")
                }
                val listFile = runCatching {
                    json.decodeFromString<WikiEntryListFile>(file.readText(Charsets.UTF_8))
                }.getOrElse { e ->
                    fail(relPath, "-", "json", "解析战备列表 JSON 失败: ${e.message}")
                }
                for (entry in listFile.entries) {
                    loadedEntries.add(
                        LoadedEntry(
                            relativePath = relPath,
                            expectedStratagemCategory = categoryFromFile,
                            entry = entry,
                        ),
                    )
                }
            }
        }

        // 2. 武器（四级目录：weapons/<slot>/<weaponClass>.json）
        val weaponsDir = File(contentDir, "weapons")
        if (weaponsDir.isDirectory) {
            val strayFiles = weaponsDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?: emptyList()
            strayFiles.firstOrNull()?.let { stray ->
                fail(
                    "weapons/${stray.name}",
                    "-",
                    "filePath",
                    "武器文件必须放在 weapons/<slot>/<weaponClass>.json 子目录下",
                )
            }
            val slotDirs = weaponsDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?: emptyList()
            for (slotDir in slotDirs) {
                val slotFromDir = slotDir.name
                if (slotFromDir !in validWeaponSlots) {
                    fail(
                        "weapons/$slotFromDir",
                        "-",
                        "dirName",
                        "武器槽位目录名不合法: $slotFromDir",
                    )
                }
                val allowedClasses = allowedWeaponClassesBySlot[slotFromDir].orEmpty()
                val files = slotDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                for (file in files) {
                    val relPath = "weapons/$slotFromDir/${file.name}"
                    val classFromFile = file.nameWithoutExtension
                    if (classFromFile !in allowedClasses) {
                        fail(
                            relPath,
                            "-",
                            "fileName",
                            "武器子类文件名 ($classFromFile) 不属于槽位 $slotFromDir",
                        )
                    }
                    val listFile = runCatching {
                        json.decodeFromString<WikiEntryListFile>(file.readText(Charsets.UTF_8))
                    }.getOrElse { e ->
                        fail(relPath, "-", "json", "解析武器列表 JSON 失败: ${e.message}")
                    }
                    for (entry in listFile.entries) {
                        loadedEntries.add(
                            LoadedEntry(
                                relativePath = relPath,
                                expectedWeaponSlot = slotFromDir,
                                expectedWeaponClass = classFromFile,
                                entry = entry,
                            ),
                        )
                    }
                }
            }
        }

        // 3. 敌人（四级目录：enemies/<faction>/<sizeClass>.json）
        val enemiesDir = File(contentDir, "enemies")
        if (enemiesDir.isDirectory) {
            val strayFiles = enemiesDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?: emptyList()
            strayFiles.firstOrNull()?.let { stray ->
                fail(
                    "enemies/${stray.name}",
                    "-",
                    "filePath",
                    "敌人文件必须放在 enemies/<faction>/<sizeClass>.json 子目录下",
                )
            }
            val factionDirs = enemiesDir.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedBy { it.name }
                ?: emptyList()
            for (factionDir in factionDirs) {
                val factionFromDir = factionDir.name
                if (factionFromDir !in validFactions) {
                    fail(
                        "enemies/$factionFromDir",
                        "-",
                        "dirName",
                        "敌人阵营目录名不合法: $factionFromDir",
                    )
                }
                val files = factionDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                    ?.sortedBy { it.name }
                    ?: emptyList()
                for (file in files) {
                    val relPath = "enemies/$factionFromDir/${file.name}"
                    val sizeFromFile = file.nameWithoutExtension
                    if (sizeFromFile !in validEnemySizeClasses) {
                        fail(
                            relPath,
                            "-",
                            "fileName",
                            "敌人体型文件名不合法: ${file.name}",
                        )
                    }
                    val listFile = runCatching {
                        json.decodeFromString<WikiEntryListFile>(file.readText(Charsets.UTF_8))
                    }.getOrElse { e ->
                        fail(relPath, "-", "json", "解析敌人列表 JSON 失败: ${e.message}")
                    }
                    for (entry in listFile.entries) {
                        loadedEntries.add(
                            LoadedEntry(
                                relativePath = relPath,
                                expectedEnemyFaction = factionFromDir,
                                expectedEnemySizeClass = sizeFromFile,
                                entry = entry,
                            ),
                        )
                    }
                }
            }
        }

        // 校验单条目基础字段、ID 唯一性及各类型专属结构
        val entryById = linkedMapOf<String, LoadedEntry>()
        val trainingLegacyIds = mutableSetOf<String>()
        val trainingOrders = mutableSetOf<Int>()

        for (loaded in loadedEntries) {
            val rel = loaded.relativePath
            val e = loaded.entry
            if (e.entryId.isBlank()) {
                fail(rel, "-", "entryId", "entryId 不能为空")
            }
            if (e.kind !in validKinds) {
                fail(rel, e.entryId, "kind", "非法 kind: ${e.kind}")
            }
            if (e.nameZh.isBlank()) {
                fail(rel, e.entryId, "nameZh", "nameZh 不能为空")
            }
            if (e.sourceId.isBlank() || e.sourceId !in sourcesById) {
                fail(rel, e.entryId, "sourceId", "未登记的 sourceId: ${e.sourceId}")
            }
            if (!e.iconKey.isNullOrBlank() && e.iconKey !in availableIconKeys) {
                fail(rel, e.entryId, "iconKey", "moko-resources 中不存在图标: ${e.iconKey}")
            }
            if (entryById.put(e.entryId, loaded) != null) {
                fail(rel, e.entryId, "entryId", "entryId 重复: ${e.entryId}")
            }

            when (e.kind) {
                "stratagem" -> validateStratagem(
                    loaded,
                    sourcesById,
                    trainingLegacyIds,
                    trainingOrders,
                )

                "weapon" -> validateWeapon(loaded, sourcesById)
                "enemy" -> validateEnemyStructure(loaded, sourcesById)
            }
        }

        // 校验跨条目引用关系与敌人继承目标存在性
        for (loaded in loadedEntries) {
            val rel = loaded.relativePath
            val e = loaded.entry
            for ((type, targetId, sourceId) in e.relations) {
                if (type !in validRelationTypes) {
                    fail(rel, e.entryId, "relations.type", "非法关系类型: $type")
                }
                if (targetId == e.entryId) {
                    fail(rel, e.entryId, "relations.targetId", "禁止自指关系: $targetId")
                }
                val targetLoaded = entryById[targetId]
                    ?: fail(rel, e.entryId, "relations.targetId", "关系目标不存在: $targetId")
                if (sourceId != null && sourceId !in sourcesById) {
                    fail(rel, e.entryId, "relations.sourceId", "未登记的 sourceId: $sourceId")
                }
                when (type) {
                    "calls_in" -> {
                        if (e.kind != "stratagem" || targetLoaded.entry.kind != "weapon") {
                            fail(
                                rel,
                                e.entryId,
                                "relations",
                                "calls_in 只允许 stratagem -> weapon，实际为 ${e.kind} -> ${targetLoaded.entry.kind}",
                            )
                        }
                    }

                    "variant_of" -> {
                        if (e.kind != targetLoaded.entry.kind || e.kind !in setOf(
                                "weapon",
                                "enemy",
                            )
                        ) {
                            fail(
                                rel,
                                e.entryId,
                                "relations",
                                "variant_of 只允许同类型 weapon/enemy 指向，实际为 ${e.kind} -> ${targetLoaded.entry.kind}",
                            )
                        }
                    }
                }
            }

            if (e.kind == "enemy") {
                val extId = e.enemy?.extends
                if (extId != null) {
                    if (extId == e.entryId) {
                        fail(rel, e.entryId, "enemy.extends", "禁止自身继承: $extId")
                    }
                    val base = entryById[extId]
                        ?: fail(rel, e.entryId, "enemy.extends", "继承目标不存在: $extId")
                    if (base.entry.kind != "enemy") {
                        fail(rel, e.entryId, "enemy.extends", "继承目标不是 enemy: $extId")
                    }
                }
            }
        }

        // 对 variant_of 关系与 enemy.extends 继承链执行循环引用检测
        checkCycles(loadedEntries, "relations.variant_of") { e ->
            e.relations.filter { it.type == "variant_of" }.map { it.targetId }
        }
        checkCycles(loadedEntries, "enemy.extends") { e ->
            listOfNotNull(e.enemy?.extends)
        }

        // 结合继承链与局部覆写展开每个敌人的最终血池与部位列表，并校验闭环引用
        val (expandedMainPoolIdById, expandedPoolsById) = expandEnemyPools(loadedEntries, entryById)
        val expandedPartsById = expandEnemyParts(loadedEntries, entryById)
        validateExpandedEnemyIntegrity(
            loadedEntries = loadedEntries,
            expandedMainPoolIdById = expandedMainPoolIdById,
            expandedPoolsById = expandedPoolsById,
            expandedPartsById = expandedPartsById,
        )

        // 校验训练器排序字段 training.order 是否从 1 到 N 连续且无空洞
        if (trainingOrders.isNotEmpty()) {
            val expectedRange = (1..trainingOrders.size).toSet()
            if (trainingOrders != expectedRange) {
                fail(
                    "stratagems/",
                    "-",
                    "training.order",
                    "training.order 必须从 1 连续编号至 ${trainingOrders.size}",
                )
            }
        }

        // 校验二期资产：WebP 解剖底图、敌人多视角热区、补丁伤害规则、实测事实表与推荐表
        val validatedAssets = WikiAnatomyValidation.loadAndValidateWebpAssets(
            assetsDir = File(contentDir, "assets/anatomy"),
            fail = ::fail,
        )
        val anatomiesByEnemyId = loadAndValidateAnatomies(
            anatomyDir = File(contentDir, "anatomy"),
            entryById = entryById,
            expandedPartsById = expandedPartsById,
            validatedAssets = validatedAssets,
            sourcesById = sourcesById,
        )
        val damageRules = loadAndValidateDamageRules(
            rulesFile = File(contentDir, "rules/damage_rules.json"),
            sourcesById = sourcesById,
        )
        val ruleSetByPatch = damageRules.associateBy { it.patchVersion }
        val validAttackRefs = collectValidAttackRefs(loadedEntries)
        val evidences = loadAndValidateEvidences(
            evidenceFile = File(contentDir, "matchups/evidence.json"),
            entryById = entryById,
            expandedPartsById = expandedPartsById,
            validAttackRefs = validAttackRefs,
            ruleSetByPatch = ruleSetByPatch,
            sourcesById = sourcesById,
        )
        val evidenceIds = evidences.mapTo(hashSetOf()) { it.evidenceId }
        val recommendations = loadAndValidateRecommendations(
            recFile = File(contentDir, "matchups/recommendations.json"),
            entryById = entryById,
            expandedPartsById = expandedPartsById,
            validAttackRefs = validAttackRefs,
            validEvidenceIds = evidenceIds,
            ruleSetByPatch = ruleSetByPatch,
            sourcesById = sourcesById,
        )

        return ValidatedContent(
            sourcesById = sourcesById,
            entries = loadedEntries,
            expandedEnemyPartsById = expandedPartsById,
            expandedEnemyPoolsById = expandedPoolsById,
            expandedEnemyMainPoolIdById = expandedMainPoolIdById,
            availableIconKeys = availableIconKeys,
            damageRules = damageRules,
            anatomiesByEnemyId = anatomiesByEnemyId,
            anatomyAssetFilesByKey = validatedAssets.mapValues { it.value.file },
            evidences = evidences,
            recommendations = recommendations,
        )
    }

    /**
     * 校验 `kind=stratagem` 条目：互斥字段、分类与文件目录一致性、方向序列合法性、
     * 数值事实字段约束，以及可选训练元数据（`legacyId` 唯一性、`order` 正整数唯一性、`iconKey` 必填）。
     */
    private fun validateStratagem(
        loaded: LoadedEntry,
        sourcesById: Map<String, WikiSourceRecord>,
        trainingLegacyIds: MutableSet<String>,
        trainingOrders: MutableSet<Int>,
    ) {
        val rel = loaded.relativePath
        val e = loaded.entry
        val s =
            e.stratagem ?: fail(rel, e.entryId, "stratagem", "kind=stratagem 时缺少 stratagem 对象")
        if (e.weapon != null || e.enemy != null) {
            fail(rel, e.entryId, "kind", "stratagem 条目不能携带 weapon 或 enemy 数据")
        }
        if (s.category !in validStratagemCategories) {
            fail(rel, e.entryId, "stratagem.category", "非法战备分类: ${s.category}")
        }
        if (loaded.expectedStratagemCategory != null && s.category != loaded.expectedStratagemCategory) {
            fail(
                rel,
                e.entryId,
                "stratagem.category",
                "条目的 category (${s.category}) 与所在文件 (${loaded.expectedStratagemCategory}.json) 不一致",
            )
        }
        if (s.directions.isEmpty() || s.directions.any { it !in validDirections }) {
            fail(
                rel,
                e.entryId,
                "stratagem.directions",
                "方向序列不能为空且只允许 UP/RIGHT/DOWN/LEFT",
            )
        }
        validateFactDouble(
            rel,
            e.entryId,
            "stratagem.callInSeconds",
            s.callInSeconds,
            sourcesById,
            allowUnlimited = false,
        )
        validateFactDouble(
            rel,
            e.entryId,
            "stratagem.cooldownSeconds",
            s.cooldownSeconds,
            sourcesById,
            allowUnlimited = false,
        )
        validateFactInt(
            rel,
            e.entryId,
            "stratagem.uses",
            s.uses,
            sourcesById,
            allowUnlimited = true,
        )

        s.training?.let { tr ->
            if (tr.legacyId.isBlank()) {
                fail(rel, e.entryId, "stratagem.training.legacyId", "legacyId 不能为空")
            }
            if (!trainingLegacyIds.add(tr.legacyId)) {
                fail(rel, e.entryId, "stratagem.training.legacyId", "legacyId 重复: ${tr.legacyId}")
            }
            if (tr.order <= 0) {
                fail(rel, e.entryId, "stratagem.training.order", "order 必须为正整数: ${tr.order}")
            }
            if (!trainingOrders.add(tr.order)) {
                fail(rel, e.entryId, "stratagem.training.order", "order 重复: ${tr.order}")
            }
            if (e.iconKey.isNullOrBlank()) {
                fail(rel, e.entryId, "iconKey", "可训练战备必须填写 iconKey")
            }
        }

        s.weaponProfile?.let { profile ->
            if (s.category !in weaponProfileAllowedStratagemCategories) {
                fail(
                    rel,
                    e.entryId,
                    "stratagem.weaponProfile",
                    "战备分类 ${s.category} 不允许挂载 weaponProfile",
                )
            }
            validateWeaponConfigurations(
                rel = rel,
                entryId = e.entryId,
                fieldPrefix = "stratagem.weaponProfile",
                configurations = profile.configurations,
                sourcesById = sourcesById,
            )
        }
    }

    /**
     * 校验 `kind=weapon` 条目：槽位/子类与目录路径一致性，并复用武器配置与多分量校验。
     */
    private fun validateWeapon(loaded: LoadedEntry, sourcesById: Map<String, WikiSourceRecord>) {
        val rel = loaded.relativePath
        val e = loaded.entry
        val w = e.weapon ?: fail(rel, e.entryId, "weapon", "kind=weapon 时缺少 weapon 对象")
        if (e.stratagem != null || e.enemy != null) {
            fail(rel, e.entryId, "kind", "weapon 条目不能携带 stratagem 或 enemy 数据")
        }
        if (w.slot !in validWeaponSlots) {
            fail(rel, e.entryId, "weapon.slot", "非法武器槽位: ${w.slot}")
        }
        if (loaded.expectedWeaponSlot != null && w.slot != loaded.expectedWeaponSlot) {
            fail(
                rel,
                e.entryId,
                "weapon.slot",
                "条目的 slot (${w.slot}) 与所在目录 (${loaded.expectedWeaponSlot}) 不一致",
            )
        }
        val allowedClasses = allowedWeaponClassesBySlot[w.slot].orEmpty()
        if (w.weaponClass !in allowedClasses) {
            fail(
                rel,
                e.entryId,
                "weapon.weaponClass",
                "非法武器子类 ${w.weaponClass}（槽位 ${w.slot} 允许值: $allowedClasses）",
            )
        }
        if (loaded.expectedWeaponClass != null && w.weaponClass != loaded.expectedWeaponClass) {
            fail(
                rel,
                e.entryId,
                "weapon.weaponClass",
                "条目的 weaponClass (${w.weaponClass}) 与所在文件 (${loaded.expectedWeaponClass}.json) 不一致",
            )
        }
        validateWeaponConfigurations(
            rel = rel,
            entryId = e.entryId,
            fieldPrefix = "weapon",
            configurations = w.configurations,
            sourcesById = sourcesById,
        )
    }

    private fun validateWeaponConfigurations(
        rel: String,
        entryId: String,
        fieldPrefix: String,
        configurations: List<WeaponConfigInput>,
        sourcesById: Map<String, WikiSourceRecord>,
    ) {
        if (configurations.isEmpty()) {
            fail(rel, entryId, "$fieldPrefix.configurations", "至少需要一套武器配置")
        }
        val configIds = mutableSetOf<String>()
        for (config in configurations) {
            val configId = config.configId
            if (configId.isBlank() || !configIds.add(configId)) {
                fail(
                    rel,
                    entryId,
                    "$fieldPrefix.configurations.configId",
                    "configId 为空或重复: $configId",
                )
            }
            if (config.labelZh.isBlank()) {
                fail(rel, entryId, "$fieldPrefix.configurations.labelZh", "labelZh 不能为空")
            }
            validateFactInt(
                rel,
                entryId,
                "$fieldPrefix.magazineCapacity",
                config.magazineCapacity,
                sourcesById,
                allowUnlimited = true,
            )
            validateFactInt(
                rel,
                entryId,
                "$fieldPrefix.fireRateRpm",
                config.fireRateRpm,
                sourcesById,
                allowUnlimited = false,
            )
            val attackIds = mutableSetOf<String>()
            for (attack in config.attacks) {
                val attackId = attack.attackId
                if (attackId.isBlank() || !attackIds.add(attackId)) {
                    fail(
                        rel,
                        entryId,
                        "$fieldPrefix.attacks.attackId",
                        "attackId 为空或重复: $attackId",
                    )
                }
                if (attack.nameZh.isBlank()) {
                    fail(rel, entryId, "$fieldPrefix.attacks.nameZh", "攻击方式 nameZh 不能为空")
                }
                validateFactInt(
                    rel,
                    entryId,
                    "$fieldPrefix.attacks.damage",
                    attack.damage,
                    sourcesById,
                    allowUnlimited = false,
                )
                validateFactInt(
                    rel,
                    entryId,
                    "$fieldPrefix.attacks.armorPenetration",
                    attack.armorPenetration,
                    sourcesById,
                    allowUnlimited = false,
                )
                val componentIds = mutableSetOf<String>()
                for (comp in attack.components) {
                    if (comp.componentId.isBlank() || !componentIds.add(comp.componentId)) {
                        fail(
                            rel,
                            entryId,
                            "$fieldPrefix.attacks.components.componentId",
                            "componentId 为空或重复: ${comp.componentId}",
                        )
                    }
                    if (comp.labelZh.isBlank()) {
                        fail(
                            rel,
                            entryId,
                            "$fieldPrefix.attacks.components.labelZh",
                            "分量 labelZh 不能为空",
                        )
                    }
                    if (comp.componentKind !in validDamageComponentKinds) {
                        fail(
                            rel,
                            entryId,
                            "$fieldPrefix.attacks.components.componentKind",
                            "非法 componentKind: ${comp.componentKind}",
                        )
                    }
                    if (comp.sourceId != null && comp.sourceId !in sourcesById) {
                        fail(
                            rel,
                            entryId,
                            "$fieldPrefix.attacks.components.sourceId",
                            "未登记的 sourceId: ${comp.sourceId}",
                        )
                    }
                    validateFactInt(
                        rel,
                        entryId,
                        "$fieldPrefix.attacks.components.normalDamage",
                        comp.normalDamage,
                        sourcesById,
                        allowUnlimited = false,
                    )
                    validateFactInt(
                        rel,
                        entryId,
                        "$fieldPrefix.attacks.components.durableDamage",
                        comp.durableDamage,
                        sourcesById,
                        allowUnlimited = false,
                    )
                    validateFactInt(
                        rel,
                        entryId,
                        "$fieldPrefix.attacks.components.armorPenetration",
                        comp.armorPenetration,
                        sourcesById,
                        allowUnlimited = false,
                    )
                    comp.innerRadiusMeters?.let { r ->
                        if (!r.isFinite() || r < 0.0) {
                            fail(
                                rel,
                                entryId,
                                "$fieldPrefix.attacks.components.innerRadiusMeters",
                                "内半径必须为非负有限数: $r",
                            )
                        }
                    }
                    comp.outerRadiusMeters?.let { r ->
                        if (!r.isFinite() || r < 0.0) {
                            fail(
                                rel,
                                entryId,
                                "$fieldPrefix.attacks.components.outerRadiusMeters",
                                "外半径必须为非负有限数: $r",
                            )
                        }
                    }
                    if (comp.innerRadiusMeters != null &&
                        comp.outerRadiusMeters != null &&
                        comp.innerRadiusMeters > comp.outerRadiusMeters
                    ) {
                        fail(
                            rel,
                            entryId,
                            "$fieldPrefix.attacks.components.innerRadiusMeters",
                            "内半径 (${comp.innerRadiusMeters}) 不能大于外半径 (${comp.outerRadiusMeters})",
                        )
                    }
                }
            }
        }
    }

    /**
     * 校验 `kind=enemy` 条目：阵营/体型与目录路径一致性、`familyKey` 非空、
     * 基础部位与 `partOverrides` 内部 ID 唯一性，以及 `partOverrides` 必须依赖 `extends` 的前置约束。
     */
    private fun validateEnemyStructure(
        loaded: LoadedEntry,
        sourcesById: Map<String, WikiSourceRecord>,
    ) {
        val rel = loaded.relativePath
        val e = loaded.entry
        val en = e.enemy ?: fail(rel, e.entryId, "enemy", "kind=enemy 时缺少 enemy 对象")
        if (e.stratagem != null || e.weapon != null) {
            fail(rel, e.entryId, "kind", "enemy 条目不能携带 stratagem 或 weapon 数据")
        }
        if (en.faction !in validFactions) {
            fail(rel, e.entryId, "enemy.faction", "非法敌人阵营: ${en.faction}")
        }
        if (loaded.expectedEnemyFaction != null && en.faction != loaded.expectedEnemyFaction) {
            fail(
                rel,
                e.entryId,
                "enemy.faction",
                "条目的 faction (${en.faction}) 与所在目录 (${loaded.expectedEnemyFaction}) 不一致",
            )
        }
        if (en.sizeClass !in validEnemySizeClasses) {
            fail(rel, e.entryId, "enemy.sizeClass", "非法敌人体型等级: ${en.sizeClass}")
        }
        if (loaded.expectedEnemySizeClass != null && en.sizeClass != loaded.expectedEnemySizeClass) {
            fail(
                rel,
                e.entryId,
                "enemy.sizeClass",
                "条目的 sizeClass (${en.sizeClass}) 与所在文件 (${loaded.expectedEnemySizeClass}.json) 不一致",
            )
        }
        if (en.familyKey.isBlank()) {
            fail(rel, e.entryId, "enemy.familyKey", "familyKey 不能为空")
        }

        val poolIds = mutableSetOf<String>()
        for (pool in en.pools) {
            if (pool.poolId.isBlank() || !poolIds.add(pool.poolId)) {
                fail(rel, e.entryId, "enemy.pools.poolId", "poolId 为空或重复: ${pool.poolId}")
            }
            if (pool.labelZh.isBlank()) {
                fail(rel, e.entryId, "enemy.pools.labelZh", "血池 labelZh 不能为空")
            }
            if (pool.sourceId != null && pool.sourceId !in sourcesById) {
                fail(rel, e.entryId, "enemy.pools.sourceId", "未登记的 sourceId: ${pool.sourceId}")
            }
            validateFactInt(
                rel,
                e.entryId,
                "enemy.pools.maxHealth",
                pool.maxHealth,
                sourcesById,
                allowUnlimited = false,
            )
            if (pool.maxHealth.status == FactStatus.KNOWN && (pool.maxHealth.value ?: 0) <= 0) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.pools.maxHealth",
                    "血池 maxHealth 必须为正整数: ${pool.maxHealth.value}",
                )
            }
            pool.bleedHealth?.let { bh ->
                if (bh < 0) fail(
                    rel,
                    e.entryId,
                    "enemy.pools.bleedHealth",
                    "bleedHealth 不能为负: $bh",
                )
            }
            pool.bleedRatePerSecond?.let { br ->
                if (!br.isFinite() || br <= 0.0) {
                    fail(
                        rel,
                        e.entryId,
                        "enemy.pools.bleedRatePerSecond",
                        "bleedRatePerSecond 必须大于 0: $br",
                    )
                }
            }
        }

        val poolOverrideIds = mutableSetOf<String>()
        for (ov in en.poolOverrides) {
            if (ov.poolId.isBlank() || !poolOverrideIds.add(ov.poolId)) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.poolOverrides.poolId",
                    "poolId 为空或重复: ${ov.poolId}",
                )
            }
            if (en.extends == null) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.poolOverrides",
                    "未声明 extends 时不能使用 poolOverrides",
                )
            }
            if (ov.sourceId != null && ov.sourceId !in sourcesById) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.poolOverrides.sourceId",
                    "未登记的 sourceId: ${ov.sourceId}",
                )
            }
            ov.maxHealth?.let { mh ->
                validateFactInt(
                    rel,
                    e.entryId,
                    "enemy.poolOverrides.maxHealth",
                    mh,
                    sourcesById,
                    allowUnlimited = false,
                )
                if (mh.status == FactStatus.KNOWN && (mh.value ?: 0) <= 0) {
                    fail(
                        rel,
                        e.entryId,
                        "enemy.poolOverrides.maxHealth",
                        "血池 maxHealth 必须为正整数: ${mh.value}",
                    )
                }
            }
            ov.bleedHealth?.let { bh ->
                if (bh < 0) fail(
                    rel,
                    e.entryId,
                    "enemy.poolOverrides.bleedHealth",
                    "bleedHealth 不能为负: $bh",
                )
            }
            ov.bleedRatePerSecond?.let { br ->
                if (!br.isFinite() || br <= 0.0) {
                    fail(
                        rel,
                        e.entryId,
                        "enemy.poolOverrides.bleedRatePerSecond",
                        "bleedRatePerSecond 必须大于 0: $br",
                    )
                }
            }
        }

        val partIds = mutableSetOf<String>()
        for (part in en.parts) {
            if (part.partId.isBlank() || !partIds.add(part.partId)) {
                fail(rel, e.entryId, "enemy.parts.partId", "partId 为空或重复: ${part.partId}")
            }
            if (part.nameZh.isBlank()) {
                fail(rel, e.entryId, "enemy.parts.nameZh", "部位 nameZh 不能为空")
            }
            if (part.sourceId != null && part.sourceId !in sourcesById) {
                fail(rel, e.entryId, "enemy.parts.sourceId", "未登记的 sourceId: ${part.sourceId}")
            }
            validateFactInt(
                rel,
                e.entryId,
                "enemy.parts.armor",
                part.armor,
                sourcesById,
                allowUnlimited = false,
            )
            validateFactDouble(
                rel,
                e.entryId,
                "enemy.parts.durability",
                part.durability,
                sourcesById,
                allowUnlimited = false,
                maxInclusive = 1.0,
            )
            validateFactDouble(
                rel,
                e.entryId,
                "enemy.parts.toMainRatio",
                part.toMainRatio,
                sourcesById,
                allowUnlimited = false,
            )
            validateFactDouble(
                rel,
                e.entryId,
                "enemy.parts.explosionResistance",
                part.explosionResistance,
                sourcesById,
                allowUnlimited = false,
                maxInclusive = 1.0,
            )
            validateBreakEffects(rel, e.entryId, "enemy.parts.onBreak", part.onBreak, sourcesById)
        }
        val overrideIds = mutableSetOf<String>()
        for (ov in en.partOverrides) {
            if (ov.partId.isBlank() || !overrideIds.add(ov.partId)) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.partId",
                    "partId 为空或重复: ${ov.partId}",
                )
            }
            if (en.extends == null) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.partOverrides",
                    "未声明 extends 时不能使用 partOverrides",
                )
            }
            if (ov.sourceId != null && ov.sourceId !in sourcesById) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.sourceId",
                    "未登记的 sourceId: ${ov.sourceId}",
                )
            }
            ov.armor?.let {
                validateFactInt(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.armor",
                    it,
                    sourcesById,
                    allowUnlimited = false,
                )
            }
            ov.durability?.let {
                validateFactDouble(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.durability",
                    it,
                    sourcesById,
                    allowUnlimited = false,
                    maxInclusive = 1.0,
                )
            }
            ov.toMainRatio?.let {
                validateFactDouble(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.toMainRatio",
                    it,
                    sourcesById,
                    allowUnlimited = false,
                )
            }
            ov.explosionResistance?.let {
                validateFactDouble(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.explosionResistance",
                    it,
                    sourcesById,
                    allowUnlimited = false,
                    maxInclusive = 1.0,
                )
            }
            ov.onBreak?.let { effects ->
                validateBreakEffects(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.onBreak",
                    effects,
                    sourcesById,
                )
            }
        }
    }

    private fun validateBreakEffects(
        rel: String,
        entryId: String,
        field: String,
        effects: List<PartBreakEffectInput>,
        sourcesById: Map<String, WikiSourceRecord>,
    ) {
        for (eff in effects) {
            if (eff.effectType !in validBreakEffectTypes) {
                fail(rel, entryId, "$field.effectType", "非法破甲效果类型: ${eff.effectType}")
            }
            if (eff.descriptionZh.isBlank()) {
                fail(rel, entryId, "$field.descriptionZh", "破甲效果 descriptionZh 不能为空")
            }
            if (eff.effectType == "expose_part" && eff.targetPartId.isNullOrBlank()) {
                fail(rel, entryId, "$field.targetPartId", "expose_part 必须填写 targetPartId")
            }
            if (eff.sourceId != null && eff.sourceId !in sourcesById) {
                fail(rel, entryId, "$field.sourceId", "未登记的 sourceId: ${eff.sourceId}")
            }
        }
    }

    private fun expandEnemyPools(
        loadedEntries: List<LoadedEntry>,
        entryById: Map<String, LoadedEntry>,
    ): Pair<Map<String, String?>, Map<String, List<EnemyHealthPoolInput>>> {
        val mainPoolCache = linkedMapOf<String, String?>()
        val poolsCache = linkedMapOf<String, List<EnemyHealthPoolInput>>()

        fun resolve(entryId: String): Pair<String?, List<EnemyHealthPoolInput>> {
            poolsCache[entryId]?.let { return mainPoolCache[entryId] to it }
            val loaded = entryById.getValue(entryId)
            val en = loaded.entry.enemy!!
            val (parentMainPool, parentPools) = if (en.extends != null) {
                val (pm, pp) = resolve(en.extends)
                pm to pp.map { it.copy() }.toMutableList()
            } else {
                null to mutableListOf()
            }

            if (en.pools.isNotEmpty()) {
                val byId = parentPools.associateBy { it.poolId }.toMutableMap()
                for (pool in en.pools) {
                    byId[pool.poolId] = pool
                }
                parentPools.clear()
                parentPools.addAll(byId.values)
            }

            for (ov in en.poolOverrides) {
                val idx = parentPools.indexOfFirst { it.poolId == ov.poolId }
                if (ov.isNewPool) {
                    if (idx != -1) {
                        fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.poolOverrides",
                            "isNewPool=true 但血池已存在: ${ov.poolId}",
                        )
                    }
                    val labelZh = ov.labelZh ?: fail(
                        loaded.relativePath,
                        entryId,
                        "enemy.poolOverrides.labelZh",
                        "新增血池必须提供 labelZh: ${ov.poolId}",
                    )
                    parentPools.add(
                        EnemyHealthPoolInput(
                            poolId = ov.poolId,
                            labelZh = labelZh,
                            maxHealth = ov.maxHealth ?: WikiFactRecord.unknown(),
                            bleedHealth = ov.bleedHealth,
                            bleedRatePerSecond = ov.bleedRatePerSecond,
                            isMain = ov.isMain ?: false,
                            sourceId = ov.sourceId,
                            verifiedPatch = ov.verifiedPatch,
                        ),
                    )
                } else {
                    if (idx == -1) {
                        fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.poolOverrides.poolId",
                            "覆写目标血池在基础型中不存在: ${ov.poolId}",
                        )
                    }
                    val old = parentPools[idx]
                    parentPools[idx] = old.copy(
                        labelZh = ov.labelZh ?: old.labelZh,
                        maxHealth = ov.maxHealth ?: old.maxHealth,
                        bleedHealth = ov.bleedHealth ?: old.bleedHealth,
                        bleedRatePerSecond = ov.bleedRatePerSecond ?: old.bleedRatePerSecond,
                        isMain = ov.isMain ?: old.isMain,
                        sourceId = ov.sourceId ?: old.sourceId,
                        verifiedPatch = ov.verifiedPatch ?: old.verifiedPatch,
                    )
                }
            }

            val resolvedMainPool = en.mainPoolId ?: parentMainPool
            mainPoolCache[entryId] = resolvedMainPool
            poolsCache[entryId] = parentPools
            return resolvedMainPool to parentPools
        }

        for (loaded in loadedEntries) {
            if (loaded.entry.kind == "enemy") {
                resolve(loaded.entry.entryId)
            }
        }
        return mainPoolCache to poolsCache
    }

    /**
     * 递归解析敌人 `extends` 继承链并合并 `parts` 与 `partOverrides`，产出各敌人的完整部位清单。
     */
    private fun expandEnemyParts(
        loadedEntries: List<LoadedEntry>,
        entryById: Map<String, LoadedEntry>,
    ): Map<String, List<EnemyPartInput>> {
        val cache = linkedMapOf<String, List<EnemyPartInput>>()

        fun resolve(entryId: String): List<EnemyPartInput> {
            cache[entryId]?.let { return it }
            val loaded = entryById.getValue(entryId)
            val en = loaded.entry.enemy!!
            val baseParts = if (en.extends != null) {
                resolve(en.extends).map { it.copy() }.toMutableList()
            } else {
                mutableListOf()
            }

            if (en.parts.isNotEmpty()) {
                val byId = baseParts.associateBy { it.partId }.toMutableMap()
                for (p in en.parts) {
                    byId[p.partId] = p
                }
                baseParts.clear()
                baseParts.addAll(byId.values)
            }

            for (ov in en.partOverrides) {
                val existingIdx = baseParts.indexOfFirst { it.partId == ov.partId }
                if (ov.isNewPart) {
                    if (existingIdx != -1) {
                        fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.partOverrides",
                            "isNewPart=true 但部位已存在: ${ov.partId}",
                        )
                    }
                    val nameZh = ov.nameZh
                        ?: fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.partOverrides.nameZh",
                            "新增部位必须提供 nameZh: ${ov.partId}",
                        )
                    baseParts.add(
                        EnemyPartInput(
                            partId = ov.partId,
                            nameZh = nameZh,
                            armor = ov.armor ?: WikiFactRecord.unknown(),
                            notes = ov.notes,
                            sourceId = ov.sourceId,
                            verifiedPatch = ov.verifiedPatch,
                            healthPoolRef = ov.healthPoolRef,
                            durability = ov.durability ?: WikiFactRecord.unknown(),
                            toMainRatio = ov.toMainRatio ?: WikiFactRecord.unknown(),
                            damageCapToMain = ov.damageCapToMain ?: false,
                            explosionResistance = ov.explosionResistance
                                ?: WikiFactRecord.unknown(),
                            protectedByPartIds = ov.protectedByPartIds ?: emptyList(),
                            onBreak = ov.onBreak ?: emptyList(),
                        ),
                    )
                } else {
                    if (existingIdx == -1) {
                        fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.partOverrides.partId",
                            "覆写目标部位在基础型中不存在: ${ov.partId}",
                        )
                    }
                    val old = baseParts[existingIdx]
                    baseParts[existingIdx] = old.copy(
                        nameZh = ov.nameZh ?: old.nameZh,
                        armor = ov.armor ?: old.armor,
                        notes = ov.notes ?: old.notes,
                        sourceId = ov.sourceId ?: old.sourceId,
                        verifiedPatch = ov.verifiedPatch ?: old.verifiedPatch,
                        healthPoolRef = ov.healthPoolRef ?: old.healthPoolRef,
                        durability = ov.durability ?: old.durability,
                        toMainRatio = ov.toMainRatio ?: old.toMainRatio,
                        damageCapToMain = ov.damageCapToMain ?: old.damageCapToMain,
                        explosionResistance = ov.explosionResistance ?: old.explosionResistance,
                        protectedByPartIds = ov.protectedByPartIds ?: old.protectedByPartIds,
                        onBreak = ov.onBreak ?: old.onBreak,
                    )
                }
            }

            cache[entryId] = baseParts
            return baseParts
        }

        for (loaded in loadedEntries) {
            val entry = loaded.entry
            if (entry.kind == "enemy") {
                resolve(entry.entryId)
            }
        }
        return cache
    }

    private fun validateExpandedEnemyIntegrity(
        loadedEntries: List<LoadedEntry>,
        expandedMainPoolIdById: Map<String, String?>,
        expandedPoolsById: Map<String, List<EnemyHealthPoolInput>>,
        expandedPartsById: Map<String, List<EnemyPartInput>>,
    ) {
        for (loaded in loadedEntries) {
            val entry = loaded.entry
            if (entry.kind != "enemy") continue
            val rel = loaded.relativePath
            val entryId = entry.entryId
            val poolIds = expandedPoolsById[entryId].orEmpty().mapTo(hashSetOf()) { it.poolId }
            val mainPoolId = expandedMainPoolIdById[entryId]
            if (mainPoolId != null && mainPoolId !in poolIds) {
                fail(rel, entryId, "enemy.mainPoolId", "mainPoolId 引用的血池不存在: $mainPoolId")
            }

            val parts = expandedPartsById[entryId].orEmpty()
            val partIds = parts.mapTo(hashSetOf()) { it.partId }
            for (part in parts) {
                part.healthPoolRef?.let { ref ->
                    if (ref !in poolIds) {
                        fail(
                            rel,
                            entryId,
                            "enemy.parts.healthPoolRef",
                            "部位 ${part.partId} 引用的血池不存在: $ref",
                        )
                    }
                }
                for (protId in part.protectedByPartIds) {
                    if (protId == part.partId) {
                        fail(
                            rel,
                            entryId,
                            "enemy.parts.protectedByPartIds",
                            "部位禁止自指保护: $protId",
                        )
                    }
                    if (protId !in partIds) {
                        fail(
                            rel,
                            entryId,
                            "enemy.parts.protectedByPartIds",
                            "保护层部位不存在: $protId",
                        )
                    }
                }
                for (eff in part.onBreak) {
                    eff.targetPartId?.let { targetPartId ->
                        if (targetPartId == part.partId) {
                            fail(
                                rel,
                                entryId,
                                "enemy.parts.onBreak.targetPartId",
                                "onBreak 禁止指向自身: $targetPartId",
                            )
                        }
                        if (targetPartId !in partIds) {
                            fail(
                                rel,
                                entryId,
                                "enemy.parts.onBreak.targetPartId",
                                "onBreak 目标部位不存在: $targetPartId",
                            )
                        }
                    }
                }
            }

            checkPartCycles(
                rel,
                entryId,
                "enemy.parts.protectedByPartIds",
                parts,
            ) { it.protectedByPartIds }
            checkPartCycles(rel, entryId, "enemy.parts.onBreak", parts) { part ->
                part.onBreak.mapNotNull { it.targetPartId }
            }
        }
    }

    private fun checkPartCycles(
        rel: String,
        entryId: String,
        fieldLabel: String,
        parts: List<EnemyPartInput>,
        nextPartIds: (EnemyPartInput) -> List<String>,
    ) {
        val byId = parts.associateBy { it.partId }
        val state = hashMapOf<String, Int>()
        fun dfs(partId: String) {
            when (state[partId]) {
                1 -> fail(rel, entryId, fieldLabel, "检测到部位循环依赖: $partId")
                2 -> return
            }
            state[partId] = 1
            byId[partId]?.let { p ->
                for (next in nextPartIds(p)) {
                    dfs(next)
                }
            }
            state[partId] = 2
        }
        for (p in parts) dfs(p.partId)
    }

    private fun loadAndValidateAnatomies(
        anatomyDir: File,
        entryById: Map<String, LoadedEntry>,
        expandedPartsById: Map<String, List<EnemyPartInput>>,
        validatedAssets: Map<String, WikiAnatomyValidation.ValidatedWebpAsset>,
        sourcesById: Map<String, WikiSourceRecord>,
    ): Map<String, EnemyAnatomyFile> {
        if (!anatomyDir.exists()) return emptyMap()
        if (!anatomyDir.isDirectory) {
            fail("anatomy", "-", "dir", "anatomy 必须是目录")
        }
        val files = anatomyDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?: emptyList()
        val result = linkedMapOf<String, EnemyAnatomyFile>()
        for (file in files) {
            val rel = "anatomy/${file.name}"
            val anatomy = runCatching {
                json.decodeFromString<EnemyAnatomyFile>(file.readText(Charsets.UTF_8))
            }.getOrElse { e ->
                fail(rel, "-", "json", "解析解剖 JSON 失败: ${e.message}")
            }
            if (file.nameWithoutExtension != anatomy.enemyId) {
                fail(
                    rel,
                    anatomy.enemyId,
                    "enemyId",
                    "文件名 (${file.nameWithoutExtension}) 必须与 enemyId (${anatomy.enemyId}) 一致",
                )
            }
            val enemyLoaded = entryById[anatomy.enemyId]
                ?: fail(
                    rel,
                    anatomy.enemyId,
                    "enemyId",
                    "解剖文件关联的敌人不存在: ${anatomy.enemyId}",
                )
            if (enemyLoaded.entry.kind != "enemy") {
                fail(rel, anatomy.enemyId, "enemyId", "解剖文件只能关联 kind=enemy 条目")
            }
            if (anatomy.views.isEmpty()) {
                fail(rel, anatomy.enemyId, "views", "至少需要一个视角")
            }
            val partIds =
                expandedPartsById[anatomy.enemyId].orEmpty().mapTo(hashSetOf()) { it.partId }
            val viewIds = mutableSetOf<String>()
            for (view in anatomy.views) {
                if (view.viewId.isBlank() || !viewIds.add(view.viewId)) {
                    fail(rel, anatomy.enemyId, "views.viewId", "viewId 为空或重复: ${view.viewId}")
                }
                if (view.labelZh.isBlank()) {
                    fail(rel, anatomy.enemyId, "views.labelZh", "视角 labelZh 不能为空")
                }
                if (view.assetRevision.isBlank()) {
                    fail(rel, anatomy.enemyId, "views.assetRevision", "assetRevision 不能为空")
                }
                if (view.sourceId !in sourcesById) {
                    fail(
                        rel,
                        anatomy.enemyId,
                        "views.sourceId",
                        "未登记的 sourceId: ${view.sourceId}",
                    )
                }
                val defaultAsset = validatedAssets[view.defaultAssetKey]
                    ?: fail(
                        rel,
                        anatomy.enemyId,
                        "views.defaultAssetKey",
                        "默认解剖图片 WebP 不存在或未通过校验: ${view.defaultAssetKey}",
                    )
                if (view.width !in 1..WikiAnatomyValidation.MAX_IMAGE_WIDTH ||
                    view.height !in 1..WikiAnatomyValidation.MAX_IMAGE_HEIGHT
                ) {
                    fail(
                        rel,
                        anatomy.enemyId,
                        "views.width/height",
                        "声明尺寸 ${view.width}x${view.height} 非法",
                    )
                }
                val expectedRatio = view.width.toDouble() / view.height.toDouble()
                val defaultRatio = defaultAsset.width.toDouble() / defaultAsset.height.toDouble()
                if (abs(expectedRatio - defaultRatio) > 1e-3) {
                    fail(
                        rel,
                        anatomy.enemyId,
                        "views.width/height",
                        "声明宽高比 (${view.width}x${view.height}) 与默认 WebP 实际 (${defaultAsset.width}x${defaultAsset.height}) 不一致",
                    )
                }

                if (view.partAssets.isEmpty()) {
                    fail(rel, anatomy.enemyId, "views.partAssets", "视角至少需包含一个部位图片映射")
                }
                for ((partId, assetKey) in view.partAssets) {
                    if (partId !in partIds) {
                        fail(
                            rel,
                            anatomy.enemyId,
                            "views.partAssets",
                            "partAssets 引用的部位不存在: $partId",
                        )
                    }
                    val partAsset = validatedAssets[assetKey]
                        ?: fail(
                            rel,
                            anatomy.enemyId,
                            "views.partAssets",
                            "部位图片 WebP 不存在或未通过校验: $assetKey",
                        )
                    val partRatio = partAsset.width.toDouble() / partAsset.height.toDouble()
                    if (abs(expectedRatio - partRatio) > 1e-3) {
                        fail(
                            rel,
                            anatomy.enemyId,
                            "views.partAssets",
                            "部位图片 $assetKey 宽高比 (${partAsset.width}x${partAsset.height}) 与视角声明不一致",
                        )
                    }
                }

                view.hitMap?.let { hitMap ->
                    WikiAnatomyValidation.validateHitMap(
                        rel = rel,
                        enemyId = anatomy.enemyId,
                        viewId = view.viewId,
                        hitMap = hitMap,
                        validPartIds = partIds,
                        fail = ::fail,
                    )
                }
            }
            if (anatomy.defaultViewId !in viewIds) {
                fail(
                    rel,
                    anatomy.enemyId,
                    "defaultViewId",
                    "defaultViewId 不存在于 views 中: ${anatomy.defaultViewId}",
                )
            }
            result[anatomy.enemyId] = anatomy
        }
        return result
    }

    private fun loadAndValidateDamageRules(
        rulesFile: File,
        sourcesById: Map<String, WikiSourceRecord>,
    ): List<DamageRuleSetRecord> {
        if (!rulesFile.exists()) return emptyList()
        val rel = "rules/damage_rules.json"
        val parsed = runCatching {
            json.decodeFromString<DamageRulesFile>(rulesFile.readText(Charsets.UTF_8))
        }.getOrElse { e ->
            fail(rel, "-", "json", "解析 damage_rules.json 失败: ${e.message}")
        }
        val patches = mutableSetOf<String>()
        for (rule in parsed.ruleSets) {
            if (rule.patchVersion.isBlank() || !patches.add(rule.patchVersion)) {
                fail(
                    rel,
                    rule.patchVersion,
                    "patchVersion",
                    "patchVersion 为空或重复: ${rule.patchVersion}",
                )
            }
            if (!rule.penetrateMultiplier.isFinite() || rule.penetrateMultiplier < 0.0 ||
                !rule.equalArmorMultiplier.isFinite() || rule.equalArmorMultiplier < 0.0 ||
                !rule.ricochetMultiplier.isFinite() || rule.ricochetMultiplier < 0.0
            ) {
                fail(rel, rule.patchVersion, "multiplier", "护甲倍率必须为非负有限数")
            }
            if (rule.sourceId !in sourcesById) {
                fail(rel, rule.patchVersion, "sourceId", "未登记的 sourceId: ${rule.sourceId}")
            }
        }
        return parsed.ruleSets
    }

    private fun collectValidAttackRefs(loadedEntries: List<LoadedEntry>): Set<AttackRefRecord> {
        val refs = hashSetOf<AttackRefRecord>()
        for (loaded in loadedEntries) {
            val e = loaded.entry
            val configs =
                e.weapon?.configurations ?: e.stratagem?.weaponProfile?.configurations ?: continue
            for (cfg in configs) {
                for (atk in cfg.attacks) {
                    refs.add(AttackRefRecord(e.entryId, cfg.configId, atk.attackId))
                }
            }
        }
        return refs
    }

    private fun loadAndValidateEvidences(
        evidenceFile: File,
        entryById: Map<String, LoadedEntry>,
        expandedPartsById: Map<String, List<EnemyPartInput>>,
        validAttackRefs: Set<AttackRefRecord>,
        ruleSetByPatch: Map<String, DamageRuleSetRecord>,
        sourcesById: Map<String, WikiSourceRecord>,
    ): List<MatchEvidenceRecord> {
        if (!evidenceFile.exists()) return emptyList()
        val rel = "matchups/evidence.json"
        val parsed = runCatching {
            json.decodeFromString<MatchEvidenceFile>(evidenceFile.readText(Charsets.UTF_8))
        }.getOrElse { e ->
            fail(rel, "-", "json", "解析 evidence.json 失败: ${e.message}")
        }
        val ids = mutableSetOf<String>()
        for (ev in parsed.evidences) {
            if (ev.evidenceId.isBlank() || !ids.add(ev.evidenceId)) {
                fail(rel, ev.evidenceId, "evidenceId", "evidenceId 为空或重复: ${ev.evidenceId}")
            }
            if (entryById[ev.enemyId]?.entry?.kind != "enemy") {
                fail(rel, ev.evidenceId, "enemyId", "目标敌人不存在: ${ev.enemyId}")
            }
            val partIds = expandedPartsById[ev.enemyId].orEmpty().map { it.partId }
            if (ev.partId !in partIds) {
                fail(rel, ev.evidenceId, "partId", "敌人 ${ev.enemyId} 不存在部位 ${ev.partId}")
            }
            if (ev.partState !in validPartStates) {
                fail(rel, ev.evidenceId, "partState", "非法 partState: ${ev.partState}")
            }
            if (ev.attackRef !in validAttackRefs) {
                fail(rel, ev.evidenceId, "attackRef", "引用的攻击不存在: ${ev.attackRef}")
            }
            if (ev.verdict !in validMatchVerdicts) {
                fail(rel, ev.evidenceId, "verdict", "非法 verdict: ${ev.verdict}")
            }
            if (ev.summaryZh.isBlank()) {
                fail(rel, ev.evidenceId, "summaryZh", "summaryZh 不能为空")
            }
            if (ev.patchVersion.isBlank() || ev.patchVersion !in ruleSetByPatch) {
                fail(
                    rel,
                    ev.evidenceId,
                    "patchVersion",
                    "patchVersion 未在 damage_rules.json 中定义: ${ev.patchVersion}",
                )
            }
            if (ev.sourceId !in sourcesById) {
                fail(rel, ev.evidenceId, "sourceId", "未登记的 sourceId: ${ev.sourceId}")
            }
            validateFactInt(
                rel,
                ev.evidenceId,
                "shotsToBreak",
                ev.shotsToBreak,
                sourcesById,
                allowUnlimited = false,
            )
            validateFactInt(
                rel,
                ev.evidenceId,
                "shotsToKill",
                ev.shotsToKill,
                sourcesById,
                allowUnlimited = false,
            )
        }
        return parsed.evidences
    }

    private fun loadAndValidateRecommendations(
        recFile: File,
        entryById: Map<String, LoadedEntry>,
        expandedPartsById: Map<String, List<EnemyPartInput>>,
        validAttackRefs: Set<AttackRefRecord>,
        validEvidenceIds: Set<String>,
        ruleSetByPatch: Map<String, DamageRuleSetRecord>,
        sourcesById: Map<String, WikiSourceRecord>,
    ): List<PartRecommendationRecord> {
        if (!recFile.exists()) return emptyList()
        val rel = "matchups/recommendations.json"
        val parsed = runCatching {
            json.decodeFromString<PartRecommendationsFile>(recFile.readText(Charsets.UTF_8))
        }.getOrElse { e ->
            fail(rel, "-", "json", "解析 recommendations.json 失败: ${e.message}")
        }
        val ids = mutableSetOf<String>()
        for (rec in parsed.recommendations) {
            if (rec.recId.isBlank() || !ids.add(rec.recId)) {
                fail(rel, rec.recId, "recId", "recId 为空或重复: ${rec.recId}")
            }
            if (entryById[rec.enemyId]?.entry?.kind != "enemy") {
                fail(rel, rec.recId, "enemyId", "目标敌人不存在: ${rec.enemyId}")
            }
            val partIds = expandedPartsById[rec.enemyId].orEmpty().map { it.partId }
            if (rec.partId !in partIds) {
                fail(rel, rec.recId, "partId", "敌人 ${rec.enemyId} 不存在部位 ${rec.partId}")
            }
            if (rec.partState !in validPartStates) {
                fail(rel, rec.recId, "partState", "非法 partState: ${rec.partState}")
            }
            if (rec.goal !in validRecommendationGoals) {
                fail(rel, rec.recId, "goal", "非法推荐目的 goal: ${rec.goal}")
            }
            if (rec.attackRef !in validAttackRefs) {
                fail(rel, rec.recId, "attackRef", "引用的攻击不存在: ${rec.attackRef}")
            }
            if (rec.priorityOrder <= 0) {
                fail(
                    rel,
                    rec.recId,
                    "priorityOrder",
                    "priorityOrder 必须大于 0: ${rec.priorityOrder}",
                )
            }
            if (rec.reasonZh.isBlank()) {
                fail(rel, rec.recId, "reasonZh", "reasonZh 不能为空")
            }
            for (evId in rec.evidenceIds) {
                if (evId !in validEvidenceIds) {
                    fail(rel, rec.recId, "evidenceIds", "引用的实测记录不存在: $evId")
                }
            }
            if (rec.patchVersion.isBlank() || rec.patchVersion !in ruleSetByPatch) {
                fail(
                    rel,
                    rec.recId,
                    "patchVersion",
                    "patchVersion 未在 damage_rules.json 中定义: ${rec.patchVersion}",
                )
            }
            if (rec.sourceId !in sourcesById) {
                fail(rel, rec.recId, "sourceId", "未登记的 sourceId: ${rec.sourceId}")
            }
        }
        return parsed.recommendations
    }

    /**
     * 校验浮点型事实字段的来源合法性及四态（`known`/`unknown`/`not_applicable`/`unlimited`）与 `value` 的互斥约束：
     * 仅 `known` 状态允许且必须携带非负 `value`，`unlimited` 受 `allowUnlimited` 控制且不允许携带 `value`，
     * 其余状态 `value` 必须为 `null`。
     */
    private fun validateFactDouble(
        rel: String,
        entryId: String,
        field: String,
        fact: WikiFactRecord<Double>,
        sourcesById: Map<String, WikiSourceRecord>,
        allowUnlimited: Boolean,
        maxInclusive: Double? = null,
    ) {
        if (fact.sourceId != null && fact.sourceId !in sourcesById) {
            fail(rel, entryId, "$field.sourceId", "未登记的 sourceId: ${fact.sourceId}")
        }
        when (fact.status) {
            FactStatus.KNOWN -> {
                val v = fact.value ?: fail(rel, entryId, field, "status=known 时 value 不能为空")
                if (!v.isFinite() || v < 0.0) fail(rel, entryId, field, "数值不能为负或非有限: $v")
                if (maxInclusive != null && v > maxInclusive) {
                    fail(rel, entryId, field, "数值 ($v) 不能大于 $maxInclusive")
                }
            }

            FactStatus.UNLIMITED -> {
                if (!allowUnlimited) fail(rel, entryId, field, "该字段不允许 status=unlimited")
                if (fact.value != null) fail(
                    rel,
                    entryId,
                    field,
                    "status=unlimited 时 value 必须为 null",
                )
            }

            FactStatus.UNKNOWN, FactStatus.NOT_APPLICABLE -> {
                if (fact.value != null) fail(
                    rel,
                    entryId,
                    field,
                    "非 known 状态时 value 必须为 null",
                )
            }
        }
    }

    /**
     * 校验整型事实字段的来源合法性及四态（`known`/`unknown`/`not_applicable`/`unlimited`）与 `value` 的互斥约束：
     * 仅 `known` 状态允许且必须携带非负 `value`，`unlimited` 受 `allowUnlimited` 控制且不允许携带 `value`，
     * 其余状态 `value` 必须为 `null`。
     */
    private fun validateFactInt(
        rel: String,
        entryId: String,
        field: String,
        fact: WikiFactRecord<Int>,
        sourcesById: Map<String, WikiSourceRecord>,
        allowUnlimited: Boolean,
    ) {
        if (fact.sourceId != null && fact.sourceId !in sourcesById) {
            fail(rel, entryId, "$field.sourceId", "未登记的 sourceId: ${fact.sourceId}")
        }
        when (fact.status) {
            FactStatus.KNOWN -> {
                val v = fact.value ?: fail(rel, entryId, field, "status=known 时 value 不能为空")
                if (v < 0) fail(rel, entryId, field, "数值不能为负: $v")
            }

            FactStatus.UNLIMITED -> {
                if (!allowUnlimited) fail(rel, entryId, field, "该字段不允许 status=unlimited")
                if (fact.value != null) fail(
                    rel,
                    entryId,
                    field,
                    "status=unlimited 时 value 必须为 null",
                )
            }

            FactStatus.UNKNOWN, FactStatus.NOT_APPLICABLE -> {
                if (fact.value != null) fail(
                    rel,
                    entryId,
                    field,
                    "非 known 状态时 value 必须为 null",
                )
            }
        }
    }

    /**
     * 使用三色标记 DFS 检测有向引用图中的循环依赖，发现环时立即抛出带上下文的校验异常。
     */
    private fun checkCycles(
        entries: List<LoadedEntry>,
        fieldLabel: String,
        nextIds: (WikiEntryInput) -> List<String>,
    ) {
        val byId = entries.associateBy { it.entry.entryId }
        val state = hashMapOf<String, Int>() // 0 = 未访问，1 = 访问中（当前递归栈），2 = 已完成

        fun dfs(id: String, relPath: String) {
            when (state[id]) {
                1 -> fail(relPath, id, fieldLabel, "检测到循环引用: $id")
                2 -> return
            }
            state[id] = 1
            val current = byId[id]
            if (current != null) {
                for (next in nextIds(current.entry)) {
                    dfs(next, current.relativePath)
                }
            }
            state[id] = 2
        }

        for (loaded in entries) {
            dfs(loaded.entry.entryId, loaded.relativePath)
        }
    }

    private fun fail(file: String, entryId: String, field: String, reason: String): Nothing {
        throw GradleException("[Wiki校验失败] 文件=$file entryId=$entryId 字段=$field: $reason")
    }
}
