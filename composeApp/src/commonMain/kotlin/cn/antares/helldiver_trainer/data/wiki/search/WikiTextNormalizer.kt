package cn.antares.helldiver_trainer.data.wiki.search

import cn.antares.helldiver_trainer.data.wiki.model.NormalizedTextPair

/**
 * 运行期搜索文本规范化工具。
 *
 * 注意：此处的剥离字符集、全半角转换与空白折叠规则必须与构建期
 * `WikiTextNormalizerBuild` 保持完全同步，否则会导致运行时查询与索引预计算结果不一致。
 */
object WikiTextNormalizer {

    private val strippedChars = setOf('“', '”', '‘', '’', '"', '\'', '.', '·', '-', '_')

    /**
     * 将原始文本规范化为 [NormalizedTextPair]：
     * 1. 全角转半角并统一小写；
     * 2. 剔除常见引号、连接符与标点；
     * 3. 折叠连续空白生成 `text`，再移除全部空白生成 `compact`。
     */
    fun normalize(raw: String): NormalizedTextPair {
        val sb = StringBuilder(raw.length)
        var lastWasSpace = true
        for (ch in raw) {
            val half = toHalfWidth(ch).lowercaseChar()
            if (half in strippedChars) {
                continue
            }
            if (half.isWhitespace()) {
                if (!lastWasSpace) {
                    sb.append(' ')
                    lastWasSpace = true
                }
            } else {
                sb.append(half)
                lastWasSpace = false
            }
        }
        val normalized = sb.toString().trim()
        val compact = normalized.filterNot { it.isWhitespace() }
        return NormalizedTextPair(text = normalized, compact = compact)
    }

    /**
     * 将全角空格（U+3000）及全角 ASCII 字符（U+FF01..U+FF5E）转换为对应的半角字符。
     */
    private fun toHalfWidth(ch: Char): Char = when (ch.code) {
        0x3000 -> ' '
        in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
        else -> ch
    }
}
