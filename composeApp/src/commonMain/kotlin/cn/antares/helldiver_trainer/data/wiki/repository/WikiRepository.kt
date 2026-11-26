package cn.antares.helldiver_trainer.data.wiki.repository

import cn.antares.helldiver_trainer.data.wiki.model.AttackOption
import cn.antares.helldiver_trainer.data.wiki.model.EnemyAnatomyBundle
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartRef
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluateRequest
import cn.antares.helldiver_trainer.data.wiki.model.MatchupEvaluation
import cn.antares.helldiver_trainer.data.wiki.model.PartRecommendationResult
import cn.antares.helldiver_trainer.data.wiki.model.RecommendationGoal
import cn.antares.helldiver_trainer.data.wiki.model.WikiEntryBundle
import cn.antares.helldiver_trainer.data.wiki.model.WikiRelatedItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiResult
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest

/**
 * 资料库数据仓库接口，负责管理离线资料包的初始化、目录检索、详情懒加载、关联查询与二期交互图鉴/对位分析。
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

    /** 按敌人 ID 懒加载多视角解剖与热区定义；未录入图鉴时返回 [WikiResult.Missing]。 */
    suspend fun getAnatomy(enemyId: String): WikiResult<EnemyAnatomyBundle>

    /** 按底图 assetKey 读取校验后的 WebP 字节数组。 */
    suspend fun readAnatomyImageBytes(assetKey: String): WikiResult<ByteArray>

    /** 对指定敌人部位与攻击方式执行规则推算与实测冲突仲裁。 */
    suspend fun evaluateMatchup(request: MatchupEvaluateRequest): WikiResult<MatchupEvaluation>

    /** 按部位状态与玩家目的查询分组推荐列表（编辑实证推荐 + 其他可用武器）。 */
    suspend fun recommendForPart(
        partRef: EnemyPartRef,
        state: EnemyPartState,
        goal: RecommendationGoal,
        patch: String? = null,
    ): WikiResult<PartRecommendationResult>

    /** 汇总所有可用于对位对比的武器与支援武器攻击方式。 */
    suspend fun listAvailableAttacks(): WikiResult<List<AttackOption>>
}
