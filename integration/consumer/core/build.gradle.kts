import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }
application { mainClass = "MainKt" }
val sdkVersion = providers.gradleProperty("sdkVersion").get()
dependencies {
    implementation("com.github.better-auth-rs.better-auth-kotlin-client:better-auth-kotlin-client:$sdkVersion")
}

tasks.register("verifyIsolation") {
    val runtime = configurations.runtimeClasspath
    doLast {
        val artifacts =
            runtime
                .get()
                .incoming.artifacts.artifacts
        val modules = artifacts.mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
        check(artifacts.none { it.file.extension == "aar" }) { "The core consumer must not resolve Android archives" }
        check(modules.none { it.group.startsWith("androidx.") || it.module.endsWith("-android") }) {
            "The core consumer must not resolve Android components"
        }
        check(modules.none { it.group == "io.lettuce" || it.module.endsWith("-redis") }) {
            "The core consumer must not resolve Redis components"
        }
        println("Core dependency isolation passed")
    }
}
