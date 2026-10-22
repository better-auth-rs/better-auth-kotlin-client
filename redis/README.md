# Redis storage

The Redis module provides `RedisStorage` for Redis and Valkey. Each storage instance uses a key prefix and a write-renewed TTL. The default TTL is 30 days. Pass `ttl = null` to retain values until explicit removal.

```kotlin
import io.github.betterauth.client.redis.AesGcmStringCipher
import io.github.betterauth.client.redis.RedisStorage

val storage = RedisStorage.connect(
    uri = "redis://localhost:6379",
    keyPrefix = "myapp:",
    cipher = AesGcmStringCipher(encryptionKey),
)
val authForUser = auth.withStorage(storage, prefix = "user-$userId")
```

Use a 32-byte encryption key from your secret manager. Decryption failures propagate instead of returning a missing value. Keep the encryption key available for the lifetime of stored credentials. Remove invalid credentials explicitly after determining the cause.

Call `storage.close()` when the application stops. A storage created with `connect` owns its connection and Redis client. For an existing standalone, Sentinel, or Cluster connection, pass the Lettuce asynchronous commands to the `RedisStorage` constructor. The caller retains ownership of that connection.

Use one prefix per user. Client session locks protect writes within one process. Coordinate writers across processes when multiple processes can update the same user's prefix.

Run unit checks with `./gradlew :redis:check`. Run integration tests against a dedicated Redis database:

```sh
REDIS_URL=redis://localhost:6379 ./gradlew :redis:redisIntegrationTest
```

Set `VALKEY_URL` to run the same integration tests against Valkey. A configured server that cannot be reached fails the integration task. Tests use unique key prefixes and do not flush the database.

The implementation is adapted from the Apache-2.0 upstream project. See [NOTICE](../NOTICE) and the [Apache-2.0 license](../licenses/Apache-2.0.txt).
