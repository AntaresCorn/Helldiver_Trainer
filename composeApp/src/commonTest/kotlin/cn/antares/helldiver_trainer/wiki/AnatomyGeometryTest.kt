package cn.antares.helldiver_trainer.wiki

import cn.antares.helldiver_trainer.data.wiki.anatomy.AnatomyGeometry
import cn.antares.helldiver_trainer.data.wiki.anatomy.Vec2
import cn.antares.helldiver_trainer.data.wiki.model.AnatomyHitMap
import cn.antares.helldiver_trainer.data.wiki.model.EnemyPartState
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnatomyGeometryTest {

    @Test
    fun `computeFitRect centers letterboxed image inside container`() {
        // 容器 1000x800，图像 1200x800（宽高比 3:2 -> 绘制 1000x666.67，上下各留白 66.67）
        val rect = AnatomyGeometry.computeFitRect(
            containerWidth = 1000f,
            containerHeight = 800f,
            imageWidth = 1200,
            imageHeight = 800,
        )
        assertNotNull(rect)
        assertEquals(1000f, rect.drawWidth, 0.01f)
        assertTrue(abs(rect.drawHeight - 666.6667f) < 0.1f)
        assertEquals(0f, rect.offsetX, 0.01f)
        assertTrue(abs(rect.offsetY - 66.6667f) < 0.1f)
    }

    @Test
    fun `screenToNormalized handles zoom and pan and rejects letterbox margin`() {
        val cw = 1000f
        val ch = 800f
        val rect = AnatomyGeometry.computeFitRect(cw, ch, 1200, 800)!!
        val zoom = 2f
        val pan = Vec2(60f, -40f)

        // 归一化中心 (0.5, 0.5) 在 zoom=2, pan=(60, -40) 时对应屏幕 (560, 360)
        val norm = AnatomyGeometry.screenToNormalized(
            tap = Vec2(560f, 360f),
            fitRect = rect,
            containerWidth = cw,
            containerHeight = ch,
            zoom = zoom,
            pan = pan,
        )
        assertNotNull(norm)
        assertTrue(abs(norm.x - 0.5f) < 1e-4f)
        assertTrue(abs(norm.y - 0.5f) < 1e-4f)

        // 点击留白外侧 -> null
        val out = AnatomyGeometry.screenToNormalized(
            tap = Vec2(500f, 10f),
            fitRect = rect,
            containerWidth = cw,
            containerHeight = ch,
            zoom = 1f,
            pan = Vec2(0f, 0f),
        )
        assertNull(out)
    }

    @Test
    fun `decodeHitMap switches between intact and broken RLE and hitTestGrid resolves parts`() {
        // 4x2 网格（8 像素）
        // intact: 前 4 像素为 1(leg_plate)，后 4 像素为 0(空白)
        // broken: 前 4 像素为 2(leg_flesh)，后 4 像素为 0(空白)
        val hitMap = AnatomyHitMap(
            width = 4,
            height = 2,
            partPalette = listOf("leg_plate", "leg_flesh"),
            intactRle = listOf(1, 4, 0, 4),
            brokenRle = listOf(2, 4, 0, 4),
        )

        val intactDecoded = AnatomyGeometry.decodeHitMap(hitMap, EnemyPartState.INTACT)
        assertNotNull(intactDecoded)
        assertEquals("leg_plate", AnatomyGeometry.hitTestGrid(0.25f, 0.2f, intactDecoded))
        assertNull(AnatomyGeometry.hitTestGrid(0.25f, 0.8f, intactDecoded))

        val brokenDecoded = AnatomyGeometry.decodeHitMap(hitMap, EnemyPartState.BROKEN)
        assertNotNull(brokenDecoded)
        assertEquals("leg_flesh", AnatomyGeometry.hitTestGrid(0.25f, 0.2f, brokenDecoded))
        assertNull(AnatomyGeometry.hitTestGrid(0.25f, 0.8f, brokenDecoded))
    }

    @Test
    fun `tapping blank area preserves current selection`() {
        assertEquals("head", AnatomyGeometry.resolveSelectionOnTap(null, "head"))
        assertEquals("leg_plate", AnatomyGeometry.resolveSelectionOnTap("leg_plate", "head"))
    }
}
