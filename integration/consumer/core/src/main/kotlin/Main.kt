import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.plugins.twofactor.twoFactor
import io.github.betterauth.client.plugins.twofactor.twoFactorClient

fun main() {
    BetterAuthClient {
        baseUrl = "https://auth.example.com/api/auth"
        plugins(twoFactorClient())
    }.use { auth ->
        println(auth.twoFactor.javaClass.name)
    }
}
