# Usage

All remote methods are suspend functions returning `BetterAuthResult`. Use `getOrThrow()` for exception-based handling, or branch on `Success` and `Failure`. Required server features are independent of client plugin registration.

## Configuration and session storage

```kotlin
val auth = BetterAuthClient {
    baseUrl = "https://example.com/api/auth"
    mode = AuthMode.Bearer // Or AuthMode.Cookie.
    cookiePrefix = "better-auth" // Match advanced.cookiePrefix on the server.
    storage = secureStorage // KeyValueStorage; default storage is in memory.
    storagePrefix = "account-123"
    requestTimeoutMillis = 30_000
}
```

The SDK sends the base URL's origin by default. A custom `origin` must appear in the server's trusted origins. Authentication headers come from session storage; configuration and request hooks cannot override `Authorization` or `Cookie`. A custom `SessionTransport` must persist signed email credentials in `StoredSession.token`.

`cachedSession()` reads the last unexpired session response. Use the cached response for initial display, then call `getSession()` to confirm the current server state. A server response of JSON `null` clears the local credentials and cache. Errors leave the failure visible to the caller.

Use `restoreSession`, `restoreToken`, and `clearLocalSession` for local lifecycle operations. `storedSession()` contains sensitive credentials and must not be logged. `cookieHeader()` exports cookies for an application-owned request to the same authentication service.

## Core operations

The client supports email sign-up/sign-in, social sign-in, session lookup/update/list/revocation, profile updates, email/password changes, account deletion, email verification, password reset/verification, account linking, provider tokens, and the server health check. See the [endpoint inventory](coverage.md) for method names and verification evidence.

`signIn.email` can return `SignInOutcome.Authenticated` or `TwoFactorRequired`. Finish the latter with a registered two-factor plugin. Sign-up can return a user with a null token when email verification is required.

## Plugins

```kotlin
import io.github.betterauth.client.plugins.twofactor.twoFactor
import io.github.betterauth.client.plugins.twofactor.twoFactorClient
import io.github.betterauth.client.plugins.organization.organization
import io.github.betterauth.client.plugins.organization.organizationClient

val auth = BetterAuthClient {
    baseUrl = "https://example.com/api/auth"
    plugins(twoFactorClient(), organizationClient())
}
val enrollment = auth.twoFactor.enable(password).getOrThrow()
val organizations = auth.organization.list().getOrThrow()
```

| Registration | API | Capabilities |
| --- | --- | --- |
| `twoFactorClient()` | `twoFactor` | TOTP, OTP, enrollment, backup codes, trusted devices |
| `emailOtpClient()` | `emailOtp`, `signIn.emailOtp` | Email OTP sign-in, verification, password reset, email change |
| `phoneNumberClient()` | `phoneNumber`, `signIn.phoneNumber` | Phone verification, sign-in, password reset |
| `magicLinkClient()` | `magicLink`, `signIn.magicLink` | Request and verify links |
| `anonymousClient()` | `anonymous`, `signIn.anonymous` | Guest sign-in and account removal |
| `oneTimeTokenClient()` | `oneTimeToken` | Generate and redeem a session token once |
| `multiSessionClient()` | `multiSession` | List, switch, and revoke device sessions |
| `jwtClient()` | `jwt` | JWT issuance and JWKS retrieval |
| `passkeyClient()` | `passkey`, `signIn.passkey` | WebAuthn options, credential registration/authentication, list/update/delete |
| `adminClient()` | `admin` | User management, bans, impersonation, sessions, permissions |
| `organizationClient()` | `organization` | Organizations, invitations, members, teams, roles, dynamic permissions |

Import each API from `io.github.betterauth.client.plugins.<plugin>`. An unregistered plugin fails with an instruction naming the required registration. Client plugin registration does not grant server permissions. Dynamic organization roles require a server access-control instance (`ac`) and `dynamicAccessControl.enabled`.

## Android

```kotlin
import io.github.betterauth.client.android.androidClient

val auth = BetterAuthClient {
    baseUrl = "https://example.com/api/auth"
    plugins(androidClient(applicationContext) { scheme = "myapp" })
}
```

The Android plugin supplies AES-256-GCM encrypted DataStore persistence using Android Keystore. It also configures the Expo origin and relative callback URLs. Add `myapp://` to the server's trusted origins and declare Android's `INTERNET` permission. The plugin does not open a browser or display an authentication UI.

Use `EncryptedDataStoreStorage.create(applicationContext)` directly when only persistence is needed. Corruption, key loss, and decryption errors propagate; let the application show a recovery action and explicitly clear unusable credentials. Exclude `datastore/better_auth_kt_client.preferences_pb` from Android Auto Backup because Keystore keys are device-bound. Do not configure different key aliases for the same storage file.

## Redis and Valkey

```kotlin
import io.github.betterauth.client.redis.AesGcmStringCipher
import io.github.betterauth.client.redis.RedisStorage

RedisStorage.connect("rediss://redis.example.com:6379", cipher = AesGcmStringCipher(key32Bytes)).use { storage ->
    auth.withStorage(storage, prefix = "user-$userId").use { userClient ->
        val session = userClient.getSession().getOrThrow()
    }
}
```

Redis and Valkey use the same adapter. Inject Lettuce async commands for existing pools, Sentinel, or Cluster. The default TTL is 30 days and renews on writes. Encryption is optional; provide a 32-byte AES key when stored values must be encrypted. Keep the key outside Redis.

`withSession(token)` and `withStorage(storage, prefix)` share the parent's HTTP connection pool and plugins while keeping user sessions separate. Each session's operations serialize. Views over the same store and prefix share bounded lock stripes; unrelated users can occasionally serialize. Locks are process-local. Do not write one session prefix from multiple processes concurrently.

## Social authentication and passkeys

For native provider sign-in, pass `IdTokenCredentials` to `signIn.social`. For Apple's first sign-in, include `IdTokenUser` profile data.

For a browser flow, the application opens `authorizationProxyUrl(redirect.url)` from `SocialSignInOutcome.RedirectRequired` in Custom Tabs. This flow requires the server's Expo plugin. The application must validate the callback URI and pass its `cookie` query parameter to `completeBrowserSignIn`, then fetch the session. The SDK does not choose deep-link intent filters or verify a provider's UI on behalf of the application.

Passkey APIs preserve WebAuthn JSON. Pass the registration/authentication options to Android Credential Manager and submit the resulting credential JSON to `verifyRegistration` or `signIn.passkey`. Credential Manager UI and relying-party association remain application responsibilities.

## Custom fields and extensions

User, session, and organization models preserve unknown response fields in `additionalFields`. Typed fields take precedence when models are serialized. Submit server-defined fields through the APIs' `additionalFields` arguments.

`request("POST", "/plugin/action", body)` returns JSON. `request<MyResponse>("GET", "/plugin/item")` decodes a serializable response. For reusable behavior, implement `ClientPlugin`, use `PluginContext.request`, and register `PluginHooks`. Request hooks can change headers, query parameters, and bodies. Response hooks observe every completed response. Hooks run outside the session lock, so a hook can read the session without deadlocking.
