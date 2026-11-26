package cn.antares.helldiver_trainer.data.wiki.model

import kotlinx.serialization.Serializable

@Serializable
data class AnatomyHitMap(
    val width: Int,
    val height: Int,
    val partPalette: List<String> = emptyList(),
    val intactRle: List<Int> = emptyList(),
    val brokenRle: List<Int> = emptyList(),
)

@Serializable
data class AnatomyView(
    val viewId: String,
    val labelZh: String,
    val defaultAssetKey: String,
    val partAssets: Map<String, String> = emptyMap(),
    val assetRevision: String,
    val width: Int,
    val height: Int,
    val sourceId: String,
    val hitMap: AnatomyHitMap? = null,
) {
    /** 根据当前选中部位解析应显示的图片 `assetKey`；当前视角无该部位图时回退为 [defaultAssetKey]。 */
    fun resolveAssetKey(selectedPartId: String?): String =
        selectedPartId?.let { partAssets[it] } ?: defaultAssetKey
}

@Serializable
data class EnemyAnatomyBundle(
    val enemyId: String,
    val defaultViewId: String,
    val views: List<AnatomyView> = emptyList(),
    val sources: Map<String, WikiSource> = emptyMap(),
)
