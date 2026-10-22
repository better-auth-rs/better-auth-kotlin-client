plugins {
    kotlin("jvm") version "2.4.21" apply false
    id("com.android.application") version "9.4.1" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
    }
}

tasks.register("verifyConsumers") {
    group = "verification"
    description = "Runs JVM consumers, checks dependency isolation, and assembles the Android consumer APK."
    dependsOn(":core:run", ":core:verifyIsolation", ":redis:run", ":redis:verifyIsolation", ":android:assembleDebug")
}
