plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

java {
    withSourcesJar()
}

ktlint {
    version.set("1.8.0")
}

dependencies {
    api("io.ktor:ktor-client-core:3.6.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("io.ktor:ktor-client-cio:3.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:3.6.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("Better Auth Kotlin Client")
                description.set("A Kotlin/JVM client for Better Auth servers.")
                url.set("https://github.com/better-auth-rs/better-auth-kotlin-client")
            }
        }
    }
}
