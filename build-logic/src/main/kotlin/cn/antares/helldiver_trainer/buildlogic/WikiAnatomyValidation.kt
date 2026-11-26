package cn.antares.helldiver_trainer.buildlogic

import java.io.File

/**
 * 二期敌人解剖底图（WebP）与绿色区域 RLE 映射层的构建期校验辅助。
 */
internal object WikiAnatomyValidation {

    const val MAX_WEBP_BYTES = 350 * 1024
    const val MAX_IMAGE_WIDTH = 1200
    const val MAX_IMAGE_HEIGHT = 900
    const val MAX_HITMAP_DIM = 600

    data class ValidatedWebpAsset(
        val assetKey: String,
        val file: File,
        val width: Int,
        val height: Int,
    )

    fun loadAndValidateWebpAssets(
        assetsDir: File,
        fail: (file: String, entryId: String, field: String, reason: String) -> Nothing,
    ): Map<String, ValidatedWebpAsset> {
        if (!assetsDir.exists()) return emptyMap()
        if (!assetsDir.isDirectory) {
            fail("assets/anatomy", "-", "dir", "assets/anatomy 必须是目录")
        }
        val children = assetsDir.listFiles()?.sortedBy { it.name }.orEmpty()
        val result = linkedMapOf<String, ValidatedWebpAsset>()
        for (file in children) {
            val rel = "assets/anatomy/${file.name}"
            if (!file.isFile) {
                fail(rel, "-", "file", "anatomy_assets 下不允许存在子目录: ${file.name}")
            }
            val ext = file.extension.lowercase()
            if (ext == "svg") {
                fail(rel, "-", "format", "严禁使用 SVG 作为解剖底图: ${file.name}")
            }
            if (ext != "webp") {
                fail(rel, "-", "format", "anatomy_assets 仅允许 .webp 文件，实际为: ${file.name}")
            }
            val assetKey = file.nameWithoutExtension
            if (assetKey.isBlank()) {
                fail(rel, "-", "assetKey", "WebP 文件名不能为空")
            }
            val bytes = file.readBytes()
            if (bytes.size > MAX_WEBP_BYTES) {
                fail(
                    rel,
                    assetKey,
                    "sizeBytes",
                    "WebP 图片体积 (${bytes.size} B) 超过硬上限 $MAX_WEBP_BYTES B (350 KB)",
                )
            }
            val (width, height) = parseWebpDimensions(bytes)
                ?: fail(rel, assetKey, "webpHeader", "非法或不受支持的 WebP 文件头")
            if (width !in 1..MAX_IMAGE_WIDTH || height !in 1..MAX_IMAGE_HEIGHT) {
                fail(
                    rel,
                    assetKey,
                    "dimensions",
                    "WebP 分辨率 ${width}x$height 超出上限 (${MAX_IMAGE_WIDTH}x$MAX_IMAGE_HEIGHT)",
                )
            }
            if (result.put(assetKey, ValidatedWebpAsset(assetKey, file, width, height)) != null) {
                fail(rel, assetKey, "assetKey", "重复的 assetKey: $assetKey")
            }
        }
        return result
    }

    /**
     * 按 RIFF/WEBP 规范解析 `VP8 `、`VP8L`、`VP8X` 分块的逻辑宽高。
     */
    internal fun parseWebpDimensions(bytes: ByteArray): Pair<Int, Int>? {
        if (bytes.size < 30) return null
        if (!bytes.matchesAscii(0, "RIFF") || !bytes.matchesAscii(8, "WEBP")) return null
        val chunk = String(bytes, 12, 4, Charsets.US_ASCII)
        return when (chunk) {
            "VP8 " -> {
                if (bytes[23].toInt() and 0xFF != 0x9D ||
                    bytes[24].toInt() and 0xFF != 0x01 ||
                    bytes[25].toInt() and 0xFF != 0x2A
                ) {
                    null
                } else {
                    val w = le16(bytes, 26) and 0x3FFF
                    val h = le16(bytes, 28) and 0x3FFF
                    w to h
                }
            }

            "VP8L" -> {
                if (bytes[20].toInt() and 0xFF != 0x2F) {
                    null
                } else {
                    val bits = le32(bytes, 21)
                    val w = (bits and 0x3FFF) + 1
                    val h = ((bits ushr 14) and 0x3FFF) + 1
                    w to h
                }
            }

            "VP8X" -> {
                val w = le24(bytes, 24) + 1
                val h = le24(bytes, 27) + 1
                w to h
            }

            else -> null
        }
    }

