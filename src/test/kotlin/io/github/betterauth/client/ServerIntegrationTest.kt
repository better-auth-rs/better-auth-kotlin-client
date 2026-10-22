package io.github.betterauth.client

import io.github.betterauth.client.http.AuthMode
import io.github.betterauth.client.model.EmailOtpType
import io.github.betterauth.client.model.SignInOutcome
import io.github.betterauth.client.model.TwoFactorMethod
import io.github.betterauth.client.plugins.admin.admin
import io.github.betterauth.client.plugins.anonymous.anonymous
import io.github.betterauth.client.plugins.emailotp.emailOtp
import io.github.betterauth.client.plugins.jwt.jwt
import io.github.betterauth.client.plugins.magiclink.magicLink
import io.github.betterauth.client.plugins.multisession.multiSession
import io.github.betterauth.client.plugins.onetimetoken.oneTimeToken
import io.github.betterauth.client.plugins.organization.organization
import io.github.betterauth.client.plugins.passkey.passkey
import io.github.betterauth.client.plugins.phonenumber.phoneNumber
import io.github.betterauth.client.plugins.twofactor.twoFactor
import io.github.betterauth.client.result.BetterAuthError
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Tag
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("server")
class ServerIntegrationTest {
    private val baseUrl = requireNotNull(System.getenv("BETTER_AUTH_TEST_URL"))
    private val testKey = requireNotNull(System.getenv("BETTER_AUTH_TEST_KEY"))
    private val password = "integration-password-123"

    private fun client(mode: AuthMode = AuthMode.Bearer): BetterAuthClient =
        BetterAuthClient {
            this.baseUrl = this@ServerIntegrationTest.baseUrl
            this.mode = mode
            plugins(*allPlugins())
        }

    private fun email(): String = "${UUID.randomUUID()}@example.com"

    private fun message(key: String): JsonObject {
        val request =
            HttpRequest
                .newBuilder(
                    URI("${baseUrl.removeSuffix("/api/auth")}/test/mailbox?key=${URLEncoder.encode(key, Charsets.UTF_8)}"),
                ).header("X-Test-Key", testKey)
                .build()
        val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        return Json.parseToJsonElement(response.body()).jsonObject
    }

    private fun value(
        key: String,
        field: String,
    ): String = message(key).getValue(field).jsonPrimitive.content

    @Test
    fun `email verification password reset profile and session lifecycle against Better Auth`() =
        runBlocking<Unit> {
            val email = email()
            client().use { auth ->
                val user =
                    assertNotNull(
                        auth.signUp
                            .email("Ada", email, password)
                            .getOrThrow()
                            .user,
                    )
                assertNotNull(auth.currentToken())
                assertEquals(
                    user.id,
                    auth
                        .getSession()
                        .getOrThrow()
                        ?.user
                        ?.id,
                )
                auth.sendVerificationEmail(email).getOrThrow()
                assertTrue(auth.verifyEmail(value("verify:$email", "token")).getOrThrow().success)
                assertTrue(assertNotNull(auth.getSession().getOrThrow()).user.emailVerified)
                auth.updateUser(name = "Grace").getOrThrow()
                assertEquals(
                    "Grace",
                    auth
                        .getSession(disableCookieCache = true)
                        .getOrThrow()
                        ?.user
                        ?.name,
                )
                assertEquals(
                    "credential",
                    auth
                        .listAccounts()
                        .getOrThrow()
                        .single()
                        .providerId,
                )
                assertTrue(auth.verifyPassword(password).getOrThrow().success)
                assertFalse(auth.verifyPassword("incorrect-password").isSuccess)
                client().use { other ->
                    other.signIn.email(email, password, rememberMe = false).getOrThrow()
                    assertEquals(2, auth.listSessions().getOrThrow().size)
                    auth.revokeOtherSessions().getOrThrow()
                    assertNull(other.getSession().getOrThrow())
                }
                auth.signOut().getOrThrow()
                assertNull(auth.currentToken())
                auth.requestPasswordReset(email).getOrThrow()
                auth.resetPassword("replacement-password-456", value("reset:$email", "token")).getOrThrow()
                auth.signIn.email(email, "replacement-password-456").getOrThrow()
                assertEquals(
                    user.id,
                    auth
                        .getSession()
                        .getOrThrow()
                        ?.user
                        ?.id,
                )
                auth.deleteUser(password = "replacement-password-456").getOrThrow()
                assertNull(auth.getSession().getOrThrow())
            }
        }

