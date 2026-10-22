// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.
plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
}

group = rootProject.group
version = rootProject.version
base.archivesName = "better-auth-kotlin-client-redis"

repositories {
    mavenCentral()
}

dependencies {
    api(project(":"))
    api("io.lettuce:lettuce-core:7.9.0.RELEASE")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test-junit"))
    testImplementation("io.ktor:ktor-client-mock:3.6.0")
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

tasks.test {
    exclude("**/RedisStorageTest.class")
}

tasks.register<Test>("redisIntegrationTest") {
    description = "Tests Redis storage against REDIS_URL and optional VALKEY_URL."
    group = "verification"
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/RedisStorageTest.class")
    outputs.upToDateWhen { false }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "better-auth-kotlin-client-redis"
        }
    }
}
