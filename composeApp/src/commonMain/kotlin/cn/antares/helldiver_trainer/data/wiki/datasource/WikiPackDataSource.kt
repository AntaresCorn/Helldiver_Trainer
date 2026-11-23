package cn.antares.helldiver_trainer.data.wiki.datasource

/**
 * 平台无关的离线资料包字节流读取抽象。
 *
 * 用于隔离 `commonMain` 仓库层与具体资源载体（如 Compose Resources），
 * 方便在单元测试中直接注入内存或假数据源实现。
 */
interface WikiPackDataSource {
    /** 根据资料包相对路径读取原始 UTF-8 字节内容。 */
    suspend fun readBytes(relativePath: String): ByteArray
}
