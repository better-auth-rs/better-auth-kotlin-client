// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.config

import io.github.betterauth.client.http.AuthMode
import io.github.betterauth.client.http.BearerSessionTransport
import io.github.betterauth.client.http.CookieSessionTransport
import io.github.betterauth.client.http.SessionTransport
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.session.InMemorySessionStore
import io.github.betterauth.client.session.SessionStore
import io.github.betterauth.client.session.StorageSessionStore
import io.github.betterauth.client.storage.KeyValueStorage
import io.ktor.client.engine.HttpClientEngine
import java.net.URI

@DslMarker
public annotation class BetterAuthDsl

/** Configuration for [io.github.betterauth.client.BetterAuthClient]. */
@BetterAuthDsl
public class BetterAuthConfig {
    /**
     * Full URL of the Better Auth handler *including its base path*, e.g. `https://api.example.com/api/auth`.
     * Endpoint paths are appended to this verbatim.
     */
    public var baseUrl: String = ""

    /** How the session is presented to the server. Defaults to [AuthMode.Bearer]. */
    public var mode: AuthMode = AuthMode.Bearer

    /** Override to supply your own [SessionTransport]; takes precedence over [mode]. */
    public var sessionTransport: SessionTransport? = null

    /**
     * Where the session (cookies, token) and the `get-session` cache are persisted. Like the Better Auth Expo
     * plugin's `storage` option, this is an adapter you pass in:
     * `EncryptedDataStoreStorage` on Android, `RedisStorage` on a backend. Defaults to memory only.
     */
    public var storage: KeyValueStorage? = null

    /** Prefix for the storage keys (`<prefix>_cookie`, `<prefix>_session_data`). */
    public var storagePrefix: String = StorageSessionStore.DEFAULT_PREFIX

    /** Skip caching the `get-session` result in [storage] (see `BetterAuthClient.cachedSession`). */
    public var disableCache: Boolean = false

    /** Full control over persistence. Takes precedence over [storage]. */
    public var sessionStore: SessionStore? = null

    /**
     * The server's `advanced.cookiePrefix` (Better Auth default `better-auth`). **Must match the server.**
     * Like the Expo plugin, the SDK only keeps cookies in the `<prefix>.` namespace (ignoring `__Secure-`), so
     * third-party cookies are never stored. With a wrong prefix the session cookie, the two-factor challenge,
     * `trust_device` and the multi-session cookies are all silently dropped: bearer sign-in still works, but 2FA and
     * multi-session do not. A blank prefix keeps every cookie.
     */
    public var cookiePrefix: String = "better-auth"

    /**
     * Value for the `Origin` header. Better Auth rejects POSTs that carry cookies unless their origin is in
     * `trustedOrigins`, and the SDK's cookie jar (2FA, multi-session, last-login-method, ...) means most POSTs do.
     *
     * - `null` (default): use the origin of [baseUrl]. Better Auth always trusts its own base URL, so this works
     *   when [baseUrl] is the server's configured `baseURL`.
     * - a value (e.g. your app scheme `myapp://`): send that; it must be in the server's `trustedOrigins`.
     * - blank (`""`): send no `Origin` header.
     */
    public var origin: String? = null

    /** Value for the `expo-origin` header, for servers running the Expo plugin. */
    public var expoOrigin: String? = null

    /** Sent as `User-Agent`. */
    public var userAgent: String = "better-auth-kotlin-client/$SDK_VERSION"

    /** Extra headers added to every request. `Authorization` and `Cookie` are reserved for the session transport. */
    public val headers: MutableMap<String, String> = LinkedHashMap()

    private val registered = ArrayList<ClientPlugin<*>>()

    /**
     * Registers plugins, like `createAuthClient({ plugins: [...] })`. Only registered plugins exist on the client:
     * `plugins(twoFactorClient(), organizationClient())` makes `auth.twoFactor` and `auth.organization` available.
     * May be called several times; registering the same plugin twice is an error.
     */
    public fun plugins(vararg plugins: ClientPlugin<*>) {
        registered += plugins
    }

    internal val registeredPlugins: List<ClientPlugin<*>> get() = registered

    /**
     * HTTP engine. Defaults to OkHttp, which works on Android and on the JVM.
     * After successful client creation, the parent client owns the engine and closes the engine in `close()`.
     * Scoped views share the engine and do not close the engine.
     */
    public var engine: HttpClientEngine? = null

    public var requestTimeoutMillis: Long = 30_000

    public var connectTimeoutMillis: Long = 10_000

    internal fun resolveSessionStore(): SessionStore =
        sessionStore ?: storage?.let { StorageSessionStore(it, storagePrefix) } ?: InMemorySessionStore()

    internal fun resolveTransport(): SessionTransport =
        sessionTransport ?: when (mode) {
            AuthMode.Bearer -> BearerSessionTransport
            AuthMode.Cookie -> CookieSessionTransport
        }

    internal fun snapshot(): TransportConfig {
        validate()
        return TransportConfig(
            baseUrl = baseUrl,
            origin = origin,
            expoOrigin = expoOrigin,
            userAgent = userAgent,
            headers = headers.toMap(),
            cookiePrefix = cookiePrefix,
            storagePrefix = storagePrefix,
            disableCache = disableCache,
            requestTimeoutMillis = requestTimeoutMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            engine = engine,
            store = resolveSessionStore(),
            sessionTransport = resolveTransport(),
        )
    }

    internal fun validate() {
        require(baseUrl.isNotBlank()) { "BetterAuthConfig.baseUrl must be set" }
        val uri = URI.create(baseUrl)
        require(uri.isAbsolute && uri.scheme.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "BetterAuthConfig.baseUrl must be an absolute HTTP or HTTPS URL with a host"
        }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "BetterAuthConfig.baseUrl must not contain credentials, a query, or a fragment"
        }
    }

    internal companion object {
        const val SDK_VERSION = "0.1.1"
    }
}

/** Values captured after plugins configure the client. */
internal data class TransportConfig(
    val baseUrl: String,
    val origin: String?,
    val expoOrigin: String?,
    val userAgent: String,
    val headers: Map<String, String>,
    val cookiePrefix: String,
    val storagePrefix: String,
    val disableCache: Boolean,
    val requestTimeoutMillis: Long,
    val connectTimeoutMillis: Long,
    val engine: HttpClientEngine?,
    val store: SessionStore,
    val sessionTransport: SessionTransport,
)
