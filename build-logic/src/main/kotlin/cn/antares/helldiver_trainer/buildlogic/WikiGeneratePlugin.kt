package cn.antares.helldiver_trainer.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension

/**
 * Wiki 资产构建期校验与代码生成插件。
 *
 * 负责从 `libs.versions.toml` 读取 `versionName`，注册 `generateWikiAssets` 任务，
 * 将生成的 Kotlin 代码目录接入 `commonMain` 源码集，并把该任务挂载为 KSP、Kotlin 编译
 * 与 Compose 资源处理任务的前置依赖，确保每次构建前自动校验 `wiki-content` 并刷新离线资源包。
 */
@Suppress("unused")
class WikiGeneratePlugin : Plugin<Project> {

    /**
     * 配置目标项目的 Wiki 生成任务与构建任务依赖图。
     *
     * @param target 应用本插件的 Gradle 子项目（通常为 `:composeApp`）。
     */
    override fun apply(target: Project) {
        val generatedKotlinDir = target.layout.buildDirectory.dir("generated/wiki/kotlin")
        val generatedWikiResDir =
            target.layout.projectDirectory.dir("src/commonMain/composeResources/files/wiki")
        val contentDir = target.layout.projectDirectory.dir("../wiki-content")
        val mokoImagesDir =
            target.layout.projectDirectory.dir("src/commonMain/moko-resources/images")

        val versionName = runCatching {
            val catalogs = target.extensions.getByType(VersionCatalogsExtension::class.java)
            catalogs.named("libs").findVersion("versionName").get().requiredVersion
        }.getOrDefault("1.0.0")

        val generateTask =
            target.tasks.register("generateWikiAssets", WikiGenerateTask::class.java) { task ->
                task.group = "wiki"
                task.description =
                    "Validates wiki-content JSON and generates Kotlin catalog + offline pack"
                task.contentDir.set(contentDir)
                task.mokoImagesDir.set(mokoImagesDir)
                task.appVersion.set(versionName)
                task.packVersion.set("1.0.0")
                task.outputKotlinDir.set(generatedKotlinDir)
                task.outputResourcesDir.set(generatedWikiResDir)
            }

        // 若存在 kotlin 扩展，则将生成的 Kotlin 源码目录注册到 commonMain 源码集（通过反射避免硬编码依赖 KMP 插件类）
        target.extensions.findByName("kotlin")?.let { kotlinExt ->
            runCatching {
                val getSourceSets = kotlinExt::class.java.getMethod("getSourceSets")
                val sourceSets =
                    getSourceSets.invoke(kotlinExt) as org.gradle.api.NamedDomainObjectContainer<*>
                val commonMain = sourceSets.getByName("commonMain")
                val getKotlin = commonMain::class.java.getMethod("getKotlin")
                val kotlinSrcDirSet =
                    getKotlin.invoke(commonMain) as org.gradle.api.file.SourceDirectorySet
                kotlinSrcDirSet.srcDir(generatedKotlinDir)
            }.onFailure { err ->
                target.logger.warn("Failed to add generated wiki kotlin dir to commonMain: ${err.message}")
            }
        }

        target.tasks.matching { task ->
            val name = task.name
            name.startsWith("ksp") ||
                    (name.startsWith("compile") && name.contains("Kotlin")) ||
                    name.startsWith("prepareComposeResourcesTask") ||
                    name.startsWith("copyNonXmlValueResources") ||
                    name == "generateComposeResClass"
        }.configureEach { task ->
            task.dependsOn(generateTask)
        }
    }
}
