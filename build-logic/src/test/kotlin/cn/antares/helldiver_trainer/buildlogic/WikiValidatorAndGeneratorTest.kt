package cn.antares.helldiver_trainer.buildlogic

import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WikiValidatorAndGeneratorTest {

    private val json = Json { ignoreUnknownKeys = false }

    private fun createBaseWorkspace(): Pair<File, File> {
        val root = Files.createTempDirectory("wiki-buildlogic-test").toFile()
        val contentDir = File(root, "wiki-content").apply { mkdirs() }
        val mokoImages = File(root, "images").apply { mkdirs() }

        File(contentDir, "stratagems").mkdirs()
        File(contentDir, "weapons/primary").mkdirs()
        File(contentDir, "weapons/secondary").mkdirs()
        File(contentDir, "weapons/throwable").mkdirs()
        File(contentDir, "enemies/terminid").mkdirs()

        for (icon in listOf("eagle_airstrike", "ms_upload_data", "sw_autocannon", "ic_terminid")) {
            File(mokoImages, "$icon.svg").writeText("<svg/>")
        }

        File(contentDir, "sources.json").writeText(
            """
            {
              "sources": [
                {
                  "sourceId": "src_mig",
                  "title": "Project Catalog",
                  "sourceType": "project_migration",
                  "location": "StratagemStore.kt"
                },
                {
                  "sourceId": "src_wiki",
                  "title": "Wiki.gg",
                  "sourceType": "wiki_gg",
                  "url": "https://helldivers.wiki.gg/",
                  "license": "CC BY-NC-SA 4.0",
                  "attribution": "Helldivers Wiki Contributors"
                }
              ]
            }
            """.trimIndent(),
        )
        return contentDir to mokoImages
    }

    @Test
    fun `valid 4-tier content generates all artifacts and expands variants and relations`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        val root = contentDir.parentFile

        File(contentDir, "stratagems/eagle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.eagle_airstrike",
                  "kind": "stratagem",
                  "nameZh": "“飞鹰”空袭",
                  "nameEn": "Eagle Airstrike",
                  "aliases": [{ "locale": "zh-CN", "text": "飞鹰空袭" }],
                  "iconKey": "eagle_airstrike",
                  "sourceId": "src_mig",
                  "stratagem": {
                    "category": "eagle",
                    "directions": ["UP", "RIGHT", "DOWN", "RIGHT"],
                    "training": { "legacyId": "eagle_3", "order": 1 }
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "stratagems/mission.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.upload_data",
                  "kind": "stratagem",
                  "nameZh": "上传数据",
                  "iconKey": "ms_upload_data",
                  "sourceId": "src_mig",
                  "stratagem": {
                    "category": "mission",
                    "directions": ["LEFT", "RIGHT", "UP", "UP", "UP"],
                    "training": { "legacyId": "ms_13", "order": 2 }
                  }
                },
                {
                  "entryId": "hd2.stratagem.connect_pod",
                  "kind": "stratagem",
                  "nameZh": "将绝地喷射舱连接至驱逐舰",
                  "iconKey": "ms_upload_data",
                  "sourceId": "src_mig",
                  "stratagem": {
                    "category": "mission",
                    "directions": ["LEFT", "RIGHT", "UP", "UP", "UP"],
                    "training": { "legacyId": "ms_18", "order": 3 }
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "weapons/primary/assault_rifle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.weapon.liberator",
                  "kind": "weapon",
                  "nameZh": "AR-23 解放者",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "primary",
                    "weaponClass": "assault_rifle",
                    "configurations": [
                      { "configId": "base", "labelZh": "基础", "modelCode": "AR-23" }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "weapons/secondary/melee.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.weapon.stun_baton",
                  "kind": "weapon",
                  "nameZh": "CQC-30 眩晕棍",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "secondary",
                    "weaponClass": "melee",
                    "configurations": [
                      { "configId": "base", "labelZh": "基础", "modelCode": "CQC-30" }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "weapons/throwable/standard.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.weapon.g12",
                  "kind": "weapon",
                  "nameZh": "G-12 高爆手雷",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "throwable",
                    "weaponClass": "standard",
                    "configurations": [
                      { "configId": "base", "labelZh": "基础", "modelCode": "G-12" }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "enemies/terminid/heavy.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.enemy.charger",
                  "kind": "enemy",
                  "nameZh": "强袭虫",
                  "sourceId": "src_wiki",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "heavy",
                    "familyKey": "charger",
                    "parts": [
                      { "partId": "head", "nameZh": "头部", "armor": { "status": "known", "value": 4 } },
                      { "partId": "abdomen", "nameZh": "腹部", "armor": { "status": "known", "value": 0 } }
                    ]
                  }
                },
                {
                  "entryId": "hd2.enemy.charger_behemoth",
                  "kind": "enemy",
                  "nameZh": "巨兽强袭虫",
                  "sourceId": "src_wiki",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "heavy",
                    "familyKey": "charger",
                    "extends": "hd2.enemy.charger",
                    "partOverrides": [
                      { "partId": "head", "notes": "更耐打" }
                    ]
                  },
                  "relations": [
                    { "type": "variant_of", "targetId": "hd2.enemy.charger" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        val validated = WikiValidator.loadAndValidate(contentDir, mokoImages)
        val outKt = File(root, "out-kt")
        val outRes = File(root, "out-res")
        WikiGenerator.generate(validated, "1.8.0", "1.0.0", outKt, outRes)

        val manifest =
            json.decodeFromString<WikiManifestOutput>(File(outRes, "manifest.json").readText())
        assertEquals(2, manifest.schemaVersion)
        assertEquals(8, manifest.entryCount)

        val index = json.decodeFromString<WikiIndexOutput>(File(outRes, "index.json").readText())
        assertEquals(8, index.entries.size)
        val batonIndex = index.entries.first { it.entryId == "hd2.weapon.stun_baton" }
        assertEquals("secondary", batonIndex.weaponSlot)
        assertEquals("melee", batonIndex.weaponClass)
        val chargerIndex = index.entries.first { it.entryId == "hd2.enemy.charger" }
        assertEquals("terminid", chargerIndex.enemyFaction)
        assertEquals("heavy", chargerIndex.enemySizeClass)
        assertTrue(index.relations.any { it.ownerEntryId == "hd2.enemy.charger" && it.isReverse && it.targetEntryId == "hd2.enemy.charger_behemoth" })

        val enemyShard = json.decodeFromString<WikiDetailShardOutput>(
            File(
                outRes,
                "details/enemy.json",
            ).readText(),
        )
        val behemoth = enemyShard.entries.getValue("hd2.enemy.charger_behemoth").enemy!!
        assertEquals("heavy", behemoth.sizeClass)
        assertEquals(2, behemoth.parts.size)
        assertEquals("更耐打", behemoth.parts.first { it.partId == "head" }.notes)

        val catalogKt = File(
            outKt,
            "cn/antares/helldiver_trainer/wiki/generated/GeneratedStratagemCatalog.kt",
        ).readText()
        assertTrue(catalogKt.contains("import cn.antares.helldiver_trainer.eagle_airstrike"))
        assertTrue(catalogKt.contains("id = \"ms_13\""))
        assertTrue(catalogKt.contains("id = \"ms_18\""))

        val registryKt = File(
            outKt,
            "cn/antares/helldiver_trainer/wiki/generated/WikiIconRegistry.kt",
        ).readText()
        assertTrue(registryKt.contains("\"ic_terminid\" -> MR.images.ic_terminid"))
        assertFalse(registryKt.contains("else -> MR"))
    }

    @Test
    fun `rejects invalid slot and weaponClass combination`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "weapons/throwable/melee.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.weapon.bad_combo",
                  "kind": "weapon",
                  "nameZh": "非法组合",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "throwable",
                    "weaponClass": "melee",
                    "configurations": [{ "configId": "base", "labelZh": "基础" }]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
    }

    @Test
    fun `rejects weapon in mismatched class file`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "weapons/primary/assault_rifle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.weapon.wrong_file",
                  "kind": "weapon",
                  "nameZh": "放错子类文件",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "primary",
                    "weaponClass": "shotgun",
                    "configurations": [{ "configId": "base", "labelZh": "基础" }]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        val ex = assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
        assertTrue(ex.message!!.contains("weapon.weaponClass"))
    }

    @Test
    fun `rejects invalid enemy sizeClass`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "enemies/terminid/boss.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.enemy.bad_size",
                  "kind": "enemy",
                  "nameZh": "非法体型",
                  "sourceId": "src_wiki",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "boss",
                    "familyKey": "boss"
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
    }

    @Test
    fun `rejects stratagem in mismatched category file`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "stratagems/eagle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.wrong",
                  "kind": "stratagem",
                  "nameZh": "放错分类",
                  "iconKey": "eagle_airstrike",
                  "sourceId": "src_mig",
                  "stratagem": {
                    "category": "orbital",
                    "directions": ["UP", "DOWN", "LEFT"]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        val ex = assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
        assertTrue(ex.message!!.contains("stratagem.category"))
    }

    @Test
    fun `rejects missing sourceId`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "weapons/primary/assault_rifle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.weapon.bad",
                  "kind": "weapon",
                  "nameZh": "坏武器",
                  "sourceId": "nonexistent_src",
                  "weapon": {
                    "slot": "primary",
                    "weaponClass": "assault_rifle",
                    "configurations": [{ "configId": "base", "labelZh": "基础" }]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
    }

    @Test
    fun `rejects unknown iconKey`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "stratagems/eagle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.bad_icon",
                  "kind": "stratagem",
                  "nameZh": "缺失图标",
                  "iconKey": "does_not_exist",
                  "sourceId": "src_mig",
                  "stratagem": {
                    "category": "eagle",
                    "directions": ["UP", "DOWN", "LEFT"]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
    }

    @Test
    fun `rejects variant_of cycle`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "weapons/primary/assault_rifle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.weapon.a",
                  "kind": "weapon",
                  "nameZh": "A",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "primary",
                    "weaponClass": "assault_rifle",
                    "configurations": [{ "configId": "base", "labelZh": "基础" }]
                  },
                  "relations": [{ "type": "variant_of", "targetId": "hd2.weapon.b" }]
                },
                {
                  "entryId": "hd2.weapon.b",
                  "kind": "weapon",
                  "nameZh": "B",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "primary",
                    "weaponClass": "assault_rifle",
                    "configurations": [{ "configId": "base", "labelZh": "基础" }]
                  },
                  "relations": [{ "type": "variant_of", "targetId": "hd2.weapon.a" }]
                }
              ]
            }
            """.trimIndent(),
        )
        val ex = assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
        assertTrue(ex.message!!.contains("循环引用"))
    }

    @Test
    fun `rejects known fact without value`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "stratagems/eagle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.bad_fact",
                  "kind": "stratagem",
                  "nameZh": "坏数值",
                  "iconKey": "eagle_airstrike",
                  "sourceId": "src_mig",
                  "stratagem": {
                    "category": "eagle",
                    "directions": ["UP", "DOWN", "LEFT"],
                    "cooldownSeconds": { "status": "known" }
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
    }

    @Test
    fun `rejects duplicate entryId`() {
        val (contentDir, mokoImages) = createBaseWorkspace()
        File(contentDir, "stratagems/eagle.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.dup",
                  "kind": "stratagem",
                  "nameZh": "1",
                  "iconKey": "eagle_airstrike",
                  "sourceId": "src_mig",
                  "stratagem": { "category": "eagle", "directions": ["UP", "DOWN", "LEFT"] }
                },
                {
                  "entryId": "hd2.stratagem.dup",
                  "kind": "stratagem",
                  "nameZh": "2",
                  "iconKey": "eagle_airstrike",
                  "sourceId": "src_mig",
                  "stratagem": { "category": "eagle", "directions": ["UP", "DOWN", "RIGHT"] }
                }
              ]
            }
            """.trimIndent(),
        )
        assertFailsWith<GradleException> {
            WikiValidator.loadAndValidate(contentDir, mokoImages)
        }
    }
}
