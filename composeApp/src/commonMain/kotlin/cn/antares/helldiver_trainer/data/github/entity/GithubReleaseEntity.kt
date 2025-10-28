package cn.antares.helldiver_trainer.data.github.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubReleaseEntity(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("name") val releaseName: String? = null,
    @SerialName("html_url") val url: String? = null,
)