package cn.antares.helldiver_trainer.buildlogic

/**
 * 构建期文本标准化工具。
 *
 * 用于在生成 `index.json` 时预计算各条目名称、别名与型号代码的检索字段。
 * 注意：此处的标准化规则（全角转半角、小写化、符号剔除与空白折叠）必须与运行时
 * `WikiTextNormalizer` 保持完全一致，否则预计算索引与用户查询将无法正确匹配。
 */
object WikiTextNormalizerBuild {

    private val strippedChars = setOf('“', '”', '‘', '’', '"', '\'', '.', '·', '-', '_')

    /**
     * 对原始文本执行标准化，返回保留词间单空格与去空白的两种表示。
     *
     * @param raw 待标准化的原始字符串。
     * @return [NormalizedTextPairOutput]，其中 `text` 将连续空白折叠为单空格（适用于分词/常规匹配），
     * `compact` 去除所有空白字符（适用于忽略空格的紧凑子串匹配）。
     */
    fun normalize(raw: String): NormalizedTextPairOutput {
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
        return NormalizedTextPairOutput(text = normalized, compact = compact)
    }

    /**
     * 将全角空格及全角 ASCII 字符转换为对应的半角字符，其它字符保持不变。
     */
    private fun toHalfWidth(ch: Char): Char = when (ch.code) {
        0x3000 -> ' '
        in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
        else -> ch
    }
}
