// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.magiclink

import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.api.SignInApi
import io.github.betterauth.client.api.get
import io.github.betterauth.client.api.json
import io.github.betterauth.client.api.jsonOrRedirect
import io.github.betterauth.client.api.postForResult
import io.github.betterauth.client.api.toAuthResponse
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.AuthOrRedirect
import io.github.betterauth.client.model.OperationResult
import io.github.betterauth.client.model.SignInOutcome
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `client.magicLink`. Requesting a link is `client.signIn.magicLink(...)`. */
public class MagicLinkApi internal constructor(
    private val t: Transport,
) {
    /**
     * Verifies the token from a magic link. Without a callback URL the result carries the session; with one the
     * server answers with a redirect, returned (not followed) in [AuthOrRedirect.redirect].
     */
    public suspend fun verify(
        token: String,
        callbackUrl: String? = null,
        errorCallbackUrl: String? = null,
        newUserCallbackUrl: String? = null,
    ): BetterAuthResult<AuthOrRedirect> =
        t.jsonOrRedirect(
            HttpMethod.Get,
            "/magic-link/verify",
            query =
                mapOf(
                    "token" to token,
                    "callbackURL" to callbackUrl,
                    "errorCallbackURL" to errorCallbackUrl,
                    "newUserCallbackURL" to newUserCallbackUrl,
                ),
            onRedirect = { AuthOrRedirect(redirect = it) },
        ) { AuthOrRedirect(response = it.toAuthResponse()) }

    internal suspend fun sendLink(
        email: String,
        name: String?,
        callbackUrl: String?,
        newUserCallbackUrl: String?,
        errorCallbackUrl: String?,
        metadata: JsonObject?,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/sign-in/magic-link",
            jsonBody {
                put("email", email)
                put("name", name)
                put("callbackURL", callbackUrl)
                put("newUserCallbackURL", newUserCallbackUrl)
                put("errorCallbackURL", errorCallbackUrl)
                put("metadata", metadata)
            },
        )
}

private val MagicLinkKey = PluginKey<MagicLinkApi>("magicLink", "magicLinkClient()")

private object MagicLinkPlugin : ClientPlugin<MagicLinkApi> {
    override val key: PluginKey<MagicLinkApi> = MagicLinkKey

    override fun createApi(context: PluginContext): MagicLinkApi = MagicLinkApi(context.transport)
}

/**
 * Registers the Magic link plugin: `BetterAuthClient { plugins(magicLinkClient()) }`. Its API is then `client.magicLink`.
 */
public fun magicLinkClient(): ClientPlugin<MagicLinkApi> = MagicLinkPlugin

/** The Magic link API. Throws if [magicLinkClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.magicLink: MagicLinkApi get() = plugin(MagicLinkKey)

/** Emails a one-click sign-in link (magic-link plugin). Complete it with `client.magicLink.verify(token)`. */
public suspend fun SignInApi.magicLink(
    email: String,
    name: String? = null,
    callbackUrl: String? = null,
    newUserCallbackUrl: String? = null,
    errorCallbackUrl: String? = null,
    metadata: JsonObject? = null,
): BetterAuthResult<OperationResult> = client.magicLink.sendLink(email, name, callbackUrl, newUserCallbackUrl, errorCallbackUrl, metadata)