    @Test
    fun `cookie mode email OTP magic links anonymous and one time sessions`() =
        runBlocking<Unit> {
            val email = email()
            client(AuthMode.Cookie).use { auth ->
                auth.emailOtp.sendVerificationOtp(email, EmailOtpType.SignIn).getOrThrow()
                assertIs<SignInOutcome.Authenticated>(auth.signIn.emailOtp(email, value("sign-in:$email", "otp")).getOrThrow())
                val userId =
                    auth
                        .getSession()
                        .getOrThrow()
                        ?.user
                        ?.id
                assertNotNull(userId)
                assertNotNull(auth.cookieHeader())
                assertNotNull(auth.jwt.token().getOrThrow())
                assertTrue(
                    auth.jwt
                        .jwks()
                        .getOrThrow()
                        .isNotEmpty(),
                )
                val token = auth.oneTimeToken.generate().getOrThrow()
                client().use { recipient ->
                    assertEquals(
                        userId,
                        recipient.oneTimeToken
                            .verify(token)
                            .getOrThrow()
                            .user
                            ?.id,
                    )
                    assertFalse(recipient.oneTimeToken.verify(token).isSuccess)
                }
                auth.signOut().getOrThrow()
                auth.signIn.magicLink(email).getOrThrow()
                auth.magicLink.verify(value("magic:$email", "token")).getOrThrow()
                assertEquals(
                    userId,
                    auth
                        .getSession()
                        .getOrThrow()
                        ?.user
                        ?.id,
                )
            }
            client().use { auth ->
                val anonymous = auth.signIn.anonymous().getOrThrow()
                assertTrue(assertIs<SignInOutcome.Authenticated>(anonymous).user?.isAnonymous == true)
                auth.anonymous.deleteUser().getOrThrow()
                assertNull(auth.currentToken())
            }
        }

    @Test
    fun `two factor challenge and phone verification retain protocol cookies`() =
        runBlocking<Unit> {
            val email = email()
            client().use { auth ->
                auth.signUp.email("Two Factor", email, password).getOrThrow()
                auth.sendVerificationEmail(email).getOrThrow()
                auth.verifyEmail(value("verify:$email", "token")).getOrThrow()
                auth.twoFactor.enable(password, method = TwoFactorMethod.Otp).getOrThrow()
                auth.signOut().getOrThrow()
                assertIs<SignInOutcome.TwoFactorRequired>(auth.signIn.email(email, password).getOrThrow())
                auth.twoFactor.sendOtp().getOrThrow()
                auth.twoFactor.verifyOtp(value("2fa:$email", "otp")).getOrThrow()
                assertEquals(
                    email,
                    auth
                        .getSession()
                        .getOrThrow()
                        ?.user
                        ?.email,
                )
                auth.twoFactor.disable(password).getOrThrow()
                val phone = "+1555${System.currentTimeMillis().toString().takeLast(7)}"
                auth.phoneNumber.sendOtp(phone).getOrThrow()
                auth.phoneNumber.verify(phone, value("phone:$phone", "code"), updatePhoneNumber = true).getOrThrow()
                assertTrue(
                    auth
                        .getSession()
                        .getOrThrow()
                        ?.user
                        ?.phoneNumberVerified == true,
                )
                assertTrue(
                    auth.passkey
                        .listUserPasskeys()
                        .getOrThrow()
                        .isEmpty(),
                )
                val options =
                    auth.passkey
                        .generateRegisterOptions()
                        .getOrThrow()
                        .json.jsonObject
                assertTrue("challenge" in options)
                assertTrue("rp" in options)
            }
        }

    @Test
    fun `admin organization teams invitations and dynamic roles against Better Auth`() =
        runBlocking<Unit> {
            client().use { auth ->
                auth.signIn.email("admin@example.com", "integration-admin-password").getOrThrow()
                val managed = auth.admin.createUser(email(), "Managed", password).getOrThrow()
                assertEquals(
                    managed.id,
                    auth.admin
                        .getUser(managed.id)
                        .getOrThrow()
                        .id,
                )
                assertTrue(
                    auth.admin
                        .banUser(managed.id, "integration")
                        .getOrThrow()
                        .banned == true,
                )
                auth.admin.unbanUser(managed.id).getOrThrow()
                assertTrue(
                    auth.admin
                        .listUsers(limit = 10)
                        .getOrThrow()
                        .total > 0,
                )
                val org = auth.organization.create("SDK", "sdk-${UUID.randomUUID()}").getOrThrow()
                assertEquals(org.id, assertNotNull(auth.organization.getFullOrganization(org.id).getOrThrow()).organization.id)
                val team = auth.organization.createTeam("Engineering", org.id).getOrThrow()
                assertTrue(
                    auth.organization
                        .listTeams(org.id)
                        .getOrThrow()
                        .any { it.id == team.id },
                )
                val invite = auth.organization.inviteMember(managed.email, "member", organizationId = org.id).getOrThrow()
                client().use { invitee ->
                    invitee.signIn.email(managed.email, password).getOrThrow()
                    assertEquals(
                        invite.id,
                        invitee.organization
                            .getInvitation(invite.id)
                            .getOrThrow()
                            .id,
                    )
                }
                auth.organization.cancelInvitation(invite.id).getOrThrow()
                val permissions = mapOf("organization" to listOf("update"))
                val role = auth.organization.createRole("editor", permissions, org.id).getOrThrow()
                assertEquals("editor", assertNotNull(role).role)
                assertTrue(
                    auth.organization
                        .hasPermission(permissions, org.id)
                        .getOrThrow()
                        .success,
                )
                assertTrue(
                    auth.organization
                        .listRoles(org.id)
                        .getOrThrow()
                        .any { it.role == "editor" },
                )
                auth.organization.deleteRole(roleName = "editor", organizationId = org.id).getOrThrow()
                auth.organization.removeTeam(team.id, org.id).getOrThrow()
                auth.organization.delete(org.id).getOrThrow()
                auth.admin.removeUser(managed.id).getOrThrow()
                assertIs<BetterAuthError.Api>(auth.admin.getUser(managed.id).failure())
            }
        }
}
