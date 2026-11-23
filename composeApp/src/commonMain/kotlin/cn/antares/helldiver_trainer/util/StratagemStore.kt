package cn.antares.helldiver_trainer.util

import cn.antares.helldiver_trainer.viewmodel.GameViewModel.StratagemItem
import cn.antares.helldiver_trainer.wiki.generated.GeneratedStratagemCatalog

/**
 * 战备目录门面。
 *
 * 作为薄门面将战备读取委托给构建期生成的 [GeneratedStratagemCatalog.items]，
 * 以保持训练器既有调用方兼容。
 */
object StratagemStore {

    /**
     * 获取全部战备条目列表。
     *
     * 直接返回构建期生成的 [GeneratedStratagemCatalog.items]。
     */
    fun getAllStratagems(): List<StratagemItem> = GeneratedStratagemCatalog.items
}
