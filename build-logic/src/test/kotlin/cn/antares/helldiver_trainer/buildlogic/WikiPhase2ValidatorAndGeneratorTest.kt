package cn.antares.helldiver_trainer.buildlogic

import kotlinx.serialization.json.Json
import org.gradle.api.GradleException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WikiPhase2ValidatorAndGeneratorTest {

    private val json = Json { ignoreUnknownKeys = false }

    private fun createValidVp8lWebp(width: Int, height: Int, paddingBytes: Int = 0): ByteArray {
        val totalSize = maxOf(32, 25 + paddingBytes)
        val out = ByteArray(totalSize)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(out, 0)
        val riffSize = totalSize - 8
        out[4] = (riffSize and 0xFF).toByte()
        out[5] = ((riffSize ushr 8) and 0xFF).toByte()
        out[6] = ((riffSize ushr 16) and 0xFF).toByte()
        out[7] = ((riffSize ushr 24) and 0xFF).toByte()
        "WEBP".toByteArray(Charsets.US_ASCII).copyInto(out, 8)
        "VP8L".toByteArray(Charsets.US_ASCII).copyInto(out, 12)
        val chunkSize = totalSize - 20
        out[16] = (chunkSize and 0xFF).toByte()
        out[17] = ((chunkSize ushr 8) and 0xFF).toByte()
        out[18] = ((chunkSize ushr 16) and 0xFF).toByte()
        out[19] = ((chunkSize ushr 24) and 0xFF).toByte()
        out[20] = 0x2F
        val bits = ((width - 1) and 0x3FFF) or (((height - 1) and 0x3FFF) shl 14)
        out[21] = (bits and 0xFF).toByte()
        out[22] = ((bits ushr 8) and 0xFF).toByte()
        out[23] = ((bits ushr 16) and 0xFF).toByte()
        out[24] = ((bits ushr 24) and 0xFF).toByte()
        return out
    }

    private fun createPhase2Workspace(includeSampleMatchups: Boolean = true): Pair<File, File> {
        val root = Files.createTempDirectory("wiki-phase2-test").toFile()
        val contentDir = File(root, "wiki-content").apply { mkdirs() }
        val mokoImages = File(root, "images").apply { mkdirs() }

        File(contentDir, "stratagems").mkdirs()
        File(contentDir, "weapons/primary").mkdirs()
        File(contentDir, "enemies/terminid").mkdirs()
        File(contentDir, "rules").mkdirs()
        File(contentDir, "anatomy").mkdirs()
        File(contentDir, "assets/anatomy").mkdirs()
        File(contentDir, "matchups").mkdirs()

        File(mokoImages, "sw_autocannon.svg").writeText("<svg/>")
        File(mokoImages, "ic_terminid.svg").writeText("<svg/>")

        File(contentDir, "sources.json").writeText(
            """
            {
              "sources": [
                {
                  "sourceId": "src_wiki",
                  "title": "Helldivers Wiki",
                  "sourceType": "wiki_gg",
                  "url": "https://helldivers.wiki.gg/",
                  "license": "CC BY-NC-SA 4.0",
                  "attribution": "Helldivers Wiki Contributors"
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "rules/damage_rules.json").writeText(
            """
            {
              "ruleSets": [
                {
                  "patchVersion": "01.001.200",
                  "penetrateMultiplier": 1.0,
                  "equalArmorMultiplier": 0.65,
                  "ricochetMultiplier": 0.0,
                  "sourceId": "src_wiki"
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "stratagems/support_weapon.json").writeText(
            """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.autocannon",
                  "kind": "stratagem",
                  "nameZh": "机炮",
                  "iconKey": "sw_autocannon",
                  "sourceId": "src_wiki",
                  "stratagem": {
                    "category": "support_weapon",
                    "directions": ["DOWN", "LEFT", "DOWN", "UP", "UP", "RIGHT"],
                    "training": { "legacyId": "sw_1", "order": 1 },
                    "weaponProfile": {
                      "configurations": [
                        {
                          "configId": "aphet",
                          "labelZh": "穿甲高爆",
                          "modelCode": "AC-8",
                          "attacks": [
                            {
                              "attackId": "aphet_round",
                              "nameZh": "20mm 穿甲高爆弹",
                              "components": [
                                {
                                  "componentId": "direct",
                                  "labelZh": "弹头直击",
                                  "componentKind": "direct",
                                  "normalDamage": { "status": "known", "value": 260 },
                                  "durableDamage": { "status": "known", "value": 260 },
                                  "armorPenetration": { "status": "known", "value": 4 }
                                },
                                {
                                  "componentId": "blast",
                                  "labelZh": "近距爆炸",
                                  "componentKind": "explosive",
                                  "normalDamage": { "status": "known", "value": 150 },
                                  "durableDamage": { "status": "known", "value": 150 },
                                  "armorPenetration": { "status": "known", "value": 3 },
                                  "innerRadiusMeters": 1.0,
                                  "outerRadiusMeters": 3.0
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
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
                      {
                        "configId": "base",
                        "labelZh": "基础",
                        "modelCode": "AR-23",
                        "attacks": [
                          {
                            "attackId": "bullet",
                            "nameZh": "标准弹药",
                            "components": [
                              {
                                "componentId": "direct",
                                "labelZh": "弹丸直击",
                                "componentKind": "direct",
                                "normalDamage": { "status": "known", "value": 70 },
                                "durableDamage": { "status": "known", "value": 14 },
                                "armorPenetration": { "status": "known", "value": 2 }
                              }
                            ]
                          }
                        ]
                      }
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
                  "iconKey": "ic_terminid",
                  "sourceId": "src_wiki",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "heavy",
                    "familyKey": "charger",
                    "mainPoolId": "main",
                    "pools": [
                      {
                        "poolId": "main",
                        "labelZh": "主体血池",
                        "maxHealth": { "status": "known", "value": 2400 },
                        "bleedHealth": 750,
                        "bleedRatePerSecond": 100.0,
                        "isMain": true
                      },
                      {
                        "poolId": "leg_plate_pool",
                        "labelZh": "前腿外甲",
                        "maxHealth": { "status": "known", "value": 800 }
                      },
                      {
                        "poolId": "leg_flesh_pool",
                        "labelZh": "前腿内部组织",
                        "maxHealth": { "status": "known", "value": 800 }
                      }
                    ],
                    "parts": [
                      {
                        "partId": "leg_plate",
                        "nameZh": "左前腿外甲",
                        "healthPoolRef": "leg_plate_pool",
                        "armor": { "status": "known", "value": 4 },
                        "durability": { "status": "known", "value": 0.7 },
                        "toMainRatio": { "status": "known", "value": 0.6 },
                        "explosionResistance": { "status": "known", "value": 0.25 },
                        "onBreak": [
                          {
                            "effectType": "expose_part",
                            "targetPartId": "leg_flesh",
                            "descriptionZh": "外甲剥离，暴露 AV2 内部组织"
                          }
                        ]
                      },
                      {
                        "partId": "leg_flesh",
                        "nameZh": "左前腿内部组织",
                        "healthPoolRef": "leg_flesh_pool",
                        "armor": { "status": "known", "value": 2 },
                        "durability": { "status": "known", "value": 0.7 },
                        "toMainRatio": { "status": "known", "value": 0.5 },
                        "explosionResistance": { "status": "known", "value": 0.25 },
                        "protectedByPartIds": ["leg_plate"],
                        "onBreak": [
                          {
                            "effectType": "fatal",
                            "descriptionZh": "致死"
                          }
                        ]
                      }
                    ]
                  }
                },
                {
                  "entryId": "hd2.enemy.charger_behemoth",
                  "kind": "enemy",
                  "nameZh": "巨兽强袭虫",
                  "iconKey": "ic_terminid",
                  "sourceId": "src_wiki",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "heavy",
                    "familyKey": "charger",
                    "extends": "hd2.enemy.charger",
                    "poolOverrides": [
                      {
                        "poolId": "main",
                        "maxHealth": { "status": "known", "value": 3000 }
                      }
                    ],
                    "partOverrides": [
                      {
                        "partId": "leg_plate",
                        "notes": "变种前腿甲"
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        File(contentDir, "assets/anatomy/charger_front_leg_plate.webp").writeBytes(
            createValidVp8lWebp(1200, 800),
        )
        File(contentDir, "assets/anatomy/charger_front_leg_flesh.webp").writeBytes(
            createValidVp8lWebp(1200, 800),
        )
        File(contentDir, "anatomy/hd2.enemy.charger.json").writeText(
            """
            {
              "enemyId": "hd2.enemy.charger",
              "defaultViewId": "front",
              "views": [
                {
                  "viewId": "front",
                  "labelZh": "正面视角",
                  "defaultAssetKey": "charger_front_leg_plate",
                  "partAssets": {
                    "leg_plate": "charger_front_leg_plate",
                    "leg_flesh": "charger_front_leg_flesh"
                  },
                  "assetRevision": "v2",
                  "width": 1200,
                  "height": 800,
                  "sourceId": "src_wiki",
                  "hitMap": {
                    "width": 10,
                    "height": 10,
                    "partPalette": ["leg_plate", "leg_flesh"],
                    "intactRle": [1, 40, 0, 60],
                    "brokenRle": [2, 40, 0, 60]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        if (includeSampleMatchups) {
            File(contentDir, "matchups/evidence.json").writeText(
                """
                {
                  "evidences": [
                    {
                      "evidenceId": "ev_ac_charger_leg",
                      "enemyId": "hd2.enemy.charger",
                      "partId": "leg_plate",
                      "partState": "intact",
                      "attackRef": {
                        "entryId": "hd2.stratagem.autocannon",
                        "configId": "aphet",
                        "attackId": "aphet_round"
                      },
                      "verdict": "strips_armor",
                      "shotsToBreak": { "status": "known", "value": 5, "unit": "shots" },
                      "summaryZh": "实测多发机炮可剥离前腿外甲。",
                      "patchVersion": "01.001.200",
                      "sourceId": "src_wiki"
                    }
                  ]
                }
                """.trimIndent(),
            )

            File(contentDir, "matchups/recommendations.json").writeText(
                """
                {
                  "recommendations": [
                    {
                      "recId": "rec_liberator_exposed",
                      "enemyId": "hd2.enemy.charger",
                      "partId": "leg_flesh",
                      "partState": "broken",
                      "goal": "attack_exposed",
                      "attackRef": {
                        "entryId": "hd2.weapon.liberator",
                        "configId": "base",
                        "attackId": "bullet"
                      },
                      "priorityOrder": 2,
                      "reasonZh": "外甲剥离后可快速补枪击杀。",
                      "patchVersion": "01.001.200",
                      "sourceId": "src_wiki"
                    },
                    {
                      "recId": "rec_ac_strip",
                      "enemyId": "hd2.enemy.charger",
                      "partId": "leg_plate",
                      "partState": "intact",
                      "goal": "strip_armor",
                      "attackRef": {
                        "entryId": "hd2.stratagem.autocannon",
                        "configId": "aphet",
                        "attackId": "aphet_round"
                      },
                      "priorityOrder": 1,
                      "reasonZh": "支持跨类推荐支援武器。",
                      "evidenceIds": ["ev_ac_charger_leg"],
                      "patchVersion": "01.001.200",
                      "sourceId": "src_wiki"
                    }
                  ]
                }
                """.trimIndent(),
            )
        } else {
            File(contentDir, "matchups/evidence.json").writeText("""{"evidences": []}""")
            File(
                contentDir,
                "matchups/recommendations.json",
            ).writeText("""{"recommendations": []}""")
        }

        return contentDir to mokoImages
    }

    @Test
    fun `phase 2 full content generates schema v2 pack with anatomy rules and matchups`() {
        val (contentDir, mokoImages) = createPhase2Workspace()
        val root = contentDir.parentFile
        val validated = WikiValidator.loadAndValidate(contentDir, mokoImages)
        val outKt = File(root, "out-kt")
        val outRes = File(root, "out-res")
        WikiGenerator.generate(validated, "1.8.0", "2.0.0", outKt, outRes)

        val manifest =
            json.decodeFromString<WikiManifestOutput>(File(outRes, "manifest.json").readText())
        assertEquals(2, manifest.schemaVersion)
        assertEquals("2.0.0", manifest.packVersion)
        assertEquals("files/wiki/rules.json", manifest.rulesPath)
        assertEquals(
            "files/wiki/anatomy/hd2.enemy.charger.json",
            manifest.anatomyPaths["hd2.enemy.charger"],
        )
        assertEquals(
            "files/wiki/anatomy_assets/charger_front_leg_plate.webp",
            manifest.anatomyAssetPaths["charger_front_leg_plate"],
        )
        assertTrue(File(outRes, "anatomy_assets/charger_front_leg_plate.webp").isFile)
        assertTrue(File(outRes, "anatomy_assets/charger_front_leg_flesh.webp").isFile)

        val index = json.decodeFromString<WikiIndexOutput>(File(outRes, "index.json").readText())
        val acIndex = index.entries.first { it.entryId == "hd2.stratagem.autocannon" }
        assertEquals(listOf("AC-8"), acIndex.modelCodes)

        val enemyShard = json.decodeFromString<WikiDetailShardOutput>(
            File(
                outRes,
                "details/enemy.json",
            ).readText(),
        )
        val behemoth = enemyShard.entries.getValue("hd2.enemy.charger_behemoth").enemy!!
        assertEquals("main", behemoth.mainPoolId)
        assertEquals(3000, behemoth.pools.first { it.poolId == "main" }.maxHealth.value)
        assertEquals("变种前腿甲", behemoth.parts.first { it.partId == "leg_plate" }.notes)

        val evPack = json.decodeFromString<MatchEvidencePackOutput>(
            File(
                outRes,
                "matchups/evidence.json",
            ).readText(),
        )
        assertEquals(
            listOf("ev_ac_charger_leg"),
            evPack.byEnemyPart["hd2.enemy.charger::leg_plate"],
        )
        assertEquals(
            listOf("ev_ac_charger_leg"),
            evPack.byAttackRef["hd2.stratagem.autocannon::aphet::aphet_round"],
        )

        val recPack = json.decodeFromString<PartRecommendationsPackOutput>(
            File(outRes, "matchups/recommendations.json").readText(),
        )
        assertEquals(
            listOf("rec_ac_strip", "rec_liberator_exposed"),
            recPack.recommendations.map { it.recId },
        )
        assertEquals(
            listOf("rec_ac_strip"),
            recPack.byGroupKey["hd2.enemy.charger::leg_plate::intact::strip_armor"],
        )
    }

    @Test
    fun `rejects invalid hitMaps and partAssets - odd RLE, pixel sum mismatch, out of bounds index, unknown part`() {
        val cases = listOf(
            "\"intactRle\": [1, 40, 0, 60]" to "\"intactRle\": [1, 40, 0]",
            "\"intactRle\": [1, 40, 0, 60]" to "\"intactRle\": [1, 40, 0, 50]",
            "\"intactRle\": [1, 40, 0, 60]" to "\"intactRle\": [5, 40, 0, 60]",
            "\"partPalette\": [\"leg_plate\", \"leg_flesh\"]" to "\"partPalette\": [\"leg_plate\", \"ghost_part\"]",
            "\"leg_flesh\": \"charger_front_leg_flesh\"" to "\"leg_flesh\": \"missing_webp\"",
            "\"leg_flesh\": \"charger_front_leg_flesh\"" to "\"ghost_part\": \"charger_front_leg_flesh\"",
        )
        for ((target, replacement) in cases) {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            val anatomyFile = File(contentDir, "anatomy/hd2.enemy.charger.json")
            anatomyFile.writeText(anatomyFile.readText().replace(target, replacement))
            assertFailsWith<GradleException>("Expected hitMap/partAssets failure for $replacement") {
                WikiValidator.loadAndValidate(contentDir, mokoImages)
            }
        }
    }

    @Test
    fun `rejects invalid webp assets - svg forbidden, bad header, oversized dimensions, oversized bytes, aspect mismatch`() {
        // 1. .svg forbidden
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            File(contentDir, "assets/anatomy/bad.svg").writeText("<svg/>")
            val ex = assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
            assertTrue(ex.message!!.contains("SVG"))
        }
        // 2. bad header
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            File(
                contentDir,
                "assets/anatomy/charger_front_leg_plate.webp",
            ).writeBytes(ByteArray(40))
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
        // 3. dimensions > 1200x900
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            File(contentDir, "assets/anatomy/charger_front_leg_plate.webp").writeBytes(
                createValidVp8lWebp(1400, 800),
            )
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
        // 4. bytes > 350 KB
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            File(contentDir, "assets/anatomy/charger_front_leg_plate.webp")
                .writeBytes(createValidVp8lWebp(1200, 800, paddingBytes = 360 * 1024))
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
        // 5. aspect ratio mismatch with anatomy view
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            File(contentDir, "assets/anatomy/charger_front_leg_plate.webp").writeBytes(
                createValidVp8lWebp(800, 800),
            )
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
    }

    @Test
    fun `rejects broken onBreak target, protectedByPartIds cycle, and missing pool`() {
        // 1. onBreak target missing
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            val file = File(contentDir, "enemies/terminid/heavy.json")
            file.writeText(
                file.readText().replace(
                    "\"targetPartId\": \"leg_flesh\"",
                    "\"targetPartId\": \"missing_part\"",
                ),
            )
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
        // 2. protectedByPartIds cycle
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            val file = File(contentDir, "enemies/terminid/heavy.json")
            file.writeText(
                file.readText().replace(
                    "\"partId\": \"leg_plate\",",
                    "\"partId\": \"leg_plate\",\n                        \"protectedByPartIds\": [\"leg_flesh\"],",
                ),
            )
            val ex = assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
            assertTrue(ex.message!!.contains("循环"))
        }
        // 3. mainPoolId missing
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            val file = File(contentDir, "enemies/terminid/heavy.json")
            file.writeText(
                file.readText()
                    .replace("\"mainPoolId\": \"main\"", "\"mainPoolId\": \"no_such_pool\""),
            )
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
    }

    @Test
    fun `rejects disallowed weaponProfile category, invalid attackRef, missing patchVersion, and missing wiki_gg license`() {
        // 1. mission stratagem with weaponProfile
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            File(contentDir, "stratagems/mission.json").writeText(
                """
                {
                  "entries": [
                    {
                      "entryId": "hd2.stratagem.bad_mission",
                      "kind": "stratagem",
                      "nameZh": "非法武器任务",
                      "iconKey": "sw_autocannon",
                      "sourceId": "src_wiki",
                      "stratagem": {
                        "category": "mission",
                        "directions": ["UP", "DOWN"],
                        "weaponProfile": {
                          "configurations": [{ "configId": "base", "labelZh": "基础" }]
                        }
                      }
                    }
                  ]
                }
                """.trimIndent(),
            )
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
        // 2. invalid AttackRef in recommendation
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = true)
            val recFile = File(contentDir, "matchups/recommendations.json")
            recFile.writeText(
                recFile.readText()
                    .replace("\"attackId\": \"bullet\"", "\"attackId\": \"nonexistent\""),
            )
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
        // 3. undefined patchVersion in evidence
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = true)
            val evFile = File(contentDir, "matchups/evidence.json")
            evFile.writeText(
                evFile.readText()
                    .replace(
                        "\"patchVersion\": \"01.001.200\"",
                        "\"patchVersion\": \"99.999.999\""
                    ),
            )
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
        // 4. wiki_gg source missing license
        run {
            val (contentDir, mokoImages) = createPhase2Workspace(includeSampleMatchups = false)
            val srcFile = File(contentDir, "sources.json")
            srcFile.writeText(srcFile.readText().replace("\"license\": \"CC BY-NC-SA 4.0\",", ""))
            assertFailsWith<GradleException> {
                WikiValidator.loadAndValidate(
                    contentDir,
                    mokoImages,
                )
            }
        }
    }
}
