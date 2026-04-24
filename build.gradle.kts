// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false

    /* Kotlin Android plugin — enables Kotlin compilation for Android */
       alias(libs.plugins.kotlin.android) apply false

    /* KSP — Kotlin Symbol Processing for Room and Hilt code generation */
      alias(libs.plugins.ksp) apply false

    /* Hilt — dependency injection plugin for generating DI components */
      alias(libs.plugins.hilt.android) apply false

    /* Google Services — processes google-services.json at build time */
       alias(libs.plugins.google.services) apply false
}