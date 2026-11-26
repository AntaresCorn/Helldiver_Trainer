package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.model.AnatomyHitMap
import cn.antares.helldiver_trainer.data.wiki.model.AnatomyView
import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyAnatomyBundle
import cn.antares.helldiver_trainer.data.wiki.model.EnemyDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyFaction
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.EnemySizeClass
import cn.antares.helldiver_trainer.data.wiki.model.MatchVerdict
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluateRequest
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
import cn.antares.helldiver_trainer.data.wiki.model.PartBreakEffectDetail
import cn.antares.helldiver_trainer.data.wiki.model.PartBreakEffectType
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.RuleMatchupResult
import cn.antares.helldiver_trainer.data.wiki.model.WeaponClass
import cn.antares.helldiver_trainer.data.wiki.model.WeaponConfigDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponDetail
import cn.antares.helldiver_trainer.data.wiki.model.WeaponSlot
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiFact
import cn.antares.helldiver_trainer.data.wiki.model.WikiFactStatus
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiMatchReason
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest
import cn.antares.helldiver_trainer.data.wiki.model.WikiSource
import cn.antares.helldiver_trainer.data.wiki.repository.WikiRepository
import cn.antares.helldiver_trainer.viewmodel.WikiLoadState
import cn.antares.helldiver_trainer.viewmodel.WikiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WikiViewModelPhase2Test {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Phase2FakeRepository : WikiRepository {
        var missingAnatomy = false
        var failAnatomy = false
        var failRecommendation = false

        val eatRef = AttackRef("hd2.stratagem.eat", "base", "rocket")
        val rifleRef = AttackRef("hd2.weapon.liberator", "base", "bullet")

        private val attacks = listOf(
            AttackOption(eatRef, "消耗性反坦克炮", WikiKind.STRATAGEM, "基础", "火箭弹"),
            AttackOption(rifleRef, "AR-23 解放者", WikiKind.WEAPON, "基础", "实弹"),
        )

        override suspend fun ensureInitialized(): WikiResult<Unit> = WikiResult.Success(Unit)

        override suspend fun search(request: WikiSearchRequest): WikiResult<List<WikiSearchHit>> {
            val charger = WikiIndexItem(
                entryId = "hd2.enemy.charger",
                kind = WikiKind.ENEMY,
                nameZh = "强袭虫",
                enemyFaction = EnemyFaction.TERMINID,
                enemySizeClass = EnemySizeClass.HEAVY,
                normalizedNameZh = "强袭虫",
                normalizedNameZhCompact = "强袭虫",
            )
            return WikiResult.Success(listOf(WikiSearchHit(charger, WikiMatchReason.CATALOG)))
        }

        override suspend fun getEntry(entryId: String): WikiResult<WikiEntryBundle> {
            return when (entryId) {
                "hd2.enemy.charger" -> WikiResult.Success(
                    WikiEntryBundle(
                        detail = WikiEntryDetail(
                            entryId = entryId,
                            kind = WikiKind.ENEMY,
                            nameZh = "强袭虫",
                            sourceId = "src",
                            enemy = EnemyDetail(
                                faction = EnemyFaction.TERMINID,
                                sizeClass = EnemySizeClass.HEAVY,
                                familyKey = "charger",
                                parts = listOf(
                                    EnemyPartDetail(
                                        partId = "leg_armor",
                                        nameZh = "前腿甲",
                                        armor = WikiFact(WikiFactStatus.KNOWN, 5),
                                        onBreak = listOf(
                                            PartBreakEffectDetail(
                                                effectType = PartBreakEffectType.EXPOSE_PART,
                                                targetPartId = "leg_flesh",
                                                descriptionZh = "暴露前腿肉体",
                                            ),
                                        ),
                                    ),
                                    EnemyPartDetail(
                                        partId = "leg_flesh",
                                        nameZh = "前腿肉体",
                                        armor = WikiFact(WikiFactStatus.KNOWN, 0),
                                        protectedByPartIds = listOf("leg_armor"),
                                    ),
                                    EnemyPartDetail(
                                        partId = "abdomen",
                                        nameZh = "尾部臀部",
                                        armor = WikiFact(WikiFactStatus.KNOWN, 0),
                                    ),
                                ),
                            ),
                        ),
                        sources = mapOf("src" to WikiSource("src", "Wiki", "wiki_gg")),
                    ),
                )

                "hd2.stratagem.eat" -> WikiResult.Success(
                    WikiEntryBundle(
                        detail = WikiEntryDetail(
                            entryId = entryId,
                            kind = WikiKind.STRATAGEM,
                            nameZh = "消耗性反坦克炮",
                            sourceId = "src",
                            weapon = WeaponDetail(
                                WeaponSlot.PRIMARY,
                                WeaponClass.EXPLOSIVE,
                                listOf(WeaponConfigDetail("base", "基础")),
                            ),
                        ),
                        sources = mapOf("src" to WikiSource("src", "Wiki", "wiki_gg")),
                    ),
                )

                else -> WikiResult.Missing(entryId)
            }
        }

        override suspend fun related(entryId: String): WikiResult<List<WikiRelatedItem>> =
            WikiResult.Success(emptyList())

        override suspend fun getAnatomy(enemyId: String): WikiResult<EnemyAnatomyBundle> {
            if (failAnatomy) return WikiResult.ReadError("模拟图鉴读取失败")
            if (missingAnatomy) return WikiResult.Missing(enemyId)
            return WikiResult.Success(
                EnemyAnatomyBundle(
                    enemyId = enemyId,
                    defaultViewId = "front",
                    views = listOf(
                        AnatomyView(
                            viewId = "front",
                            labelZh = "正视图",
                            defaultAssetKey = "charger_front_leg_armor",
                            partAssets = mapOf(
                                "leg_armor" to "charger_front_leg_armor",
                                "leg_flesh" to "charger_front_leg_flesh",
                            ),
                            assetRevision = "2",
                            width = 512,
                            height = 512,
                            sourceId = "src",
                            hitMap = AnatomyHitMap(
                                width = 2,
                                height = 2,
                                partPalette = listOf("leg_armor", "leg_flesh"),
                                intactRle = listOf(1, 4),
                                brokenRle = listOf(2, 4),
                            ),
                        ),
                        AnatomyView(
                            viewId = "side",
                            labelZh = "侧视图",
                            defaultAssetKey = "charger_side_abdomen",
                            partAssets = mapOf(
                                "leg_armor" to "charger_side_leg_armor",
                                "abdomen" to "charger_side_abdomen",
                            ),
                            assetRevision = "2",
                            width = 512,
                            height = 512,
                            sourceId = "src",
                        ),
                    ),
                ),
            )
        }

        override suspend fun readAnatomyImageBytes(assetKey: String): WikiResult<ByteArray> =
            WikiResult.Success(byteArrayOf(1, 2, 3))

        override suspend fun evaluateMatchup(request: MatchupEvaluateRequest): WikiResult<MatchupEvaluation> =
            WikiResult.Success(
                MatchupEvaluation(
                    partRef = request.partRef,
                    partState = request.partState,
                    attackRef = request.attackRef,
                    targetPatch = "1.0",
                    ruleResult = RuleMatchupResult(
                        overallVerdict = if (request.attackRef == eatRef) {
                            MatchVerdict.STRIPS_ARMOR
                        } else {
                            MatchVerdict.RICOCHET
                        },
                    ),
                ),
            )

        override suspend fun recommendForPart(
            partRef: EnemyPartRef,
            state: EnemyPartState,
            goal: RecommendationGoal,
            patch: String?,
        ): WikiResult<PartRecommendationResult> {
            if (failRecommendation) return WikiResult.ReadError("模拟推荐计算失败")
            return WikiResult.Success(
                PartRecommendationResult(
                    partRef = partRef,
                    partState = state,
                    goal = goal,
                    targetPatch = "1.0",
                ),
            )
        }

        override suspend fun listAvailableAttacks(): WikiResult<List<AttackOption>> =
            WikiResult.Success(attacks)
    }

    @Test
    fun `loading enemy initializes anatomy part goal and analysis then selecting protected part switches to BROKEN`() =
        runTest(testDispatcher) {
            val repo = Phase2FakeRepository()
            val vm = WikiViewModel(repo).apply { searchDebounceMs = 0L }
            advanceUntilIdle()

            vm.openResultItem("hd2.enemy.charger", isWideScreen = false)
            advanceUntilIdle()

            val top = vm.uiState.value.currentDetailEntry
            assertNotNull(top)
            assertEquals("front", top.viewId)
            assertEquals("leg_armor", top.selectedPartId)
            assertEquals(EnemyPartState.INTACT, top.previewState)
            assertEquals(RecommendationGoal.STRIP_ARMOR, top.goal)
            assertEquals(repo.eatRef, top.selectedAttackRef)

            val content = assertIs<WikiLoadState.Content<*>>(vm.uiState.value.currentDetailState)
            val bundle = content.value as cn.antares.helldiver_trainer.viewmodel.WikiDetailBundle
            assertNotNull(bundle.anatomy)
            assertIs<WikiLoadState.Content<*>>(bundle.recommendationState)
            assertIs<WikiLoadState.Content<*>>(bundle.matchupState)

            // 切换视角保留当前选中部位（leg_armor 同时存在于 front 和 side，可自由切换）
            vm.selectAnatomyView("side")
            assertEquals("side", vm.uiState.value.currentDetailEntry?.viewId)
            assertEquals("leg_armor", vm.uiState.value.currentDetailEntry?.selectedPartId)

            // 点击受外甲保护的 leg_flesh 后，自动切换为 BROKEN 状态、ATTACK_EXPOSED 目的及包含该部位的 front 视角
            vm.selectAnatomyPart("leg_flesh")
            advanceUntilIdle()
            val afterSelectExposed = vm.uiState.value.currentDetailEntry
            assertEquals(EnemyPartState.BROKEN, afterSelectExposed?.previewState)
            assertEquals("leg_flesh", afterSelectExposed?.selectedPartId)
            assertEquals("front", afterSelectExposed?.viewId)
            assertEquals(RecommendationGoal.ATTACK_EXPOSED, afterSelectExposed?.goal)
            val exposedBundle = (vm.uiState.value.currentDetailState as WikiLoadState.Content).value
            val recContent =
                assertIs<WikiLoadState.Content<PartRecommendationResult>>(exposedBundle.recommendationState)
            assertEquals(EnemyPartState.BROKEN, recContent.value.partState)

            // 切回无外甲保护的 leg_armor 后，自动恢复为 INTACT 状态且目的不再停留在 ATTACK_EXPOSED
            vm.selectAnatomyPart("leg_armor")
            advanceUntilIdle()
            val afterBackToArmor = vm.uiState.value.currentDetailEntry
            assertEquals(EnemyPartState.INTACT, afterBackToArmor?.previewState)
            assertEquals("leg_armor", afterBackToArmor?.selectedPartId)
            assertEquals(RecommendationGoal.STRIP_ARMOR, afterBackToArmor?.goal)
        }

    @Test
    fun `opening recommended attack and popping back restores enemy anatomy selection state`() =
        runTest(testDispatcher) {
            val repo = Phase2FakeRepository()
            val vm = WikiViewModel(repo).apply { searchDebounceMs = 0L }
            advanceUntilIdle()

            vm.openResultItem("hd2.enemy.charger", isWideScreen = false)
            advanceUntilIdle()

            vm.selectAnatomyView("side")
            vm.selectRecommendationGoal(RecommendationGoal.KILL)
            vm.selectCompareAttack(repo.rifleRef)
            advanceUntilIdle()

            vm.openRecommendedAttack(repo.eatRef)
            advanceUntilIdle()
            assertEquals(
                listOf("hd2.enemy.charger", "hd2.stratagem.eat"),
                vm.uiState.value.detailStack.map { it.entryId },
            )

            assertTrue(vm.onBack())
            advanceUntilIdle()
            val restored = vm.uiState.value.currentDetailEntry
            assertEquals("hd2.enemy.charger", restored?.entryId)
            assertEquals("side", restored?.viewId)
            assertEquals(RecommendationGoal.KILL, restored?.goal)
            assertEquals(repo.rifleRef, restored?.selectedAttackRef)
        }

    @Test
    fun `missing and error anatomy states plus retryEnemyAnalysis recover cleanly`() =
        runTest(testDispatcher) {
            val repo = Phase2FakeRepository().apply {
                failAnatomy = true
                failRecommendation = true
            }
            val vm = WikiViewModel(repo).apply { searchDebounceMs = 0L }
            advanceUntilIdle()

            vm.openResultItem("hd2.enemy.charger", isWideScreen = false)
            advanceUntilIdle()

            val firstContent = (vm.uiState.value.currentDetailState as WikiLoadState.Content).value
            assertNotNull(firstContent.anatomyError)
            assertIs<WikiLoadState.Error>(firstContent.recommendationState)

            // 修复后重试
            repo.failAnatomy = false
            repo.failRecommendation = false
            vm.retryEnemyAnalysis()
            advanceUntilIdle()

            val recovered = (vm.uiState.value.currentDetailState as WikiLoadState.Content).value
            assertEquals(null, recovered.anatomyError)
            assertNotNull(recovered.anatomy)
            assertIs<WikiLoadState.Content<*>>(recovered.recommendationState)
        }

    @Test
    fun `selecting a part missing from current view auto-switches and locks single-view parts until switching back`() =
        runTest(testDispatcher) {
            val repo = Phase2FakeRepository()
            val vm = WikiViewModel(repo).apply { searchDebounceMs = 0L }
            advanceUntilIdle()

            vm.openResultItem("hd2.enemy.charger", isWideScreen = false)
            advanceUntilIdle()
            assertEquals("front", vm.uiState.value.currentDetailEntry?.viewId)

            // abdomen 仅存在于 side 视角，选中时自动切到 side 并锁定视角不可切换
            vm.selectAnatomyPart("abdomen")
            advanceUntilIdle()
            assertEquals("abdomen", vm.uiState.value.currentDetailEntry?.selectedPartId)
            assertEquals("side", vm.uiState.value.currentDetailEntry?.viewId)
            vm.selectAnatomyView("front")
            assertEquals("side", vm.uiState.value.currentDetailEntry?.viewId)

            // leg_flesh 仅存在于 front 视角，选中时自动切到 front 并锁定视角不可切换
            vm.selectAnatomyPart("leg_flesh")
            advanceUntilIdle()
            assertEquals("leg_flesh", vm.uiState.value.currentDetailEntry?.selectedPartId)
            assertEquals("front", vm.uiState.value.currentDetailEntry?.viewId)
            assertEquals(EnemyPartState.BROKEN, vm.uiState.value.currentDetailEntry?.previewState)
            vm.selectAnatomyView("side")
            assertEquals("front", vm.uiState.value.currentDetailEntry?.viewId)

            // 切回同时存在于 front 和 side 的 leg_armor 后恢复可切换视角
            vm.selectAnatomyPart("leg_armor")
            advanceUntilIdle()
            assertEquals(EnemyPartState.INTACT, vm.uiState.value.currentDetailEntry?.previewState)
            vm.selectAnatomyView("side")
            assertEquals("side", vm.uiState.value.currentDetailEntry?.viewId)
        }
}
