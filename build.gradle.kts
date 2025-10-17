plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeHotReload) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    //alias(libs.plugins.kotlinKsp) apply false
    //alias(libs.plugins.ktorfitPlugin) apply false
    alias(libs.plugins.mokoPlugin) apply false
}