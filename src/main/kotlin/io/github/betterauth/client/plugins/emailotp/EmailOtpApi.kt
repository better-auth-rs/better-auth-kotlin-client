// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.emailotp

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
import io.github.betterauth.client.model.EmailOtpType
import io.github.betterauth.client.model.OperationResult
import io.github.betterauth.client.model.SignInOutcome
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** `client.emailOtp`. Signing in with a code is `client.signIn.emailOtp(...)`. */
public class EmailOtpApi internal constructor(
    private val t: Transport,
) {
    public suspend fun sendVerificationOtp(
        email: String,
        type: EmailOtpType,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/email-otp/send-verification-otp",
            jsonBody {
                put("email", email)
                put("type", type.wire)
            },
        )

    /** Checks a code without consuming it. */
    public suspend fun checkVerificationOtp(
        email: String,
        type: EmailOtpType,
        otp: String,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/email-otp/check-verification-otp",
            jsonBody {
                put("email", email)
                put("type", type.wire)
                put("otp", otp)
            },
        )

    /** Verifies the address. The response carries a token when the server auto-signs-in on verification. */
    public suspend fun verifyEmail(
        email: String,
        otp: String,
    ): BetterAuthResult<AuthResponse> =
        t.post(
            "/email-otp/verify-email",
            jsonBody {
                put("email", email)
                put("otp", otp)
            },
        ) { it.toAuthResponse() }

    public suspend fun requestPasswordReset(email: String): BetterAuthResult<OperationResult> =
        t.postForResult("/email-otp/request-password-reset", jsonBody { put("email", email) })

    /** Older name for [requestPasswordReset]. */
    public suspend fun forgetPassword(email: String): BetterAuthResult<OperationResult> =
        t.postForResult("/forget-password/email-otp", jsonBody { put("email", email) })

    public suspend fun resetPassword(
        email: String,
        otp: String,
        password: String,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/email-otp/reset-password",
            jsonBody {
                put("email", email)
                put("otp", otp)
                put("password", password)
            },
        )

    /** Starts an email change; depending on server config [otp] (sent to the current address) is required. */
    public suspend fun requestEmailChange(
        newEmail: String,
        otp: String? = null,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/email-otp/request-email-change",
            jsonBody {
                put("newEmail", newEmail)
                put("otp", otp)
            },
        )

    public suspend fun changeEmail(
        newEmail: String,
        otp: String,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/email-otp/change-email",
            jsonBody {
                put("newEmail", newEmail)
                put("otp", otp)
            },
        )

    internal suspend fun signIn(
        email: String,
        otp: String,
        name: String?,
        image: String?,
    ): BetterAuthResult<SignInOutcome> =
        t.post(
            "/sign-in/email-otp",
            jsonBody {
                put("email", email)
                put("otp", otp)
                put("name", name)
                put("image", image)
            },
        ) { it.toSignInOutcome() }
}

private val EmailOtpKey = PluginKey<EmailOtpApi>("emailOtp", "emailOtpClient()")

private object EmailOtpPlugin : ClientPlugin<EmailOtpApi> {
    override val key: PluginKey<EmailOtpApi> = EmailOtpKey

    override fun createApi(context: PluginContext): EmailOtpApi = EmailOtpApi(context.transport)
}

/**
 * Registers the Email OTP plugin: `BetterAuthClient { plugins(emailOtpClient()) }`. Its API is then `client.emailOtp`.
 */
public fun emailOtpClient(): ClientPlugin<EmailOtpApi> = EmailOtpPlugin

/** The Email OTP API. Throws if [emailOtpClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.emailOtp: EmailOtpApi get() = plugin(EmailOtpKey)

/** Signs in with a code sent by `client.emailOtp.sendVerificationOtp(email, EmailOtpType.SignIn)`. */
public suspend fun SignInApi.emailOtp(
    email: String,
    otp: String,
    name: String? = null,
    image: String? = null,
): BetterAuthResult<SignInOutcome> = client.emailOtp.signIn(email, otp, name, image)
