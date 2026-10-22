import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }
application { mainClass = "MainKt" }
val sdkVersion = providers.gradleProperty("sdkVersion").get()
dependencies {
    implementation("com.github.better-auth-rs.better-auth-kotlin-client:better-auth-kotlin-client-redis:$sdkVersion")
}

tasks.register("verifyIsolation") {
    val runtime = configurations.runtimeClasspath
    doLast {
        val artifacts =
            runtime
                .get()
                .incoming.artifacts.artifacts
        val modules = artifacts.mapNotNull { it.id.componentIdentifier as? ModuleComponentIdentifier }
        check(artifacts.none { it.file.extension == "aar" }) { "The Redis consumer must not resolve Android archives" }
        check(modules.none { it.group.startsWith("androidx.") || it.module.endsWith("-android") }) {
            "The Redis consumer must not resolve Android components"
        }
        check(modules.any { it.module == "better-auth-kotlin-client" }) { "The Redis consumer must resolve the core client" }
        check(modules.any { it.group == "io.lettuce" }) { "The Redis consumer must resolve Lettuce" }
        println("Redis dependency isolation passed")
    }
}