    fun validateHitMap(
        rel: String,
        enemyId: String,
        viewId: String,
        hitMap: AnatomyHitMapRecord,
        validPartIds: Set<String>,
        fail: (file: String, entryId: String, field: String, reason: String) -> Nothing,
    ) {
        val fieldPrefix = "views[$viewId].hitMap"
        if (hitMap.width !in 1..MAX_HITMAP_DIM || hitMap.height !in 1..MAX_HITMAP_DIM) {
            fail(
                rel,
                enemyId,
                "$fieldPrefix.dimensions",
                "hitMap 尺寸 ${hitMap.width}x${hitMap.height} 必须在 1..$MAX_HITMAP_DIM 范围内",
            )
        }
        val seen = mutableSetOf<String>()
        for (partId in hitMap.partPalette) {
            if (partId.isBlank() || !seen.add(partId)) {
                fail(
                    rel,
                    enemyId,
                    "$fieldPrefix.partPalette",
                    "partPalette 中 partId 为空或重复: $partId",
                )
            }
            if (partId !in validPartIds) {
                fail(
                    rel,
                    enemyId,
                    "$fieldPrefix.partPalette",
                    "partPalette 引用了不存在的部位: $partId",
                )
            }
        }
        val expectedPixels = hitMap.width * hitMap.height
        val maxPaletteIndex = hitMap.partPalette.size
        validateRle(
            rel,
            enemyId,
            "$fieldPrefix.intactRle",
            hitMap.intactRle,
            expectedPixels,
            maxPaletteIndex,
            fail,
        )
        validateRle(
            rel,
            enemyId,
            "$fieldPrefix.brokenRle",
            hitMap.brokenRle,
            expectedPixels,
            maxPaletteIndex,
            fail,
        )
    }

    private fun validateRle(
        rel: String,
        enemyId: String,
        field: String,
        rle: List<Int>,
        expectedPixels: Int,
        maxPaletteIndex: Int,
        fail: (file: String, entryId: String, field: String, reason: String) -> Nothing,
    ) {
        if (rle.isEmpty()) return
        if (rle.size % 2 != 0) {
            fail(rel, enemyId, field, "RLE 数组长度必须为偶数，实际为 ${rle.size}")
        }
        var totalPixels = 0L
        var i = 0
        while (i < rle.size) {
            val value = rle[i]
            val count = rle[i + 1]
            if (value !in 0..maxPaletteIndex) {
                fail(rel, enemyId, field, "RLE 部位索引越界: $value (允许 0..$maxPaletteIndex)")
            }
            if (count <= 0) {
                fail(rel, enemyId, field, "RLE 游程长度必须大于 0: $count")
            }
            totalPixels += count.toLong()
            i += 2
        }
        if (totalPixels != expectedPixels.toLong()) {
            fail(
                rel,
                enemyId,
                field,
                "RLE 像素总数 ($totalPixels) 与 hitMap 尺寸 ($expectedPixels) 不一致",
            )
        }
    }

    private fun ByteArray.matchesAscii(offset: Int, text: String): Boolean {
        if (offset + text.length > size) return false
        for (i in text.indices) {
            if (this[offset + i].toInt().toChar() != text[i]) return false
        }
        return true
    }

    private fun le16(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)

    private fun le24(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
                ((b[offset + 1].toInt() and 0xFF) shl 8) or
                ((b[offset + 2].toInt() and 0xFF) shl 16)

    private fun le32(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
                ((b[offset + 1].toInt() and 0xFF) shl 8) or
                ((b[offset + 2].toInt() and 0xFF) shl 16) or
                ((b[offset + 3].toInt() and 0xFF) shl 24)
}
