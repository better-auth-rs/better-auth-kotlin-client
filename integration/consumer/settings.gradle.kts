pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroup("com.github.better-auth-rs.better-auth-kotlin-client") }
        }
    }
}

rootProject.name = "better-auth-release-consumers"
include(":core", ":redis", ":android")
