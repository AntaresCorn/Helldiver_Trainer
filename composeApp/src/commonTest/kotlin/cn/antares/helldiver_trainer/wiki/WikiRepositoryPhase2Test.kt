package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.datasource.WikiPackDataSource
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.MatchVerdict
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluateRequest
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.repository.WikiRepositoryImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class WikiRepositoryPhase2Test {

    private class FakePackDataSource(
        initialFiles: Map<String, ByteArray>,
        private val readDelayMs: Long = 0L,
    ) : WikiPackDataSource {
        val files = initialFiles.toMutableMap()
        val readCounts = HashMap<String, Int>()

        override suspend fun readBytes(relativePath: String): ByteArray {
            readCounts[relativePath] = (readCounts[relativePath] ?: 0) + 1
            if (readDelayMs > 0L) delay(readDelayMs.milliseconds)
            return files[relativePath] ?: error("Missing file: $relativePath")
        }
    }

    private fun phase2Files(): MutableMap<String, ByteArray> = mutableMapOf(
        "files/wiki/manifest.json" to """
            {
              "schemaVersion": 2,
              "packVersion": "2.0.0",
              "generatedFromAppVersion": "1.8.0",
              "entryCount": 3,
              "indexPath": "files/wiki/index.json",
              "detailPaths": {
                "stratagem": "files/wiki/details/stratagem.json",
                "weapon": "files/wiki/details/weapon.json",
                "enemy": "files/wiki/details/enemy.json"
              },
              "rulesPath": "files/wiki/rules.json",
              "anatomyPaths": {
                "hd2.enemy.charger": "files/wiki/anatomy/charger.json"
              },
              "anatomyAssetPaths": {
                "charger_side": "files/wiki/anatomy/assets/charger_side.webp"
              },
              "evidencePath": "files/wiki/matchups/evidence.json",
              "recommendationsPath": "files/wiki/matchups/recommendations.json"
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/index.json" to """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.eat",
                  "kind": "stratagem",
                  "nameZh": "消耗性反坦克武器",
                  "normalizedNameZh": "消耗性反坦克武器",
                  "normalizedNameZhCompact": "消耗性反坦克武器"
                },
                {
                  "entryId": "hd2.weapon.rr",
                  "kind": "weapon",
                  "nameZh": "GR-8 无后坐力炮",
                  "normalizedNameZh": "gr8 无后坐力炮",
                  "normalizedNameZhCompact": "gr8无后坐力炮"
                },
                {
                  "entryId": "hd2.enemy.charger",
                  "kind": "enemy",
                  "nameZh": "强袭虫",
                  "normalizedNameZh": "强袭虫",
                  "normalizedNameZhCompact": "强袭虫"
                }
              ],
              "relations": []
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/details/stratagem.json" to """
            {
              "kind": "stratagem",
              "entries": {
                "hd2.stratagem.eat": {
                  "entryId": "hd2.stratagem.eat",
                  "kind": "stratagem",
                  "nameZh": "消耗性反坦克武器",
                  "sourceId": "src_strat",
                  "stratagem": {
                    "category": "support_weapon",
                    "weaponProfile": {
                      "configurations": [
                        {
                          "configId": "base",
                          "labelZh": "默认",
                          "attacks": [
                            {
                              "attackId": "eat_heat",
                              "nameZh": "聚能破甲弹",
                              "components": [
                                {
                                  "componentId": "c1",
                                  "labelZh": "直击",
                                  "componentKind": "direct",
                                  "normalDamage": { "status": "known", "value": 2000 },
                                  "durableDamage": { "status": "known", "value": 2000 },
                                  "armorPenetration": { "status": "known", "value": 6 }
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  }
                }
              },
              "sources": {
                "src_strat": { "sourceId": "src_strat", "title": "Stratagems", "sourceType": "wiki_gg" }
              }
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/details/weapon.json" to """
            {
              "kind": "weapon",
              "entries": {
                "hd2.weapon.rr": {
                  "entryId": "hd2.weapon.rr",
                  "kind": "weapon",
                  "nameZh": "GR-8 无后坐力炮",
                  "sourceId": "src_wpn",
                  "weapon": {
                    "slot": "primary",
                    "weaponClass": "explosive",
                    "configurations": [
                      {
                        "configId": "heat",
                        "labelZh": "破甲模式",
                        "attacks": [
                          {
                            "attackId": "rr_heat",
                            "nameZh": "HEAT 弹头",
                            "components": [
                              {
                                "componentId": "c1",
                                "labelZh": "直击",
                                "componentKind": "direct",
                                "normalDamage": { "status": "known", "value": 1600 },
                                "durableDamage": { "status": "known", "value": 1600 },
                                "armorPenetration": { "status": "known", "value": 6 }
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
              },
              "sources": {
                "src_wpn": { "sourceId": "src_wpn", "title": "Weapons", "sourceType": "wiki_gg" }
              }
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/details/enemy.json" to """
            {
              "kind": "enemy",
              "entries": {
                "hd2.enemy.charger": {
                  "entryId": "hd2.enemy.charger",
                  "kind": "enemy",
                  "nameZh": "强袭虫",
                  "sourceId": "src_enemy",
                  "verifiedPatch": "01.001.200",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "heavy",
                    "familyKey": "charger",
                    "pools": [
                      { "poolId": "head_pool", "labelZh": "头部", "maxHealth": { "status": "known", "value": 1200 } }
                    ],
                    "parts": [
                      {
                        "partId": "head",
                        "nameZh": "头部",
                        "armor": { "status": "known", "value": 4 },
                        "healthPoolRef": "head_pool",
                        "durability": { "status": "known", "value": 0.5 },
                        "toMainRatio": { "status": "known", "value": 1.0 },
                        "onBreak": [{ "effectType": "fatal", "descriptionZh": "击破致死" }]
                      }
                    ]
                  }
                }
              },
              "sources": {
                "src_enemy": { "sourceId": "src_enemy", "title": "Enemies", "sourceType": "wiki_gg" }
              }
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/rules.json" to """
            {
              "ruleSets": [
                {
                  "patchVersion": "01.001.200",
                  "penetrateMultiplier": 1.0,
                  "equalArmorMultiplier": 0.65,
                  "ricochetMultiplier": 0.0,
                  "sourceId": "src_rules"
                }
              ],
              "sources": {
                "src_rules": { "sourceId": "src_rules", "title": "Damage Rules", "sourceType": "official" }
              }
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/anatomy/charger.json" to """
            {
              "enemyId": "hd2.enemy.charger",
              "defaultViewId": "side",
              "views": [
                {
                  "viewId": "side",
                  "labelZh": "侧面图",
                  "defaultAssetKey": "charger_side",
                  "partAssets": {
                    "head": "charger_side"
                  },
                  "assetRevision": "r1",
                  "width": 1000,
                  "height": 600,
                  "sourceId": "src_anat",
                  "hitMap": {
                    "width": 2,
                    "height": 2,
                    "partPalette": ["head"],
                    "intactRle": [1, 4]
                  }
                }
              ],
              "sources": {
                "src_anat": { "sourceId": "src_anat", "title": "Anatomy", "sourceType": "community" }
              }
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/anatomy/assets/charger_side.webp" to byteArrayOf(0x52, 0x49, 0x46, 0x46),
        "files/wiki/matchups/evidence.json" to """
            {
              "evidences": [
                {
                  "evidenceId": "ev_rr_head",
                  "enemyId": "hd2.enemy.charger",
                  "partId": "head",
                  "partState": "intact",
                  "attackRef": { "entryId": "hd2.weapon.rr", "configId": "heat", "attackId": "rr_heat" },
                  "verdict": "kills",
                  "shotsToBreak": { "status": "known", "value": 1 },
                  "shotsToKill": { "status": "known", "value": 1 },
                  "summaryZh": "一发击杀强袭虫头部",
                  "patchVersion": "01.001.200",
                  "sourceId": "src_ev"
                }
              ],
              "sources": {
                "src_ev": { "sourceId": "src_ev", "title": "Field Test", "sourceType": "community" }
              }
            }
        """.trimIndent().encodeToByteArray(),
        "files/wiki/matchups/recommendations.json" to """
            {
              "recommendations": [
                {
                  "recId": "rec_rr_head",
                  "enemyId": "hd2.enemy.charger",
                  "partId": "head",
                  "partState": "intact",
                  "goal": "kill",
                  "attackRef": { "entryId": "hd2.weapon.rr", "configId": "heat", "attackId": "rr_heat" },
                  "priorityOrder": 1,
                  "reasonZh": "直接一发带走",
                  "evidenceIds": ["ev_rr_head"],
                  "patchVersion": "01.001.200",
                  "sourceId": "src_rec"
                }
              ],
              "sources": {
                "src_rec": { "sourceId": "src_rec", "title": "Editor Picks", "sourceType": "editorial" }
              }
            }
        """.trimIndent().encodeToByteArray(),
    )

    @Test
    fun `getAnatomy deduplicates concurrent loads, returns Missing for unknown enemy, and retries after error`() =
        runTest {
            val files = phase2Files()
            val ds = FakePackDataSource(files, readDelayMs = 20L)
            val repo = WikiRepositoryImpl(ds)

            assertIs<WikiResult.Missing>(repo.getAnatomy("hd2.enemy.unknown"))

            // Corrupt anatomy first -> should return ReadError and NOT cache
            ds.files["files/wiki/anatomy/charger.json"] = "{ bad json".encodeToByteArray()
            assertIs<WikiResult.ReadError>(repo.getAnatomy("hd2.enemy.charger"))

            // Restore and run 6 concurrent calls -> single read
            ds.files["files/wiki/anatomy/charger.json"] =
                phase2Files().getValue("files/wiki/anatomy/charger.json")
            val results = (1..6).map {
                async { repo.getAnatomy("hd2.enemy.charger") }
            }.awaitAll()

            assertTrue(results.all { it is WikiResult.Success })
            assertEquals(2, ds.readCounts["files/wiki/anatomy/charger.json"])
            val bundle = (results.first() as WikiResult.Success).data
            assertEquals("side", bundle.defaultViewId)
        }

    @Test
    fun `readAnatomyImageBytes caches bytes and handles missing or empty assets`() = runTest {
        val files = phase2Files()
        val ds = FakePackDataSource(files, readDelayMs = 15L)
        val repo = WikiRepositoryImpl(ds)

        assertIs<WikiResult.Missing>(repo.readAnatomyImageBytes("missing_key"))

        ds.files["files/wiki/anatomy/assets/charger_side.webp"] = byteArrayOf()
        assertIs<WikiResult.ReadError>(repo.readAnatomyImageBytes("charger_side"))

        ds.files["files/wiki/anatomy/assets/charger_side.webp"] =
            phase2Files().getValue("files/wiki/anatomy/assets/charger_side.webp")
        val results = (1..5).map {
            async { repo.readAnatomyImageBytes("charger_side") }
        }.awaitAll()
        assertTrue(results.all { it is WikiResult.Success })
        assertEquals(2, ds.readCounts["files/wiki/anatomy/assets/charger_side.webp"])
    }

    @Test
    fun `listAvailableAttacks collects both weapon and stratagem configurations`() = runTest {
        val repo = WikiRepositoryImpl(FakePackDataSource(phase2Files()))
        val res = assertIs<WikiResult.Success<*>>(repo.listAvailableAttacks())

        @Suppress("UNCHECKED_CAST")
        val options = res.data as List<cn.antares.helldiver_trainer.data.wiki.model.AttackOption>
        assertEquals(2, options.size)
        assertTrue(options.any { it.ownerKind == WikiKind.WEAPON && it.ref.attackId == "rr_heat" })
        assertTrue(options.any { it.ownerKind == WikiKind.STRATAGEM && it.ref.attackId == "eat_heat" })
    }

    @Test
    fun `evaluateMatchup and recommendForPart work end to end with default patch and merged sources`() =
        runTest {
            val repo = WikiRepositoryImpl(FakePackDataSource(phase2Files()))

            assertIs<WikiResult.Missing>(
                repo.evaluateMatchup(
                    MatchupEvaluateRequest(
                        partRef = EnemyPartRef("hd2.enemy.charger", "unknown_part"),
                        partState = EnemyPartState.INTACT,
                        attackRef = AttackRef("hd2.weapon.rr", "heat", "rr_heat"),
                    ),
                ),
            )

            val evalRes = assertIs<WikiResult.Success<*>>(
                repo.evaluateMatchup(
                    MatchupEvaluateRequest(
                        partRef = EnemyPartRef("hd2.enemy.charger", "head"),
                        partState = EnemyPartState.INTACT,
                        attackRef = AttackRef("hd2.weapon.rr", "heat", "rr_heat"),
                    ),
                ),
            )
            val eval =
                evalRes.data as cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
            assertEquals("01.001.200", eval.targetPatch)
            assertEquals(MatchVerdict.PENETRATES, eval.ruleResult.overallVerdict)
            assertEquals(1, eval.ruleResult.estimatedShotsToKill)
            assertTrue("src_rules" in eval.sources)
            assertTrue("src_ev" in eval.sources)

            val recRes = assertIs<WikiResult.Success<*>>(
                repo.recommendForPart(
                    partRef = EnemyPartRef("hd2.enemy.charger", "head"),
                    state = EnemyPartState.INTACT,
                    goal = RecommendationGoal.KILL,
                ),
            )
            val rec =
                recRes.data as cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
            assertEquals(1, rec.curatedRecommendations.size)
            assertEquals(
                "hd2.weapon.rr",
                rec.curatedRecommendations.first().attackOption.ref.entryId,
            )
            assertEquals(1, rec.otherViableWeapons.size)
            assertEquals(
                "hd2.stratagem.eat",
                rec.otherViableWeapons.first().attackOption.ref.entryId,
            )
            assertTrue("src_rec" in rec.sources)
        }
}
