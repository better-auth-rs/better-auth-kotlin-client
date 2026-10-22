package io.github.betterauth.client

import io.github.betterauth.client.http.BetterAuthJson
import io.github.betterauth.client.model.Organization
import io.github.betterauth.client.model.Session
import io.github.betterauth.client.model.User
import io.github.betterauth.client.result.BetterAuthError
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResponseValidationTest {
    @Test
    fun `malformed session arrays are decoding failures instead of empty lists`() =
        runTest {
            TestServer { json("{}") }.client().use { auth ->
                assertIs<BetterAuthError.Decoding>(auth.listSessions().failure())
            }
        }

    @Test
    fun `invalid timestamps are decoding failures instead of absent dates`() =
        runTest {
            val user = USER_JSON.replace("2026-01-02T03:04:05.000Z", "not-a-date")
            TestServer { json("""{"user":$user,"token":"raw"}""") }.client().use { auth ->
                assertIs<BetterAuthError.Decoding>(auth.signUp.email("Ada", "ada@example.com", "password123").failure())
            }
        }

    @Test
    fun `custom response fields round trip without overriding typed fields`() {
        val extra =
            buildJsonObject {
                put("id", "untrusted")
                put("department", "engineering")
            }
        val values =
            listOf(
                BetterAuthJson.encodeToString(User(id = "user-1", additionalFields = extra)),
                BetterAuthJson.encodeToString(Session(id = "session-1", additionalFields = extra)),
                BetterAuthJson.encodeToString(Organization(id = "org-1", additionalFields = extra)),
            )
        for ((body, id) in values.zip(listOf("user-1", "session-1", "org-1"))) {
            val decoded = BetterAuthJson.parseToJsonElement(body).jsonObject
            assertEquals(JsonPrimitive(id), decoded["id"])
            assertEquals(JsonPrimitive("engineering"), decoded["department"])
        }
    }
}
