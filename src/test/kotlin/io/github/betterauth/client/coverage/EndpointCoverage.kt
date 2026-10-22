package io.github.betterauth.client.coverage

import io.ktor.client.request.HttpRequestData
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

internal object EndpointCoverage {
    private val observedFile: Path =
        Path
            .of(
                System.getProperty("betterAuth.endpointCoverageFile", "build/reports/endpoint-coverage/observed.tsv"),
            ).toAbsolutePath()
    private val recorded = mutableSetOf<String>()

    fun currentTest(): String {
        val frame =
            Throwable().stackTrace.first {
                it.className.startsWith("io.github.betterauth.client.") &&
                    it.className.substringBefore('$').endsWith("Test")
            }
        val suite = frame.className.substringBefore('$').substringAfterLast('.')
        val method = if ('$' in frame.className) frame.className.substringAfter('$').substringBefore('$') else frame.methodName
        return "$suite.$method"
    }

    @Synchronized
    fun record(
        request: HttpRequestData,
        test: String,
    ) {
        val path = request.url.encodedPath.removePrefix("/api/auth")
        val contractPath = if (path.startsWith("/reset-password/")) "/reset-password/{token}" else path
        val line = "${request.method.value} $contractPath\t$test\n"
        if (recorded.add(line)) {
            Files.createDirectories(observedFile.parent)
            Files.writeString(observedFile, line, CREATE, APPEND)
        }
    }

    @JvmStatic
    fun main(arguments: Array<String>) {
        val file = Path.of(arguments.single())
        val observed = Files.readAllLines(file).filter(String::isNotBlank).map { it.split('\t', limit = 2) }
        val byContract = observed.groupBy({ it[0] }, { it[1] })
        val missing = expectedContracts - byContract.keys
        check(missing.isEmpty()) { "No test request observed for: \n${missing.sorted().joinToString("\n")}" }
        val evidence =
            expectedContracts.sorted().joinToString("\n", postfix = "\n") { contract ->
                "$contract\t${byContract.getValue(contract).distinct().sorted().joinToString("; ")}"
            }
        Files.writeString(file.resolveSibling("verified.tsv"), evidence)
        println("Verified ${expectedContracts.size} endpoint contracts from observed HTTP requests.")
    }

    private val expectedContracts: Set<String> =
        """
        GET /account-info
        GET /admin/get-user
        GET /admin/list-users
        GET /get-session
        GET /jwks
        GET /list-accounts
        GET /list-sessions
        GET /magic-link/verify
        GET /multi-session/list-device-sessions
        GET /ok
        GET /one-time-token/generate
        GET /organization/get-active-member
        GET /organization/get-active-member-role
        GET /organization/get-full-organization
        GET /organization/get-invitation
        GET /organization/get-organization
        GET /organization/get-role
        GET /organization/list
        GET /organization/list-invitations
        GET /organization/list-members
        GET /organization/list-roles
        GET /organization/list-team-members
        GET /organization/list-teams
        GET /organization/list-user-invitations
        GET /organization/list-user-teams
        GET /passkey/generate-authenticate-options
        GET /passkey/generate-register-options
        GET /passkey/list-user-passkeys
        GET /reset-password/{token}
        GET /token
        GET /verify-email
        POST /admin/ban-user
        POST /admin/create-user
        POST /admin/has-permission
        POST /admin/impersonate-user
        POST /admin/list-user-sessions
        POST /admin/remove-user
        POST /admin/revoke-user-session
        POST /admin/revoke-user-sessions
        POST /admin/set-role
        POST /admin/set-user-password
        POST /admin/stop-impersonating
        POST /admin/unban-user
        POST /admin/update-user
        POST /change-email
        POST /change-password
        POST /delete-anonymous-user
        POST /delete-user
        POST /email-otp/change-email
        POST /email-otp/check-verification-otp
        POST /email-otp/request-email-change
        POST /email-otp/request-password-reset
        POST /email-otp/reset-password
        POST /email-otp/send-verification-otp
        POST /email-otp/verify-email
        POST /forget-password/email-otp
        POST /get-access-token
        POST /link-social
        POST /multi-session/revoke
        POST /multi-session/set-active
        POST /one-time-token/verify
        POST /organization/accept-invitation
        POST /organization/add-team-member
        POST /organization/cancel-invitation
        POST /organization/check-slug
        POST /organization/create
        POST /organization/create-role
        POST /organization/create-team
        POST /organization/delete
        POST /organization/delete-role
        POST /organization/has-permission
        POST /organization/invite-member
        POST /organization/leave
        POST /organization/reject-invitation
        POST /organization/remove-member
        POST /organization/remove-team
        POST /organization/remove-team-member
        POST /organization/set-active
        POST /organization/set-active-team
        POST /organization/update
        POST /organization/update-member-role
        POST /organization/update-role
        POST /organization/update-team
        POST /passkey/delete-passkey
        POST /passkey/update-passkey
        POST /passkey/verify-authentication
        POST /passkey/verify-registration
        POST /phone-number/request-password-reset
        POST /phone-number/reset-password
        POST /phone-number/send-otp
        POST /phone-number/verify
        POST /refresh-token
        POST /request-password-reset
        POST /reset-password
        POST /revoke-other-sessions
        POST /revoke-session
        POST /revoke-sessions
        POST /send-verification-email
        POST /sign-in/anonymous
        POST /sign-in/email
        POST /sign-in/email-otp
        POST /sign-in/magic-link
        POST /sign-in/phone-number
        POST /sign-in/social
        POST /sign-out
        POST /sign-up/email
        POST /two-factor/disable
        POST /two-factor/enable
        POST /two-factor/generate-backup-codes
        POST /two-factor/get-totp-uri
        POST /two-factor/send-otp
        POST /two-factor/verify-backup-code
        POST /two-factor/verify-otp
        POST /two-factor/verify-totp
        POST /unlink-account
        POST /update-session
        POST /update-user
        POST /verify-password
        """.trimIndent().lines().toSet()
}
