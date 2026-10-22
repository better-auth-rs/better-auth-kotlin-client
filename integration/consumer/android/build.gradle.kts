plugins { id("com.android.application") }

android {
    namespace = "io.github.betterauth.consumer"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.betterauth.consumer"
        minSdk = 24
        targetSdk = 37
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val sdkVersion = providers.gradleProperty("sdkVersion").get()
dependencies {
    implementation("com.github.better-auth-rs.better-auth-kotlin-client:better-auth-kotlin-client-android:$sdkVersion")
}
