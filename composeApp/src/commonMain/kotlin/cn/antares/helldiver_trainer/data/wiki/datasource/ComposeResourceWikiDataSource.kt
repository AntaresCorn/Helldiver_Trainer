package cn.antares.helldiver_trainer.data.wiki.datasource

import helldiver_trainer.composeapp.generated.resources.Res

/**
 * 基于 Compose Multiplatform `Res.readBytes` 的离线资料包数据源实现。
 */
class ComposeResourceWikiDataSource : WikiPackDataSource {
    override suspend fun readBytes(relativePath: String): ByteArray = Res.readBytes(relativePath)
}
