// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.api

import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.AuthResponse
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.result.BetterAuthResult
import kotlinx.serialization.json.JsonObject

/** `client.signUp` */
public class SignUpApi internal constructor(
    private val t: Transport,
) {
    /**
     * Creates a user with email and password.
     *
     * [additionalFields] are merged into the request body. Use them for fields your server requires or accepts on
     * sign-up (`user.additionalFields`, consent flags added by plugins, ...).
     *
     * [AuthResponse.token] is `null` when the server requires email verification before the first sign-in.
     */
    public suspend fun email(
        name: String,
        email: String,
        password: String,
        image: String? = null,
        callbackUrl: String? = null,
        rememberMe: Boolean? = null,
        additionalFields: JsonObject? = null,
    ): BetterAuthResult<AuthResponse> =
        t.postCredentialResponse(
            "/sign-up/email",
            jsonBody {
                putAll(additionalFields)
                put("name", name)
                put("email", email)
                put("password", password)
                put("image", image)
                put("callbackURL", callbackUrl)
                put("rememberMe", rememberMe)
            },
            requiresCredential = { it.token != null },
        ) { it.toAuthResponse() }
}
