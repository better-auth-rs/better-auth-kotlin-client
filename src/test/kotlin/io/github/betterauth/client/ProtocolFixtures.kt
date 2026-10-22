package io.github.betterauth.client

internal object ProtocolFixtures {
    val user =
        """
        {
          "id": "user-1",
          "name": "Ada",
          "email": "ada@example.com",
          "emailVerified": true,
          "image": null,
          "createdAt": "2026-10-22T00:00:00.000Z",
          "updatedAt": "2026-10-22T00:00:00.000Z",
          "customRole": "reader"
        }
        """.trimIndent()

    val signIn =
        """
        {"redirect": false, "token": "raw-session-token", "user": $user, "customField": true}
        """.trimIndent()

    val session =
        """
        {
          "session": {
            "id": "session-1",
            "token": "raw-session-token",
            "userId": "user-1",
            "expiresAt": "2026-10-29T00:00:00.000Z",
            "createdAt": "2026-10-22T00:00:00.000Z",
            "updatedAt": "2026-10-22T00:00:00.000Z",
            "ipAddress": "127.0.0.1",
            "userAgent": null,
            "customField": 1
          },
          "user": $user
        }
        """.trimIndent()
}
