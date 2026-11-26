package cn.antares.helldiver_trainer.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyAnatomyBundle
import cn.antares.helldiver_trainer.data.wiki.model.EnemyFaction
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartDetail
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.EnemySizeClass
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluateRequest
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
import cn.antares.helldiver_trainer.data.wiki.model.PartBreakEffectType
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.StratagemCategory
import cn.antares.helldiver_trainer.data.wiki.model.WeaponClass
import cn.antares.helldiver_trainer.data.wiki.model.WeaponSlot
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest
import cn.antares.helldiver_trainer.data.wiki.repository.WikiRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 资料库检索与筛选条件状态，同时保存列表的滚动位置以便返回列表时恢复。
 */
data class WikiQueryState(
    val queryText: String = "",
    val selectedKind: WikiKind? = null,
    val stratagemCategory: StratagemCategory? = null,
    val weaponSlot: WeaponSlot? = null,
    val weaponClass: WeaponClass? = null,
    val enemyFaction: EnemyFaction? = null,
    val enemySizeClass: EnemySizeClass? = null,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
) {
    /** 未选中大类且搜索词为空时处于首页浏览态。 */
    val isHomeMode: Boolean get() = selectedKind == null && queryText.isBlank()

    /** 当前是否激活了任意细分筛选条件。 */
    val hasActiveFacet: Boolean
        get() = stratagemCategory != null ||
                weaponSlot != null ||
                weaponClass != null ||
                enemyFaction != null ||
                enemySizeClass != null
}

/**
 * 详情浏览栈中的单个节点，记录条目 ID、列表滚动偏移及二期敌人图鉴/对位分析交互状态。
 */
data class DetailStackEntry(
    val entryId: String,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    val viewId: String? = null,
    val selectedPartId: String? = null,
    val previewState: EnemyPartState = EnemyPartState.INTACT,
    val goal: RecommendationGoal? = null,
    val selectedAttackRef: AttackRef? = null,
    val targetPatch: String? = null,
)

/**
 * 详情页渲染所需的聚合数据包，包含条目正文、关联条目列表及敌人解剖/对位分析状态。
 */
data class WikiDetailBundle(
    val entry: WikiEntryBundle,
    val related: List<WikiRelatedItem>,
    val anatomy: EnemyAnatomyBundle? = null,
    val anatomyMissing: Boolean = false,
    val anatomyError: String? = null,
    val availableAttacks: List<AttackOption> = emptyList(),
    val recommendationState: WikiLoadState<PartRecommendationResult>? = null,
    val matchupState: WikiLoadState<MatchupEvaluation>? = null,
)

/**
 * 资料库异步加载状态，统一建模加载中、成功内容与可区分版本不兼容的错误态。
 */
sealed interface WikiLoadState<out T> {
    data object Loading : WikiLoadState<Nothing>
    data class Content<T>(val value: T) : WikiLoadState<T>
    data class Error(
        val message: String,
        val isUnsupportedVersion: Boolean = false,
    ) : WikiLoadState<Nothing>
}

/**
 * 资料库页面的完整 UI 状态快照，覆盖索引初始化、检索结果与详情浏览栈。
 */
data class WikiUiState(
    val indexState: WikiLoadState<Unit> = WikiLoadState.Loading,
    val kindCounts: Map<WikiKind, Int> = emptyMap(),
    val query: WikiQueryState = WikiQueryState(),
    val results: List<WikiSearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val detailStack: List<DetailStackEntry> = emptyList(),
    val currentDetailState: WikiLoadState<WikiDetailBundle>? = null,
) {
    /** 当前栈顶正在展示的条目 ID。 */
    val currentDetailId: String? get() = detailStack.lastOrNull()?.entryId

    /** 当前栈顶的详情栈节点。 */
    val currentDetailEntry: DetailStackEntry? get() = detailStack.lastOrNull()
}

/**
 * 资料库业务状态管理器，负责资料包初始化、防抖检索、细筛联动与详情栈导航。
 */
