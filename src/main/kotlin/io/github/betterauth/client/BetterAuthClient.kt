package io.github.betterauth.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.URI

/**
 * A single-user client for a server with the Better Auth bearer plugin enabled.
 * Authenticated email responses must include the signed set-auth-token header.
 * [baseUrl] includes the auth handler path. This client owns and closes [engine].
 */
public class BetterAuthClient(
    baseUrl: String,
    private val tokenStore: TokenStore = InMemoryTokenStore(),
    engine: HttpClientEngine? = null,
) : Closeable {
    private val baseUrl =
        URI(baseUrl)
            .also {
                require(it.scheme?.lowercase() in setOf("http", "https") && it.host != null) {
                    "baseUrl must be an absolute HTTP or HTTPS URL"
                }
                require(it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null) {
                    "baseUrl must not contain user information, a query, or a fragment"
                }
            }.toASCIIString()
            .trimEnd('/')
    private val engine = engine ?: CIO.create()
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val http =
        HttpClient(this.engine) {
            followRedirects = false
            expectSuccess = false
        }

    public suspend fun signUpEmail(
        name: String,
        email: String,
        password: String,
        image: String? = null,
        callbackUrl: String? = null,
    ): SignUpResponse =
        mutex.withLock {
            request(
                "sign-up/email",
                HttpMethod.Post,
                json.encodeToString(SignUpRequest(name, email, password, image, callbackUrl)),
            )
        }

    public suspend fun signInEmail(
        email: String,
        password: String,
        callbackUrl: String? = null,
    ): SignInResponse =
        mutex.withLock {
            request(
                "sign-in/email",
                HttpMethod.Post,
                json.encodeToString(SignInRequest(email, password, callbackUrl)),
            )
        }

    public suspend fun getSession(): SessionData? =
        mutex.withLock {
            request<SessionData?>("get-session", HttpMethod.Get).also {
                if (it == null) tokenStore.write(null)
            }
        }

    public suspend fun signOut(): Unit =
        mutex.withLock {
            request<SignOutResponse>("sign-out", HttpMethod.Post, "{}")
            tokenStore.write(null)
        }

    private suspend inline fun <reified T> request(
        path: String,
        method: HttpMethod,
        body: String? = null,
    ): T {
        val token = tokenStore.read()
        val response =
            http.request("$baseUrl/$path") {
                this.method = method
                accept(ContentType.Application.Json)
                if (token != null) bearerAuth(token)
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
        if (!response.status.isSuccess()) throw response.toException()
        val result = json.decodeFromString<T>(response.bodyAsText())
        val credential = response.headers["set-auth-token"]
        if (result is SignInResponse || (result is SignUpResponse && result.token != null)) {
            check(!credential.isNullOrBlank()) {
                "Authenticated responses must include set-auth-token. Enable the Better Auth bearer plugin."
            }
        }
        if (credential != null) tokenStore.write(credential)
        return result
    }

    private suspend fun HttpResponse.toException(): BetterAuthException {
        val type = contentType()
        val error =
            if (type?.match(ContentType.Application.Json) == true || type?.contentSubtype?.endsWith("+json") == true) {
                json.decodeFromString<ApiError>(bodyAsText())
            } else {
                ApiError(status.description)
            }
        return BetterAuthException(status.value, error.code, error.message)
    }

    override fun close() {
        try {
            http.close()
        } finally {
            engine.close()
        }
    }
}

@Serializable
private data class SignUpRequest(
    val name: String,
    val email: String,
    val password: String,
    val image: String? = null,
    @SerialName("callbackURL") val callbackUrl: String? = null,
)

@Serializable
private data class SignInRequest(
    val email: String,
    val password: String,
    @SerialName("callbackURL") val callbackUrl: String? = null,
)

@Serializable
private data class SignOutResponse(
    val success: Boolean,
)

@Serializable
private data class ApiError(
    val message: String,
    val code: String? = null,
)
