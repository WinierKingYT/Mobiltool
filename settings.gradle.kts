pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PersonalMobileTool"

include(":app")
include(":core-common")
include(":core-model")
include(":core-designsystem")
include(":core-storage")
include(":core-security")
include(":core-jobs")
include(":call-capture-api")
include(":media-extractor-api")
include(":transcription-api")
include(":desktop-bridge")
