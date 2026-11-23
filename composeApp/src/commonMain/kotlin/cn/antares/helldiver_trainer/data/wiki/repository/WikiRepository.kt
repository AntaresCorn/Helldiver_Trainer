package cn.antares.helldiver_trainer.data.wiki.repository

import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest

/**
 * 资料库数据仓库接口，负责管理离线资料包的初始化、目录检索、详情懒加载与关联查询。
 */
interface WikiRepository {
    /** 确保资料清单与目录索引已加载并校验 schema 版本；若已就绪则直接返回成功。 */
    suspend fun ensureInitialized(): WikiResult<Unit>

    /** 基于内存索引执行关键词/方向搜索与多维过滤，返回按匹配档位排序的结果列表。 */
    suspend fun search(request: WikiSearchRequest): WikiResult<List<WikiSearchHit>>

    /** 按条目所属分类懒加载对应详情分片，并返回条目完整详情及其分片来源字典。 */
    suspend fun getEntry(entryId: String): WikiResult<WikiEntryBundle>

    /** 查询指定条目的正反向关联条目，并附带面向界面展示的中文关系标签。 */
    suspend fun related(entryId: String): WikiResult<List<WikiRelatedItem>>
}
