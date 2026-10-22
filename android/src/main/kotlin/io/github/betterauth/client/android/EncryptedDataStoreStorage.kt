// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.

package io.github.betterauth.client.android

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.betterauth.client.storage.KeyValueStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlin.io.encoding.Base64

/**
 * Stores encrypted session values in Jetpack DataStore.
 * Decryption failures and file corruption propagate without deleting the stored data.
 * Use [create] to share one DataStore instance per file within the process.
 */
public class EncryptedDataStoreStorage(
    private val dataStore: DataStore<Preferences>,
    private val cipher: ValueCipher,
) : KeyValueStorage {
    override suspend fun getItem(key: String): String? {
        val encoded = dataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return String(cipher.decrypt(Base64.decode(encoded)), Charsets.UTF_8)
    }

    override suspend fun setItem(
        key: String,
        value: String,
    ) {
        val encoded = Base64.encode(cipher.encrypt(value.toByteArray(Charsets.UTF_8)))
        dataStore.edit { it[stringPreferencesKey(key)] = encoded }
    }

    override suspend fun removeItem(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    public companion object {
        public const val DEFAULT_FILE_NAME: String = "better_auth_kt_client"

        private data class SharedStorage(
            val keyAlias: String,
            val storage: EncryptedDataStoreStorage,
        )

        private val stores = HashMap<String, SharedStorage>()

        /**
         * Returns storage for the application's DataStore file and Android Keystore [keyAlias].
         * Repeated calls for the same file must use the same key alias.
         * Exclude the DataStore file from Auto Backup because Keystore keys cannot be restored.
         */
        public fun create(
            context: Context,
            fileName: String = DEFAULT_FILE_NAME,
            keyAlias: String = AndroidKeystoreCipher.DEFAULT_ALIAS,
        ): EncryptedDataStoreStorage {
            val file = context.applicationContext.preferencesDataStoreFile(fileName).canonicalFile
            return synchronized(stores) {
                val existing = stores[file.path]
                if (existing != null) {
                    require(existing.keyAlias == keyAlias) {
                        "The DataStore file already uses a different Android Keystore alias"
                    }
                    existing.storage
                } else {
                    val dataStore =
                        PreferenceDataStoreFactory.create(
                            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                            produceFile = { file },
                        )
                    EncryptedDataStoreStorage(dataStore, AndroidKeystoreCipher(keyAlias)).also {
                        stores[file.path] = SharedStorage(keyAlias, it)
                    }
                }
            }
        }
    }
}
