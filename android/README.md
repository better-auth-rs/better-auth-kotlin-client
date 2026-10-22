# Android adapter

The Android adapter provides encrypted Jetpack DataStore storage and the `androidClient` plugin. The adapter requires Android API 24 or later and compile SDK 37. The Android Keystore holds the AES-256-GCM key; each stored value uses a fresh initialization vector.

## Use

Add the Android artifact alongside the core client, then register the plugin with the application context:

```kotlin
import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.android.android
import io.github.betterauth.client.android.androidClient

val auth = BetterAuthClient {
    baseUrl = "https://api.example.com/api/auth"
    plugins(androidClient(applicationContext) {
        scheme = "myapp"
    })
}

val callbackUrl = auth.android.deepLink("/signed-in")
```

Declare `android.permission.INTERNET` in the application manifest. For browser OAuth, enable the server's Expo plugin and add `myapp://` to `trustedOrigins`. The plugin sends `expo-origin`, skips the OAuth proxy, and converts relative callback URLs into deep links. Native ID-token requests preserve their callback URLs. The application must implement Custom Tabs and receive the final deep link.

The plugin supplies encrypted storage when the client has no configured storage. To configure storage directly:

```kotlin
import io.github.betterauth.client.android.EncryptedDataStoreStorage

val storage = EncryptedDataStoreStorage.create(
    applicationContext,
    fileName = "account-one",
    keyAlias = "account-one-session-key",
)
```

Use one file per distinct key alias. Repeated calls for the same file return the same storage instance. A different key alias for an existing file raises an error. Separate storage wrappers observe DataStore changes without a plaintext cache.

Exclude the DataStore file from Auto Backup because Android Keystore keys cannot be restored. Add an exclusion to each applicable backup rules file:

```xml
<exclude domain="file" path="datastore/better_auth_kt_client.preferences_pb" />
```

The default file name is `better_auth_kt_client`. Adjust the exclusion when setting `storageFileName`. Decryption failures, invalid encoding, and DataStore corruption propagate without deleting stored credentials. The application must decide whether to remove an unreadable entry and require sign-in again.

## Verify

Run the JVM storage and plugin tests and Android lint:

```sh
./gradlew :android:testDebugUnitTest :android:lintDebug :android:assembleRelease
```

With a running Android emulator or connected device, verify the real Keystore and DataStore:

```sh
./gradlew :android:connectedDebugAndroidTest
```

See the root [NOTICE](../NOTICE) for upstream attribution and modification details.
