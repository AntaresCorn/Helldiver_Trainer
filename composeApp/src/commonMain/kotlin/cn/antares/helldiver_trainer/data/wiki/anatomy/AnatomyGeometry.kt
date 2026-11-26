package cn.antares.helldiver_trainer.data.wiki.anatomy

import cn.antares.helldiver_trainer.data.wiki.model.AnatomyHitMap
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import kotlin.math.min

/**
 * 二维坐标点（屏幕像素或归一化坐标）。
 */
data class Vec2(
    val x: Float,
    val y: Float,
)

/**
 * 图像按 `ContentScale.Fit` 居中适配容器后的绘制矩形与缩放比例。
 */
data class FitRect(
    val offsetX: Float,
    val offsetY: Float,
    val drawWidth: Float,
    val drawHeight: Float,
    val scale: Float,
)

/**
 * 解码后的点击区域网格映射表：每个像素格存放 `0`（空白）或 `1..partPalette.size`（1-based 部位索引）。
 */
class DecodedHitMap(
    val width: Int,
    val height: Int,
    val partPalette: List<String>,
    val cells: IntArray,
)

/**
 * 解剖图几何逆变换与绿色区域 RLE 查表工具集。
 */
object AnatomyGeometry {

    /**
     * 计算图像在容器内保持宽高比居中适配（Fit）时的绘制矩形。
     */
    fun computeFitRect(
        containerWidth: Float,
        containerHeight: Float,
        imageWidth: Int,
        imageHeight: Int,
    ): FitRect? {
        if (containerWidth <= 0f || containerHeight <= 0f || imageWidth <= 0 || imageHeight <= 0) {
            return null
        }
        val imgW = imageWidth.toFloat()
        val imgH = imageHeight.toFloat()
        val scale = min(containerWidth / imgW, containerHeight / imgH)
        val drawWidth = imgW * scale
        val drawHeight = imgH * scale
        val offsetX = (containerWidth - drawWidth) / 2f
        val offsetY = (containerHeight - drawHeight) / 2f
        return FitRect(
            offsetX = offsetX,
            offsetY = offsetY,
            drawWidth = drawWidth,
            drawHeight = drawHeight,
            scale = scale,
        )
    }

    /**
     * 将屏幕点击坐标逆变换为底图归一化 `[0, 1]` 坐标；若落点在底图范围外或参数无效则返回 `null`。
     */
    fun screenToNormalized(
        tap: Vec2,
        fitRect: FitRect,
        containerWidth: Float,
        containerHeight: Float,
        zoom: Float,
        pan: Vec2,
    ): Vec2? {
        if (zoom <= 0f || fitRect.drawWidth <= 0f || fitRect.drawHeight <= 0f) {
            return null
        }
        val cx = containerWidth / 2f
        val cy = containerHeight / 2f
        val fitX = (tap.x - pan.x - cx) / zoom + cx
        val fitY = (tap.y - pan.y - cy) / zoom + cy
        val u = (fitX - fitRect.offsetX) / fitRect.drawWidth
        val v = (fitY - fitRect.offsetY) / fitRect.drawHeight
        if (u < 0f || u > 1f || v < 0f || v > 1f) {
            return null
        }
        return Vec2(u, v)
    }

    /**
     * 按当前预览状态 [previewState] 解码 RLE 区域映射表；若无独立 `brokenRle`，回退使用 `intactRle`。
     */
    fun decodeHitMap(
        hitMap: AnatomyHitMap?,
        previewState: EnemyPartState,
    ): DecodedHitMap? {
        if (hitMap == null || hitMap.width <= 0 || hitMap.height <= 0) return null
        val rle = if (previewState == EnemyPartState.BROKEN && hitMap.brokenRle.isNotEmpty()) {
            hitMap.brokenRle
        } else {
            hitMap.intactRle
        }
        if (rle.isEmpty() || rle.size % 2 != 0) return null
        val total = hitMap.width * hitMap.height
        val cells = IntArray(total)
        var cursor = 0
        var i = 0
        while (i < rle.size && cursor < total) {
            val value = rle[i]
            val count = rle[i + 1]
            if (count > 0) {
                val end = (cursor + count).coerceAtMost(total)
                for (pos in cursor until end) {
                    cells[pos] = value
                }
                cursor = end
            }
            i += 2
        }
        if (cursor != total) return null
        return DecodedHitMap(
            width = hitMap.width,
            height = hitMap.height,
            partPalette = hitMap.partPalette,
            cells = cells,
        )
    }

    /**
     * 根据归一化 `[0, 1]` 坐标在已解码映射表中以 O(1) 复杂度查询命中的部位 ID；命中空白返回 `null`。
     */
    fun hitTestGrid(
        u: Float,
        v: Float,
        decoded: DecodedHitMap?,
    ): String? {
        if (decoded == null || u < 0f || u > 1f || v < 0f || v > 1f) return null
        val col = (u * decoded.width).toInt().coerceIn(0, decoded.width - 1)
        val row = (v * decoded.height).toInt().coerceIn(0, decoded.height - 1)
        val paletteIdx = decoded.cells[row * decoded.width + col]
        if (paletteIdx <= 0 || paletteIdx > decoded.partPalette.size) return null
        return decoded.partPalette[paletteIdx - 1]
    }

    /**
     * 根据点击命中结果解析下一个选中部位 ID；点击空白区域时保留当前选中状态。
     */
    fun resolveSelectionOnTap(
        hitPartId: String?,
        currentSelectedPartId: String?,
    ): String? = hitPartId ?: currentSelectedPartId
}
