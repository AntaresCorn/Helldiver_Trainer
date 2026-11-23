package cn.antares.helldiver_trainer.data.wiki.search

import cn.antares.helldiver_trainer.data.wiki.model.NormalizedTextPair
import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexItem
import cn.antares.helldiver_trainer.data.wiki.model.WikiIndexPack
import cn.antares.helldiver_trainer.data.wiki.model.WikiKind
import cn.antares.helldiver_trainer.data.wiki.model.WikiMatchReason
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchHit
import cn.antares.helldiver_trainer.data.wiki.model.WikiSearchRequest

/**
 * 资料库离线搜索引擎，基于预构建的 [WikiIndexPack] 执行多维过滤与五档相关度评分。
 */
object WikiSearchEngine {

    private data class ScoredHit(
        val hit: WikiSearchHit,
        val score: Int,
        val matchedLength: Int,
    )

    /**
     * 执行资料库搜索与过滤：
     * - 空查询：先按 [matchesFilter] 过滤，再按“战备 -> 武器 -> 敌人”的分类顺序、`trainingOrder`、中文名及 `entryId` 稳定目录排序返回。
     * - 关键词/方向查询：并行计算文本规范化结果与方向指令序列（已刻意移除简介文本检索以减少噪音），
     *   按五档权重（100/90/80/60/40）评分，并依次以命中长度（越短越靠前）、`trainingOrder`、中文名与 `entryId` 作为多级 tie-breaker 排序。
     */
    fun search(index: WikiIndexPack, request: WikiSearchRequest): List<WikiSearchHit> {
        val filtered = index.entries.filter { item -> matchesFilter(item, request) }
        if (request.text.isBlank()) {
            return filtered
                .sortedWith(
                    compareBy(
                        { kindSortOrder(it.kind) },
                        { it.trainingOrder ?: Int.MAX_VALUE },
                        { it.nameZh },
                        { it.entryId },
                    ),
                )
                .map { WikiSearchHit(item = it, matchReason = WikiMatchReason.CATALOG) }
        }

        val queryNorm = WikiTextNormalizer.normalize(request.text)
        val queryDirs = WikiDirectionParser.parseOrNull(request.text)
        if (queryNorm.compact.isEmpty() && queryDirs == null) {
            return emptyList()
        }

        return filtered.mapNotNull { item ->
            scoreItem(item, queryNorm, queryDirs)
        }.sortedWith(
            compareByDescending<ScoredHit> { it.score }
                .thenBy { it.matchedLength }
                .thenBy { it.hit.item.trainingOrder ?: Int.MAX_VALUE }
                .thenBy { it.hit.item.nameZh }
                .thenBy { it.hit.item.entryId },
        ).map { it.hit }
    }

    /**
     * 校验条目是否满足请求中的顶层分类与各子类二级联动过滤条件（空条件视为不限制）。
     */
    private fun matchesFilter(item: WikiIndexItem, request: WikiSearchRequest): Boolean {
        if (request.kind != null && item.kind != request.kind) return false
        if (request.stratagemCategory != null && item.stratagemCategory != request.stratagemCategory) {
            return false
        }
        if (request.weaponSlot != null && item.weaponSlot != request.weaponSlot) {
            return false
        }
        if (request.weaponClass != null && item.weaponClass != request.weaponClass) {
            return false
        }
        if (request.enemyFaction != null && item.enemyFaction != request.enemyFaction) {
            return false
        }
        if (request.enemySizeClass != null && item.enemySizeClass != request.enemySizeClass) {
            return false
        }
        return true
    }

