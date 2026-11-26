package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.datasource.WikiPackDataSource
import cn.antares.helldiver_trainer.data.wiki.model.WikiFactStatus
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelationType
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest
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

class WikiRepositoryTest {

    private class FakePackDataSource(
        initialFiles: Map<String, String>,
        private val readDelayMs: Long = 0L,
    ) : WikiPackDataSource {
        val files = initialFiles.toMutableMap()
        val readCounts = HashMap<String, Int>()

        override suspend fun readBytes(relativePath: String): ByteArray {
            readCounts[relativePath] = (readCounts[relativePath] ?: 0) + 1
            if (readDelayMs > 0L) delay(readDelayMs.milliseconds)
            val content = files[relativePath] ?: error("Missing file: $relativePath")
            return content.encodeToByteArray()
        }
    }

    private fun baseFiles(): MutableMap<String, String> = mutableMapOf(
        "files/wiki/manifest.json" to """
            {
              "schemaVersion": 2,
              "packVersion": "2.0.0",
              "generatedFromAppVersion": "1.8.0",
              "entryCount": 4,
              "indexPath": "files/wiki/index.json",
              "detailPaths": {
                "stratagem": "files/wiki/details/stratagem.json",
                "weapon": "files/wiki/details/weapon.json",
                "enemy": "files/wiki/details/enemy.json"
              }
            }
        """.trimIndent(),
        "files/wiki/index.json" to """
            {
              "entries": [
                {
                  "entryId": "hd2.stratagem.autocannon",
                  "kind": "stratagem",
                  "nameZh": "机炮",
                  "iconKey": "sw_autocannon",
                  "stratagemCategory": "support_weapon",
                  "directions": ["DOWN", "LEFT", "DOWN", "UP", "UP", "RIGHT"],
                  "trainingOrder": 24,
                  "normalizedNameZh": "机炮",
                  "normalizedNameZhCompact": "机炮"
                },
                {
                  "entryId": "hd2.weapon.autocannon",
                  "kind": "weapon",
                  "nameZh": "AC-8 机炮",
                  "iconKey": "sw_autocannon",
                  "weaponSlot": "primary",
                  "weaponClass": "assault_rifle",
                  "modelCodes": ["AC-8"],
                  "normalizedNameZh": "ac8 机炮",
                  "normalizedNameZhCompact": "ac8机炮",
                  "normalizedAliases": [{ "text": "机炮", "compact": "机炮" }],
                  "normalizedModelCodes": [{ "text": "ac8", "compact": "ac8" }]
                },
                {
                  "entryId": "hd2.enemy.charger",
                  "kind": "enemy",
                  "nameZh": "强袭虫",
                  "enemyFaction": "terminid",
                  "enemySizeClass": "heavy",
                  "normalizedNameZh": "强袭虫",
                  "normalizedNameZhCompact": "强袭虫"
                },
                {
                  "entryId": "hd2.enemy.charger_behemoth",
                  "kind": "enemy",
                  "nameZh": "巨兽强袭虫",
                  "enemyFaction": "terminid",
                  "enemySizeClass": "heavy",
                  "normalizedNameZh": "巨兽强袭虫",
                  "normalizedNameZhCompact": "巨兽强袭虫"
                }
              ],
              "relations": [
                {
                  "ownerEntryId": "hd2.stratagem.autocannon",
                  "targetEntryId": "hd2.weapon.autocannon",
                  "relationType": "calls_in",
                  "isReverse": false
                },
                {
                  "ownerEntryId": "hd2.weapon.autocannon",
                  "targetEntryId": "hd2.stratagem.autocannon",
                  "relationType": "calls_in",
                  "isReverse": true
                },
                {
                  "ownerEntryId": "hd2.enemy.charger_behemoth",
                  "targetEntryId": "hd2.enemy.charger",
                  "relationType": "variant_of",
                  "isReverse": false
                },
                {
                  "ownerEntryId": "hd2.enemy.charger",
                  "targetEntryId": "hd2.enemy.charger_behemoth",
                  "relationType": "variant_of",
                  "isReverse": true
                }
              ]
            }
        """.trimIndent(),
        "files/wiki/details/stratagem.json" to """
            {
              "kind": "stratagem",
              "entries": {
                "hd2.stratagem.autocannon": {
                  "entryId": "hd2.stratagem.autocannon",
                  "kind": "stratagem",
                  "nameZh": "机炮",
                  "iconKey": "sw_autocannon",
                  "sourceId": "src_mig",
                  "stratagem": {
                    "category": "support_weapon",
                    "directions": ["DOWN", "LEFT", "DOWN", "UP", "UP", "RIGHT"],
                    "cooldownSeconds": { "status": "unknown" }
                  }
                }
              },
              "sources": {
                "src_mig": {
                  "sourceId": "src_mig",
                  "title": "Project Catalog",
                  "sourceType": "project_migration"
                }
              }
            }
        """.trimIndent(),
        "files/wiki/details/weapon.json" to """
            {
              "kind": "weapon",
              "entries": {
                "hd2.weapon.autocannon": {
                  "entryId": "hd2.weapon.autocannon",
                  "kind": "weapon",
                  "nameZh": "AC-8 机炮",
                  "sourceId": "src_wiki",
                  "weapon": {
                    "slot": "primary",
                    "weaponClass": "assault_rifle",
                    "configurations": [
                      { "configId": "base", "labelZh": "基础配置", "modelCode": "AC-8" }
                    ]
                  }
                }
              },
              "sources": {
                "src_wiki": {
                  "sourceId": "src_wiki",
                  "title": "Wiki.gg",
                  "sourceType": "wiki_gg"
                }
              }
            }
        """.trimIndent(),
        "files/wiki/details/enemy.json" to """
            {
              "kind": "enemy",
              "entries": {
                "hd2.enemy.charger": {
                  "entryId": "hd2.enemy.charger",
                  "kind": "enemy",
                  "nameZh": "强袭虫",
                  "sourceId": "src_wiki",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "heavy",
                    "familyKey": "charger",
                    "parts": [{ "partId": "head", "nameZh": "头部", "armor": { "status": "known", "value": 4 } }]
                  }
                },
                "hd2.enemy.charger_behemoth": {
                  "entryId": "hd2.enemy.charger_behemoth",
                  "kind": "enemy",
                  "nameZh": "巨兽强袭虫",
                  "sourceId": "src_wiki",
                  "enemy": {
                    "faction": "terminid",
                    "sizeClass": "heavy",
                    "familyKey": "charger",
                    "parts": [{ "partId": "head", "nameZh": "头部", "armor": { "status": "known", "value": 4 }, "notes": "变种强化" }]
                  }
                }
              },
              "sources": {
                "src_wiki": {
                  "sourceId": "src_wiki",
                  "title": "Wiki.gg",
                  "sourceType": "wiki_gg"
                }
              }
            }
        """.trimIndent(),
    )

