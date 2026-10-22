# Better Auth Kotlin Client

A Kotlin/JVM and Android SDK for [Better Auth](https://www.better-auth.com), including compatible [better-auth-rs](https://github.com/better-auth-rs/better-auth-rs) endpoints. The SDK covers account recovery, sessions, social authentication, 11 authentication and management plugins, and optional Android and Redis storage. Each server must enable the plugins used by the client.

## Install

Use Kotlin 2.4.21 and JVM 17. Android projects require `compileSdk` 37 and `minSdk` 24.

```kotlin
// settings.gradle.kts
repositories {
    google()
    mavenCentral()
    maven("https://jitpack.io")
}

// build.gradle.kts
implementation("com.github.better-auth-rs.better-auth-kotlin-client:better-auth-kotlin-client:v0.2.0")
// Optional Android persistence:
implementation("com.github.better-auth-rs.better-auth-kotlin-client:better-auth-kotlin-client-android:v0.2.0")
// Optional backend persistence:
implementation("com.github.better-auth-rs.better-auth-kotlin-client:better-auth-kotlin-client-redis:v0.2.0")
```

Add the core and the adapter required by your application. Each adapter already depends on the core. The repository-level JitPack coordinate aggregates all three modules; use the module coordinates above.

## Use

```kotlin
import io.github.betterauth.client.BetterAuthClient

suspend fun example() {
    BetterAuthClient {
        baseUrl = "https://example.com/api/auth"
    }.use { auth ->
        auth.signIn.email("ada@example.com", "your-password").getOrThrow()
        val session = auth.getSession().getOrThrow()
        println(session?.user?.name)
        auth.signOut().getOrThrow()
    }
}
```

Enable the server's bearer plugin for the default transport, or select cookie authentication. The default HTTP engine is Ktor OkHttp. Create one session per user and close the parent client when its owner is destroyed. Persist production credentials with a secure storage adapter.

See [usage and plugins](docs/usage.md), [coverage and verification](docs/coverage.md), [migration from v0.1.0](docs/migration.md), and [development and releases](docs/development.md).

## License

Original code uses [MIT](LICENSE). Adapted reference code uses [Apache-2.0](licenses/Apache-2.0.txt); see [NOTICE](NOTICE) for attribution and modifications.
