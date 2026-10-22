// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.onetimetoken

import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.api.asObject
import io.github.betterauth.client.api.get
import io.github.betterauth.client.api.post
import io.github.betterauth.client.api.toAuthResponse
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.AuthResponse
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.model.str
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult

/** `client.oneTimeToken`: hand a session to another client (for example a web view or a second app). */
public class OneTimeTokenApi internal constructor(
    private val t: Transport,
) {
    /** Creates a single-use token for the signed-in session. */
    public suspend fun generate(): BetterAuthResult<String> =
        t.get("/one-time-token/generate") { it.asObject().str("token") ?: error("`token` missing in response") }

    /** Redeems a token on this client, signing it in. */
    public suspend fun verify(token: String): BetterAuthResult<AuthResponse> =
        t.post("/one-time-token/verify", jsonBody { put("token", token) }) { it.toAuthResponse() }
}

private val OneTimeTokenKey = PluginKey<OneTimeTokenApi>("oneTimeToken", "oneTimeTokenClient()")

private object OneTimeTokenPlugin : ClientPlugin<OneTimeTokenApi> {
    override val key: PluginKey<OneTimeTokenApi> = OneTimeTokenKey

    override fun createApi(context: PluginContext): OneTimeTokenApi = OneTimeTokenApi(context.transport)
}

/**
 * Registers the One-time tokens plugin: `BetterAuthClient { plugins(oneTimeTokenClient()) }`. Its API is then `client.oneTimeToken`.
 */
public fun oneTimeTokenClient(): ClientPlugin<OneTimeTokenApi> = OneTimeTokenPlugin

/** The One-time tokens API. Throws if [oneTimeTokenClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.oneTimeToken: OneTimeTokenApi get() = plugin(OneTimeTokenKey)
