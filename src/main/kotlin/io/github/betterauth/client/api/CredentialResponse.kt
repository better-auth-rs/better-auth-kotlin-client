package io.github.betterauth.client.api

import io.github.betterauth.client.http.CookieJar
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.result.BetterAuthError
import io.github.betterauth.client.result.BetterAuthResult
import io.github.betterauth.client.session.StoredSession
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement

/** Authenticated email responses must persist the signed credential from the current response. */
internal suspend fun <T> Transport.postCredentialResponse(
    path: String,
    body: JsonElement,
    requiresCredential: (T) -> Boolean,
    map: (JsonElement) -> T,
): BetterAuthResult<T> =
    executeAndProcess(HttpMethod.Post, path, body = body) { raw ->
        when (raw) {
            is BetterAuthResult.Failure -> {
                raw
            }

            is BetterAuthResult.Success -> {
                val result = mapBody(raw.value, map)
                if (result is BetterAuthResult.Success && requiresCredential(result.value)) {
                    val credential =
                        CookieJar
                            .apply(
                                StoredSession(),
                                raw.value.headers,
                                cookiePrefix,
                                System.currentTimeMillis(),
                            ).token
                    val current = store.get()
                    if (credential.isNullOrBlank() || current.token != credential) {
                        store.setSessionCache(null)
                        store.set(CookieJar.clearSession(current, cookiePrefix))
                        BetterAuthResult.Failure(
                            BetterAuthError.Decoding(
                                IllegalStateException(
                                    "Authenticated response must include a signed session credential. Enable the server bearer plugin or cookie authentication.",
                                ),
                                raw.value.bodyText,
                            ),
                        )
                    } else {
                        result
                    }
                } else {
                    result
                }
            }
        }
    }