    /**
     * 按五档优先级对单条索引进行匹配打分，未命中任何规则则返回 `null`。
     */
    private fun scoreItem(
        item: WikiIndexItem,
        query: NormalizedTextPair,
        queryDirs: List<WikiDirection>?,
    ): ScoredHit? {
        val namePairs = buildList {
            add(NormalizedTextPair(item.normalizedNameZh, item.normalizedNameZhCompact))
            if (item.normalizedNameEn != null && item.normalizedNameEnCompact != null) {
                add(NormalizedTextPair(item.normalizedNameEn, item.normalizedNameEnCompact))
            }
        }
        val modelPairs = item.normalizedModelCodes
        val aliasPairs = item.normalizedAliases

        // 第 1 档 (100)：正式名称（中/英）或型号精确匹配
        if (query.compact.isNotEmpty()) {
            if (namePairs.any { it.matchesExact(query) }) {
                return ScoredHit(
                    WikiSearchHit(item, WikiMatchReason.EXACT_NAME),
                    score = 100,
                    matchedLength = query.compact.length,
                )
            }
            if (modelPairs.any { it.matchesExact(query) }) {
                return ScoredHit(
                    WikiSearchHit(item, WikiMatchReason.EXACT_MODEL),
                    score = 100,
                    matchedLength = query.compact.length,
                )
            }
        }

        // 第 2 档 (90)：战备方向指令完全匹配
        if (queryDirs != null && item.directions == queryDirs) {
            return ScoredHit(
                WikiSearchHit(item, WikiMatchReason.DIRECTION_EXACT),
                score = 90,
                matchedLength = queryDirs.size,
            )
        }

        // 第 3 档 (80)：已核验别名精确匹配
        if (query.compact.isNotEmpty() && aliasPairs.any { it.matchesExact(query) }) {
            return ScoredHit(
                WikiSearchHit(item, WikiMatchReason.EXACT_ALIAS),
                score = 80,
                matchedLength = query.compact.length,
            )
        }

        // 第 4 档 (60)：名称、型号或别名的前缀匹配
        if (query.compact.isNotEmpty()) {
            val prefixLen = (namePairs + modelPairs + aliasPairs)
                .filter { it.matchesPrefix(query) }
                .minOfOrNull { it.compact.length }
            if (prefixLen != null) {
                return ScoredHit(
                    WikiSearchHit(item, WikiMatchReason.PREFIX),
                    score = 60,
                    matchedLength = prefixLen,
                )
            }
        }

        // 第 5 档 (40)：文本子串包含或战备方向连续子序列匹配
        if (query.compact.isNotEmpty()) {
            val subLen = (namePairs + modelPairs + aliasPairs)
                .filter { it.matchesContains(query) }
                .minOfOrNull { it.compact.length }
            if (subLen != null) {
                return ScoredHit(
                    WikiSearchHit(item, WikiMatchReason.SUBSTRING),
                    score = 40,
                    matchedLength = subLen,
                )
            }
        }
        if (queryDirs != null && containsSubsequence(item.directions, queryDirs)) {
            return ScoredHit(
                WikiSearchHit(item, WikiMatchReason.DIRECTION_SUBSEQUENCE),
                score = 40,
                matchedLength = item.directions.size,
            )
        }

        return null
    }

    private fun NormalizedTextPair.matchesExact(q: NormalizedTextPair): Boolean =
        (q.text.isNotEmpty() && text == q.text) || (q.compact.isNotEmpty() && compact == q.compact)

    private fun NormalizedTextPair.matchesPrefix(q: NormalizedTextPair): Boolean =
        (q.text.isNotEmpty() && text.startsWith(q.text)) ||
                (q.compact.isNotEmpty() && compact.startsWith(q.compact))

    private fun NormalizedTextPair.matchesContains(q: NormalizedTextPair): Boolean =
        (q.text.isNotEmpty() && text.contains(q.text)) ||
                (q.compact.isNotEmpty() && compact.contains(q.compact))

    /**
     * 判断 [sub] 是否作为连续子序列（而非跳位子序列）出现在 [full] 方向列表中。
     */
    private fun containsSubsequence(full: List<WikiDirection>, sub: List<WikiDirection>): Boolean {
        if (sub.isEmpty() || sub.size > full.size) return false
        for (start in 0..(full.size - sub.size)) {
            if (full.subList(start, start + sub.size) == sub) return true
        }
        return false
    }

    private fun kindSortOrder(kind: WikiKind): Int = when (kind) {
        WikiKind.STRATAGEM -> 0
        WikiKind.WEAPON -> 1
        WikiKind.ENEMY -> 2
    }
}
