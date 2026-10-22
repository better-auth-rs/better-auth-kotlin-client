package io.github.betterauth.client

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BetterAuthClientHttpTest {
    @Test
    fun `CIO sends the signed credential through a real HTTP authentication lifecycle`() =
        runBlocking {
            val requests = Collections.synchronizedList(mutableListOf<Pair<String, String?>>())
            val signedToken = "session.signature%2Bencoded%3D"
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/custom/auth/") { exchange ->
                requests.add(exchange.requestURI.path to exchange.requestHeaders.getFirst("Authorization"))
                exchange.requestBody.use { it.readBytes() }
                val response =
                    when (exchange.requestURI.path) {
                        "/custom/auth/sign-in/email" -> {
                            exchange.responseHeaders.add("set-auth-token", signedToken)
                            ProtocolFixtures.signIn
                        }

                        "/custom/auth/get-session" -> {
                            ProtocolFixtures.session
                        }

                        "/custom/auth/sign-out" -> {
                            """{"success": true}"""
                        }

                        else -> {
                            error("Unexpected path: ${exchange.requestURI.path}")
                        }
                    }.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
            server.start()

            try {
                val store = InMemoryTokenStore()
                BetterAuthClient("http://127.0.0.1:${server.address.port}/custom/auth", store).use { auth ->
                    withTimeout(10_000) {
                        auth.signInEmail("ada@example.com", "correct-password")
                        assertEquals("user-1", auth.getSession()?.user?.id)
                        auth.signOut()
                        assertNull(store.read())
                    }
                }
                assertEquals(
                    listOf(
                        "/custom/auth/sign-in/email" to null,
                        "/custom/auth/get-session" to "Bearer $signedToken",
                        "/custom/auth/sign-out" to "Bearer $signedToken",
                    ),
                    requests.toList(),
                )
            } finally {
                server.stop(0)
            }
        }
}
