package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.model.EnemyFaction
import cn.antares.helldiver_trainer.data.wiki.model.EnemySizeClass
import cn.antares.helldiver_trainer.data.wiki.model.NormalizedTextPair
import cn.antares.helldiver_trainer.data.wiki.model.StratagemCategory
import cn.antares.helldiver_trainer.data.wiki.model.WeaponClass
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.DOWN
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.LEFT
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.RIGHT
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection.UP
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexPack
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WeaponSlot
import cn.antares.helldiver_trainer.data.wiki.model.WikiMatchReason
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest
import cn.antares.helldiver_trainer.data.wiki.search.WikiSearchEngine
import cn.antares.helldiver_trainer.data.wiki.search.WikiTextNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WikiSearchEngineTest {

    private fun norm(s: String): NormalizedTextPair {
        val n = WikiTextNormalizer.normalize(s)
        return NormalizedTextPair(n.text, n.compact)
    }

    private val sampleIndex = WikiIndexPack(
        entries = listOf(
            WikiIndexItem(
                entryId = "hd2.stratagem.eagle_500kg_bomb",
                kind = WikiKind.STRATAGEM,
                nameZh = "“飞鹰”500KG炸弹",
                nameEn = "Eagle 500kg Bomb",
                iconKey = "eagle_500kg_bomb",
                stratagemCategory = StratagemCategory.EAGLE,
                directions = listOf(UP, RIGHT, DOWN, DOWN, DOWN),
                trainingOrder = 2,
                normalizedNameZh = norm("“飞鹰”500KG炸弹").text,
                normalizedNameZhCompact = norm("“飞鹰”500KG炸弹").compact,
                normalizedNameEn = norm("Eagle 500kg Bomb").text,
                normalizedNameEnCompact = norm("Eagle 500kg Bomb").compact,
                normalizedAliases = listOf(norm("500kg"), norm("飞鹰500kg")),
            ),
            WikiIndexItem(
                entryId = "hd2.stratagem.autocannon",
                kind = WikiKind.STRATAGEM,
                nameZh = "机炮",
                nameEn = "AC-8 Autocannon",
                iconKey = "sw_autocannon",
                stratagemCategory = StratagemCategory.SUPPORT_WEAPON,
                directions = listOf(DOWN, LEFT, DOWN, UP, UP, RIGHT),
                trainingOrder = 24,
                normalizedNameZh = norm("机炮").text,
                normalizedNameZhCompact = norm("机炮").compact,
                normalizedNameEn = norm("AC-8 Autocannon").text,
                normalizedNameEnCompact = norm("AC-8 Autocannon").compact,
                normalizedAliases = listOf(norm("机炮")),
            ),
            WikiIndexItem(
                entryId = "hd2.weapon.stun_baton",
                kind = WikiKind.WEAPON,
                nameZh = "CQC-30 眩晕棍",
                nameEn = "CQC-30 Stun Baton",
                weaponSlot = WeaponSlot.SECONDARY,
                weaponClass = WeaponClass.MELEE,
                modelCodes = listOf("CQC-30"),
                normalizedNameZh = norm("CQC-30 眩晕棍").text,
                normalizedNameZhCompact = norm("CQC-30 眩晕棍").compact,
                normalizedAliases = listOf(norm("眩晕棍")),
                normalizedModelCodes = listOf(norm("CQC-30")),
            ),
            WikiIndexItem(
                entryId = "hd2.weapon.g12",
                kind = WikiKind.WEAPON,
                nameZh = "G-12 高爆手雷",
                nameEn = "G-12 High Explosive",
                weaponSlot = WeaponSlot.THROWABLE,
                weaponClass = WeaponClass.STANDARD,
                modelCodes = listOf("G-12"),
                normalizedNameZh = norm("G-12 高爆手雷").text,
                normalizedNameZhCompact = norm("G-12 高爆手雷").compact,
                normalizedAliases = listOf(norm("高爆手雷")),
                normalizedModelCodes = listOf(norm("G-12")),
            ),
            WikiIndexItem(
                entryId = "hd2.weapon.liberator_penetrator",
                kind = WikiKind.WEAPON,
                nameZh = "AR-23P 穿甲解放者",
                nameEn = "AR-23P Liberator Penetrator",
                weaponSlot = WeaponSlot.PRIMARY,
                weaponClass = WeaponClass.ASSAULT_RIFLE,
                modelCodes = listOf("AR-23P"),
                normalizedNameZh = norm("AR-23P 穿甲解放者").text,
                normalizedNameZhCompact = norm("AR-23P 穿甲解放者").compact,
                normalizedModelCodes = listOf(norm("AR-23P")),
            ),
            WikiIndexItem(
                entryId = "hd2.weapon.liberator",
                kind = WikiKind.WEAPON,
                nameZh = "AR-23 解放者",
                nameEn = "AR-23 Liberator",
                weaponSlot = WeaponSlot.PRIMARY,
                weaponClass = WeaponClass.ASSAULT_RIFLE,
                modelCodes = listOf("AR-23"),
                normalizedNameZh = norm("AR-23 解放者").text,
                normalizedNameZhCompact = norm("AR-23 解放者").compact,
                normalizedModelCodes = listOf(norm("AR-23")),
            ),
            WikiIndexItem(
                entryId = "hd2.stratagem.sw_sta_x3_wasp_launcher",
                kind = WikiKind.STRATAGEM,
                nameZh = "StA-X3 W.A.S.P.发射器",
                stratagemCategory = StratagemCategory.SUPPORT_WEAPON,
                directions = listOf(DOWN, DOWN, UP, DOWN, RIGHT),
                trainingOrder = 40,
                normalizedNameZh = norm("StA-X3 W.A.S.P.发射器").text,
                normalizedNameZhCompact = norm("StA-X3 W.A.S.P.发射器").compact,
            ),
            WikiIndexItem(
                entryId = "hd2.stratagem.ms_upload_data",
                kind = WikiKind.STRATAGEM,
                nameZh = "上传数据",
                stratagemCategory = StratagemCategory.MISSION,
                directions = listOf(LEFT, RIGHT, UP, UP, UP),
                trainingOrder = 108,
                normalizedNameZh = norm("上传数据").text,
                normalizedNameZhCompact = norm("上传数据").compact,
            ),
            WikiIndexItem(
                entryId = "hd2.stratagem.ms_upload_data_connect_pod",
                kind = WikiKind.STRATAGEM,
                nameZh = "将绝地喷射舱连接至驱逐舰",
                stratagemCategory = StratagemCategory.MISSION,
                directions = listOf(LEFT, RIGHT, UP, UP, UP),
                trainingOrder = 113,
                normalizedNameZh = norm("将绝地喷射舱连接至驱逐舰").text,
                normalizedNameZhCompact = norm("将绝地喷射舱连接至驱逐舰").compact,
            ),
            WikiIndexItem(
                entryId = "hd2.enemy.scavenger",
                kind = WikiKind.ENEMY,
                nameZh = "食腐虫",
                enemyFaction = EnemyFaction.TERMINID,
                enemySizeClass = EnemySizeClass.SMALL,
                normalizedNameZh = norm("食腐虫").text,
                normalizedNameZhCompact = norm("食腐虫").compact,
            ),
            WikiIndexItem(
                entryId = "hd2.enemy.charger",
                kind = WikiKind.ENEMY,
                nameZh = "强袭虫",
                summary = "重甲正面冲击单位，腹部为弱点。",
                enemyFaction = EnemyFaction.TERMINID,
                enemySizeClass = EnemySizeClass.HEAVY,
                normalizedNameZh = norm("强袭虫").text,
                normalizedNameZhCompact = norm("强袭虫").compact,
            ),
            WikiIndexItem(
                entryId = "hd2.enemy.hulk_bruiser",
                kind = WikiKind.ENEMY,
                nameZh = "浩劫屠夫",
                enemyFaction = EnemyFaction.AUTOMATON,
                enemySizeClass = EnemySizeClass.HEAVY,
                normalizedNameZh = norm("浩劫屠夫").text,
                normalizedNameZhCompact = norm("浩劫屠夫").compact,
            ),
        ),
    )

    @Test
    fun `500kg hits eagle 500kg bomb`() {
        val hits = WikiSearchEngine.search(sampleIndex, WikiSearchRequest(text = "500kg"))
        assertEquals("hd2.stratagem.eagle_500kg_bomb", hits.first().item.entryId)
    }

    @Test
    fun `stratagem query hits matching stratagem`() {
        val hits = WikiSearchEngine.search(sampleIndex, WikiSearchRequest(text = "机炮"))
        assertEquals(listOf("hd2.stratagem.autocannon"), hits.map { it.item.entryId })
    }

    @Test
    fun `AR-23 ranks base liberator before AR-23P variant`() {
        val hits = WikiSearchEngine.search(sampleIndex, WikiSearchRequest(text = "AR-23"))
        assertEquals(
            listOf("hd2.weapon.liberator", "hd2.weapon.liberator_penetrator"),
            hits.map { it.item.entryId },
        )
        assertEquals(WikiMatchReason.EXACT_MODEL, hits.first().matchReason)
    }

    @Test
    fun `wasp matches stripped dots name`() {
        val hits = WikiSearchEngine.search(sampleIndex, WikiSearchRequest(text = "wasp"))
        assertEquals(listOf("hd2.stratagem.sw_sta_x3_wasp_launcher"), hits.map { it.item.entryId })
    }

    @Test
    fun `direction search returns all stratagems with matching directions`() {
        val hits = WikiSearchEngine.search(sampleIndex, WikiSearchRequest(text = "adwww"))
        assertEquals(
            listOf("hd2.stratagem.ms_upload_data", "hd2.stratagem.ms_upload_data_connect_pod"),
            hits.map { it.item.entryId },
        )
        assertTrue(hits.all { it.matchReason == WikiMatchReason.DIRECTION_EXACT })
    }

    @Test
    fun `faction and sizeClass filter and blank catalog query work`() {
        val hits = WikiSearchEngine.search(
            sampleIndex,
            WikiSearchRequest(
                kind = WikiKind.ENEMY,
                enemyFaction = EnemyFaction.TERMINID,
                enemySizeClass = EnemySizeClass.SMALL,
            ),
        )
        assertEquals(listOf("hd2.enemy.scavenger"), hits.map { it.item.entryId })
        assertEquals(WikiMatchReason.CATALOG, hits.first().matchReason)
    }

    @Test
    fun `weapon slot and class filter work for secondary melee and throwable`() {
        val meleeHits = WikiSearchEngine.search(
            sampleIndex,
            WikiSearchRequest(
                kind = WikiKind.WEAPON,
                weaponSlot = WeaponSlot.SECONDARY,
                weaponClass = WeaponClass.MELEE,
            ),
        )
        assertEquals(listOf("hd2.weapon.stun_baton"), meleeHits.map { it.item.entryId })

        val grenadeHits = WikiSearchEngine.search(
            sampleIndex,
            WikiSearchRequest(
                kind = WikiKind.WEAPON,
                weaponSlot = WeaponSlot.THROWABLE,
                weaponClass = WeaponClass.STANDARD,
            ),
        )
        assertEquals(listOf("hd2.weapon.g12"), grenadeHits.map { it.item.entryId })
    }

    @Test
    fun `summary-only text does not match search`() {
        val hits = WikiSearchEngine.search(sampleIndex, WikiSearchRequest(text = "腹部"))
        assertTrue(hits.isEmpty())
    }
}
