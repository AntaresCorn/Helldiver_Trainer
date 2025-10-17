import org.jetbrains.kotlin.compose.compiler.gradle.ComposeFeatureFlag
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    //alias(libs.plugins.kotlinKsp)
    //alias(libs.plugins.ktorfitPlugin)
    alias(libs.plugins.mokoPlugin)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        androidMain.dependencies {
            implementation(compose.runtime)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.appcompat)
            implementation(libs.androidx.fragment)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.preview)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kmpessentials)
            //implementation(libs.ktorfit.lib)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.compose.viewmodel.navigation)
            implementation(libs.koin.core)
            //implementation(libs.coil.compose)
            //implementation(libs.coil.network.okhttp)
            implementation(libs.navigation.compose)
            implementation(libs.moko.resources)
            implementation(libs.moko.resources.compose)
            implementation(libs.multiplatform.settings)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.korau.jvm)
                implementation(libs.korio.jvm)
            }
        }
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
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "cn.antares.helldiver_trainer.MainKt"

        nativeDistributions {
            packageName = "HelldiverTrainer"
            packageVersion = libs.versions.versionName.get()
            vendor = "Antares"
            description = "Helldiver Trainer"

            /* exe没有声音，解决前不打包
            targetFormats(TargetFormat.Exe)
            windows {
                iconFile.set(project.file("src/desktopMain/resources/ic_launcher.ico"))
                menuGroup = "Antares"
            }*/
        }
    }
}

composeCompiler {
    featureFlags.add(ComposeFeatureFlag.OptimizeNonSkippingGroups)
}

// moko要求
multiplatformResources {
    resourcesPackage = "cn.antares.helldiver_trainer"
}

dependencies {
    debugImplementation(compose.uiTooling)
}