// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client

import io.github.betterauth.client.config.BetterAuthConfig
import io.github.betterauth.client.coverage.EndpointCoverage
import io.github.betterauth.client.http.AuthMode
import io.github.betterauth.client.plugins.admin.adminClient
import io.github.betterauth.client.plugins.anonymous.anonymousClient
import io.github.betterauth.client.plugins.emailotp.emailOtpClient
import io.github.betterauth.client.plugins.jwt.jwtClient
import io.github.betterauth.client.plugins.magiclink.magicLinkClient
import io.github.betterauth.client.plugins.multisession.multiSessionClient
import io.github.betterauth.client.plugins.onetimetoken.oneTimeTokenClient
import io.github.betterauth.client.plugins.organization.organizationClient
import io.github.betterauth.client.plugins.passkey.passkeyClient
import io.github.betterauth.client.plugins.phonenumber.phoneNumberClient
import io.github.betterauth.client.plugins.twofactor.twoFactorClient
import io.github.betterauth.client.result.BetterAuthError
import io.github.betterauth.client.result.BetterAuthResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertIs

const val BASE = "https://auth.example.com/api/auth"

fun allPlugins(): Array<io.github.betterauth.client.plugin.ClientPlugin<*>> =
    arrayOf(
        twoFactorClient(),
        emailOtpClient(),
        phoneNumberClient(),
        magicLinkClient(),
        anonymousClient(),
        oneTimeTokenClient(),
        multiSessionClient(),
        jwtClient(),
        passkeyClient(),
        adminClient(),
        organizationClient(),
    )

class TestServer(
    private val handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    private val coverageTest = EndpointCoverage.currentTest()
    val requests = mutableListOf<HttpRequestData>()
    val last: HttpRequestData get() = requests.last()

    val engine =
        MockEngine { request ->
            EndpointCoverage.record(request, coverageTest)
            requests += request
            handler(request)
        }

    /** A client with every plugin registered, unless [withPlugins] is false. */
    fun client(
        mode: AuthMode = AuthMode.Bearer,
        withPlugins: Boolean = true,
        configure: BetterAuthConfig.() -> Unit = {},
    ): BetterAuthClient =
        BetterAuthClient {
            baseUrl = BASE
            this.mode = mode
            engine = this@TestServer.engine
            if (withPlugins) plugins(*allPlugins())
            configure()
        }
}

fun MockRequestHandleScope.json(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    vararg headers: Pair<String, List<String>>,
): HttpResponseData =
    respond(
        content = body,
        status = status,
        headers =
            headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                *headers,
            ),
    )

fun HttpRequestData.bodyJson(): JsonObject = Json.parseToJsonElement((body as TextContent).text).jsonObject

fun HttpRequestData.bodyElement(): JsonElement = Json.parseToJsonElement((body as TextContent).text)

fun HttpRequestData.header(name: String): String? = headers[name]

val HttpRequestData.path: String get() = url.encodedPath

const val USER_JSON = """{"id":"u1","name":"Ada","email":"ada@example.com","emailVerified":true,
 "createdAt":"2026-01-02T03:04:05.000Z","updatedAt":"2026-01-02T03:04:05.000Z",
 "isProvider":true,"countryId":"ke","dateOfBirth":"1990-01-01"}"""

const val SESSION_JSON = """{"id":"s1","token":"tok-abc","userId":"u1","expiresAt":"2026-02-01T00:00:00.000Z",

 "createdAt":"2026-01-02T03:04:05.000Z","updatedAt":"2026-01-02T03:04:05.000Z",
"ipAddress":"1.2.3.4","userAgent":"ua",
 "activeOrganizationId":"org1"}"""

inline fun <reified T> BetterAuthResult<*>.success(): T {
    if (this is BetterAuthResult.Failure) error("Expected success but was $error")
    val value = (this as BetterAuthResult.Success<*>).value
    assertIs<T>(value)
    return value
}

fun BetterAuthResult<*>.failure(): BetterAuthError =
    (this as? BetterAuthResult.Failure)?.error
        ?: error("Expected failure but was $this")
