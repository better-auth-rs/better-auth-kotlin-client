package io.github.betterauth.client.coverage

import io.github.betterauth.client.SESSION_JSON
import io.github.betterauth.client.TestServer
import io.github.betterauth.client.USER_JSON
import io.github.betterauth.client.bodyJson
import io.github.betterauth.client.header
import io.github.betterauth.client.json
import io.github.betterauth.client.path
import io.github.betterauth.client.plugins.admin.admin
import io.github.betterauth.client.result.BetterAuthError
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreEndpointContractTest {
    @Test
    fun `session update preserves custom fields and decodes returned session`() =
        runTest {
            val fields =
                buildJsonObject {
                    put("activeOrganizationId", "org2")
                    put("device", buildJsonObject { put("trusted", true) })
                }
            val server =
                TestServer {
                    json("""{"session":{"id":"s2","token":"rotated","activeOrganizationId":"org2","device":{"trusted":true}}}""")
                }
            server.client().use { auth ->
                val session = assertNotNull(auth.updateSession(fields).getOrThrow())
                assertEquals("s2", session.id)
                assertEquals("org2", session.activeOrganizationId)
                assertEquals(fields["device"], session.additionalFields["device"])
                server.assertPost("/update-session", fields.toString())
            }
        }

    @Test
    fun `session update allows an absent response session`() =
        runTest {
            val server = TestServer { json("""{"session":null}""") }
            server.client().use { auth ->
                assertNull(auth.updateSession(buildJsonObject { put("device", "phone") }).getOrThrow())
            }
        }

    @Test
    fun `session revocations forward the target and preserve false acknowledgements`() =
        runTest {
            val server =
                TestServer { request ->
                    when (request.path) {
                        "/api/auth/revoke-session" -> json("""{"status":true}""")
                        "/api/auth/revoke-sessions" -> json("""{"success":false,"message":"Session policy rejected revocation"}""")
                        else -> error("Unexpected request: ${request.path}")
                    }
                }
            server.client().use { auth ->
                auth.restoreToken("current-signed-token")
                assertTrue(auth.revokeSession("other-session").getOrThrow().success)
                server.assertPost("/revoke-session", """{"token":"other-session"}""")
                assertEquals("Bearer current-signed-token", server.last.header("Authorization"))
                val result = auth.revokeSessions().getOrThrow()
                assertFalse(result.success)
                assertEquals("Session policy rejected revocation", result.message)
                assertEquals("POST", server.last.method.value)
                assertEquals("/api/auth/revoke-sessions", server.last.path)
                assertEquals("current-signed-token", auth.currentToken())
            }
        }

    @Test
    fun `email verification and email change forward callbackURL`() =
        runTest {
            val server = TestServer { json("""{"status":true}""") }
            server.client().use { auth ->
                assertTrue(auth.sendVerificationEmail("ada@example.com", "https://app.example.com/verified").getOrThrow().success)
                server.assertPost(
                    "/send-verification-email",
                    """{"email":"ada@example.com","callbackURL":"https://app.example.com/verified"}""",
                )
                assertTrue(auth.changeEmail("new@example.com", "https://app.example.com/changed").getOrThrow().success)
                server.assertPost(
                    "/change-email",
                    """{"newEmail":"new@example.com","callbackURL":"https://app.example.com/changed"}""",
                )
            }
        }

    @Test
    fun `password reset supports explicit token and cookie token selection`() =
        runTest {
            val server = TestServer { json("""{"status":true}""") }
            server.client().use { auth ->
                assertTrue(auth.resetPassword("new-password", "reset-token").getOrThrow().success)
                server.assertPost("/reset-password", """{"newPassword":"new-password","token":"reset-token"}""")
                assertTrue(auth.resetPassword("newer-password").getOrThrow().success)
                server.assertPost("/reset-password", """{"newPassword":"newer-password"}""")
            }
        }

    @Test
    fun `password verification propagates API errors without replacing the session`() =
        runTest {
            val server =
                TestServer {
                    json("""{"code":"INVALID_PASSWORD","message":"Invalid password"}""", HttpStatusCode.BadRequest)
                }
            server.client().use { auth ->
                auth.restoreToken("existing")
                val failure = assertIs<BetterAuthError.Api>(auth.verifyPassword("wrong-password").errorOrNull())
                assertEquals(400, failure.status)
                assertEquals("INVALID_PASSWORD", failure.code)
                assertEquals("Invalid password", failure.message)
                server.assertPost("/verify-password", """{"password":"wrong-password"}""")
                assertEquals("existing", auth.currentToken())
            }
        }

    @Test
    fun `password reset callback returns the server redirect without following it`() =
        runTest {
            val callback = "https://app.example.com/reset?source=email"
            val location = "$callback&token=reset-token"
            val server = TestServer { respond("", HttpStatusCode.Found, headersOf("Location", location)) }
            server.client().use { auth ->
                val redirect = auth.resetPasswordCallback("reset-token", callback).getOrThrow()
                assertEquals(302, redirect.status)
                assertEquals(location, redirect.location)
                assertEquals("GET", server.last.method.value)
                assertEquals("/api/auth/reset-password/reset-token", server.last.path)
                assertEquals(callback, server.last.url.parameters["callbackURL"])
                assertEquals(1, server.requests.size)
            }
        }

    @Test
    fun `provider token refresh preserves account selection and token expiry`() =
        runTest {
            val server =
                TestServer {
                    json(
                        """{"accessToken":"access","refreshToken":"refresh","idToken":"identity","tokenType":"Bearer",
                "accessTokenExpiresAt":"2026-10-22T12:00:00Z","refreshTokenExpiresAt":"2026-11-22T12:00:00Z"}""",
                    )
                }
            server.client().use { auth ->
                val result = auth.refreshToken(accountId = "a1", userId = "u1").getOrThrow()
                server.assertPost("/refresh-token", """{"accountId":"a1","userId":"u1"}""")
                assertEquals("access", result.accessToken)
                assertEquals("refresh", result.refreshToken)
                assertEquals("identity", result.idToken)
                assertEquals("Bearer", result.tokenType)
                assertEquals("2026-10-22T12:00:00Z", result.accessTokenExpiresAt.toString())
                assertEquals("2026-11-22T12:00:00Z", result.refreshTokenExpiresAt.toString())
                auth.refreshToken(useAccountCookie = true).getOrThrow()
                server.assertPost("/refresh-token", """{"useAccountCookie":true}""")
            }
        }

    @Test
    fun `account profile preserves provider data and unlink forwards account ID`() =
        runTest {
            val server =
                TestServer { request ->
                    when (request.path) {
                        "/api/auth/account-info" -> {
                            json(
                                """{"user":{"sub":"remote-user"},"account":{"id":"a1"},"data":{"locale":"fr","groups":["developers"]}}""",
                            )
                        }

                        "/api/auth/unlink-account" -> {
                            json("""{"status":true}""")
                        }

                        else -> {
                            error("Unexpected request: ${request.path}")
                        }
                    }
                }
            server.client().use { auth ->
                val result = auth.accountInfo("a1").getOrThrow()
                assertEquals("GET", server.last.method.value)
                assertEquals("/api/auth/account-info", server.last.path)
                assertEquals("a1", server.last.url.parameters["accountId"])
                assertEquals(JsonPrimitive("remote-user"), result.user["sub"])
                assertEquals(JsonPrimitive("a1"), result.account["id"])
                assertEquals(Json.parseToJsonElement("""{"locale":"fr","groups":["developers"]}"""), result.data)
                assertTrue(auth.unlinkAccount("a1").getOrThrow().success)
                server.assertPost("/unlink-account", """{"accountId":"a1"}""")
            }
        }

    @Test
    fun `stop impersonation decodes the admin session and restores its signed token`() =
        runTest {
            val server =
                TestServer {
                    json("""{"session":$SESSION_JSON,"user":$USER_JSON}""", headers = arrayOf("set-auth-token" to listOf("admin.signed")))
                }
            server.client().use { auth ->
                auth.restoreToken("impersonated.signed")
                val result = auth.admin.stopImpersonating().getOrThrow()
                assertEquals("POST", server.last.method.value)
                assertEquals("/api/auth/admin/stop-impersonating", server.last.path)
                assertEquals("Bearer impersonated.signed", server.last.header("Authorization"))
                assertEquals("tok-abc", result.token)
                assertEquals("s1", result.session?.id)
                assertEquals("u1", result.user?.id)
                assertEquals("admin.signed", auth.currentToken())
            }
        }

    private fun TestServer.assertPost(
        path: String,
        body: String,
    ) {
        assertEquals("POST", last.method.value)
        assertEquals("/api/auth$path", last.path)
        assertEquals(Json.parseToJsonElement(body).jsonObject, last.bodyJson())
    }
}
