package cn.antares.helldiver_trainer.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * 可缓存的 Wiki 内容校验与资产生成任务。
 *
 * 通过 `@CacheableTask` 与相对路径敏感的输入/输出目录声明支持 Gradle 增量构建与构建缓存：
 * 仅当 `wiki-content` 目录、`moko-resources/images` 图标目录或版本属性发生变更时才重新执行，
 * 并输出至独立的 Kotlin 生成目录与 Compose 资源目录，避免污染非声明路径以兼容配置缓存。
 */
@CacheableTask
abstract class WikiGenerateTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contentDir: DirectoryProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mokoImagesDir: DirectoryProperty

    @get:Input
    abstract val appVersion: Property<String>

    @get:Input
    abstract val packVersion: Property<String>

    @get:OutputDirectory
    abstract val outputKotlinDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputResourcesDir: DirectoryProperty

    /**
     * 任务执行入口：先调用 [WikiValidator.loadAndValidate] 完成全量内容与资源校验，
     * 校验通过后再由 [WikiGenerator.generate] 输出生成的 Kotlin 代码与离线 Wiki 资源包。
     */
    @TaskAction
    fun run() {
        val validated = WikiValidator.loadAndValidate(
            contentDir = contentDir.get().asFile,
            mokoImagesDir = mokoImagesDir.get().asFile,
        )
        WikiGenerator.generate(
            validated = validated,
            appVersion = appVersion.get(),
            packVersion = packVersion.get(),
            outputKotlinDir = outputKotlinDir.get().asFile,
            outputResourcesDir = outputResourcesDir.get().asFile,
        )
    }
}
