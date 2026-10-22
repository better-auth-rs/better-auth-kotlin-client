# Better Auth Kotlin Client

A Kotlin/JVM client for [Better Auth](https://www.better-auth.com) servers, including [better-auth-rs](https://github.com/better-auth-rs/better-auth-rs). The initial API covers email sign-up, email sign-in, session lookup, and sign-out.

The client uses Ktor CIO and suspend functions. It targets JVM 17 and Android API 24 or later. Android device compatibility has not been verified. Cookie authentication, social sign-in, two-factor authentication, and Android-specific storage are not included.

## Use

This repository has no published release. Build and publish the snapshot to your local Maven repository:

```sh
devenv shell -- ./gradlew check publishToMavenLocal
```

Use Kotlin 2.4.21 in the consuming project. Add `mavenLocal()` to your application's repositories, then add:

```kotlin
dependencies {
    implementation("io.github.better-auth-rs:better-auth-kotlin-client:0.1.0-SNAPSHOT")
}
```

Enable email/password authentication and the [bearer plugin](https://www.better-auth.com/docs/plugins/bearer) on your server. Use the complete authentication base URL, including `/api/auth` or your custom handler path.

```kotlin
import io.github.betterauth.client.BetterAuthClient

suspend fun example() {
    BetterAuthClient("https://example.com/api/auth").use { auth ->
        val result = auth.signInEmail("ada@example.com", "your-password")
        println(result.user.name)
        val session = auth.getSession() // null means signed out
        auth.signOut()
    }
}
```

Create one client per signed-in user. Close the client when its owner is destroyed. Android applications must declare the `android.permission.INTERNET` permission and call suspend functions from a coroutine.

The client stores the signed `set-auth-token` response header in memory and sends that exact credential with subsequent requests. Signed credentials also work when the server enables `requireSignature`. A server that requires email verification can return a successful sign-up with `token = null`.

The bearer-only API does not expose `rememberMe = false`; that mode depends on an additional cookie.

Pass a `TokenStore` implementation to persist credentials across restarts. Implement `read()` and `write(token)`, where `write(null)` removes the credential. Use secure platform storage for production credentials. The default `InMemoryTokenStore` loses credentials when the process exits. Do not share a store between users or servers.

HTTP failures throw `BetterAuthException` with `status`, `code`, and `message`. Network failures, coroutine cancellation, malformed responses, and storage failures propagate. A failed sign-out retains the local credential so the application can retry. Timestamps preserve the server's ISO 8601 strings. Unknown response fields are ignored.

## Develop

The [devenv](https://devenv.sh) configuration installs JDK 17 and Gradle. The Gradle Wrapper pins Gradle 9.7.0 and verifies its download checksum.

```sh
devenv shell
./gradlew ktlintFormat
./gradlew check
```

`check` runs Kotlin lint and protocol tests. Tests use Ktor MockEngine and a local HTTP server; tests do not require a running Better Auth server.

## License

[MIT](LICENSE).
