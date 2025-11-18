package cn.antares.helldiver_trainer.data.github.repository

import cn.antares.helldiver_trainer.data.github.entity.GithubReleaseEntity

interface GithubRepository {

    suspend fun getLatestRelease(): GithubReleaseEntity
}