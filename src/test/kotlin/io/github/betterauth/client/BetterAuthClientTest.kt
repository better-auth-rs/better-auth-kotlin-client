package io.github.betterauth.client

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BetterAuthClientTest {
    @Test
    fun `signed credentials survive encoding and rotate across the authentication lifecycle`() =
        runTest {
            val store = InMemoryTokenStore()
            val signedToken = "raw-session-token.signature%2Bpart%2Fend%3D"
            val rotatedToken = "rotated-session.signature%2Bnext%3D"
            var requests = 0
            val engine =
                MockEngine { request ->
                    requests += 1
                    when (requests) {
                        1 -> {
                            assertEquals("/custom/auth/sign-in/email", request.url.encodedPath)
                            assertEquals(HttpMethod.Post, request.method)
                            assertNull(request.headers[HttpHeaders.Authorization])
                            assertEquals(
                                Json.parseToJsonElement(
                                    """
                                    {
                                      "email": "ada@example.com",
                                      "password": "correct-password",
                                      "callbackURL": "myapp://signed-in"
                                    }
                                    """.trimIndent(),
                                ),
                                Json.parseToJsonElement(request.body.toByteArray().decodeToString()),
                            )
                            respond(
                                ProtocolFixtures.signIn,
                                headers =
                                    headersOf(
                                        HttpHeaders.ContentType to listOf("application/json"),
                                        "set-auth-token" to listOf(signedToken),
                                    ),
                            )
                        }

                        2 -> {
                            assertEquals("/custom/auth/get-session", request.url.encodedPath)
                            assertEquals(HttpMethod.Get, request.method)
                            assertEquals("Bearer $signedToken", request.headers[HttpHeaders.Authorization])
                            assertNull(request.headers[HttpHeaders.Cookie])
                            respond(
                                ProtocolFixtures.session,
                                headers =
                                    headersOf(
                                        HttpHeaders.ContentType to listOf("application/json"),
                                        "set-auth-token" to listOf(rotatedToken),
                                    ),
                            )
                        }

                        else -> {
                            assertEquals(3, requests)
                            assertEquals("/custom/auth/sign-out", request.url.encodedPath)
                            assertEquals(HttpMethod.Post, request.method)
                            assertEquals("Bearer $rotatedToken", request.headers[HttpHeaders.Authorization])
                            respond("""{"success": true}""", headers = jsonHeaders)
                        }
                    }
                }

            BetterAuthClient("https://example.com/custom/auth/", store, engine).use { auth ->
                val result =
                    auth.signInEmail(
                        "ada@example.com",
                        "correct-password",
                        callbackUrl = "myapp://signed-in",
                    )
                assertEquals("Ada", result.user.name)
                assertEquals(signedToken, store.read())
                assertEquals("user-1", auth.getSession()?.user?.id)
                assertEquals(rotatedToken, store.read())
                auth.signOut()
                assertNull(store.read())
            }
            assertEquals(3, requests)
        }

    @Test
    fun `signup supports verification without a session and preserves wire field names`() =
        runTest {
            val store = InMemoryTokenStore()
            val engine =
                MockEngine { request ->
                    assertEquals("/api/auth/sign-up/email", request.url.encodedPath)
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        Json.parseToJsonElement(
                            """
                            {
                              "name": "Ada",
                              "email": "ada@example.com",
                              "password": "correct-password",
                              "image": "https://example.com/ada.png",
                              "callbackURL": "myapp://verified"
                            }
                            """.trimIndent(),
                        ),
                        Json.parseToJsonElement(request.body.toByteArray().decodeToString()),
                    )
                    respond("""{"token": null, "user": ${ProtocolFixtures.user}}""", headers = jsonHeaders)
                }

            BetterAuthClient("https://example.com/api/auth", store, engine).use { auth ->
                val result =
                    auth.signUpEmail(
                        "Ada",
                        "ada@example.com",
                        "correct-password",
                        image = "https://example.com/ada.png",
                        callbackUrl = "myapp://verified",
                    )
                assertNull(result.token)
                assertNull(store.read())
            }
        }

    @Test
    fun `signin rejects missing or blank bearer credentials without replacing the existing session`() =
        runTest {
            for (credential in listOf(null, "", " ")) {
                val store = InMemoryTokenStore("existing.signature")
                val engine =
                    MockEngine {
                        val headers =
                            if (credential == null) {
                                jsonHeaders
                            } else {
                                headersOf(
                                    HttpHeaders.ContentType to listOf("application/json"),
                                    "set-auth-token" to listOf(credential),
                                )
                            }
                        respond(ProtocolFixtures.signIn, headers = headers)
                    }

                BetterAuthClient("https://example.com/api/auth", store, engine).use { auth ->
                    val error =
                        assertFailsWith<IllegalStateException> {
                            auth.signInEmail("ada@example.com", "correct-password")
                        }
                    assertTrue(error.message.orEmpty().contains("bearer", ignoreCase = true))
                    assertEquals("existing.signature", store.read())
                }
            }
        }

    @Test
    fun `signup with a session requires a bearer credential`() =
        runTest {
            val store = InMemoryTokenStore("existing.signature")
            val engine =
                MockEngine {
                    respond(
                        """{"token": "raw-session-token", "user": ${ProtocolFixtures.user}}""",
                        headers = jsonHeaders,
                    )
                }

            BetterAuthClient("https://example.com/api/auth", store, engine).use { auth ->
                assertFailsWith<IllegalStateException> {
                    auth.signUpEmail("Ada", "ada@example.com", "correct-password")
                }
                assertEquals("existing.signature", store.read())
            }
        }

    @Test
    fun `a null session clears the expired credential`() =
        runTest {
            val store = InMemoryTokenStore("expired.signature")
            val engine =
                MockEngine { request ->
                    assertEquals("Bearer expired.signature", request.headers[HttpHeaders.Authorization])
                    respond("null", headers = jsonHeaders)
                }

            BetterAuthClient("https://example.com/api/auth", store, engine).use { auth ->
                assertNull(auth.getSession())
                assertNull(store.read())
            }
        }

    @Test
    fun `failed signout preserves the credential and exposes the API error`() =
        runTest {
            val store = InMemoryTokenStore("current.signature")
            val engine =
                MockEngine {
                    respond(
                        """{"code": "SESSION_REVOKE_FAILED", "message": "Session could not be revoked"}""",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = jsonHeaders,
                    )
                }

            BetterAuthClient("https://example.com/api/auth", store, engine).use { auth ->
                val error = assertFailsWith<BetterAuthException> { auth.signOut() }
                assertEquals(503, error.status)
                assertEquals("SESSION_REVOKE_FAILED", error.code)
                assertEquals("Session could not be revoked", error.message)
                assertEquals("current.signature", store.read())
            }
        }

    @Test
    fun `network errors propagate with their type and message`() =
        runTest {
            val failure = IOException("Connection closed")
            val engine = MockEngine { throw failure }

            BetterAuthClient("https://example.com/api/auth", engine = engine).use { auth ->
                val error = assertFailsWith<IOException> { auth.getSession() }
                assertEquals(failure.message, error.message)
            }
        }

    @Test
    fun `coroutine cancellation propagates`() =
        runTest {
            val engine = MockEngine { throw CancellationException("Request cancelled") }

            BetterAuthClient("https://example.com/api/auth", engine = engine).use { auth ->
                assertFailsWith<CancellationException> { auth.getSession() }
            }
        }

    @Test
    fun `authentication redirects are not followed`() =
        runTest {
            var requests = 0
            val engine =
                MockEngine {
                    requests += 1
                    respond(
                        "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(HttpHeaders.Location, "https://unrelated.example/sign-in"),
                    )
                }

            BetterAuthClient("https://example.com/api/auth", engine = engine).use { auth ->
                val error =
                    assertFailsWith<BetterAuthException> {
                        auth.signInEmail("ada@example.com", "correct-password")
                    }
                assertEquals(302, error.status)
            }
            assertEquals(1, requests)
        }

    private companion object {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
