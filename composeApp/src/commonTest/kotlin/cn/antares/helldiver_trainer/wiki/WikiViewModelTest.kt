package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.EnemyAnatomyBundle
import cn.antares.helldiver_trainer.data.wiki.model.EnemyDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyFaction
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.EnemySizeClass
import cn.antares.helldiver_trainer.data.wiki.model.MatchVerdict
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluateRequest
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.RuleMatchupResult
import cn.antares.helldiver_trainer.data.wiki.model.StratagemCategory
import cn.antares.helldiver_trainer.data.wiki.model.StratagemDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponClass
import cn.antares.helldiver_trainer.data.wiki.model.WeaponConfigDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponSlot
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiMatchReason
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelationType
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest
import cn.antares.helldiver_trainer.data.wiki.model.WikiSource
import cn.antares.helldiver_trainer.data.wiki.repository.WikiRepository
import cn.antares.helldiver_trainer.viewmodel.WikiLoadState
import cn.antares.helldiver_trainer.viewmodel.WikiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WikiViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeWikiRepository : WikiRepository {
        var searchDelays = mutableMapOf<String, Long>()
        var failDetailFor = mutableSetOf<String>()

        private val items = mapOf(
            "hd2.stratagem.autocannon" to WikiIndexItem(
                entryId = "hd2.stratagem.autocannon",
                kind = WikiKind.STRATAGEM,
                nameZh = "机炮",
                stratagemCategory = StratagemCategory.SUPPORT_WEAPON,
                normalizedNameZh = "机炮",
                normalizedNameZhCompact = "机炮",
            ),
            "hd2.weapon.autocannon" to WikiIndexItem(
                entryId = "hd2.weapon.autocannon",
                kind = WikiKind.WEAPON,
                nameZh = "AC-8 机炮",
                weaponSlot = WeaponSlot.PRIMARY,
                weaponClass = WeaponClass.ASSAULT_RIFLE,
                normalizedNameZh = "ac8 机炮",
                normalizedNameZhCompact = "ac8机炮",
            ),
            "hd2.enemy.charger" to WikiIndexItem(
                entryId = "hd2.enemy.charger",
                kind = WikiKind.ENEMY,
                nameZh = "强袭虫",
                enemyFaction = EnemyFaction.TERMINID,
                enemySizeClass = EnemySizeClass.HEAVY,
                normalizedNameZh = "强袭虫",
                normalizedNameZhCompact = "强袭虫",
            ),
        )

        override suspend fun ensureInitialized(): WikiResult<Unit> = WikiResult.Success(Unit)

        override suspend fun search(request: WikiSearchRequest): WikiResult<List<WikiSearchHit>> {
            val delayMs = searchDelays[request.text] ?: 0L
            if (delayMs > 0L) delay(delayMs)
            val matched = items.values.filter { item ->
                (request.kind == null || item.kind == request.kind) &&
                        (request.text.isBlank() || item.nameZh.contains(request.text))
            }
            return WikiResult.Success(matched.map { WikiSearchHit(it, WikiMatchReason.CATALOG) })
        }

        override suspend fun getEntry(entryId: String): WikiResult<WikiEntryBundle> {
            if (entryId in failDetailFor) {
                return WikiResult.ReadError("模拟分片读取失败")
            }
            val item = items[entryId] ?: return WikiResult.Missing(entryId)
            val detail = WikiEntryDetail(
                entryId = entryId,
                kind = item.kind,
                nameZh = item.nameZh,
                sourceId = "src",
                stratagem = if (item.kind == WikiKind.STRATAGEM) StratagemDetail(StratagemCategory.SUPPORT_WEAPON) else null,
                weapon = if (item.kind == WikiKind.WEAPON) {
                    WeaponDetail(
                        WeaponSlot.PRIMARY,
                        WeaponClass.ASSAULT_RIFLE,
                        listOf(WeaponConfigDetail("base", "基础")),
                    )
                } else null,
                enemy = if (item.kind == WikiKind.ENEMY) {
                    EnemyDetail(EnemyFaction.TERMINID, EnemySizeClass.HEAVY, "charger")
                } else null,
            )
            return WikiResult.Success(
                WikiEntryBundle(detail, mapOf("src" to WikiSource("src", "Test", "wiki_gg"))),
            )
        }

        override suspend fun related(entryId: String): WikiResult<List<WikiRelatedItem>> {
            val list = when (entryId) {
                "hd2.stratagem.autocannon" -> listOf(
                    WikiRelatedItem(
                        WikiRelationType.CALLS_IN,
                        false,
                        "呼叫的装备",
                        items.getValue("hd2.weapon.autocannon"),
                    ),
                )

                "hd2.weapon.autocannon" -> listOf(
                    WikiRelatedItem(
                        WikiRelationType.CALLS_IN,
                        true,
                        "对应战备",
                        items.getValue("hd2.stratagem.autocannon"),
                    ),
                )

                else -> emptyList()
            }
            return WikiResult.Success(list)
        }

        override suspend fun getAnatomy(enemyId: String): WikiResult<EnemyAnatomyBundle> =
            WikiResult.Missing(enemyId)

        override suspend fun readAnatomyImageBytes(assetKey: String): WikiResult<ByteArray> =
            WikiResult.Missing(assetKey)

        override suspend fun evaluateMatchup(request: MatchupEvaluateRequest): WikiResult<MatchupEvaluation> =
            WikiResult.Success(
                MatchupEvaluation(
                    partRef = request.partRef,
                    partState = request.partState,
                    attackRef = request.attackRef,
                    targetPatch = request.targetPatch ?: "latest",
                    ruleResult = RuleMatchupResult(overallVerdict = MatchVerdict.UNKNOWN),
                ),
            )

        override suspend fun recommendForPart(
            partRef: EnemyPartRef,
            state: EnemyPartState,
            goal: RecommendationGoal,
            patch: String?,
        ): WikiResult<PartRecommendationResult> =
            WikiResult.Success(
                PartRecommendationResult(
                    partRef = partRef,
                    partState = state,
                    goal = goal,
                    targetPatch = patch ?: "latest",
                ),
            )

        override suspend fun listAvailableAttacks(): WikiResult<List<AttackOption>> =
            WikiResult.Success(emptyList())
    }

    @Test
    fun `narrow screen pushes stack and back unwinds detail then list then home`() =
        runTest(testDispatcher) {
            val vm = WikiViewModel(FakeWikiRepository()).apply { searchDebounceMs = 0L }
            advanceUntilIdle()

            assertTrue(vm.uiState.value.query.isHomeMode)
            vm.updateQueryText("机炮", isWideScreen = false)
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.results.size)

            vm.openResultItem("hd2.stratagem.autocannon", isWideScreen = false)
            advanceUntilIdle()
            assertEquals(
                listOf("hd2.stratagem.autocannon"),
                vm.uiState.value.detailStack.map { it.entryId },
            )

            vm.openRelatedItem("hd2.weapon.autocannon")
            advanceUntilIdle()
            assertEquals(
                listOf("hd2.stratagem.autocannon", "hd2.weapon.autocannon"),
                vm.uiState.value.detailStack.map { it.entryId },
            )

            // 点击栈中已存在的关联条目时回退到该层，而不是重复压栈
            vm.openRelatedItem("hd2.stratagem.autocannon")
            advanceUntilIdle()
            assertEquals(
                listOf("hd2.stratagem.autocannon"),
                vm.uiState.value.detailStack.map { it.entryId },
            )

            // 重新打开武器详情后逐步返回
            vm.openRelatedItem("hd2.weapon.autocannon")
            advanceUntilIdle()

            assertTrue(vm.onBack()) // 弹出武器详情 -> 返回战备详情
            advanceUntilIdle()
            assertEquals(
                listOf("hd2.stratagem.autocannon"),
                vm.uiState.value.detailStack.map { it.entryId },
            )

            assertTrue(vm.onBack()) // 弹出战备详情 -> 返回搜索列表
            advanceUntilIdle()
            assertTrue(vm.uiState.value.detailStack.isEmpty())
            assertNull(vm.uiState.value.currentDetailState)
            assertEquals("机炮", vm.uiState.value.query.queryText)

            assertTrue(vm.onBack()) // 搜索列表 -> 返回首页
            advanceUntilIdle()
            assertTrue(vm.uiState.value.query.isHomeMode)

            assertFalse(vm.onBack()) // 首页 -> 退出百科
        }

    @Test
    fun `wide screen resets stack on result click and clears detail when query changes`() =
        runTest(testDispatcher) {
            val vm = WikiViewModel(FakeWikiRepository()).apply { searchDebounceMs = 0L }
            advanceUntilIdle()

            vm.selectKind(WikiKind.STRATAGEM)
            advanceUntilIdle()

            vm.openResultItem("hd2.stratagem.autocannon", isWideScreen = true)
            advanceUntilIdle()
            vm.openRelatedItem("hd2.weapon.autocannon")
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.detailStack.size)

            // 宽屏下点击另一条搜索结果会将详情栈重置为单个根节点
            vm.openResultItem("hd2.enemy.charger", isWideScreen = true)
            advanceUntilIdle()
            assertEquals(
                listOf("hd2.enemy.charger"),
                vm.uiState.value.detailStack.map { it.entryId },
            )

            // 宽屏下修改查询词会清空详情栈
            vm.updateQueryText("强袭", isWideScreen = true)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.detailStack.isEmpty())
            assertNull(vm.uiState.value.currentDetailState)
        }

    @Test
    fun `slow older search does not overwrite newer fast search and detail retry preserves list`() =
        runTest(testDispatcher) {
            val repo = FakeWikiRepository()
            repo.searchDelays["强袭"] = 500L
            repo.searchDelays["机炮"] = 10L
            repo.failDetailFor.add("hd2.weapon.autocannon")

            val vm = WikiViewModel(repo).apply { searchDebounceMs = 0L }
            advanceUntilIdle()

            vm.updateQueryText("强袭", isWideScreen = false)
            vm.updateQueryText("机炮", isWideScreen = false)
            advanceUntilIdle()
            assertEquals(2, vm.uiState.value.results.size)
            assertTrue(vm.uiState.value.results.all { it.item.nameZh.contains("机炮") })

            // 详情加载失败时保留搜索结果列表，且重试后可成功加载
            vm.openResultItem("hd2.weapon.autocannon", isWideScreen = false)
            advanceUntilIdle()
            assertIs<WikiLoadState.Error>(vm.uiState.value.currentDetailState)
            assertEquals(2, vm.uiState.value.results.size)

            repo.failDetailFor.remove("hd2.weapon.autocannon")
            vm.retryCurrentDetail()
            advanceUntilIdle()
            assertIs<WikiLoadState.Content<*>>(vm.uiState.value.currentDetailState)
        }

    @Test
    fun `switching weapon slot clears incompatible weaponClass`() = runTest(testDispatcher) {
        val vm = WikiViewModel(FakeWikiRepository()).apply { searchDebounceMs = 0L }
        advanceUntilIdle()

        vm.updateWeaponSlot(WeaponSlot.PRIMARY)
        advanceUntilIdle()
        vm.updateWeaponClass(WeaponClass.ASSAULT_RIFLE)
        advanceUntilIdle()
        assertEquals(WeaponClass.ASSAULT_RIFLE, vm.uiState.value.query.weaponClass)

        vm.updateWeaponSlot(WeaponSlot.SECONDARY)
        advanceUntilIdle()
        assertNull(vm.uiState.value.query.weaponClass)

        vm.updateWeaponClass(WeaponClass.MELEE)
        advanceUntilIdle()
        assertEquals(WeaponClass.MELEE, vm.uiState.value.query.weaponClass)

        vm.updateEnemySizeClass(EnemySizeClass.SMALL)
        advanceUntilIdle()
        assertNull(vm.uiState.value.query.weaponSlot)
        assertNull(vm.uiState.value.query.weaponClass)
        assertEquals(EnemySizeClass.SMALL, vm.uiState.value.query.enemySizeClass)
    }
}
