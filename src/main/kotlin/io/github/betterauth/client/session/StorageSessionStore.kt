// SPDX-License-Identifier: Apache-2.0
// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.session

import io.github.betterauth.client.storage.KeyValueStorage
import kotlinx.serialization.json.Json

/**
 * A [SessionStore] on top of any [KeyValueStorage], laid out like the Better Auth Expo plugin:
 * the cookie jar (plus token and JWT) lives under `<prefix>_cookie` and the cached `get-session` result under
 * `<prefix>_session_data`.
 *
 * Use a different [prefix] per user when one storage serves many users (a backend with Redis, for example).
 * Storage failures and corrupt stored data propagate to the caller.
 */
public class StorageSessionStore(
    private val storage: KeyValueStorage,
    prefix: String = DEFAULT_PREFIX,
) : SessionStore {
    internal val lockKey: StorageLockKey = StorageLockKey(System.identityHashCode(storage), prefix)
    private val cookieKey = "${prefix}_cookie"
    private val cacheKey = "${prefix}_session_data"

    override suspend fun get(): StoredSession {
        val raw = storage.getItem(cookieKey) ?: return StoredSession()
        return json.decodeFromString(StoredSession.serializer(), raw)
    }

    override suspend fun set(session: StoredSession) {
        if (session.isEmpty) {
            storage.removeItem(cookieKey)
        } else {
            storage.setItem(cookieKey, json.encodeToString(StoredSession.serializer(), session))
        }
    }

    override suspend fun clear() {
        storage.removeItem(cookieKey)
        storage.removeItem(cacheKey)
    }

    override suspend fun getSessionCache(): String? = storage.getItem(cacheKey)

    override suspend fun setSessionCache(json: String?) {
        if (json == null) storage.removeItem(cacheKey) else storage.setItem(cacheKey, json)
    }

    public companion object {
        public const val DEFAULT_PREFIX: String = "better-auth"
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
    }
}

internal data class StorageLockKey(
    val storageIdentity: Int,
    val prefix: String,
)
