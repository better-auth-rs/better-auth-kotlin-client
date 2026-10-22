import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.redis.AesGcmStringCipher
import io.github.betterauth.client.redis.RedisStorage

fun main() {
    val cipher = AesGcmStringCipher(ByteArray(32) { it.toByte() })
    check(cipher.decrypt(cipher.encrypt("consumer-check")) == "consumer-check")
    BetterAuthClient { baseUrl = "https://auth.example.com/api/auth" }.close()
    println(RedisStorage.DEFAULT_TTL)
}
