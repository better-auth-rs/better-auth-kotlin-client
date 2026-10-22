// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.jwt

import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.api.asObject
import io.github.betterauth.client.api.get
import io.github.betterauth.client.api.json
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.JsonWebKey
import io.github.betterauth.client.model.arr
import io.github.betterauth.client.model.str
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult
import kotlinx.serialization.json.JsonObject

/** `client.jwt` (JWT plugin). The latest `set-auth-jwt` header is also available as `storedSession().jwt`. */
public class JwtApi internal constructor(
    private val t: Transport,
) {
    /** A JWT for the signed-in session, for calling services that verify against [jwks]. */
    public suspend fun token(): BetterAuthResult<String> =
        t.get("/token") { it.asObject().str("token") ?: error("`token` missing in response") }

    /** The public keys to verify tokens with. Backends should cache this. */
    public suspend fun jwks(): BetterAuthResult<List<JsonWebKey>> =
        t.get("/jwks") { element ->
            element.asObject().arr("keys").orEmpty().map { key ->
                val o = key as JsonObject
                JsonWebKey(
                    kid = o.str("kid") ?: "",
                    kty = o.str("kty") ?: "",
                    alg = o.str("alg"),
                    use = o.str("use"),
                    raw = o,
                )
            }
        }
}

private val JwtKey = PluginKey<JwtApi>("jwt", "jwtClient()")

private object JwtPlugin : ClientPlugin<JwtApi> {
    override val key: PluginKey<JwtApi> = JwtKey

    override fun createApi(context: PluginContext): JwtApi = JwtApi(context.transport)
}

/**
 * Registers the JWT plugin: `BetterAuthClient { plugins(jwtClient()) }`. Its API is then `client.jwt`.
 */
public fun jwtClient(): ClientPlugin<JwtApi> = JwtPlugin

/** The JWT API. Throws if [jwtClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.jwt: JwtApi get() = plugin(JwtKey)
