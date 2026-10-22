// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.
package io.github.betterauth.client.redis

import io.github.betterauth.client.BetterAuthClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.coroutines.runBlocking
import java.security.GeneralSecurityException
import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs against REDIS_URL and optional VALKEY_URL. Connection failures fail the test. */
class RedisStorageTest {
    private val servers: List<Pair<String, String>> =
        listOfNotNull(
            "redis" to
                requireNotNull(System.getenv("REDIS_URL")) {
                    "Set REDIS_URL before running redisIntegrationTest."
                },
            System.getenv("VALKEY_URL")?.let { "valkey" to it },
        )

    private fun eachServer(block: suspend (name: String, uri: String, prefix: String) -> Unit) {
        for ((name, uri) in servers) {
            runBlocking {
                block(name, uri, "test:${UUID.randomUUID()}:")
            }
        }
    }

    private inline fun <T> withCommands(
        uri: String,
        block: (RedisCommands<String, String>) -> T,
    ): T {
        val client = RedisClient.create(RedisURI.create(uri))
        try {
            return client.connect().use { block(it.sync()) }
        } finally {
            client.shutdown()
        }
    }

    @Test
    fun `set get remove`() =
        eachServer { _, uri, prefix ->
            RedisStorage.connect(uri, keyPrefix = prefix).use { storage ->
                assertNull(storage.getItem("k"))
                storage.setItem("k", "v1")
                assertEquals("v1", storage.getItem("k"))
                storage.setItem("k", "v2")
                assertEquals("v2", storage.getItem("k"))
                storage.removeItem("k")
                assertNull(storage.getItem("k"))
            }
        }

    @Test
    fun `values with unicode and json round trip`() =
        eachServer { _, uri, prefix ->
            RedisStorage.connect(uri, keyPrefix = prefix).use { storage ->
                val value = """{"name":"Zuri Muñoz 🌍","cookies":{"a":{"value":"x%3D"}}}"""
                storage.setItem("better-auth_cookie", value)
                assertEquals(value, storage.getItem("better-auth_cookie"))
                storage.removeItem("better-auth_cookie")
            }
        }

    @Test
    fun `keys are namespaced by the prefix`() =
        eachServer { _, uri, prefix ->
            RedisStorage.connect(uri, keyPrefix = prefix + "a:").use { a ->
                RedisStorage.connect(uri, keyPrefix = prefix + "b:").use { b ->
                    a.setItem("k", "from-a")
                    assertNull(b.getItem("k"))
                    b.setItem("k", "from-b")
                    assertEquals("from-a", a.getItem("k"))
                    a.removeItem("k")
                    b.removeItem("k")
                }
            }
        }

    @Test
    fun `ttl is applied on write and can be disabled`() =
        eachServer { _, uri, prefix ->
            withCommands(uri) { sync ->
                RedisStorage.connect(uri, keyPrefix = prefix, ttl = Duration.ofSeconds(120)).use {
                    it.setItem("a", "v")
                    assertTrue(sync.ttl(prefix + "a") in 1..120, "ttl was ${sync.ttl(prefix + "a")}")
                    sync.expire(prefix + "a", 1)
                    it.setItem("a", "updated")
                    assertTrue(sync.ttl(prefix + "a") > 1, "A write must renew the TTL")
                    it.removeItem("a")
                }
                RedisStorage.connect(uri, keyPrefix = prefix, ttl = null).use {
                    it.setItem("b", "v")
                    assertEquals(-1L, sync.ttl(prefix + "b"))
                    it.removeItem("b")
                }
            }
        }

    @Test
    fun `values are encrypted at rest and unreadable with another key`() =
        eachServer { _, uri, prefix ->
            val keyA = ByteArray(32) { 1 }
            val keyB = ByteArray(32) { 2 }
            withCommands(uri) { raw ->
                RedisStorage.connect(uri, keyPrefix = prefix, cipher = AesGcmStringCipher(keyA)).use { enc ->
                    enc.setItem("k", "secret-session-token")
                    val atRest = raw.get(prefix + "k")
                    assertFalse(atRest.contains("secret-session-token"))
                    assertTrue(atRest.startsWith("v1:"))
                    assertEquals("secret-session-token", enc.getItem("k"))
                }
                RedisStorage.connect(uri, keyPrefix = prefix, cipher = AesGcmStringCipher(keyB)).use { wrongKey ->
                    assertFailsWith<GeneralSecurityException> { wrongKey.getItem("k") }
                }
                RedisStorage.connect(uri, keyPrefix = prefix).use { plain ->
                    assertNotEquals("secret-session-token", plain.getItem("k"))
                    plain.removeItem("k")
                }
            }
        }

    @Test
    fun `an unencrypted legacy value reports the encryption format mismatch`() =
        eachServer { _, uri, prefix ->
            RedisStorage.connect(uri, keyPrefix = prefix).use { plain -> plain.setItem("k", "plaintext") }
            RedisStorage.connect(uri, keyPrefix = prefix, cipher = AesGcmStringCipher(ByteArray(32))).use { enc ->
                assertFailsWith<IllegalArgumentException> { enc.getItem("k") }
                enc.removeItem("k")
            }
        }

    @Test
    fun `a client persists one session per user in redis`() =
        eachServer { _, uri, prefix ->
            val engine =
                MockEngine { request ->
                    val who = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ") ?: "new"
                    respond(
                        content = """{"ok":true}""",
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                "set-auth-token" to listOf("rotated-$who"),
                            ),
                    )
                }
            BetterAuthClient {
                baseUrl = "https://auth.example.com/api/auth"
                this.engine = engine
            }.use { auth ->
                RedisStorage.connect(uri, keyPrefix = prefix).use { storage ->
                    val alice = auth.withStorage(storage, "user-alice").also { it.restoreToken("alice") }
                    val bob = auth.withStorage(storage, "user-bob").also { it.restoreToken("bob") }
                    alice.ok()
                    bob.ok()

                    // A new session view reads each user's rotated credential from Redis.
                    assertEquals("rotated-alice", auth.withStorage(storage, "user-alice").currentToken())
                    assertEquals("rotated-bob", auth.withStorage(storage, "user-bob").currentToken())

                    auth.withStorage(storage, "user-alice").clearLocalSession()
                    assertNull(auth.withStorage(storage, "user-alice").currentToken())
                    assertEquals("rotated-bob", auth.withStorage(storage, "user-bob").currentToken())
                    auth.withStorage(storage, "user-bob").clearLocalSession()
                }
            }
        }
}
