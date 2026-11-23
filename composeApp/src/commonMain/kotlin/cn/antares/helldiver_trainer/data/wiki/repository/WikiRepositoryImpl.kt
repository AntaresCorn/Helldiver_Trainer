package cn.antares.helldiver_trainer.data.wiki.repository

import cn.antares.helldiver_trainer.data.wiki.datasource.WikiPackDataSource
import cn.antares.helldiver_trainer.data.wiki.model.WikiDetailShard
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle
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
        const val SUPPORTED_SCHEMA_VERSION = 1
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

    /**
     * 将有向关联类型与正反向标记映射为详情页展示使用的中文关系文案。
     */
    private fun relationLabel(type: WikiRelationType, isReverse: Boolean): String = when (type) {
        WikiRelationType.CALLS_IN -> if (isReverse) "对应战备" else "呼叫的装备"
        WikiRelationType.VARIANT_OF -> if (isReverse) "已收录变种" else "基础型号"
    }
}
