// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jlleitschuh.gradle.ktlint")
    `maven-publish`
}

group = rootProject.group
base.archivesName = "better-auth-kotlin-client-android"
version = rootProject.version

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "io.github.betterauth.client.android"
    compileSdk = 37

    defaultConfig {
        // DataStore 1.3.0 requires API 24.
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }
}

ktlint {
    version.set("1.8.0")
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    api(project(":"))
    api("androidx.datastore:datastore-preferences:1.3.0")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.ktor:ktor-client-mock:3.6.0")

    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation("io.ktor:ktor-client-mock:3.6.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                artifactId = "better-auth-kotlin-client-android"
            }
        }
    }
}

abstract class PrepareLegalAssets : DefaultTask() {
    @get:InputFiles
    abstract val legalFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:javax.inject.Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun prepare() {
        fileSystem.sync {
            from(legalFiles)
            into(outputDirectory.dir("better-auth"))
        }
    }
}

val legalAssets =
    tasks.register<PrepareLegalAssets>("prepareLegalAssets") {
        legalFiles.from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("licenses/Apache-2.0.txt"))
        outputDirectory.set(layout.buildDirectory.dir("generated/legal-assets"))
    }

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(legalAssets, PrepareLegalAssets::outputDirectory)
}
