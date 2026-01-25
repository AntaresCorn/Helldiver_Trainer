import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val appPackageName = "cn.antares.helldiver_trainer"

plugins {
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinKsp)
    alias(libs.plugins.ktorfitPlugin)
    alias(libs.plugins.mokoPlugin)
    alias(libs.plugins.buildKonfig)
}

kotlin {
    androidLibrary {
        namespace = appPackageName
        minSdk = libs.versions.android.minSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }

        androidResources {
            enable = true
        }
    }

    jvm("desktop")

    dependencies {
        implementation(libs.runtime)
        implementation(libs.foundation)
        implementation(libs.material3)
        implementation(libs.material.icons.extended)
        implementation(libs.ui)
        implementation(libs.components.resources)
        implementation(libs.ui.tooling.preview)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.io.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.datetime)
        implementation(libs.ktorfit.lib)
        implementation(libs.ktor.client.core)
        implementation(libs.ktor.client.cio)
        implementation(libs.ktor.client.content.negotiation)
        implementation(libs.ktor.client.logging)
        implementation(libs.ktor.serialization.kotlinx.json)
        implementation(libs.napier)
        implementation(libs.koin.compose)
        implementation(libs.koin.compose.viewmodel)
        implementation(libs.koin.compose.viewmodel.navigation)
        implementation(libs.koin.core)
        implementation(libs.navigation.compose)
        implementation(libs.moko.resources)
        implementation(libs.moko.resources.compose)
        implementation(libs.multiplatform.settings)
    }
}

buildkonfig {
    packageName = appPackageName

    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "VERSION_NAME", libs.versions.versionName.get())
    }
}

composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}

// moko要求
multiplatformResources {
    resourcesPackage = appPackageName
}

dependencies {
    androidRuntimeClasspath(libs.ui.tooling)
}