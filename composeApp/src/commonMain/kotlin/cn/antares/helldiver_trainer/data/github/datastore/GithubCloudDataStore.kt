package cn.antares.helldiver_trainer.data.github.datastore

import cn.antares.helldiver_trainer.data.github.entity.GithubReleaseEntity
import de.jensklingenberg.ktorfit.http.GET

interface GithubCloudDataStore {
    @GET("repos/AntaresCorn/Helldiver_Trainer/releases/latest")
    suspend fun getLatestRelease(): GithubReleaseEntity
}