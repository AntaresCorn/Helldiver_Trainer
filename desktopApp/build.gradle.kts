plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    dependencies {
        implementation(projects.composeApp)
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