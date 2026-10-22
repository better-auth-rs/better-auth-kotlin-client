// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.anonymous

import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.api.SignInApi
import io.github.betterauth.client.api.get
import io.github.betterauth.client.api.json
import io.github.betterauth.client.api.mapBody
import io.github.betterauth.client.api.post
import io.github.betterauth.client.api.toOperationResult
import io.github.betterauth.client.api.toSignInOutcome
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.OperationResult
import io.github.betterauth.client.model.SignInOutcome
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult
import io.github.betterauth.client.result.BetterAuthResult.Success
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `client.anonymous`. Creating a guest user is `client.signIn.anonymous()`. */
public class AnonymousApi internal constructor(
    private val t: Transport,
) {
    /** Deletes the signed-in anonymous user and forgets the local session. */
    public suspend fun deleteUser(): BetterAuthResult<OperationResult> =
        t.executeAndProcess(HttpMethod.Post, "/delete-anonymous-user") { raw ->
            val result =
                when (raw) {
                    is BetterAuthResult.Failure -> raw
                    is Success -> mapBody(raw.value) { it.toOperationResult() }
                }
            if (result is Success && result.value.success) t.store.clear()
            result
        }

    internal suspend fun signIn(): BetterAuthResult<SignInOutcome> = t.post("/sign-in/anonymous") { it.toSignInOutcome() }
}

private val AnonymousKey = PluginKey<AnonymousApi>("anonymous", "anonymousClient()")

private object AnonymousPlugin : ClientPlugin<AnonymousApi> {
    override val key: PluginKey<AnonymousApi> = AnonymousKey

    override fun createApi(context: PluginContext): AnonymousApi = AnonymousApi(context.transport)
}

/**
 * Registers the Anonymous (guest) users plugin: `BetterAuthClient { plugins(anonymousClient()) }`. Its API is then `client.anonymous`.
 */
public fun anonymousClient(): ClientPlugin<AnonymousApi> = AnonymousPlugin

/** The Anonymous (guest) users API. Throws if [anonymousClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.anonymous: AnonymousApi get() = plugin(AnonymousKey)

/** Creates a guest user (anonymous plugin). */
public suspend fun SignInApi.anonymous(): BetterAuthResult<SignInOutcome> = client.anonymous.signIn()
