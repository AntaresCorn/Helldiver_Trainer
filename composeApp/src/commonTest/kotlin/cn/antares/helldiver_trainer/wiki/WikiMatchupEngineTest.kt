package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.matchup.WikiMatchupEngine
import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.ConflictStatus
import cn.antares.helldiver_trainer.data.wiki.model.DamageComponentKind
import cn.antares.helldiver_trainer.data.wiki.model.DamageRuleSet
import cn.antares.helldiver_trainer.data.wiki.model.EnemyDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyFaction
import cn.antares.helldiver_trainer.data.wiki.model.EnemyHealthPoolDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.EnemySizeClass
import cn.antares.helldiver_trainer.data.wiki.model.MatchEvidence
import cn.antares.helldiver_trainer.data.wiki.model.MatchVerdict
import cn.antares.helldiver_trainer.data.wiki.model.PartBreakEffectDetail
import cn.antares.helldiver_trainer.data.wiki.model.PartBreakEffectType
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendation
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.WeaponAttackDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponClass
import cn.antares.helldiver_trainer.data.wiki.model.WeaponConfigDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponDamageComponentDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponSlot
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiFact
import cn.antares.helldiver_trainer.data.wiki.model.WikiFactStatus
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WikiMatchupEngineTest {

    private val defaultRules = DamageRuleSet(
        patchVersion = "01.001.200",
        penetrateMultiplier = 1.0,
        equalArmorMultiplier = 0.65,
        ricochetMultiplier = 0.0,
        sourceId = "src_rules",
    )

    private fun <T> known(value: T): WikiFact<T> =
        WikiFact(status = WikiFactStatus.KNOWN, value = value)

    private fun sampleEnemy(): WikiEntryDetail = WikiEntryDetail(
        entryId = "hd2.enemy.charger",
        kind = WikiKind.ENEMY,
        nameZh = "强袭虫",
        sourceId = "src_wiki",
        verifiedPatch = "01.001.200",
        enemy = EnemyDetail(
            faction = EnemyFaction.TERMINID,
            sizeClass = EnemySizeClass.HEAVY,
            familyKey = "charger",
            mainPoolId = "main",
            pools = listOf(
                EnemyHealthPoolDetail(
                    poolId = "head_pool",
                    labelZh = "头部血池",
                    maxHealth = known(1200),
                ),
                EnemyHealthPoolDetail(
                    poolId = "leg_inner_pool",
                    labelZh = "前腿内肉血池",
                    maxHealth = known(500),
                ),
            ),
            parts = listOf(
                EnemyPartDetail(
                    partId = "head",
                    nameZh = "头部",
                    armor = known(4),
                    healthPoolRef = "head_pool",
                    durability = known(0.5),
                    toMainRatio = known(1.0),
                    explosionResistance = known(0.25),
                    onBreak = listOf(
                        PartBreakEffectDetail(
                            effectType = PartBreakEffectType.FATAL,
                            descriptionZh = "击破头部立即致死",
                        ),
                    ),
                ),
                EnemyPartDetail(
                    partId = "leg_armor",
                    nameZh = "前腿外甲",
                    armor = known(4),
                    healthPoolRef = "leg_inner_pool",
                    durability = known(0.0),
                    toMainRatio = known(0.0),
                ),
                EnemyPartDetail(
                    partId = "leg_flesh",
                    nameZh = "前腿内肉",
                    armor = known(2),
                    healthPoolRef = "leg_inner_pool",
                    durability = known(0.2),
                    toMainRatio = known(0.5),
                    protectedByPartIds = listOf("leg_armor"),
                ),
            ),
        ),
    )

    private fun weaponEntry(
        entryId: String,
        nameZh: String,
        attack: WeaponAttackDetail,
        configId: String = "base",
    ): WikiEntryDetail = WikiEntryDetail(
        entryId = entryId,
        kind = WikiKind.WEAPON,
        nameZh = nameZh,
        sourceId = "src_wiki",
        weapon = WeaponDetail(
            slot = WeaponSlot.PRIMARY,
            weaponClass = WeaponClass.EXPLOSIVE,
            configurations = listOf(
                WeaponConfigDetail(
                    configId = configId,
                    labelZh = "默认配置",
                    attacks = listOf(attack),
                ),
            ),
        ),
    )

    @Test
    fun `intact protected part returns BLOCKED with blocker names`() {
        val enemy = sampleEnemy()
        val attack = WeaponAttackDetail(
            attackId = "shot",
            nameZh = "普通弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "c1",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(100),
                    durableDamage = known(50),
                    armorPenetration = known(5),
                ),
            ),
        )
        val owner = weaponEntry("hd2.weapon.test", "测试步枪", attack)

        val eval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "leg_flesh",
            partState = EnemyPartState.INTACT,
            ownerEntry = owner,
            configId = "base",
            attackId = "shot",
            attack = attack,
            ruleSet = defaultRules,
            evidences = emptyList(),
            targetPatch = "01.001.200",
        )

        assertEquals(MatchVerdict.BLOCKED, eval.ruleResult.overallVerdict)
        assertEquals(listOf("leg_armor"), eval.ruleResult.blockedByPartIds)
        assertEquals(listOf("前腿外甲"), eval.ruleResult.blockedByPartNamesZh)
        assertTrue(eval.ruleResult.assumptions.first().contains("前腿外甲"))
    }

    @Test
    fun `broken state bypasses protectedByPartIds and evaluates damage`() {
        val enemy = sampleEnemy()
        val attack = WeaponAttackDetail(
            attackId = "shot",
            nameZh = "普通弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "c1",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(100),
                    durableDamage = known(50),
                    armorPenetration = known(3),
                ),
            ),
        )
        val owner = weaponEntry("hd2.weapon.test", "测试步枪", attack)

        val eval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "leg_flesh",
            partState = EnemyPartState.BROKEN,
            ownerEntry = owner,
            configId = "base",
            attackId = "shot",
            attack = attack,
            ruleSet = defaultRules,
            evidences = emptyList(),
            targetPatch = "01.001.200",
        )

        assertEquals(MatchVerdict.PENETRATES, eval.ruleResult.overallVerdict)
        // (0.8 * 100 + 0.2 * 50) * 1.0 = 90.0
        assertEquals(90.0, eval.ruleResult.totalPartDamagePerShot)
        assertEquals(45.0, eval.ruleResult.totalMainDamagePerShot)
        assertEquals(6, eval.ruleResult.estimatedShotsToBreak)
        assertNull(eval.ruleResult.estimatedShotsToKill)
    }

    @Test
    fun `phase 1 attack fallback wraps direct component with unknown durableDamage`() {
        val enemy = sampleEnemy()
        val attack = WeaponAttackDetail(
            attackId = "legacy",
            nameZh = "旧版单发",
            damage = known(200),
            armorPenetration = known(5),
        )
        val owner = weaponEntry("hd2.weapon.legacy", "旧版武器", attack)

        val eval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = owner,
            configId = "base",
            attackId = "legacy",
            attack = attack,
            ruleSet = defaultRules,
            evidences = emptyList(),
            targetPatch = "01.001.200",
        )

        assertEquals(MatchVerdict.PENETRATES, eval.ruleResult.overallVerdict)
        assertEquals(1, eval.ruleResult.componentResults.size)
        assertNull(eval.ruleResult.totalPartDamagePerShot)
        assertNull(eval.ruleResult.estimatedShotsToBreak)
        assertNull(eval.ruleResult.estimatedShotsToKill)
    }

    @Test
    fun `penetrate equal ricochet and explosive resistance calculations`() {
        val enemy = sampleEnemy()

        // AP 5 > AV 4, direct (600, 400) + explosive (400, 400)
        // direct = (0.5 * 600 + 0.5 * 400) * 1.0 = 500
        // explosive = (0.5 * 400 + 0.5 * 400) * 1.0 * (1 - 0.25) = 300
        // total = 800, head maxHealth = 1200 -> ceil(1200 / 800) = 2, FATAL -> kill = 2
        val rocket = WeaponAttackDetail(
            attackId = "rocket",
            nameZh = "反坦克火箭",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(600),
                    durableDamage = known(400),
                    armorPenetration = known(5),
                ),
                WeaponDamageComponentDetail(
                    componentId = "e",
                    labelZh = "爆炸",
                    componentKind = DamageComponentKind.EXPLOSIVE,
                    normalDamage = known(400),
                    durableDamage = known(400),
                    armorPenetration = known(5),
                ),
                WeaponDamageComponentDetail(
                    componentId = "s",
                    labelZh = "震荡",
                    componentKind = DamageComponentKind.STATUS,
                ),
            ),
        )
        val rocketEval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = weaponEntry("hd2.weapon.rr", "无后坐力炮", rocket),
            configId = "base",
            attackId = "rocket",
            attack = rocket,
            ruleSet = defaultRules,
            evidences = emptyList(),
            targetPatch = "01.001.200",
        )
        assertEquals(MatchVerdict.PENETRATES, rocketEval.ruleResult.overallVerdict)
        assertEquals(800.0, rocketEval.ruleResult.totalPartDamagePerShot)
        assertEquals(2, rocketEval.ruleResult.estimatedShotsToBreak)
        assertEquals(2, rocketEval.ruleResult.estimatedShotsToKill)

        // AP 4 == AV 4 -> REDUCED (0.65)
        val equalAttack = WeaponAttackDetail(
            attackId = "ac",
            nameZh = "机炮弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(200),
                    durableDamage = known(200),
                    armorPenetration = known(4),
                ),
            ),
        )
        val equalEval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = weaponEntry("hd2.weapon.ac", "机炮", equalAttack),
            configId = "base",
            attackId = "ac",
            attack = equalAttack,
            ruleSet = defaultRules,
            evidences = emptyList(),
            targetPatch = "01.001.200",
        )
        assertEquals(MatchVerdict.REDUCED, equalEval.ruleResult.overallVerdict)
        assertEquals(130.0, equalEval.ruleResult.totalPartDamagePerShot)

        // AP 2 < AV 4 -> RICOCHET (0.0)
        val lightAttack = WeaponAttackDetail(
            attackId = "smg",
            nameZh = "轻甲弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(100),
                    durableDamage = known(20),
                    armorPenetration = known(2),
                ),
            ),
        )
        val lightEval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = weaponEntry("hd2.weapon.smg", "冲锋枪", lightAttack),
            configId = "base",
            attackId = "smg",
            attack = lightAttack,
            ruleSet = defaultRules,
            evidences = emptyList(),
            targetPatch = "01.001.200",
        )
        assertEquals(MatchVerdict.RICOCHET, lightEval.ruleResult.overallVerdict)
        assertEquals(0.0, lightEval.ruleResult.totalPartDamagePerShot)
        assertNull(lightEval.ruleResult.estimatedShotsToBreak)
    }

    @Test
    fun `missing non-status component fields nullify totals`() {
        val enemy = sampleEnemy()
        val partialAttack = WeaponAttackDetail(
            attackId = "partial",
            nameZh = "部分未知",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(200),
                    durableDamage = known(100),
                    armorPenetration = known(5),
                ),
                WeaponDamageComponentDetail(
                    componentId = "e",
                    labelZh = "爆炸",
                    componentKind = DamageComponentKind.EXPLOSIVE,
                    normalDamage = WikiFact.unknown(),
                    durableDamage = known(100),
                    armorPenetration = known(5),
                ),
            ),
        )
        val eval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = weaponEntry("hd2.weapon.partial", "未知武器", partialAttack),
            configId = "base",
            attackId = "partial",
            attack = partialAttack,
            ruleSet = defaultRules,
            evidences = emptyList(),
            targetPatch = "01.001.200",
        )

        assertEquals(MatchVerdict.PENETRATES, eval.ruleResult.overallVerdict)
        assertNull(eval.ruleResult.totalPartDamagePerShot)
        assertNull(eval.ruleResult.estimatedShotsToBreak)
    }

    @Test
    fun `evidence conflict detection ignores historical patch and flags matched conflicts`() {
        val enemy = sampleEnemy()
        val lightAttack = WeaponAttackDetail(
            attackId = "light",
            nameZh = "轻甲弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(100),
                    durableDamage = known(20),
                    armorPenetration = known(2),
                ),
            ),
        )
        val owner = weaponEntry("hd2.weapon.light", "轻甲武器", lightAttack)
        val ref = AttackRef("hd2.weapon.light", "base", "light")

        val oldPatchEvidence = MatchEvidence(
            evidenceId = "ev_old",
            enemyId = "hd2.enemy.charger",
            partId = "head",
            partState = EnemyPartState.INTACT,
            attackRef = ref,
            verdict = MatchVerdict.KILLS,
            summaryZh = "旧版本实测",
            patchVersion = "01.000.100",
            sourceId = "src_test",
        )
        val currentConflictEvidence = MatchEvidence(
            evidenceId = "ev_new",
            enemyId = "hd2.enemy.charger",
            partId = "head",
            partState = EnemyPartState.INTACT,
            attackRef = ref,
            verdict = MatchVerdict.PENETRATES,
            summaryZh = "当前版本实测冲突",
            patchVersion = "01.001.200",
            sourceId = "src_test",
        )

        // Only historical evidence -> no conflict
        val noConflictEval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = owner,
            configId = "base",
            attackId = "light",
            attack = lightAttack,
            ruleSet = defaultRules,
            evidences = listOf(oldPatchEvidence),
            targetPatch = "01.001.200",
        )
        assertEquals(ConflictStatus.NONE, noConflictEval.conflictStatus)
        assertEquals(0, noConflictEval.matchedEvidences.size)
        assertEquals(1, noConflictEval.historicalEvidences.size)

        // Matched evidence with directional conflict -> NEEDS_REVIEW
        val conflictEval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = owner,
            configId = "base",
            attackId = "light",
            attack = lightAttack,
            ruleSet = defaultRules,
            evidences = listOf(oldPatchEvidence, currentConflictEvidence),
            targetPatch = "01.001.200",
        )
        assertEquals(ConflictStatus.NEEDS_REVIEW, conflictEval.conflictStatus)
        assertNotNull(conflictEval.conflictReasonZh)
        assertEquals(1, conflictEval.matchedEvidences.size)
        assertEquals(1, conflictEval.historicalEvidences.size)
    }

    @Test
    fun `shots conflict flags NEEDS_REVIEW when difference exceeds tolerance`() {
        val enemy = sampleEnemy()
        // head = 1200, damage = 600 -> 2 shots
        val heavyAttack = WeaponAttackDetail(
            attackId = "heavy",
            nameZh = "重击",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(600),
                    durableDamage = known(600),
                    armorPenetration = known(5),
                ),
            ),
        )
        val owner = weaponEntry("hd2.weapon.heavy", "重武器", heavyAttack)
        val ref = AttackRef("hd2.weapon.heavy", "base", "heavy")
        // evShots = 5 -> threshold = max(1, ceil(2.5)) = 3 -> |2 - 5| = 3 (not > 3 -> OK)
        // evShots = 1 -> threshold = max(1, ceil(0.5)) = 1 -> |2 - 1| = 1 (not > 1 -> OK)
        // evShots = 6 -> threshold = max(1, ceil(3.0)) = 3 -> |2 - 6| = 4 > 3 -> NEEDS_REVIEW
        val conflictingShotsEvidence = MatchEvidence(
            evidenceId = "ev_shots",
            enemyId = "hd2.enemy.charger",
            partId = "head",
            partState = EnemyPartState.INTACT,
            attackRef = ref,
            verdict = MatchVerdict.PENETRATES,
            shotsToBreak = known(6),
            summaryZh = "实测 6 发击破",
            patchVersion = "01.001.200",
            sourceId = "src_test",
        )

        val eval = WikiMatchupEngine.evaluateMatchup(
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            ownerEntry = owner,
            configId = "base",
            attackId = "heavy",
            attack = heavyAttack,
            ruleSet = defaultRules,
            evidences = listOf(conflictingShotsEvidence),
            targetPatch = "01.001.200",
        )
        assertEquals(ConflictStatus.NEEDS_REVIEW, eval.conflictStatus)
    }

    @Test
    fun `recommendForPart handles blocked part and sorts curated and other viable weapons`() {
        val enemy = sampleEnemy()

        val blockedResult = WikiMatchupEngine.recommendForPart(
            enemyEntryId = enemy.entryId,
            enemyDetail = enemy,
            partId = "leg_flesh",
            partState = EnemyPartState.INTACT,
            goal = RecommendationGoal.ATTACK_EXPOSED,
            targetPatch = "01.001.200",
            ruleSet = defaultRules,
            evidences = emptyList(),
            recommendations = emptyList(),
            attackCatalog = emptyList(),
        )
        assertEquals(listOf("leg_armor"), blockedResult.blockedByPartIds)
        assertNotNull(blockedResult.blockedNoticeZh)
        assertTrue(blockedResult.curatedRecommendations.isEmpty())
        assertTrue(blockedResult.otherViableWeapons.isEmpty())

        val rrAttack = WeaponAttackDetail(
            attackId = "rr_shot",
            nameZh = "反坦克弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(1200),
                    durableDamage = known(1200),
                    armorPenetration = known(5),
                ),
            ),
        )
        val acAttack = WeaponAttackDetail(
            attackId = "ac_shot",
            nameZh = "机炮弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(260),
                    durableDamage = known(260),
                    armorPenetration = known(4),
                ),
            ),
        )
        val railAttack = WeaponAttackDetail(
            attackId = "rail_shot",
            nameZh = "磁轨弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(600),
                    durableDamage = known(600),
                    armorPenetration = known(5),
                ),
            ),
        )
        val lightAttack = WeaponAttackDetail(
            attackId = "smg_shot",
            nameZh = "轻甲弹",
            components = listOf(
                WeaponDamageComponentDetail(
                    componentId = "d",
                    labelZh = "直击",
                    componentKind = DamageComponentKind.DIRECT,
                    normalDamage = known(80),
                    durableDamage = known(20),
                    armorPenetration = known(2),
                ),
            ),
        )

        val rrOwner = weaponEntry("hd2.weapon.rr", "无后坐力炮", rrAttack)
        val acOwner = weaponEntry("hd2.weapon.ac", "机炮", acAttack)
        val railOwner = weaponEntry("hd2.weapon.rail", "磁轨炮", railAttack)
        val lightOwner = weaponEntry("hd2.weapon.smg", "冲锋枪", lightAttack)

        val catalog = listOf(
            WikiMatchupEngine.AttackCatalogItem(
                option = AttackOption(
                    ref = AttackRef("hd2.weapon.rr", "base", "rr_shot"),
                    ownerNameZh = "无后坐力炮",
                    ownerKind = WikiKind.WEAPON,
                    configLabelZh = "默认配置",
                    attackNameZh = "反坦克弹",
                ),
                ownerEntry = rrOwner,
                attack = rrAttack,
            ),
            WikiMatchupEngine.AttackCatalogItem(
                option = AttackOption(
                    ref = AttackRef("hd2.weapon.ac", "base", "ac_shot"),
                    ownerNameZh = "机炮",
                    ownerKind = WikiKind.WEAPON,
                    configLabelZh = "默认配置",
                    attackNameZh = "机炮弹",
                ),
                ownerEntry = acOwner,
                attack = acAttack,
            ),
            WikiMatchupEngine.AttackCatalogItem(
                option = AttackOption(
                    ref = AttackRef("hd2.weapon.rail", "base", "rail_shot"),
                    ownerNameZh = "磁轨炮",
                    ownerKind = WikiKind.WEAPON,
                    configLabelZh = "默认配置",
                    attackNameZh = "磁轨弹",
                ),
                ownerEntry = railOwner,
                attack = railAttack,
            ),
            WikiMatchupEngine.AttackCatalogItem(
                option = AttackOption(
                    ref = AttackRef("hd2.weapon.smg", "base", "smg_shot"),
                    ownerNameZh = "冲锋枪",
                    ownerKind = WikiKind.WEAPON,
                    configLabelZh = "默认配置",
                    attackNameZh = "轻甲弹",
                ),
                ownerEntry = lightOwner,
                attack = lightAttack,
            ),
        )

        val rec = PartRecommendation(
            recId = "rec_1",
            enemyId = "hd2.enemy.charger",
            partId = "head",
            partState = EnemyPartState.INTACT,
            goal = RecommendationGoal.KILL,
            attackRef = AttackRef("hd2.weapon.rr", "base", "rr_shot"),
            priorityOrder = 1,
            reasonZh = "一发击杀强袭虫头部",
            patchVersion = "01.001.200",
            sourceId = "src_rec",
        )

        val result = WikiMatchupEngine.recommendForPart(
            enemyEntryId = "hd2.enemy.charger",
            enemyDetail = enemy,
            partId = "head",
            partState = EnemyPartState.INTACT,
            goal = RecommendationGoal.KILL,
            targetPatch = "01.001.200",
            ruleSet = defaultRules,
            evidences = emptyList(),
            recommendations = listOf(rec),
            attackCatalog = catalog,
        )

        assertEquals(1, result.curatedRecommendations.size)
        assertEquals(
            "hd2.weapon.rr",
            result.curatedRecommendations.first().attackOption.ref.entryId,
        )
        // Other viable: rail (PENETRATES) before ac (REDUCED), smg (RICOCHET) excluded, rr already curated
        assertEquals(2, result.otherViableWeapons.size)
        assertEquals("hd2.weapon.rail", result.otherViableWeapons[0].attackOption.ref.entryId)
        assertEquals("hd2.weapon.ac", result.otherViableWeapons[1].attackOption.ref.entryId)
    }
}
