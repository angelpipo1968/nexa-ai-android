pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://nexa-sdk.s3.amazonaws.com/android/repo") }
        maven { url = uri("https://raw.githubusercontent.com/NexaAI/core/main") }
    }
}

rootProject.name = "Nexa AI"
include(":android-native-app")
