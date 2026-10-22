# Migrate from v0.1.0 to v0.2.0

Version 0.2.0 expands the client and changes the public API and JitPack coordinates. Existing 0.1.0 artifacts remain available.

| v0.1.0 | v0.2.0 |
| --- | --- |
| `com.github.better-auth-rs:better-auth-kotlin-client:v0.1.0` | `com.github.better-auth-rs.better-auth-kotlin-client:better-auth-kotlin-client:v0.2.0` |
| `BetterAuthClient(url)` | `BetterAuthClient { baseUrl = url }` |
| `signUpEmail(...)` | `signUp.email(...).getOrThrow()` |
| `signInEmail(...)` | `signIn.email(...).getOrThrow()`; handle `SignInOutcome.TwoFactorRequired` when the server enables 2FA |
| `getSession()` / `signOut()` | `getSession().getOrThrow()` / `signOut().getOrThrow()` |
| Exceptions for HTTP failures | `BetterAuthResult.Success` or `Failure`; `.getOrThrow()` converts failures to exceptions |
| `TokenStore` | `KeyValueStorage` for serialized credentials and cache, or `SessionStore` for complete session control |
| Models in `io.github.betterauth.client` | Models in `io.github.betterauth.client.model` |
| String timestamps | Nullable `kotlin.time.Instant` timestamps |
| CIO default engine | OkHttp default engine; inject another Ktor engine through `engine` |

`BetterAuthError.Api` carries HTTP status, code, message, and the JSON body. `Network` and `Decoding` preserve their causes. Cancellation, storage errors, and plugin-hook errors propagate. Corrupt persistent data must be handled explicitly by the application; the SDK does not delete unreadable credentials automatically.

To migrate a saved token, read the exact signed credential from your old secure store, call `restoreToken(token)`, then remove the old entry only after the new store succeeds. Do not migrate the raw token from a response body as a signed bearer credential.

Cookie storage now retains challenge and `dont_remember` cookies, so `rememberMe = false` is supported. Configure `cookiePrefix` to match the server. Register each optional plugin and import its extension properties before using the plugin API.

The parent client owns the configured HTTP engine, including an injected engine. Closing a `withSession` or `withStorage` view does not close the parent engine. Do not assign one engine to independent parent clients unless their lifetimes end together.
