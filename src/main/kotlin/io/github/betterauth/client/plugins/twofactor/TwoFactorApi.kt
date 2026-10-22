// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.twofactor

import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.api.asObject
import io.github.betterauth.client.api.get
import io.github.betterauth.client.api.post
import io.github.betterauth.client.api.postForResult
import io.github.betterauth.client.api.strings
import io.github.betterauth.client.api.toAuthResponse
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.AuthResponse
import io.github.betterauth.client.model.OperationResult
import io.github.betterauth.client.model.TwoFactorEnrollment
import io.github.betterauth.client.model.TwoFactorMethod
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.model.str
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult

/**
 * `client.twoFactor`
 *
 * The *verify* methods finish a sign-in that returned
 * [io.github.betterauth.client.model.SignInOutcome.TwoFactorRequired]; they rely on the pending-2FA cookie the SDK stored.
 */
public class TwoFactorApi internal constructor(
    private val t: Transport,
) {
    /**
     * Enrols a second factor for the signed-in user. For [TwoFactorMethod.Totp] (default) the result carries the
     * `otpauth://` URI for an authenticator app and one-time backup codes.
     */
    public suspend fun enable(
        password: String,
        method: TwoFactorMethod? = null,
        issuer: String? = null,
    ): BetterAuthResult<TwoFactorEnrollment> =
        t.post(
            "/two-factor/enable",
            jsonBody {
                put("password", password)
                put("method", method?.wire)
                put("issuer", issuer)
            },
        ) { element ->
            val o = element.asObject()
            TwoFactorEnrollment(
                method = o.str("method") ?: method?.wire ?: "totp",
                totpUri = o.str("totpURI"),
                backupCodes = o.strings("backupCodes"),
            )
        }

    public suspend fun disable(password: String): BetterAuthResult<OperationResult> =
        t.postForResult("/two-factor/disable", jsonBody { put("password", password) })

    /** The TOTP `otpauth://` URI, e.g. to show a QR code again. */
    public suspend fun getTotpUri(password: String): BetterAuthResult<String?> =
        t.post("/two-factor/get-totp-uri", jsonBody { put("password", password) }) { it.asObject().str("totpURI") }

    /** Verifies an authenticator-app code and completes the sign-in. [trustDevice] skips 2FA on this device next time. */
    public suspend fun verifyTotp(
        code: String,
        trustDevice: Boolean? = null,
    ): BetterAuthResult<AuthResponse> =
        t.post(
            "/two-factor/verify-totp",
            jsonBody {
                put("code", code)
                put("trustDevice", trustDevice)
            },
        ) { it.toAuthResponse() }

    /** Asks the server to send a code through the configured channel (email/SMS). */
    public suspend fun sendOtp(trustDevice: Boolean? = null): BetterAuthResult<OperationResult> =
        t.postForResult("/two-factor/send-otp", jsonBody { put("trustDevice", trustDevice) })

    public suspend fun verifyOtp(
        code: String,
        trustDevice: Boolean? = null,
    ): BetterAuthResult<AuthResponse> =
        t.post(
            "/two-factor/verify-otp",
            jsonBody {
                put("code", code)
                put("trustDevice", trustDevice)
            },
        ) { it.toAuthResponse() }

    public suspend fun verifyBackupCode(
        code: String,
        disableSession: Boolean? = null,
        trustDevice: Boolean? = null,
    ): BetterAuthResult<AuthResponse> =
        t.post(
            "/two-factor/verify-backup-code",
            jsonBody {
                put("code", code)
                put("disableSession", disableSession)
                put("trustDevice", trustDevice)
            },
        ) { it.toAuthResponse() }

    /** Replaces the user's backup codes. */
    public suspend fun generateBackupCodes(password: String): BetterAuthResult<List<String>> =
        t.post("/two-factor/generate-backup-codes", jsonBody { put("password", password) }) {
            it.asObject().strings("backupCodes")
        }
}

private val TwoFactorKey = PluginKey<TwoFactorApi>("twoFactor", "twoFactorClient()")

private object TwoFactorPlugin : ClientPlugin<TwoFactorApi> {
    override val key: PluginKey<TwoFactorApi> = TwoFactorKey

    override fun createApi(context: PluginContext): TwoFactorApi = TwoFactorApi(context.transport)
}

/**
 * Registers the Two-factor authentication plugin: `BetterAuthClient { plugins(twoFactorClient()) }`. Its API is then `client.twoFactor`.
 */
public fun twoFactorClient(): ClientPlugin<TwoFactorApi> = TwoFactorPlugin

/** The Two-factor authentication API. Throws if [twoFactorClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.twoFactor: TwoFactorApi get() = plugin(TwoFactorKey)
