// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 объявляет собственную зависимость на KSP и понижает его до 2.2.10-2.0.2 —
        // это ещё KSP1, несовместимый со встроенной поддержкой Kotlin. Версия закрепляется
        // здесь, потому что через plugins-блок и каталог AGP всё равно навяжет свой пин.
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.ksp.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
}
