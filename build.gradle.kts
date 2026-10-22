plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.android.library") version "9.4.1" apply false
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
    implementation("io.ktor:ktor-client-okhttp:3.6.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

tasks.test {
    useJUnitPlatform { excludeTags("server") }
}

allprojects {
    tasks.withType<Jar>().configureEach {
        from(rootProject.files("NOTICE", "LICENSE")) { into("META-INF") }
        from(rootProject.file("licenses")) { into("META-INF/licenses") }
    }
    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                }
            }
        }
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
    }
}

val serverIntegrationTest by tasks.registering(Test::class) {
    description = "Runs against the local Better Auth fixture started by integration/test.mjs."
    testClassesDirs =
        sourceSets.test
            .get()
            .output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("server") }
    outputs.upToDateWhen { false }
}

val observedEndpointCoverage = layout.buildDirectory.file("reports/endpoint-coverage/observed.tsv")
tasks.test {
    val coverageFile = observedEndpointCoverage.get().asFile
    systemProperty("betterAuth.endpointCoverageFile", coverageFile.absolutePath)
    outputs.file(coverageFile)
    doFirst { coverageFile.delete() }
}

val verifyEndpointCoverage by tasks.registering(JavaExec::class) {
    dependsOn(tasks.test)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.betterauth.client.coverage.EndpointCoverage")
    args(observedEndpointCoverage.get().asFile.absolutePath)
    inputs.file(observedEndpointCoverage)
    outputs.file(layout.buildDirectory.file("reports/endpoint-coverage/verified.tsv"))
}

tasks.check {
    dependsOn(verifyEndpointCoverage)
}
