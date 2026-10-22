// Adapted by better-auth-rs; see NOTICE for upstream attribution and modification details.
package io.github.betterauth.client.redis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AesGcmStringCipherTest {
    private val cipher = AesGcmStringCipher(ByteArray(32) { it.toByte() })

    @Test
    fun `round trips and is randomized`() {
        val a = cipher.encrypt("hello")
        val b = cipher.encrypt("hello")
        assertNotEquals(a, b)
        assertEquals("hello", cipher.decrypt(a))
        assertEquals("hello", cipher.decrypt(b))
    }

    @Test
    fun `tampering is detected`() {
        val encoded = cipher.encrypt("hello")
        val tampered = encoded.dropLast(4) + (if (encoded.takeLast(4) == "AAAA") "BBBB" else "AAAA")
        kotlin.test.assertFails { cipher.decrypt(tampered) }
        kotlin.test.assertFails { cipher.decrypt("not-ours") }
    }

    @Test
    fun `rejects bad key sizes`() {
        kotlin.test.assertFails { AesGcmStringCipher(ByteArray(10)) }
    }
}
