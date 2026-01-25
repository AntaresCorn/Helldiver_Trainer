import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    target {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    dependencies {
        implementation(projects.composeApp)
        implementation(libs.runtime)
        implementation(libs.androidx.core.ktx)
        implementation(libs.androidx.appcompat)
        implementation(libs.androidx.fragment)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.lifecycle.viewmodel)
        implementation(libs.androidx.lifecycle.runtimeCompose)
    }
}

android {
    namespace = "cn.antares.helldiver_trainer"

    defaultConfig {
        applicationId = "cn.antares.helldiver_trainer"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = libs.versions.versionCode.get().toInt()
        versionName = libs.versions.versionName.get()

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        val signingConfigFile = file("src/androidMain/signing.properties").apply {
            println("signingConfig exists: ${exists()}")
        }
        if (signingConfigFile.exists()) {
            create("release") {
                val prop = Properties().apply {
                    signingConfigFile.inputStream().use(this::load)
                }
                val keystoreFile = file("src/androidMain/release.keystore").apply {
                    println("keystore exists: ${exists()}")
                }
                storeFile = keystoreFile
                storePassword = prop.getProperty("keystore.password").apply {
                    println("keystore.password is empty: ${isNullOrEmpty()}")
                }
                keyAlias = prop.getProperty("key.alias").apply {
                    println("key.alias is empty: ${isNullOrEmpty()}")
                }
                keyPassword = prop.getProperty("key.password").apply {
                    println("key.password is empty: ${isNullOrEmpty()}")
                }
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig =
                signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
}