class WikiViewModel(
    private val repository: WikiRepository,
) : ViewModel() {

    internal var searchDebounceMs: Long = 120L

    private val _uiState = MutableStateFlow(WikiUiState())
    val uiState: StateFlow<WikiUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var analysisJob: Job? = null
    private var searchRequestSeq = 0L
    private var detailRequestSeq = 0L
    private var analysisRequestSeq = 0L

    init {
        retryInit()
    }

    /**
     * 初始化或重试加载资料库索引；成功后统计各大类条目数，若已存在查询条件则立即执行检索。
     */
    fun retryInit() {
        initJob?.cancel()
        _uiState.update { it.copy(indexState = WikiLoadState.Loading) }
        initJob = viewModelScope.launch {
            when (val res = repository.ensureInitialized()) {
                is WikiResult.Success -> {
                    val counts = when (val all = repository.search(WikiSearchRequest())) {
                        is WikiResult.Success -> all.data.groupingBy { it.item.kind }.eachCount()
                        else -> emptyMap()
                    }
                    _uiState.update {
                        it.copy(
                            indexState = WikiLoadState.Content(Unit),
                            kindCounts = counts,
                        )
                    }
                    val currentQuery = _uiState.value.query
                    if (!currentQuery.isHomeMode) {
                        executeSearch(currentQuery, debounce = false)
                    }
                }

                is WikiResult.UnsupportedVersion -> {
                    _uiState.update {
                        it.copy(
                            indexState = WikiLoadState.Error(
                                message = "资料包格式版本过新（包版本 v${res.foundVersion}，当前应用支持 v${res.supportedVersion}），请升级应用。",
                                isUnsupportedVersion = true,
                            ),
                        )
                    }
                }

                is WikiResult.ReadError -> {
                    _uiState.update {
                        it.copy(indexState = WikiLoadState.Error(res.message))
                    }
                }

                is WikiResult.Missing -> {
                    _uiState.update {
                        it.copy(indexState = WikiLoadState.Error("未找到资料库索引文件"))
                    }
                }
            }
        }
    }

    /**
     * 更新搜索关键词并重置列表滚动位置。
     * 宽屏模式下编辑搜索词时主动清空详情栈与加载任务，避免右侧残留与当前搜索无关的详情内容。
     */
    fun updateQueryText(text: String, isWideScreen: Boolean) {
        val prev = _uiState.value
        val nextQuery = prev.query.copy(
            queryText = text,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        val shouldClearDetail = isWideScreen && prev.detailStack.isNotEmpty()
        if (shouldClearDetail) {
            detailJob?.cancel()
            detailRequestSeq++
            analysisJob?.cancel()
            analysisRequestSeq++
        }
        _uiState.update { state ->
            state.copy(
                query = nextQuery,
                detailStack = if (shouldClearDetail) emptyList() else state.detailStack,
                currentDetailState = if (shouldClearDetail) null else state.currentDetailState,
                results = if (nextQuery.isHomeMode) emptyList() else state.results,
                searchError = null,
            )
        }
        if (!nextQuery.isHomeMode) {
            executeSearch(nextQuery, debounce = text.isNotBlank())
        } else {
            searchJob?.cancel()
            searchRequestSeq++
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    /**
     * 切换条目大类：保留目标大类对应的细分筛选状态，同时清理其他大类的残留细筛条件。
     */
    fun selectKind(kind: WikiKind?) {
        val currentQuery = _uiState.value.query
        val nextQuery = currentQuery.copy(
            selectedKind = kind,
            stratagemCategory = if (kind == WikiKind.STRATAGEM) currentQuery.stratagemCategory else null,
            weaponSlot = if (kind == WikiKind.WEAPON) currentQuery.weaponSlot else null,
            weaponClass = if (kind == WikiKind.WEAPON) currentQuery.weaponClass else null,
            enemyFaction = if (kind == WikiKind.ENEMY) currentQuery.enemyFaction else null,
            enemySizeClass = if (kind == WikiKind.ENEMY) currentQuery.enemySizeClass else null,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        _uiState.update {
            it.copy(
                query = nextQuery,
                results = if (nextQuery.isHomeMode) emptyList() else it.results,
                searchError = null,
            )
        }
        if (!nextQuery.isHomeMode) {
            executeSearch(nextQuery, debounce = false)
        } else {
            searchJob?.cancel()
            searchRequestSeq++
            _uiState.update { it.copy(isSearching = false) }
        }
    }

    fun updateStratagemCategory(category: StratagemCategory?) {
        val nextQuery = _uiState.value.query.copy(
            selectedKind = WikiKind.STRATAGEM,
            stratagemCategory = category,
            weaponSlot = null,
            weaponClass = null,
            enemyFaction = null,
            enemySizeClass = null,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        _uiState.update { it.copy(query = nextQuery, searchError = null) }
        executeSearch(nextQuery, debounce = false)
    }

    /**
     * 更新武器槽位筛选，并与当前已选二级子类做联动校验（若原子类不属于新槽位则自动清空）。
     */
    fun updateWeaponSlot(slot: WeaponSlot?) {
        val currentClass = _uiState.value.query.weaponClass
        val nextClass =
            if (slot != null && currentClass != null && currentClass in slot.allowedClasses()) {
                currentClass
            } else {
                null
            }
        val nextQuery = _uiState.value.query.copy(
            selectedKind = WikiKind.WEAPON,
            stratagemCategory = null,
            weaponSlot = slot,
            weaponClass = nextClass,
            enemyFaction = null,
            enemySizeClass = null,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        _uiState.update { it.copy(query = nextQuery, searchError = null) }
        executeSearch(nextQuery, debounce = false)
    }

    /**
     * 更新武器二级子类筛选，仅在当前已选槽位允许该子类时保留，防止出现无效组合筛选。
     */
    fun updateWeaponClass(weaponClass: WeaponClass?) {
        val currentSlot = _uiState.value.query.weaponSlot
        val validClass =
            if (currentSlot != null && weaponClass != null && weaponClass in currentSlot.allowedClasses()) {
                weaponClass
            } else {
                null
            }
        val nextQuery = _uiState.value.query.copy(
            selectedKind = WikiKind.WEAPON,
            stratagemCategory = null,
            weaponClass = validClass,
            enemyFaction = null,
            enemySizeClass = null,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        _uiState.update { it.copy(query = nextQuery, searchError = null) }
        executeSearch(nextQuery, debounce = false)
    }

    fun updateEnemyFaction(faction: EnemyFaction?) {
        val nextQuery = _uiState.value.query.copy(
            selectedKind = WikiKind.ENEMY,
            stratagemCategory = null,
            weaponSlot = null,
            weaponClass = null,
            enemyFaction = faction,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        _uiState.update { it.copy(query = nextQuery, searchError = null) }
        executeSearch(nextQuery, debounce = false)
    }

    fun updateEnemySizeClass(sizeClass: EnemySizeClass?) {
        val nextQuery = _uiState.value.query.copy(
            selectedKind = WikiKind.ENEMY,
            stratagemCategory = null,
            weaponSlot = null,
            weaponClass = null,
            enemySizeClass = sizeClass,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        _uiState.update { it.copy(query = nextQuery, searchError = null) }
        executeSearch(nextQuery, debounce = false)
    }

    fun clearFilters() {
        val nextQuery = _uiState.value.query.copy(
            stratagemCategory = null,
            weaponSlot = null,
            weaponClass = null,
            enemyFaction = null,
            enemySizeClass = null,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        _uiState.update { it.copy(query = nextQuery, searchError = null) }
        if (!nextQuery.isHomeMode) {
            executeSearch(nextQuery, debounce = false)
        }
    }

    fun saveListScroll(index: Int, offset: Int) {
        _uiState.update { state ->
            state.copy(
                query = state.query.copy(
                    firstVisibleItemIndex = index,
                    firstVisibleItemScrollOffset = offset,
                ),
            )
        }
    }

    fun saveDetailScroll(entryId: String, index: Int, offset: Int) {
        _uiState.update { state ->
            val updated = state.detailStack.map { entry ->
                if (entry.entryId == entryId) {
                    entry.copy(scrollIndex = index, scrollOffset = offset)
                } else {
                    entry
                }
            }
            state.copy(detailStack = updated)
        }
    }

    /**
     * 从检索结果打开条目详情：宽屏模式直接替换为单根详情栈，窄屏模式则压入当前详情栈。
     */
    fun openResultItem(entryId: String, isWideScreen: Boolean) {
        val current = _uiState.value
        if (current.currentDetailId == entryId) return

        val nextStack = if (isWideScreen) {
            listOf(DetailStackEntry(entryId))
        } else {
            current.detailStack + DetailStackEntry(entryId)
        }
        _uiState.update { it.copy(detailStack = nextStack) }
        loadDetail(entryId)
    }

    /**
     * 跳转到关联条目：若目标条目已存在于详情栈中则直接截断回退至该节点，防止循环关联导致重复入栈。
     */
    fun openRelatedItem(entryId: String) {
        val current = _uiState.value
        if (current.currentDetailId == entryId) return

        val existingIdx = current.detailStack.indexOfFirst { it.entryId == entryId }
        val nextStack = if (existingIdx >= 0) {
            current.detailStack.subList(0, existingIdx + 1)
        } else {
            current.detailStack + DetailStackEntry(entryId)
        }
        _uiState.update { it.copy(detailStack = nextStack) }
        loadDetail(entryId)
    }

    fun retryCurrentDetail() {
        val targetId = _uiState.value.currentDetailId ?: return
        loadDetail(targetId)
    }

    /**
     * 切换当前解剖图视角，保留已选部位与状态；当当前选中部位仅在单一视角出现时拦截切换。
     */
    fun selectAnatomyView(viewId: String) {
        val top = _uiState.value.currentDetailEntry ?: return
        val anatomy = (_uiState.value.currentDetailState as? WikiLoadState.Content)?.value?.anatomy
        if (anatomy != null && anatomy.views.none { it.viewId == viewId }) {
            return
        }
        val lockedViewId = singleViewIdForPart(anatomy, top.selectedPartId)
        if (lockedViewId != null) {
            if (top.viewId != lockedViewId) {
                updateTopDetailEntry { it.copy(viewId = lockedViewId) }
            }
            return
        }
        updateTopDetailEntry { it.copy(viewId = viewId) }
    }

    /**
     * 选中指定敌人部位：自动切换至包含该部位的视角、按部位属性推导完好/破甲状态，并刷新部位推荐与对位分析。
     */
    fun selectAnatomyPart(partId: String) {
        val top = _uiState.value.currentDetailEntry ?: return
        val content = (_uiState.value.currentDetailState as? WikiLoadState.Content)?.value ?: return
        val enemy = content.entry.detail.enemy ?: return
        val part = enemy.parts.firstOrNull { it.partId == partId } ?: return
        val nextViewId = resolveViewIdForPart(content.anatomy, top.viewId, partId)
        val nextPreviewState = previewStateForPart(part)
        val nextGoal = adjustGoalForPart(
            currentGoal = top.goal,
            part = part,
            previousState = top.previewState,
            nextState = nextPreviewState,
        )
        updateTopDetailEntry {
            it.copy(
                viewId = nextViewId,
                selectedPartId = partId,
                previewState = nextPreviewState,
                goal = nextGoal,
            )
        }
        refreshEnemyAnalysis(refreshRecommendation = true, refreshMatchup = true)
    }

    private fun previewStateForPart(part: EnemyPartDetail?): EnemyPartState =
        if (part != null && part.protectedByPartIds.isNotEmpty()) {
            EnemyPartState.BROKEN
        } else {
            EnemyPartState.INTACT
        }

    private fun singleViewIdForPart(
        anatomy: EnemyAnatomyBundle?,
        partId: String?,
    ): String? {
        if (anatomy == null || partId == null) return null
        val matchingViews = anatomy.views.filter { partId in it.partAssets }
        return if (matchingViews.size == 1) matchingViews.first().viewId else null
    }

    private fun resolveViewIdForPart(
        anatomy: EnemyAnatomyBundle?,
        currentViewId: String?,
        partId: String,
    ): String? {
        val views = anatomy?.views ?: return currentViewId
        if (views.isEmpty()) return currentViewId
        val currentView = views.firstOrNull { it.viewId == currentViewId }
            ?: views.firstOrNull { it.viewId == anatomy.defaultViewId }
            ?: views.firstOrNull()
        if (currentView != null && partId in currentView.partAssets) {
            return currentView.viewId
        }
        return views.firstOrNull { partId in it.partAssets }?.viewId ?: currentView?.viewId
    }

    /**
     * 切换部位武器推荐目的并刷新推荐结果。
     */
    fun selectRecommendationGoal(goal: RecommendationGoal) {
        updateTopDetailEntry { it.copy(goal = goal) }
        refreshEnemyAnalysis(refreshRecommendation = true, refreshMatchup = false)
    }

    /**
     * 切换对位对比武器攻击方式并刷新单发伤害与穿甲评估。
     */
    fun selectCompareAttack(attackRef: AttackRef) {
        updateTopDetailEntry { it.copy(selectedAttackRef = attackRef) }
        refreshEnemyAnalysis(refreshRecommendation = false, refreshMatchup = true)
    }

    /**
     * 点击推荐武器或战备条目卡片，压入详情栈跳转查看其完整参数，保留当前敌人节点以便返回恢复。
     */
    fun openRecommendedAttack(attackRef: AttackRef) {
        openRelatedItem(attackRef.entryId)
    }

    /**
     * 重试当前敌人的图鉴加载或对位分析计算。
     */
    fun retryEnemyAnalysis() {
        val targetId = _uiState.value.currentDetailId ?: return
        val content = (_uiState.value.currentDetailState as? WikiLoadState.Content)?.value
        if (content == null || content.anatomyError != null) {
            loadDetail(targetId)
        } else {
            refreshEnemyAnalysis(refreshRecommendation = true, refreshMatchup = true)
        }
    }

    /**
     * 异步读取指定解剖底图 key 对应的 WebP 字节数组。
     */
    suspend fun loadAnatomyImageBytes(assetKey: String): WikiResult<ByteArray> {
        return repository.readAnatomyImageBytes(assetKey)
    }

    /**
     * 执行分层返回逻辑：优先弹出详情栈顶 -> 再从列表检索态退回首页态 -> 若已在首页则返回 false 交由外层退出资料库。
     *
     * @return true 表示资料库内部已消费本次返回事件；false 表示已在资料库首页，应退出页面。
     */
    fun onBack(): Boolean {
        val state = _uiState.value
        if (state.detailStack.isNotEmpty()) {
            val popped = state.detailStack.dropLast(1)
            val newTopId = popped.lastOrNull()?.entryId
            if (newTopId == null) {
                detailJob?.cancel()
                detailRequestSeq++
                analysisJob?.cancel()
                analysisRequestSeq++
                _uiState.update { it.copy(detailStack = emptyList(), currentDetailState = null) }
            } else {
                _uiState.update { it.copy(detailStack = popped) }
                loadDetail(newTopId)
            }
            return true
        }
        if (!state.query.isHomeMode) {
            searchJob?.cancel()
            searchRequestSeq++
            _uiState.update {
                it.copy(
                    query = WikiQueryState(),
                    results = emptyList(),
                    isSearching = false,
                    searchError = null,
                )
            }
            return true
        }
        return false
    }

    /**
     * 执行检索请求：按需启用输入防抖，并通过递增请求序号丢弃过期响应，防止旧请求结果覆盖新结果。
     */
    private fun executeSearch(query: WikiQueryState, debounce: Boolean) {
        searchJob?.cancel()
        val seq = ++searchRequestSeq
        _uiState.update { it.copy(isSearching = true, searchError = null) }
        searchJob = viewModelScope.launch {
            if (debounce && searchDebounceMs > 0L) {
                delay(searchDebounceMs.milliseconds)
            }
            val res = repository.search(
                WikiSearchRequest(
                    text = query.queryText,
                    kind = query.selectedKind,
                    stratagemCategory = query.stratagemCategory,
                    weaponSlot = query.weaponSlot,
                    weaponClass = query.weaponClass,
                    enemyFaction = query.enemyFaction,
                    enemySizeClass = query.enemySizeClass,
                ),
            )
            if (seq != searchRequestSeq) return@launch
            when (res) {
                is WikiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            results = res.data,
                            isSearching = false,
                            searchError = null,
                        )
                    }
                }

                is WikiResult.ReadError -> {
                    _uiState.update {
                        it.copy(isSearching = false, searchError = res.message)
                    }
                }

                is WikiResult.UnsupportedVersion -> {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            searchError = "资料包格式不兼容（v${res.foundVersion}）",
                        )
                    }
                }

                is WikiResult.Missing -> {
                    _uiState.update {
                        it.copy(isSearching = false, searchError = "未找到索引")
                    }
                }
            }
        }
    }

    /**
     * 加载指定条目的完整详情及关联项；通过取消前序任务与序号/栈顶双重校验防止快速切换时状态乱序。
     */
    private fun loadDetail(entryId: String) {
        detailJob?.cancel()
        analysisJob?.cancel()
        analysisRequestSeq++
        val seq = ++detailRequestSeq
        _uiState.update { it.copy(currentDetailState = WikiLoadState.Loading) }
        detailJob = viewModelScope.launch {
            val entryRes = repository.getEntry(entryId)
            if (seq != detailRequestSeq || _uiState.value.currentDetailId != entryId) return@launch

            when (entryRes) {
                is WikiResult.Success -> {
                    val relatedRes = repository.related(entryId)
                    if (seq != detailRequestSeq || _uiState.value.currentDetailId != entryId) return@launch
                    val relatedList = when (relatedRes) {
                        is WikiResult.Success -> relatedRes.data
                        else -> emptyList()
                    }
                    val detail = entryRes.data.detail
                    val enemy = detail.enemy
                    if (detail.kind == WikiKind.ENEMY && enemy != null) {
                        val anatomyRes = repository.getAnatomy(entryId)
                        if (seq != detailRequestSeq || _uiState.value.currentDetailId != entryId) return@launch
                        val attacksRes = repository.listAvailableAttacks()
                        if (seq != detailRequestSeq || _uiState.value.currentDetailId != entryId) return@launch

                        var anatomyBundle: EnemyAnatomyBundle? = null
                        var anatomyMissing = false
                        var anatomyError: String? = null
                        when (anatomyRes) {
                            is WikiResult.Success -> anatomyBundle = anatomyRes.data
                            is WikiResult.Missing -> anatomyMissing = true
                            is WikiResult.ReadError -> anatomyError = anatomyRes.message
                            is WikiResult.UnsupportedVersion -> {
                                anatomyError = "图鉴格式版本不兼容（v${anatomyRes.foundVersion}）"
                            }
                        }

                        val availableAttacks = when (attacksRes) {
                            is WikiResult.Success -> attacksRes.data
                            else -> emptyList()
                        }

                        val savedEntry = _uiState.value.currentDetailEntry
                        val resolvedPartId = savedEntry?.selectedPartId
                            ?.takeIf { id -> enemy.parts.any { it.partId == id } }
                            ?: enemy.parts.firstOrNull()?.partId
                        val selectedPart = enemy.parts.firstOrNull { it.partId == resolvedPartId }
                        val resolvedPreviewState = previewStateForPart(selectedPart)
                        val resolvedViewId = resolvedPartId?.let {
                            resolveViewIdForPart(
                                anatomy = anatomyBundle,
                                currentViewId = savedEntry?.viewId ?: anatomyBundle?.defaultViewId,
                                partId = it,
                            )
                        } ?: savedEntry?.viewId ?: anatomyBundle?.defaultViewId
                        val resolvedGoal =
                            savedEntry?.goal ?: defaultGoalFor(selectedPart, resolvedPreviewState)
                        val resolvedAttackRef = savedEntry?.selectedAttackRef
                            ?: availableAttacks.firstOrNull()?.ref

                        updateTopDetailEntry {
                            it.copy(
                                viewId = resolvedViewId,
                                selectedPartId = resolvedPartId,
                                previewState = resolvedPreviewState,
                                goal = resolvedGoal,
                                selectedAttackRef = resolvedAttackRef,
                            )
                        }

                        _uiState.update {
                            it.copy(
                                currentDetailState = WikiLoadState.Content(
                                    WikiDetailBundle(
                                        entry = entryRes.data,
                                        related = relatedList,
                                        anatomy = anatomyBundle,
                                        anatomyMissing = anatomyMissing,
                                        anatomyError = anatomyError,
                                        availableAttacks = availableAttacks,
                                        recommendationState = if (resolvedPartId != null) WikiLoadState.Loading else null,
                                        matchupState = if (resolvedPartId != null && resolvedAttackRef != null) {
                                            WikiLoadState.Loading
                                        } else {
                                            null
                                        },
                                    ),
                                ),
                            )
                        }

                        if (resolvedPartId != null) {
                            refreshEnemyAnalysis(
                                refreshRecommendation = true,
                                refreshMatchup = resolvedAttackRef != null,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                currentDetailState = WikiLoadState.Content(
                                    WikiDetailBundle(entry = entryRes.data, related = relatedList),
                                ),
                            )
                        }
                    }
                }

                is WikiResult.Missing -> {
                    _uiState.update {
                        it.copy(
                            currentDetailState = WikiLoadState.Error("未收录该条目：${entryRes.entryId}"),
                        )
                    }
                }

                is WikiResult.ReadError -> {
                    _uiState.update {
                        it.copy(currentDetailState = WikiLoadState.Error(entryRes.message))
                    }
                }

                is WikiResult.UnsupportedVersion -> {
                    _uiState.update {
                        it.copy(
                            currentDetailState = WikiLoadState.Error(
                                message = "资料包格式版本不兼容（v${entryRes.foundVersion}）",
                                isUnsupportedVersion = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun refreshEnemyAnalysis(
        refreshRecommendation: Boolean,
        refreshMatchup: Boolean,
    ) {
        val top = _uiState.value.currentDetailEntry ?: return
        val enemyId = top.entryId
        val partId = top.selectedPartId ?: return
        val goal = top.goal ?: RecommendationGoal.KILL
        val attackRef = top.selectedAttackRef
        val shouldRunMatchup = refreshMatchup && attackRef != null
        if (!refreshRecommendation && !shouldRunMatchup) return

        analysisJob?.cancel()
        val seq = ++analysisRequestSeq

        updateCurrentDetailContent { bundle ->
            bundle.copy(
                recommendationState = if (refreshRecommendation) WikiLoadState.Loading else bundle.recommendationState,
                matchupState = if (shouldRunMatchup) WikiLoadState.Loading else bundle.matchupState,
            )
        }

        analysisJob = viewModelScope.launch {
            val partRef = EnemyPartRef(enemyId = enemyId, partId = partId)
            val recState = if (refreshRecommendation) {
                when (val res =
                    repository.recommendForPart(partRef, top.previewState, goal, top.targetPatch)) {
                    is WikiResult.Success -> WikiLoadState.Content(res.data)
                    is WikiResult.Missing -> WikiLoadState.Error("未找到部位推荐数据：${res.entryId}")
                    is WikiResult.ReadError -> WikiLoadState.Error(res.message)
                    is WikiResult.UnsupportedVersion -> WikiLoadState.Error(
                        message = "推荐数据版本不兼容（v${res.foundVersion}）",
                        isUnsupportedVersion = true,
                    )
                }
            } else {
                null
            }

            if (seq != analysisRequestSeq || _uiState.value.currentDetailId != enemyId) return@launch

            val matchupState = if (shouldRunMatchup && attackRef != null) {
                val request = MatchupEvaluateRequest(
                    partRef = partRef,
                    partState = top.previewState,
                    attackRef = attackRef,
                    targetPatch = top.targetPatch,
                )
                when (val res = repository.evaluateMatchup(request)) {
                    is WikiResult.Success -> WikiLoadState.Content(res.data)
                    is WikiResult.Missing -> WikiLoadState.Error("未找到对位评估数据：${res.entryId}")
                    is WikiResult.ReadError -> WikiLoadState.Error(res.message)
                    is WikiResult.UnsupportedVersion -> WikiLoadState.Error(
                        message = "对位规则版本不兼容（v${res.foundVersion}）",
                        isUnsupportedVersion = true,
                    )
                }
            } else {
                null
            }

            if (seq != analysisRequestSeq || _uiState.value.currentDetailId != enemyId) return@launch

            updateCurrentDetailContent { bundle ->
                bundle.copy(
                    recommendationState = recState ?: bundle.recommendationState,
                    matchupState = matchupState ?: bundle.matchupState,
                )
            }
        }
    }

    private fun updateTopDetailEntry(transform: (DetailStackEntry) -> DetailStackEntry) {
        _uiState.update { state ->
            if (state.detailStack.isEmpty()) return@update state
            val updated = state.detailStack.toMutableList()
            updated[updated.lastIndex] = transform(updated.last())
            state.copy(detailStack = updated)
        }
    }

    private fun updateCurrentDetailContent(transform: (WikiDetailBundle) -> WikiDetailBundle) {
        _uiState.update { state ->
            val current = state.currentDetailState as? WikiLoadState.Content ?: return@update state
            state.copy(currentDetailState = WikiLoadState.Content(transform(current.value)))
        }
    }

    private fun defaultGoalFor(
        part: EnemyPartDetail?,
        previewState: EnemyPartState,
    ): RecommendationGoal {
        if (previewState == EnemyPartState.BROKEN) {
            return RecommendationGoal.ATTACK_EXPOSED
        }
        val hasExposeEffect = part?.onBreak?.any {
            it.effectType == PartBreakEffectType.EXPOSE_PART
        } == true
        val hasDisableEffect = part?.onBreak?.any {
            it.effectType == PartBreakEffectType.DISABLE_ABILITY
        } == true
        val armorVal = part?.armor?.value ?: 0
        return when {
            hasExposeEffect || armorVal >= 4 -> RecommendationGoal.STRIP_ARMOR
            hasDisableEffect -> RecommendationGoal.DISABLE_ABILITY
            else -> RecommendationGoal.KILL
        }
    }

    private fun adjustGoalForPart(
        currentGoal: RecommendationGoal?,
        part: EnemyPartDetail?,
        previousState: EnemyPartState,
        nextState: EnemyPartState,
    ): RecommendationGoal {
        if (currentGoal == null) {
            return defaultGoalFor(part, nextState)
        }
        if (previousState == EnemyPartState.INTACT && nextState == EnemyPartState.BROKEN) {
            return RecommendationGoal.ATTACK_EXPOSED
        }
        if (nextState == EnemyPartState.INTACT &&
            currentGoal == RecommendationGoal.ATTACK_EXPOSED &&
            part?.protectedByPartIds.isNullOrEmpty()
        ) {
            return defaultGoalFor(part, nextState)
        }
        return currentGoal
    }
}
