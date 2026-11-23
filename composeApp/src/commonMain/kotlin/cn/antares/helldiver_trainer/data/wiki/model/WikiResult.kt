package cn.antares.helldiver_trainer.data.wiki.model

/**
 * 资料库仓库层的统一封装返回类型，避免以异常或空值隐式传递失败状态。
 */
sealed interface WikiResult<out T> {
    /** 请求成功，携带加载或查询结果 [data]。 */
    data class Success<T>(val data: T) : WikiResult<T>

    /** 目标条目 [entryId] 在已加载的索引或分片中不存在。 */
    data class Missing(val entryId: String) : WikiResult<Nothing>

    /** 读取或反序列化离线资料包时发生错误，携带可读描述 [message] 与可选原始异常 [cause]。 */
    data class ReadError(val message: String, val cause: Throwable? = null) : WikiResult<Nothing>

    /** 离线资料包的 `schemaVersion`（[foundVersion]）与当前客户端支持版本（[supportedVersion]）不兼容。 */
    data class UnsupportedVersion(val foundVersion: Int, val supportedVersion: Int) :
        WikiResult<Nothing>
}
