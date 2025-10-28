package cn.antares.helldiver_trainer.data.github.repository

import cn.antares.helldiver_trainer.data.github.datastore.GithubCloudDataStore
import cn.antares.helldiver_trainer.data.github.entity.GithubReleaseEntity

class GithubRepositoryImpl(private val githubCloudDataStore: GithubCloudDataStore) :
    GithubRepository {

    override suspend fun getLatestRelease(): GithubReleaseEntity {
        return githubCloudDataStore.getLatestRelease()
    }
}