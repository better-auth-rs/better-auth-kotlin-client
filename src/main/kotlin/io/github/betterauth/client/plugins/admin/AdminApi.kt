// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.plugins.admin

import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.api.asObject
import io.github.betterauth.client.api.get
import io.github.betterauth.client.api.json
import io.github.betterauth.client.api.post
import io.github.betterauth.client.api.postForResult
import io.github.betterauth.client.api.rolesToJson
import io.github.betterauth.client.api.toAuthResponse
import io.github.betterauth.client.api.toJson
import io.github.betterauth.client.api.toPermissionCheck
import io.github.betterauth.client.api.toSession
import io.github.betterauth.client.api.toUser
import io.github.betterauth.client.api.toUserFlexible
import io.github.betterauth.client.http.Transport
import io.github.betterauth.client.model.AuthResponse
import io.github.betterauth.client.model.OperationResult
import io.github.betterauth.client.model.PermissionCheck
import io.github.betterauth.client.model.Permissions
import io.github.betterauth.client.model.Session
import io.github.betterauth.client.model.User
import io.github.betterauth.client.model.UsersPage
import io.github.betterauth.client.model.arr
import io.github.betterauth.client.model.jsonBody
import io.github.betterauth.client.model.long
import io.github.betterauth.client.plugin.ClientPlugin
import io.github.betterauth.client.plugin.PluginContext
import io.github.betterauth.client.plugin.PluginKey
import io.github.betterauth.client.result.BetterAuthResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** `client.admin`: needs a signed-in user with the admin role. */
public class AdminApi internal constructor(
    private val t: Transport,
) {
    public suspend fun setRole(
        userId: String,
        role: String,
    ): BetterAuthResult<User> = setRole(userId, listOf(role))

    public suspend fun setRole(
        userId: String,
        roles: List<String>,
    ): BetterAuthResult<User> =
        t.post(
            "/admin/set-role",
            jsonBody {
                put("userId", userId)
                put("role", rolesToJson(roles))
            },
        ) { it.toUserFlexible() }

    public suspend fun getUser(id: String): BetterAuthResult<User> =
        t.get("/admin/get-user", query = mapOf("id" to id)) { it.toUserFlexible() }

    /** Creates a user. [data] carries extra user fields. */
    public suspend fun createUser(
        email: String,
        name: String,
        password: String? = null,
        roles: List<String>? = null,
        data: JsonObject? = null,
    ): BetterAuthResult<User> =
        t.post(
            "/admin/create-user",
            jsonBody {
                put("email", email)
                put("name", name)
                put("password", password)
                put("role", roles?.let(::rolesToJson))
                put("data", data)
            },
        ) { it.toUserFlexible() }

    /** Updates any user fields; [data] is the field map to change. */
    public suspend fun updateUser(
        userId: String,
        data: JsonObject,
    ): BetterAuthResult<User> =
        t.post(
            "/admin/update-user",
            jsonBody {
                put("userId", userId)
                put("data", data)
            },
        ) { it.toUserFlexible() }

    /**
     * Lists users. Filtering uses the server's operators: `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `in`, `not_in`,
     * `contains`, `starts_with`, `ends_with`.
     */
    public suspend fun listUsers(
        searchValue: String? = null,
        searchField: String? = null,
        searchOperator: String? = null,
        limit: Int? = null,
        offset: Int? = null,
        sortBy: String? = null,
        sortDirection: String? = null,
        filterField: String? = null,
        filterValue: String? = null,
        filterOperator: String? = null,
    ): BetterAuthResult<UsersPage> =
        t.get(
            "/admin/list-users",
            query =
                mapOf(
                    "searchValue" to searchValue,
                    "searchField" to searchField,
                    "searchOperator" to searchOperator,
                    "limit" to limit?.toString(),
                    "offset" to offset?.toString(),
                    "sortBy" to sortBy,
                    "sortDirection" to sortDirection,
                    "filterField" to filterField,
                    "filterValue" to filterValue,
                    "filterOperator" to filterOperator,
                ),
        ) { element ->
            val o = element.asObject()
            UsersPage(
                users = o.arr("users").orEmpty().map { it.toUser() },
                total = o.long("total") ?: 0,
                limit = o.long("limit"),
                offset = o.long("offset"),
            )
        }

    public suspend fun listUserSessions(userId: String): BetterAuthResult<List<Session>> =
        t.post("/admin/list-user-sessions", jsonBody { put("userId", userId) }) { element ->
            element
                .asObject()
                .arr("sessions")
                .orEmpty()
                .map { it.toSession() }
        }

    public suspend fun banUser(
        userId: String,
        banReason: String? = null,
        banExpiresInSeconds: Long? = null,
    ): BetterAuthResult<User> =
        t.post(
            "/admin/ban-user",
            jsonBody {
                put("userId", userId)
                put("banReason", banReason)
                put("banExpiresIn", banExpiresInSeconds)
            },
        ) { it.toUserFlexible() }

    public suspend fun unbanUser(userId: String): BetterAuthResult<User> =
        t.post("/admin/unban-user", jsonBody { put("userId", userId) }) { it.toUserFlexible() }

    /**
     * Starts acting as [userId]. The SDK replaces its token with the impersonation session; call
     * [stopImpersonating] to go back, or keep the previous token yourself with `storedSession()` first.
     */
    public suspend fun impersonateUser(userId: String): BetterAuthResult<AuthResponse> =
        t.post("/admin/impersonate-user", jsonBody { put("userId", userId) }) { it.toAuthResponse() }

    public suspend fun stopImpersonating(): BetterAuthResult<AuthResponse> = t.post("/admin/stop-impersonating") { it.toAuthResponse() }

    public suspend fun revokeUserSession(sessionToken: String): BetterAuthResult<OperationResult> =
        t.postForResult("/admin/revoke-user-session", jsonBody { put("sessionToken", sessionToken) })

    public suspend fun revokeUserSessions(userId: String): BetterAuthResult<OperationResult> =
        t.postForResult("/admin/revoke-user-sessions", jsonBody { put("userId", userId) })

    public suspend fun removeUser(userId: String): BetterAuthResult<OperationResult> =
        t.postForResult("/admin/remove-user", jsonBody { put("userId", userId) })

    public suspend fun setUserPassword(
        userId: String,
        newPassword: String,
    ): BetterAuthResult<OperationResult> =
        t.postForResult(
            "/admin/set-user-password",
            jsonBody {
                put("userId", userId)
                put("newPassword", newPassword)
            },
        )

    /** Checks whether the signed-in user (or [userId] / [role]) may perform [permissions]. */
    public suspend fun hasPermission(
        permissions: Permissions,
        userId: String? = null,
        role: String? = null,
    ): BetterAuthResult<PermissionCheck> =
        t.post(
            "/admin/has-permission",
            jsonBody {
                put("permissions", permissions.toJson())
                put("userId", userId)
                put("role", role)
            },
        ) { it.toPermissionCheck() }
}

private val AdminKey = PluginKey<AdminApi>("admin", "adminClient()")

private object AdminPlugin : ClientPlugin<AdminApi> {
    override val key: PluginKey<AdminApi> = AdminKey

    override fun createApi(context: PluginContext): AdminApi = AdminApi(context.transport)
}

/**
 * Registers the Admin plugin: `BetterAuthClient { plugins(adminClient()) }`. Its API is then `client.admin`.
 */
public fun adminClient(): ClientPlugin<AdminApi> = AdminPlugin

/** The Admin API. Throws if [adminClient] was not registered with `plugins(...)`. */
public val BetterAuthClient.admin: AdminApi get() = plugin(AdminKey)
