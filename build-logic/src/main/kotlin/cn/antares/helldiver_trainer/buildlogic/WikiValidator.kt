package cn.antares.helldiver_trainer.buildlogic

import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import java.io.File

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
    private val validDirections = setOf("UP", "RIGHT", "DOWN", "LEFT")
    private val validWeaponSlots = setOf("primary", "secondary", "throwable")
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

        // 结合继承链与局部覆写展开每个敌人的最终部位列表
        val expandedPartsById = expandEnemyParts(loadedEntries, entryById)

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

        return ValidatedContent(
            sourcesById = sourcesById,
            entries = loadedEntries,
            expandedEnemyPartsById = expandedPartsById,
            availableIconKeys = availableIconKeys,
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
    }

    /**
     * 校验 `kind=weapon` 条目：槽位/子类与目录路径一致性、至少一套有效配置、
     * 配置与攻击方式 ID 唯一性，以及弹匣/射速/伤害/穿甲等数值事实约束。
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
        if (w.configurations.isEmpty()) {
            fail(rel, e.entryId, "weapon.configurations", "至少需要一套武器配置")
        }
        val configIds = mutableSetOf<String>()
        for ((configId, labelZh, _, magazineCapacity, fireRateRpm, _, attacks) in w.configurations) {
            if (configId.isBlank() || !configIds.add(configId)) {
                fail(
                    rel,
                    e.entryId,
                    "weapon.configurations.configId",
                    "configId 为空或重复: $configId",
                )
            }
            if (labelZh.isBlank()) {
                fail(rel, e.entryId, "weapon.configurations.labelZh", "labelZh 不能为空")
            }
            validateFactInt(
                rel, e.entryId, "weapon.magazineCapacity",
                magazineCapacity, sourcesById, allowUnlimited = true,
            )
            validateFactInt(
                rel, e.entryId, "weapon.fireRateRpm",
                fireRateRpm, sourcesById, allowUnlimited = false,
            )
            val attackIds = mutableSetOf<String>()
            for ((attackId, nameZh, damage, armorPenetration) in attacks) {
                if (attackId.isBlank() || !attackIds.add(attackId)) {
                    fail(
                        rel,
                        e.entryId,
                        "weapon.attacks.attackId",
                        "attackId 为空或重复: $attackId",
                    )
                }
                if (nameZh.isBlank()) {
                    fail(rel, e.entryId, "weapon.attacks.nameZh", "攻击方式 nameZh 不能为空")
                }
                validateFactInt(
                    rel, e.entryId, "weapon.attacks.damage",
                    damage, sourcesById, allowUnlimited = false,
                )
                validateFactInt(
                    rel, e.entryId, "weapon.attacks.armorPenetration",
                    armorPenetration, sourcesById, allowUnlimited = false,
                )
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
        val partIds = mutableSetOf<String>()
        for ((partId, nameZh, armor, _, sourceId) in en.parts) {
            if (partId.isBlank() || !partIds.add(partId)) {
                fail(rel, e.entryId, "enemy.parts.partId", "partId 为空或重复: $partId")
            }
            if (nameZh.isBlank()) {
                fail(rel, e.entryId, "enemy.parts.nameZh", "部位 nameZh 不能为空")
            }
            if (sourceId != null && sourceId !in sourcesById) {
                fail(rel, e.entryId, "enemy.parts.sourceId", "未登记的 sourceId: $sourceId")
            }
            validateFactInt(
                rel,
                e.entryId,
                "enemy.parts.armor",
                armor,
                sourcesById,
                allowUnlimited = false,
            )
        }
        val overrideIds = mutableSetOf<String>()
        for ((partId, _, armor, _, sourceId) in en.partOverrides) {
            if (partId.isBlank() || !overrideIds.add(partId)) {
                fail(rel, e.entryId, "enemy.partOverrides.partId", "partId 为空或重复: $partId")
            }
            if (en.extends == null) {
                fail(
                    rel,
                    e.entryId,
                    "enemy.partOverrides",
                    "未声明 extends 时不能使用 partOverrides",
                )
            }
            if (sourceId != null && sourceId !in sourcesById) {
                fail(rel, e.entryId, "enemy.partOverrides.sourceId", "未登记的 sourceId: $sourceId")
            }
            armor?.let {
                validateFactInt(
                    rel,
                    e.entryId,
                    "enemy.partOverrides.armor",
                    it,
                    sourcesById,
                    allowUnlimited = false,
                )
            }
        }
    }

    /**
     * 递归解析敌人 `extends` 继承链并合并 `parts` 与 `partOverrides`，产出各敌人的完整部位清单。
     *
     * 展开规则（配合缓存避免重复解析公共祖先）：
     * 1. 若声明了 `extends`，先递归拷贝基础型的已展开部位列表；
     * 2. 若当前条目自带 `parts`，按 `partId` 替换或追加到基础列表；
     * 3. 依次应用 `partOverrides`：`isNewPart = true` 要求部位尚不存在且必须提供 `nameZh`，
     *    `isNewPart = false` 则要求目标部位存在并仅覆盖非空字段（未提供字段沿用基础型）。
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

            for ((partId, nameZh1, armor, notes, sourceId, verifiedPatch, isNewPart) in en.partOverrides) {
                val existingIdx = baseParts.indexOfFirst { it.partId == partId }
                if (isNewPart) {
                    if (existingIdx != -1) {
                        fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.partOverrides",
                            "isNewPart=true 但部位已存在: $partId",
                        )
                    }
                    val nameZh = nameZh1
                        ?: fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.partOverrides.nameZh",
                            "新增部位必须提供 nameZh: $partId",
                        )
                    baseParts.add(
                        EnemyPartInput(
                            partId = partId,
                            nameZh = nameZh,
                            armor = armor ?: WikiFactRecord.unknown(),
                            notes = notes,
                            sourceId = sourceId,
                            verifiedPatch = verifiedPatch,
                        ),
                    )
                } else {
                    if (existingIdx == -1) {
                        fail(
                            loaded.relativePath,
                            entryId,
                            "enemy.partOverrides.partId",
                            "覆写目标部位在基础型中不存在: $partId",
                        )
                    }
                    val old = baseParts[existingIdx]
                    baseParts[existingIdx] = old.copy(
                        nameZh = nameZh1 ?: old.nameZh,
                        armor = armor ?: old.armor,
                        notes = notes ?: old.notes,
                        sourceId = sourceId ?: old.sourceId,
                        verifiedPatch = verifiedPatch ?: old.verifiedPatch,
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
    ) {
        if (fact.sourceId != null && fact.sourceId !in sourcesById) {
            fail(rel, entryId, "$field.sourceId", "未登记的 sourceId: ${fact.sourceId}")
        }
        when (fact.status) {
            FactStatus.KNOWN -> {
                val v = fact.value ?: fail(rel, entryId, field, "status=known 时 value 不能为空")
                if (v < 0.0) fail(rel, entryId, field, "数值不能为负: $v")
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
