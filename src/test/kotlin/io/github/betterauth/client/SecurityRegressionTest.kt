package io.github.betterauth.client

import io.github.betterauth.client.config.BetterAuthConfig
import io.github.betterauth.client.http.AuthMode
import io.github.betterauth.client.http.ResponseHeaders
import io.github.betterauth.client.http.SessionTransport
import io.github.betterauth.client.model.SessionData
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginHooks
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.plugin.ResponseContext
import io.github.betterauth.client.result.BetterAuthError
import io.github.betterauth.client.session.StoredCookie
import io.github.betterauth.client.session.StoredSession
import io.github.betterauth.client.storage.InMemoryStorage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecurityRegressionTest {
    private val sessionBody = """{"session":${SESSION_JSON.replace(
        "2026-02-01T00:00:00.000Z",
        "2999-01-01T00:00:00.000Z",
    )},"user":$USER_JSON}"""

    @Test
    fun `scoped credentials replace mixed case configured credentials in cookie mode`() =
        runTest {
            val server = TestServer { json("""{"ok":true}""") }
            val parent =
                server.client(AuthMode.Cookie) {
                    headers["aUtHoRiZaTiOn"] = "Bearer administrator"
                    headers["cOoKiE"] = "better-auth.session_token=administrator"
                }
            parent.restoreSession(
                StoredSession(
                    cookies =
                        mapOf("better-auth.session_token" to StoredCookie("better-auth.session_token", "parent")),
                ),
            )
            val scoped = parent.withSession("user%2Bsignature%3D")
            scoped.ok().getOrThrow()
            assertEquals(listOf("Bearer user%2Bsignature%3D"), server.last.headers.getAll("Authorization"))
            assertNull(server.last.header("Cookie"))
            parent.ok().getOrThrow()
            assertNull(server.last.header("Authorization"))
            assertEquals(listOf("better-auth.session_token=parent"), server.last.headers.getAll("Cookie"))
            parent.close()
        }

    @Test
    fun `signed credentials remain encoded in both response credential channels`() =
        runTest {
            val signed = "raw.a%2Bb%2Fc%3D"
            for (header in listOf("set-auth-token", "Set-Cookie")) {
                val value = if (header == "Set-Cookie") "better-auth.session_token=$signed; Max-Age=3600" else signed
                val server = TestServer { json("""{"ok":true}""", headers = arrayOf(header to listOf(value))) }
                val auth = server.client()
                auth.ok().getOrThrow()
                auth.ok().getOrThrow()
                assertEquals(signed, auth.currentToken())
                assertEquals("Bearer $signed", server.last.header("Authorization"))
                auth.close()
            }
        }

    @Test
    fun `cookie namespace is exact and bearer excludes every session cache chunk`() =
        runTest {
            val server =
                TestServer { request ->
                    if (request.path.endsWith("/sign-out")) {
                        json("""{"success":true}""")
                    } else {
                        json(
                            """{"ok":true}""",
                            headers =
                                arrayOf(
                                    "Set-Cookie" to
                                        listOf(
                                            "better-auth.session_token=token; Max-Age=3600",
                                            "__Secure-better-auth.session_data.0=first; Max-Age=3600",
                                            "__Secure-better-auth.session_data.1=second; Max-Age=3600",
                                            "better-auth.account_data.0=account; Max-Age=3600",
                                            "better-auth.trust_device=trusted; Max-Age=3600",
                                            "better-auth.session_token_multi-other=other; Max-Age=3600",
                                            "better-auth-evil.session_token=attacker; Max-Age=3600",
                                            "better-auth2.trust_device=attacker; Max-Age=3600",
                                        ),
                                ),
                        )
                    }
                }
            val auth = server.client()
            auth.ok().getOrThrow()
            auth.ok().getOrThrow()
            assertEquals(
                "better-auth.account_data.0=account; better-auth.trust_device=trusted; better-auth.session_token_multi-other=other",
                server.last.header("Cookie"),
            )
            assertTrue(
                auth
                    .storedSession()
                    .cookies.keys
                    .none { it.startsWith("better-auth-") || it.startsWith("better-auth2") },
            )
            auth.signOut().getOrThrow()
            assertNull(auth.currentToken())
            assertEquals(setOf("better-auth.trust_device", "better-auth.session_token_multi-other"), auth.storedSession().cookies.keys)
            auth.close()
        }

    @Test
    fun `authoritative null clears credentials and cache but retains trust cookies`() =
        runTest {
            var body = sessionBody
            val server = TestServer { json(body) }
            val auth = server.client()
            auth.restoreSession(
                StoredSession(
                    token = "old",
                    jwt = "old-jwt",
                    cookies =
                        mapOf(
                            "better-auth.session_data.0" to StoredCookie("better-auth.session_data.0", "cached"),
                            "better-auth.trust_device" to StoredCookie("better-auth.trust_device", "trusted"),
                        ),
                ),
            )
            auth.getSession().success<SessionData>()
            assertNotNull(auth.cachedSession())
            body = "null"
            assertNull(auth.getSession().getOrThrow())
            assertNull(auth.currentToken())
            assertNull(auth.storedSession().jwt)
            assertNull(auth.cachedSession())
            assertEquals(setOf("better-auth.trust_device"), auth.storedSession().cookies.keys)
            auth.close()
        }

    @Test
    fun `empty and incomplete session responses fail without signing the user out`() =
        runTest {
            for (body in listOf("", "{}", "[]", """{"user":$USER_JSON}""")) {
                val server = TestServer { json(body) }
                val auth = server.client()
                auth.restoreToken("signed")
                assertIs<BetterAuthError.Decoding>(auth.getSession().failure())
                assertEquals("signed", auth.currentToken())
                auth.close()
            }
        }

    @Test
    fun `corrupt cached session surfaces its decoding error`() =
        runTest {
            val storage = InMemoryStorage()
            val server = TestServer { json(sessionBody) }
            val auth = server.client { this.storage = storage }
            storage.setItem("better-auth_session_data", "{")
            assertFailsWith<SerializationException> { auth.cachedSession() }
            storage.setItem("better-auth_session_data", "{}")
            assertFailsWith<IllegalArgumentException> { auth.cachedSession() }
            assertTrue(server.requests.isEmpty())
            auth.close()
        }

    @Test
    fun `restoring another identity clears stale cache and old session credentials`() =
        runTest {
            val server = TestServer { json(sessionBody) }
            val auth = server.client()
            auth.restoreSession(
                StoredSession(
                    token = "alice",
                    jwt = "alice-jwt",
                    cookies = mapOf("better-auth.session_token" to StoredCookie("better-auth.session_token", "alice")),
                ),
            )
            auth.getSession().getOrThrow()
            assertNotNull(auth.cachedSession())
            auth.restoreToken("bob")
            assertNull(auth.cachedSession())
            assertEquals(StoredSession(token = "bob"), auth.storedSession())
            auth.getSession().getOrThrow()
            auth.restoreSession(StoredSession(token = "carol"))
            assertNull(auth.cachedSession())
            auth.close()
        }

    @Test
    fun `configuration changes after creation cannot change requests or session handling`() =
        runTest {
            val server =
                TestServer {
                    json("""{"ok":true}""", headers = arrayOf("Set-Cookie" to listOf("better-auth.trust_device=trusted; Max-Age=3600")))
                }
            val config =
                BetterAuthConfig().apply {
                    baseUrl = BASE
                    engine = server.engine
                    userAgent = "original-agent"
                    headers["X-App-Key"] = "original-key"
                }
            val auth = BetterAuthClient.create(config)
            config.baseUrl = "https://changed.example/other"
            config.userAgent = "changed-agent"
            config.headers["X-App-Key"] = "changed-key"
            config.cookiePrefix = "changed"
            config.mode = AuthMode.Cookie
            auth.restoreToken("signed")
            auth.ok().getOrThrow()
            assertEquals("auth.example.com", server.last.url.host)
            assertEquals("/api/auth/ok", server.last.path)
            assertEquals("original-agent", server.last.header("User-Agent"))
            assertEquals("original-key", server.last.header("X-App-Key"))
            assertEquals("Bearer signed", server.last.header("Authorization"))
            assertEquals(setOf("better-auth.trust_device"), auth.storedSession().cookies.keys)
            auth.close()
        }

    @Test
    fun `invalid base URLs fail before taking ownership of the engine`() {
        val invalid =
            listOf(
                "https://example.com/api/auth?tenant=x",
                "https://example.com/api/auth#path",
                "https://user:secret@example.com",
                "https:///api/auth",
                "//example.com/api/auth",
                "file:///tmp/auth",
            )
        val engine = TrackingEngine(MockEngine { json("{}") })
        for (url in invalid) {
            assertFailsWith<IllegalArgumentException>(url) {
                BetterAuthClient {
                    baseUrl = url
                    this.engine = engine
                }
            }
        }
        assertEquals(0, engine.closeCount)
        engine.close()
    }

    @Test
    fun `only the parent closes the shared engine and closes it once`() =
        runTest {
            val engine = TrackingEngine(MockEngine { json("""{"ok":true}""") })
            val auth =
                BetterAuthClient {
                    baseUrl = BASE
                    this.engine = engine
                }
            val scoped = auth.withSession("signed")
            scoped.close()
            auth.ok().getOrThrow()
            assertEquals(0, engine.closeCount)
            auth.close()
            auth.close()
            assertEquals(1, engine.closeCount)
        }

    @Test
    fun `clearing a session waits for an in-flight refresh and prevents resurrection`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val engine =
                MockEngine {
                    entered.complete(Unit)
                    release.await()
                    json(sessionBody, headers = arrayOf("set-auth-token" to listOf("rotated")))
                }
            val auth =
                BetterAuthClient {
                    baseUrl = BASE
                    this.engine = engine
                }
            auth.restoreToken("original")
            val refresh = async { auth.getSession().getOrThrow() }
            entered.await()
            val clear = async(start = CoroutineStart.UNDISPATCHED) { auth.clearLocalSession() }
            release.complete(Unit)
            refresh.await()
            clear.await()
            assertNull(auth.currentToken())
            assertNull(auth.cachedSession())
            auth.close()
        }

    @Test
    fun `parent and storage view serialize restore with an in-flight refresh`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val engine =
                MockEngine {
                    entered.complete(Unit)
                    release.await()
                    json(sessionBody, headers = arrayOf("set-auth-token" to listOf("rotated-alice")))
                }
            val sharedStorage = InMemoryStorage()
            val parent =
                BetterAuthClient {
                    baseUrl = BASE
                    this.engine = engine
                    storage = sharedStorage
                }
            parent.restoreToken("alice")
            val refresh = async { parent.getSession().getOrThrow() }
            entered.await()
            val view = parent.withStorage(sharedStorage)
            val restore = async(start = CoroutineStart.UNDISPATCHED) { view.restoreToken("bob") }
            release.complete(Unit)
            refresh.await()
            restore.await()
            assertEquals("bob", parent.currentToken())
            assertNull(parent.cachedSession())
            parent.close()
        }

    @Test
    fun `a delayed response hook cannot clear a later sign-in`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val server =
                TestServer { request ->
                    if (request.path.endsWith("/sign-out")) {
                        json("""{"success":true}""")
                    } else {
                        json("""{"token":"raw","user":$USER_JSON}""", headers = arrayOf("set-auth-token" to listOf("new-signed")))
                    }
                }
            val auth =
                server.client {
                    plugins(
                        responsePlugin { response ->
                            if (response.path == "/sign-out") {
                                entered.complete(Unit)
                                release.await()
                            }
                        },
                    )
                }
            auth.restoreToken("old-signed")
            val signOut = async { auth.signOut().getOrThrow() }
            entered.await()
            auth.signIn.email("new@example.com", "password").getOrThrow()
            release.complete(Unit)
            signOut.await()
            assertEquals("new-signed", auth.currentToken())
            auth.close()
        }

    @Test
    fun `a delayed response hook cannot cache a previous identity after restore`() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val server = TestServer { json(sessionBody) }
            val auth =
                server.client {
                    plugins(
                        responsePlugin {
                            entered.complete(Unit)
                            release.await()
                        },
                    )
                }
            auth.restoreToken("alice")
            val refresh = async { auth.getSession().getOrThrow() }
            entered.await()
            auth.restoreToken("bob")
            release.complete(Unit)
            refresh.await()
            assertEquals("bob", auth.currentToken())
            assertNull(auth.cachedSession())
            auth.close()
        }

    @Test
    fun `email auth cannot reuse an old credential when the new signed credential is absent or invalid`() =
        runTest {
            val invalidHeaders =
                listOf<List<Pair<String, List<String>>>>(
                    emptyList(),
                    listOf("set-auth-token" to listOf("")),
                    listOf("set-auth-token" to listOf("   ")),
                    listOf("Set-Cookie" to listOf("better-auth.session_token=expired; Max-Age=0")),
                    listOf(
                        "set-auth-token" to listOf(""),
                        "Set-Cookie" to listOf("better-auth.session_token=new-signed; Max-Age=3600"),
                    ),
                )
            for (mode in AuthMode.entries) {
                for (signUp in listOf(false, true)) {
                    for (headers in invalidHeaders) {
                        val server =
                            TestServer { request ->
                                if (request.path.endsWith("/get-session")) {
                                    json(sessionBody)
                                } else {
                                    json("""{"token":"new-raw-token","user":$USER_JSON}""", headers = headers.toTypedArray())
                                }
                            }
                        val auth = server.client(mode)
                        auth.restoreSession(
                            StoredSession(
                                token = "old-signed",
                                cookies = mapOf("better-auth.trust_device" to StoredCookie("better-auth.trust_device", "trusted")),
                            ),
                        )
                        auth.getSession().getOrThrow()
                        assertNotNull(auth.cachedSession())
                        val result =
                            if (signUp) {
                                auth.signUp.email("New", "new@example.com", "password")
                            } else {
                                auth.signIn.email("new@example.com", "password")
                            }
                        assertIs<BetterAuthError.Decoding>(result.failure())
                        assertNull(auth.currentToken())
                        assertNull(auth.cachedSession())
                        assertEquals(setOf("better-auth.trust_device"), auth.storedSession().cookies.keys)
                        auth.close()
                    }
                }
            }
        }

    @Test
    fun `cookie mode accepts a fresh signed cookie without a bearer response header`() =
        runTest {
            val signed = "new.a%2Bb%2Fc%3D"
            val server =
                TestServer { request ->
                    if (request.path.endsWith("/sign-in/email")) {
                        json(
                            """{"token":"raw","user":$USER_JSON}""",
                            headers =
                                arrayOf(
                                    "Set-Cookie" to
                                        listOf(
                                            "better-auth.trust_device=trusted; Max-Age=3600, better-auth.session_token=$signed; Max-Age=3600",
                                        ),
                                ),
                        )
                    } else {
                        json("""{"ok":true}""")
                    }
                }
            val auth = server.client(AuthMode.Cookie)
            auth.restoreToken("old-signed")
            auth.signIn.email("new@example.com", "password").getOrThrow()
            assertEquals(signed, auth.currentToken())
            auth.ok().getOrThrow()
            assertEquals("better-auth.trust_device=trusted; better-auth.session_token=$signed", server.last.header("Cookie"))
            assertNull(server.last.header("Authorization"))
            auth.close()
        }

    @Test
    fun `custom transport cannot report authentication while retaining a different user's credential`() =
        runTest {
            val server =
                TestServer {
                    json("""{"token":"raw","user":$USER_JSON}""", headers = arrayOf("set-auth-token" to listOf("new-signed")))
                }
            val auth =
                server.client {
                    sessionTransport =
                        object : SessionTransport {
                            override fun outgoingHeaders(
                                state: StoredSession,
                                cookiePrefix: String,
                                nowMillis: Long,
                            ): Map<String, String> = state.token?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

                            override fun applyResponse(
                                state: StoredSession,
                                headers: ResponseHeaders,
                                cookiePrefix: String,
                                nowMillis: Long,
                            ): StoredSession = state
                        }
                }
            auth.restoreToken("old-signed")
            assertIs<BetterAuthError.Decoding>(auth.signIn.email("new@example.com", "password").failure())
            assertNull(auth.currentToken())
            auth.close()
        }

    @Test
    fun `a changed session credential discards the previous JWT unless the response supplies a new JWT`() =
        runTest {
            var freshJwt: String? = null
            val server =
                TestServer {
                    val headers = mutableListOf("set-auth-token" to listOf("bob-signed"))
                    freshJwt?.let { headers += "set-auth-jwt" to listOf(it) }
                    json("""{"ok":true}""", headers = headers.toTypedArray())
                }
            val auth = server.client()
            auth.restoreSession(StoredSession(token = "alice-signed", jwt = "alice-jwt"))
            auth.ok().getOrThrow()
            assertEquals("bob-signed", auth.currentToken())
            assertNull(auth.storedSession().jwt)

            auth.restoreSession(StoredSession(token = "alice-signed", jwt = "alice-jwt"))
            freshJwt = "bob-jwt"
            auth.ok().getOrThrow()
            assertEquals("bob-jwt", auth.storedSession().jwt)
            auth.close()
        }
}

private class TrackingEngine(
    private val delegate: HttpClientEngine,
) : HttpClientEngine by delegate {
    var closeCount = 0
        private set

    override fun close() {
        closeCount++
        delegate.close()
    }
}

private fun responsePlugin(block: suspend (ResponseContext) -> Unit): ClientPlugin<Unit> =
    object : ClientPlugin<Unit> {
        override val key = PluginKey<Unit>("regression-hook", "regressionHook()")
        override val hooks = PluginHooks(onResponse = block)

        override fun createApi(context: PluginContext) = Unit
    }