    @Test
    fun `unsupported schemaVersion returns UnsupportedVersion`() = runTest {
        val files = baseFiles()
        files["files/wiki/manifest.json"] = files.getValue("files/wiki/manifest.json")
            .replace("\"schemaVersion\": 2", "\"schemaVersion\": 99")
        val repo = WikiRepositoryImpl(
            FakePackDataSource(files),
            coroutineContext[kotlin.coroutines.ContinuationInterceptor] as kotlinx.coroutines.CoroutineDispatcher,
        )

        val res = repo.ensureInitialized()
        val unsupported = assertIs<WikiResult.UnsupportedVersion>(res)
        assertEquals(99, unsupported.foundVersion)
        assertEquals(2, unsupported.supportedVersion)
    }

    @Test
    fun `missing entryId returns Missing`() = runTest {
        val repo = WikiRepositoryImpl(FakePackDataSource(baseFiles()))
        val res = repo.getEntry("hd2.weapon.unknown")
        val missing = assertIs<WikiResult.Missing>(res)
        assertEquals("hd2.weapon.unknown", missing.entryId)
    }

    @Test
    fun `corrupt shard returns ReadError, does not cache failure, and recovers on retry`() =
        runTest {
            val files = baseFiles()
            files["files/wiki/details/weapon.json"] = "{ invalid json"
            val ds = FakePackDataSource(files)
            val repo = WikiRepositoryImpl(ds)

            val first = repo.getEntry("hd2.weapon.autocannon")
            assertIs<WikiResult.ReadError>(first)

            // 修复分片后重试
            ds.files["files/wiki/details/weapon.json"] =
                baseFiles().getValue("files/wiki/details/weapon.json")
            val second = repo.getEntry("hd2.weapon.autocannon")
            val success = assertIs<WikiResult.Success<*>>(second)
            assertEquals(2, ds.readCounts["files/wiki/details/weapon.json"])
            assertEquals(
                WikiKind.WEAPON,
                (success.data as cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle).detail.kind,
            )
        }

    @Test
    fun `concurrent detail requests for same kind trigger single read`() = runTest {
        val ds = FakePackDataSource(baseFiles(), readDelayMs = 20L)
        val repo = WikiRepositoryImpl(ds)
        assertIs<WikiResult.Success<Unit>>(repo.ensureInitialized())

        val results = (1..10).map {
            async { repo.getEntry("hd2.stratagem.autocannon") }
        }.awaitAll()

        assertTrue(results.all { it is WikiResult.Success })
        assertEquals(1, ds.readCounts["files/wiki/details/stratagem.json"])
        val bundle = (results.first() as WikiResult.Success).data
        assertEquals(WikiFactStatus.UNKNOWN, bundle.detail.stratagem!!.cooldownSeconds.status)
    }

    @Test
    fun `bidirectional relations and search work end to end`() = runTest {
        val repo = WikiRepositoryImpl(FakePackDataSource(baseFiles()))
        val searchRes =
            assertIs<WikiResult.Success<List<cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit>>>(
                repo.search(WikiSearchRequest(text = "机炮")),
            )
        assertEquals(2, searchRes.data.size)

        val fromStrat =
            assertIs<WikiResult.Success<List<cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem>>>(
                repo.related("hd2.stratagem.autocannon"),
            )
        assertEquals(1, fromStrat.data.size)
        assertEquals("呼叫的装备", fromStrat.data.first().labelZh)
        assertEquals(WikiRelationType.CALLS_IN, fromStrat.data.first().relationType)

        val fromWeapon =
            assertIs<WikiResult.Success<List<cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem>>>(
                repo.related("hd2.weapon.autocannon"),
            )
        assertEquals("对应战备", fromWeapon.data.first().labelZh)

        val fromCharger =
            assertIs<WikiResult.Success<List<cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem>>>(
                repo.related("hd2.enemy.charger"),
            )
        assertEquals("已收录变种", fromCharger.data.first().labelZh)
        assertEquals("hd2.enemy.charger_behemoth", fromCharger.data.first().target.entryId)
    }
}
