// ============================================================
// FILE: settings.gradle.kts (Project Root)
// Purpose: Configures plugin repositories and project modules
// ============================================================

/* Plugin management block — defines where Gradle resolves plugins from */
pluginManagement {
    repositories {
        /* Google's Maven repository for Android and Firebase plugins */
        google()
        /* Maven Central for Kotlin and general JVM plugins */
        mavenCentral()
        /* Gradle Plugin Portal for community plugins */
        gradlePluginPortal()
    }
}

// REMOVED: plugins { id("org.gradle.toolchains.foojay-resolver-convention") ... }
// Reason: Foojay is a Java toolchain resolver — not needed for this project
// Our JVM target is set directly in app/build.gradle.kts

/* Dependency resolution block — defines where Gradle resolves libraries from */
dependencyResolutionManagement {
    /* Fail if any module tries to declare its own repositories */
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        /* Google's Maven for AndroidX, Firebase, and Play Services libraries */
        google()
        /* Maven Central for Kotlin, Coroutines, Hilt, and other JVM libraries */
        mavenCentral()
    }
}

/* Root project name — displayed in Android Studio project view */
 rootProject.name = "NotiSync Sender"

/* Include the app module in the build */
include(":app")
