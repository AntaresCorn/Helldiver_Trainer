plugins {
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21"
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
}

gradlePlugin {
    plugins {
        register("wikiGenerator") {
            id = "helldiver.wiki.generator"
            implementationClass = "cn.antares.helldiver_trainer.buildlogic.WikiGeneratePlugin"
        }
    }
}
