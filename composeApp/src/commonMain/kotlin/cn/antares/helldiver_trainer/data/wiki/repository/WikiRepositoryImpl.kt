package cn.antares.helldiver_trainer.data.wiki.repository

import cn.antares.helldiver_trainer.data.wiki.datasource.WikiPackDataSource
import cn.antares.helldiver_trainer.data.wiki.matchup.WikiMatchupEngine
import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.AttackRef
import cn.antares.helldiver_trainer.data.wiki.model.DamageRulesPack
import cn.antares.helldiver_trainer.data.wiki.model.EnemyAnatomyBundle
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.MatchEvidencePack
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluateRequest
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationsPack
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.WeaponConfigDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiDetailShard
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryDetail
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexPack
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexRelation
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiManifest
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelationType
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest
import cn.antares.helldiver_trainer.data.wiki.search.WikiSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

/**
 * [WikiRepository] 的默认内存缓存实现。
 *
 * 资料清单与目录索引在首次访问时加载并驻留内存；各类别详情分片按 [WikiKind] 延迟加载并分别缓存，
 * 避免一次性反序列化全量资料造成冷启动与内存开销。
 */
class WikiRepositoryImpl(
    private val dataSource: WikiPackDataSource,
    ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WikiRepository {

    companion object {
        const val SUPPORTED_SCHEMA_VERSION = 2
        private const val MANIFEST_PATH = "files/wiki/manifest.json"
    }

    private data class InitializedPack(
        val manifest: WikiManifest,
        val index: WikiIndexPack,
        val itemsById: Map<String, WikiIndexItem>,
        val relationsByOwner: Map<String, List<WikiIndexRelation>>,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    // 使用独立 SupervisorJob 作用域承接加载任务，避免单个调用方协程取消连带中断共享加载。
    private val repoScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val mutex = Mutex()

    @Volatile
    private var initializedPack: InitializedPack? = null
    private var initJob: Deferred<WikiResult<InitializedPack>>? = null

    private val detailCache = HashMap<WikiKind, WikiDetailShard>()
    private val detailJobs = HashMap<WikiKind, Deferred<WikiResult<WikiDetailShard>>>()

    private val anatomyCache = HashMap<String, EnemyAnatomyBundle>()
    private val anatomyJobs = HashMap<String, Deferred<WikiResult<EnemyAnatomyBundle>>>()

    private val anatomyImageCache = HashMap<String, ByteArray>()
    private val anatomyImageJobs = HashMap<String, Deferred<WikiResult<ByteArray>>>()

    @Volatile
    private var rulesCache: DamageRulesPack? = null
    private var rulesJob: Deferred<WikiResult<DamageRulesPack>>? = null

    @Volatile
    private var evidenceCache: MatchEvidencePack? = null
    private var evidenceJob: Deferred<WikiResult<MatchEvidencePack>>? = null

    @Volatile
    private var recommendationsCache: PartRecommendationsPack? = null
    private var recommendationsJob: Deferred<WikiResult<PartRecommendationsPack>>? = null

    override suspend fun ensureInitialized(): WikiResult<Unit> {
        return when (val res = getOrLoadPack()) {
            is WikiResult.Success -> WikiResult.Success(Unit)
            is WikiResult.Missing -> res
            is WikiResult.ReadError -> res
            is WikiResult.UnsupportedVersion -> res
        }
    }

    override suspend fun search(request: WikiSearchRequest): WikiResult<List<WikiSearchHit>> {
        return when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> WikiResult.Success(
                WikiSearchEngine.search(packRes.data.index, request),
            )

            is WikiResult.Missing -> packRes
            is WikiResult.ReadError -> packRes
            is WikiResult.UnsupportedVersion -> packRes
        }
    }

    override suspend fun getEntry(entryId: String): WikiResult<WikiEntryBundle> {
        val pack = when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> packRes.data
            is WikiResult.Missing -> return packRes
            is WikiResult.ReadError -> return packRes
            is WikiResult.UnsupportedVersion -> return packRes
        }
        val indexItem = pack.itemsById[entryId] ?: return WikiResult.Missing(entryId)
        val shard = when (val shardRes = getOrLoadShard(pack.manifest, indexItem.kind)) {
            is WikiResult.Success -> shardRes.data
            is WikiResult.Missing -> return shardRes
            is WikiResult.ReadError -> return shardRes
            is WikiResult.UnsupportedVersion -> return shardRes
        }
        val detail = shard.entries[entryId]
            ?: return WikiResult.ReadError("资料包损坏：索引存在但分片缺少条目 $entryId")
        return WikiResult.Success(
            WikiEntryBundle(
                detail = detail,
                sources = shard.sources,
            ),
        )
    }

    override suspend fun related(entryId: String): WikiResult<List<WikiRelatedItem>> {
        val pack = when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> packRes.data
            is WikiResult.Missing -> return packRes
            is WikiResult.ReadError -> return packRes
            is WikiResult.UnsupportedVersion -> return packRes
        }
        if (entryId !in pack.itemsById) {
            return WikiResult.Missing(entryId)
        }
        val rels = pack.relationsByOwner[entryId].orEmpty().mapNotNull { rel ->
            val targetItem = pack.itemsById[rel.targetEntryId] ?: return@mapNotNull null
            WikiRelatedItem(
                relationType = rel.relationType,
                isReverse = rel.isReverse,
                labelZh = relationLabel(rel.relationType, rel.isReverse),
                target = targetItem,
                condition = rel.condition,
                sourceId = rel.sourceId,
            )
        }
        return WikiResult.Success(rels)
    }

    override suspend fun getAnatomy(enemyId: String): WikiResult<EnemyAnatomyBundle> {
        val pack = when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> packRes.data
            is WikiResult.Missing -> return packRes
            is WikiResult.ReadError -> return packRes
            is WikiResult.UnsupportedVersion -> return packRes
        }
        val path = pack.manifest.anatomyPaths[enemyId] ?: return WikiResult.Missing(enemyId)
        return getOrLoadMapEntry(
            key = enemyId,
            cache = anatomyCache,
            jobs = anatomyJobs,
            errorPrefix = "读取敌人解剖数据失败",
        ) {
            loadJsonResource(path, "敌人解剖数据")
        }
    }

    override suspend fun readAnatomyImageBytes(assetKey: String): WikiResult<ByteArray> {
        val pack = when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> packRes.data
            is WikiResult.Missing -> return packRes
            is WikiResult.ReadError -> return packRes
            is WikiResult.UnsupportedVersion -> return packRes
        }
        val path = pack.manifest.anatomyAssetPaths[assetKey] ?: return WikiResult.Missing(assetKey)
        return getOrLoadMapEntry(
            key = assetKey,
            cache = anatomyImageCache,
            jobs = anatomyImageJobs,
            errorPrefix = "读取解剖底图失败",
        ) {
            val bytes = try {
                dataSource.readBytes(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return@getOrLoadMapEntry WikiResult.ReadError(
                    "读取解剖底图失败 ($path): ${e.message ?: e::class.simpleName}",
                    e,
                )
            }
            if (bytes.isEmpty()) {
                WikiResult.ReadError("解剖底图为空 ($path)")
            } else {
                WikiResult.Success(bytes)
            }
        }
    }

    override suspend fun listAvailableAttacks(): WikiResult<List<AttackOption>> {
        val pack = when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> packRes.data
            is WikiResult.Missing -> return packRes
            is WikiResult.ReadError -> return packRes
            is WikiResult.UnsupportedVersion -> return packRes
        }
        val weaponShard = when (val res = getOrLoadShard(pack.manifest, WikiKind.WEAPON)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val stratagemShard = when (val res = getOrLoadShard(pack.manifest, WikiKind.STRATAGEM)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val options = buildAttackCatalog(weaponShard, stratagemShard).map { it.option }
        return WikiResult.Success(options)
    }

    override suspend fun evaluateMatchup(
        request: MatchupEvaluateRequest,
    ): WikiResult<MatchupEvaluation> {
        val pack = when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> packRes.data
            is WikiResult.Missing -> return packRes
            is WikiResult.ReadError -> return packRes
            is WikiResult.UnsupportedVersion -> return packRes
        }
        val enemyBundle = when (val res = getEntry(request.partRef.enemyId)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val hasPart = enemyBundle.detail.enemy?.parts?.any {
            it.partId == request.partRef.partId
        } == true
        if (!hasPart) {
            return WikiResult.Missing("${request.partRef.enemyId}#${request.partRef.partId}")
        }

        val ownerBundle = when (val res = getEntry(request.attackRef.entryId)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val configs = entryConfigurations(ownerBundle.detail)
        val config = configs.firstOrNull { it.configId == request.attackRef.configId }
        val attack = config?.attacks?.firstOrNull { it.attackId == request.attackRef.attackId }
            ?: return WikiResult.Missing(
                "${request.attackRef.entryId}/${request.attackRef.configId}/${request.attackRef.attackId}",
            )

        val rulesPack = when (val res = getOrLoadRules(pack.manifest)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val evidencePack = when (val res = getOrLoadEvidence(pack.manifest)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }

        val targetPatch = resolveTargetPatch(
            requestPatch = request.targetPatch,
            enemyDetail = enemyBundle.detail,
            rulesPack = rulesPack,
        )
        val ruleSet = rulesPack.ruleSets.firstOrNull { it.patchVersion == targetPatch }
            ?: rulesPack.ruleSets.firstOrNull()
            ?: return WikiResult.ReadError("伤害规则包为空，缺少可用规则集")

        val mergedSources = enemyBundle.sources +
                ownerBundle.sources +
                rulesPack.sources +
                evidencePack.sources

        return WikiResult.Success(
            WikiMatchupEngine.evaluateMatchup(
                enemyDetail = enemyBundle.detail,
                partId = request.partRef.partId,
                partState = request.partState,
                ownerEntry = ownerBundle.detail,
                configId = config.configId,
                attackId = attack.attackId,
                attack = attack,
                ruleSet = ruleSet,
                evidences = evidencePack.evidences,
                targetPatch = targetPatch,
                hitCondition = request.hitCondition,
                sources = mergedSources,
            ),
        )
    }

    override suspend fun recommendForPart(
        partRef: EnemyPartRef,
        state: EnemyPartState,
        goal: RecommendationGoal,
        patch: String?,
    ): WikiResult<PartRecommendationResult> {
        val pack = when (val packRes = getOrLoadPack()) {
            is WikiResult.Success -> packRes.data
            is WikiResult.Missing -> return packRes
            is WikiResult.ReadError -> return packRes
            is WikiResult.UnsupportedVersion -> return packRes
        }
        val enemyBundle = when (val res = getEntry(partRef.enemyId)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val hasPart = enemyBundle.detail.enemy?.parts?.any { it.partId == partRef.partId } == true
        if (!hasPart) {
            return WikiResult.Missing("${partRef.enemyId}#${partRef.partId}")
        }

        val rulesPack = when (val res = getOrLoadRules(pack.manifest)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val evidencePack = when (val res = getOrLoadEvidence(pack.manifest)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val recommendationsPack = when (val res = getOrLoadRecommendations(pack.manifest)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val weaponShard = when (val res = getOrLoadShard(pack.manifest, WikiKind.WEAPON)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }
        val stratagemShard = when (val res = getOrLoadShard(pack.manifest, WikiKind.STRATAGEM)) {
            is WikiResult.Success -> res.data
            is WikiResult.Missing -> return res
            is WikiResult.ReadError -> return res
            is WikiResult.UnsupportedVersion -> return res
        }

        val targetPatch = resolveTargetPatch(
            requestPatch = patch,
            enemyDetail = enemyBundle.detail,
            rulesPack = rulesPack,
        )
        val ruleSet = rulesPack.ruleSets.firstOrNull { it.patchVersion == targetPatch }
            ?: rulesPack.ruleSets.firstOrNull()
            ?: return WikiResult.ReadError("伤害规则包为空，缺少可用规则集")

        val attackCatalog = buildAttackCatalog(weaponShard, stratagemShard)
        val mergedSources = enemyBundle.sources +
                weaponShard.sources +
                stratagemShard.sources +
                rulesPack.sources +
                evidencePack.sources +
                recommendationsPack.sources

        return WikiResult.Success(
            WikiMatchupEngine.recommendForPart(
                enemyEntryId = partRef.enemyId,
                enemyDetail = enemyBundle.detail,
                partId = partRef.partId,
                partState = state,
                goal = goal,
                targetPatch = targetPatch,
                ruleSet = ruleSet,
                evidences = evidencePack.evidences,
                recommendations = recommendationsPack.recommendations,
                attackCatalog = attackCatalog,
                sources = mergedSources,
            ),
        )
    }

    /**
     * 获取或并发初始化资料索引包。
     *
     * 采用“@Volatile 快路径 + Mutex 双重检查 + 共享 [Deferred] 任务”实现并发去重；
     * 仅在成功时写入 [initializedPack] 缓存，无论成功或失败均会清理 [initJob]，以便失败后可安全重试。
     */
    private suspend fun getOrLoadPack(): WikiResult<InitializedPack> {
        initializedPack?.let { return WikiResult.Success(it) }
        val deferred = mutex.withLock {
            initializedPack?.let { return WikiResult.Success(it) }
            // 复用尚未完成的并发初始化任务，避免重复读取与解析 JSON
            initJob ?: repoScope.async { loadPackInternal() }.also { initJob = it }
        }
        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WikiResult.ReadError("读取资料库索引失败: ${e.message ?: e::class.simpleName}", e)
        }
        mutex.withLock {
            // 仅缓存成功结果；失败结果不入缓存，确保下次调用能重新触发加载
            if (result is WikiResult.Success) {
                initializedPack = result.data
            }
            if (initJob === deferred) {
                initJob = null
            }
        }
        return result
    }

    /**
     * 读取并解析 `manifest.json` 与 `index.json`，校验 `schemaVersion` 后构建内存查找索引。
     */
    private suspend fun loadPackInternal(): WikiResult<InitializedPack> {
        val manifestText = try {
            dataSource.readBytes(MANIFEST_PATH).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return WikiResult.ReadError("读取资料清单失败: ${e.message ?: e::class.simpleName}", e)
        }

        val manifest = try {
            json.decodeFromString<WikiManifest>(manifestText)
        } catch (e: Throwable) {
            return WikiResult.ReadError("解析资料清单失败: ${e.message ?: e::class.simpleName}", e)
        }

        // 严格校验资料包协议版本，不兼容时显式返回 UnsupportedVersion
        if (manifest.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return WikiResult.UnsupportedVersion(
                foundVersion = manifest.schemaVersion,
                supportedVersion = SUPPORTED_SCHEMA_VERSION,
            )
        }

        val indexText = try {
            dataSource.readBytes(manifest.indexPath).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return WikiResult.ReadError("读取目录索引失败: ${e.message ?: e::class.simpleName}", e)
        }

        val index = try {
            json.decodeFromString<WikiIndexPack>(indexText)
        } catch (e: Throwable) {
            return WikiResult.ReadError("解析目录索引失败: ${e.message ?: e::class.simpleName}", e)
        }

        // 预构建按 entryId 与 ownerEntryId 分组的内存映射，加速详情定位与关联查询
        val itemsById = index.entries.associateBy { it.entryId }
        val relationsByOwner = index.relations.groupBy { it.ownerEntryId }
        return WikiResult.Success(
            InitializedPack(
                manifest = manifest,
                index = index,
                itemsById = itemsById,
                relationsByOwner = relationsByOwner,
            ),
        )
    }

    /**
     * 按 [WikiKind] 懒加载对应详情分片，并在同分类并发请求间复用同一 [Deferred] 任务。
     *
     * 加载完成后清除进行中的任务引用，且仅缓存成功分片以支持失败重试。
     */
    private suspend fun getOrLoadShard(
        manifest: WikiManifest,
        kind: WikiKind,
    ): WikiResult<WikiDetailShard> {
        mutex.withLock {
            detailCache[kind]?.let { return WikiResult.Success(it) }
        }
        val path = manifest.detailPaths[kind]
            ?: return WikiResult.ReadError("资料清单缺少 ${kind.name} 详情路径")

        val deferred = mutex.withLock {
            detailCache[kind]?.let { return WikiResult.Success(it) }
            // 按 WikiKind 复用在途加载任务，防止同一分片被并发重复读取
            detailJobs[kind] ?: repoScope.async { loadShardInternal(path) }.also {
                detailJobs[kind] = it
            }
        }

        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WikiResult.ReadError("读取详情分片失败: ${e.message ?: e::class.simpleName}", e)
        }

        mutex.withLock {
            if (result is WikiResult.Success) {
                detailCache[kind] = result.data
            }
            if (detailJobs[kind] === deferred) {
                detailJobs.remove(kind)
            }
        }
        return result
    }

    private suspend fun loadShardInternal(path: String): WikiResult<WikiDetailShard> {
        val text = try {
            dataSource.readBytes(path).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return WikiResult.ReadError(
                "读取详情文件失败 ($path): ${e.message ?: e::class.simpleName}",
                e,
            )
        }
        return try {
            WikiResult.Success(json.decodeFromString<WikiDetailShard>(text))
        } catch (e: Throwable) {
            WikiResult.ReadError("解析详情文件失败 ($path): ${e.message ?: e::class.simpleName}", e)
        }
    }

    private suspend fun getOrLoadRules(manifest: WikiManifest): WikiResult<DamageRulesPack> {
        rulesCache?.let { return WikiResult.Success(it) }
        val deferred = mutex.withLock {
            rulesCache?.let { return WikiResult.Success(it) }
            rulesJob ?: repoScope.async {
                loadJsonResource<DamageRulesPack>(manifest.rulesPath, "伤害规则包")
            }.also { rulesJob = it }
        }
        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WikiResult.ReadError("读取伤害规则包失败: ${e.message ?: e::class.simpleName}", e)
        }
        mutex.withLock {
            if (result is WikiResult.Success) {
                rulesCache = result.data
            }
            if (rulesJob === deferred) {
                rulesJob = null
            }
        }
        return result
    }

    private suspend fun getOrLoadEvidence(manifest: WikiManifest): WikiResult<MatchEvidencePack> {
        evidenceCache?.let { return WikiResult.Success(it) }
        val deferred = mutex.withLock {
            evidenceCache?.let { return WikiResult.Success(it) }
            evidenceJob ?: repoScope.async {
                loadJsonResource<MatchEvidencePack>(manifest.evidencePath, "实测证据包")
            }.also { evidenceJob = it }
        }
        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WikiResult.ReadError("读取实测证据包失败: ${e.message ?: e::class.simpleName}", e)
        }
        mutex.withLock {
            if (result is WikiResult.Success) {
                evidenceCache = result.data
            }
            if (evidenceJob === deferred) {
                evidenceJob = null
            }
        }
        return result
    }

    private suspend fun getOrLoadRecommendations(
        manifest: WikiManifest,
    ): WikiResult<PartRecommendationsPack> {
        recommendationsCache?.let { return WikiResult.Success(it) }
        val deferred = mutex.withLock {
            recommendationsCache?.let { return WikiResult.Success(it) }
            recommendationsJob ?: repoScope.async {
                loadJsonResource<PartRecommendationsPack>(
                    manifest.recommendationsPath,
                    "部位推荐包",
                )
            }.also { recommendationsJob = it }
        }
        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WikiResult.ReadError("读取部位推荐包失败: ${e.message ?: e::class.simpleName}", e)
        }
        mutex.withLock {
            if (result is WikiResult.Success) {
                recommendationsCache = result.data
            }
            if (recommendationsJob === deferred) {
                recommendationsJob = null
            }
        }
        return result
    }

    private suspend fun <T> getOrLoadMapEntry(
        key: String,
        cache: HashMap<String, T>,
        jobs: HashMap<String, Deferred<WikiResult<T>>>,
        errorPrefix: String,
        loader: suspend () -> WikiResult<T>,
    ): WikiResult<T> {
        mutex.withLock {
            cache[key]?.let { return WikiResult.Success(it) }
        }
        val deferred = mutex.withLock {
            cache[key]?.let { return WikiResult.Success(it) }
            jobs[key] ?: repoScope.async { loader() }.also { jobs[key] = it }
        }
        val result = try {
            deferred.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            WikiResult.ReadError("$errorPrefix: ${e.message ?: e::class.simpleName}", e)
        }
        mutex.withLock {
            if (result is WikiResult.Success) {
                cache[key] = result.data
            }
            if (jobs[key] === deferred) {
                jobs.remove(key)
            }
        }
        return result
    }

    private suspend inline fun <reified T> loadJsonResource(
        path: String,
        label: String,
    ): WikiResult<T> {
        val text = try {
            dataSource.readBytes(path).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return WikiResult.ReadError(
                "读取${label}失败 ($path): ${e.message ?: e::class.simpleName}",
                e,
            )
        }
        return try {
            WikiResult.Success(json.decodeFromString<T>(text))
        } catch (e: Throwable) {
            WikiResult.ReadError(
                "解析${label}失败 ($path): ${e.message ?: e::class.simpleName}",
                e,
            )
        }
    }

    private fun buildAttackCatalog(
        weaponShard: WikiDetailShard,
        stratagemShard: WikiDetailShard,
    ): List<WikiMatchupEngine.AttackCatalogItem> {
        val items = mutableListOf<WikiMatchupEngine.AttackCatalogItem>()
        for (shard in listOf(weaponShard, stratagemShard)) {
            for (entry in shard.entries.values) {
                for (config in entryConfigurations(entry)) {
                    for (attack in config.attacks) {
                        items.add(
                            WikiMatchupEngine.AttackCatalogItem(
                                option = AttackOption(
                                    ref = AttackRef(
                                        entryId = entry.entryId,
                                        configId = config.configId,
                                        attackId = attack.attackId,
                                    ),
                                    ownerNameZh = entry.nameZh,
                                    ownerKind = entry.kind,
                                    configLabelZh = config.labelZh,
                                    attackNameZh = attack.nameZh,
                                ),
                                ownerEntry = entry,
                                attack = attack,
                            ),
                        )
                    }
                }
            }
        }
        return items
    }

    private fun entryConfigurations(entry: WikiEntryDetail): List<WeaponConfigDetail> =
        entry.weapon?.configurations
            ?: entry.stratagem?.weaponProfile?.configurations
            ?: emptyList()

    private fun resolveTargetPatch(
        requestPatch: String?,
        enemyDetail: WikiEntryDetail,
        rulesPack: DamageRulesPack,
    ): String = requestPatch
        ?: enemyDetail.verifiedPatch
        ?: rulesPack.ruleSets.firstOrNull()?.patchVersion
        ?: "01.001.200"

    /**
     * 将有向关联类型与正反向标记映射为详情页展示使用的中文关系文案。
     */
    private fun relationLabel(type: WikiRelationType, isReverse: Boolean): String = when (type) {
        WikiRelationType.CALLS_IN -> if (isReverse) "对应战备" else "呼叫的装备"
        WikiRelationType.VARIANT_OF -> if (isReverse) "已收录变种" else "基础型号"
    }
}
