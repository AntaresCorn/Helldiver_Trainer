import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
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
    }
}

android {
    namespace = "cn.antares.helldiver_trainer.android"

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
        val signingConfigFile = file("src/main/signing.properties").apply {
            println("read signingConfig: ${exists()}")
        }
        if (signingConfigFile.exists()) {
            create("release") {
                val prop = Properties().apply {
                    signingConfigFile.inputStream().use(this::load)
                }
                val keystoreFile = file("src/main/release.keystore").apply {
                    println("read keystore: ${exists()}")
                }
                storeFile = keystoreFile
                storePassword = prop.getProperty("keystore.password").apply {
                    println("read keystore.password: ${isNullOrEmpty().not()}")
                }
                keyAlias = prop.getProperty("key.alias").apply {
                    println("read key.alias: ${isNullOrEmpty().not()}")
                }
                keyPassword = prop.getProperty("key.password").apply {
                    println("read key.password: ${isNullOrEmpty().not()}")
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
