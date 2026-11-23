package cn.antares.helldiver_trainer.data.wiki.search

import cn.antares.helldiver_trainer.data.wiki.model.WikiDirection

/**
 * 战备方向指令解析器。
 *
 * 支持箭头（↑→↓←）、中文（上下左右）与 WASD（不区分大小写）混合输入，
 * 忽略空白字符后逐字符映射为 [WikiDirection] 序列。
 */
object WikiDirectionParser {

    /** 方向指令识别的最小有效长度，避免 1~2 位短词（如 "as"、"ws"）误触发方向搜索。 */
    private const val MIN_DIRECTION_QUERY_LENGTH = 3

    /**
     * 将输入文本解析为方向序列；若去空白后长度不足 3 位或包含任何非方向字符，则返回 `null`。
     */
    fun parseOrNull(raw: String): List<WikiDirection>? {
        val trimmed = raw.filterNot { it.isWhitespace() }
        if (trimmed.length < MIN_DIRECTION_QUERY_LENGTH) return null

        val result = ArrayList<WikiDirection>(trimmed.length)
        for (ch in trimmed) {
            val dir = when (ch) {
                '↑', '上', 'w', 'W' -> WikiDirection.UP
                '→', '右', 'd', 'D' -> WikiDirection.RIGHT
                '↓', '下', 's', 'S' -> WikiDirection.DOWN
                '←', '左', 'a', 'A' -> WikiDirection.LEFT
                else -> return null
            }
            result.add(dir)
        }
        return result
    }
}
