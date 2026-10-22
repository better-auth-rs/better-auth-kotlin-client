// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.phonenumber

import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.api.SignInApi
import io.github.betterauth.client.api.get
import io.github.betterauth.client.api.json
import io.github.betterauth.client.api.post
import io.github.betterauth.client.api.postForResult
import io.github.betterauth.client.api.toAuthResponse
import io.github.betterauth.client.api.toSignInOutcome
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.AuthResponse
import io.github.betterauth.client.model.OperationResult
import io.github.betterauth.client.model.SignInOutcome
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `client.phoneNumber`. Signing in with phone and password is `client.signIn.phoneNumber(...)`. */
public class PhoneNumberApi internal constructor(
    private val t: Transport,
) {
    /** Sends a verification code by SMS. [OperationResult.message] is the server's note, usually `"code sent"`. */
    public suspend fun sendOtp(phoneNumber: String): BetterAuthResult<OperationResult> =
        t.postForResult("/phone-number/send-otp", jsonBody { put("phoneNumber", phoneNumber) })

    /**
     * Verifies the code. Depending on server config this also signs the user in (token in the result) unless
     * [disableSession] is set; [updatePhoneNumber] changes the signed-in user's number instead.
     */
    public suspend fun verify(
        phoneNumber: String,
        code: String,
        disableSession: Boolean? = null,
        updatePhoneNumber: Boolean? = null,
    ): BetterAuthResult<AuthResponse> =
        t.post(
            "/phone-number/verify",
            jsonBody {
                put("phoneNumber", phoneNumber)
                put("code", code)
                put("disableSession", disableSession)
                put("updatePhoneNumber", updatePhoneNumber)
            },
        ) { it.toAuthResponse() }

    public suspend fun requestPasswordReset(phoneNumber: String): BetterAuthResult<OperationResult> =
        t.postForResult("/phone-number/request-password-reset", jsonBody { put("phoneNumber", phoneNumber) })

    public suspend fun resetPassword(
        otp: String,
        phoneNumber: String,
        newPassword: String,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/phone-number/reset-password",
            jsonBody {
                put("otp", otp)
                put("phoneNumber", phoneNumber)
                put("newPassword", newPassword)
            },
        )

    internal suspend fun signIn(
        phoneNumber: String,
        password: String,
        rememberMe: Boolean?,
    ): BetterAuthResult<SignInOutcome> =
        t.post(
            "/sign-in/phone-number",
            jsonBody {
                put("phoneNumber", phoneNumber)
                put("password", password)
                put("rememberMe", rememberMe)
            },
        ) { it.toSignInOutcome() }
}

private val PhoneNumberKey = PluginKey<PhoneNumberApi>("phoneNumber", "phoneNumberClient()")

private object PhoneNumberPlugin : ClientPlugin<PhoneNumberApi> {
    override val key: PluginKey<PhoneNumberApi> = PhoneNumberKey

    override fun createApi(context: PluginContext): PhoneNumberApi = PhoneNumberApi(context.transport)
}

/**
 * Registers the Phone number plugin: `BetterAuthClient { plugins(phoneNumberClient()) }`. Its API is then `client.phoneNumber`.
 */
public fun phoneNumberClient(): ClientPlugin<PhoneNumberApi> = PhoneNumberPlugin

/** The Phone number API. Throws if [phoneNumberClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.phoneNumber: PhoneNumberApi get() = plugin(PhoneNumberKey)

/** Signs in with phone number and password (phone-number plugin). */
public suspend fun SignInApi.phoneNumber(
    phoneNumber: String,
    password: String,
    rememberMe: Boolean? = null,
): BetterAuthResult<SignInOutcome> = client.phoneNumber.signIn(phoneNumber, password, rememberMe)
