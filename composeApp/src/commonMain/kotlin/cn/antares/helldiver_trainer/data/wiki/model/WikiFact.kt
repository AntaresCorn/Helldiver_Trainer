package cn.antares.helldiver_trainer.data.wiki.model

import kotlinx.serialization.Serializable

/**
 * 带四态状态与字段级溯源元数据的资料事实包装结构。
 *
 * 通过显式区分 [WikiFactStatus] 的四种状态，避免将“不适用”或“无限制”误表达为缺失或 0，
 * 同时支持单字段独立记录数据来源 [sourceId]、核验版本号 [verifiedPatch] 及生效条件 [condition]。
 */
@Serializable
data class WikiFact<T>(
    val status: WikiFactStatus = WikiFactStatus.UNKNOWN,
    val value: T? = null,
    val unit: String? = null,
    val sourceId: String? = null,
    val verifiedPatch: String? = null,
    val condition: String? = null,
) {
    companion object {
        /** 构造默认的待核验（[WikiFactStatus.UNKNOWN]）占位事实对象。 */
        fun <T> unknown(): WikiFact<T> = WikiFact(status = WikiFactStatus.UNKNOWN)
    }
}

/**
 * 将 [WikiFact] 渲染为面向界面的中文可读文本。
 *
 * 对于 [WikiFactStatus.KNOWN] 状态下的整数值浮点（如 `3.0`），会自动省略小数部分并输出为整数；
 * 单位优先通过 [unitLabel] 转换（默认将常见英文单位映射为中文），其余状态返回对应的中文状态文案。
 */
fun <T> WikiFact<T>.formatDisplay(unitLabel: (String?) -> String = { defaultUnitLabel(it) }): String {
    return when (status) {
        WikiFactStatus.KNOWN -> {
            val formattedVal = when (val v = value) {
                is Double -> if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
                null -> "待核验"
                else -> v.toString()
            }
            val u = unitLabel(unit)
            if (u.isBlank()) formattedVal else "$formattedVal $u"
        }

        WikiFactStatus.UNKNOWN -> "待核验"
        WikiFactStatus.NOT_APPLICABLE -> "不适用"
        WikiFactStatus.UNLIMITED -> "无限制"
    }
}

private fun defaultUnitLabel(unit: String?): String = when (unit) {
    null -> ""
    "seconds" -> "秒"
    "rounds" -> "发"
    "rpm" -> "发/分"
    else -> unit
}
