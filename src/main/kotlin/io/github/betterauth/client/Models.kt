package io.github.betterauth.client

import kotlinx.serialization.Serializable

/** Date fields contain ISO 8601 strings from the server. */
@Serializable
public data class User(
    val id: String,
    val name: String,
    val email: String,
    val emailVerified: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val image: String? = null,
)

/** Date fields contain ISO 8601 strings from the server. */
@Serializable
public data class Session(
    val id: String,
    val token: String,
    val userId: String,
    val expiresAt: String,
    val createdAt: String,
    val updatedAt: String,
    val ipAddress: String? = null,
    val userAgent: String? = null,
)

@Serializable
public data class SessionData(
    val session: Session,
    val user: User,
)

@Serializable
public data class SignUpResponse(
    val token: String?,
    val user: User,
)

@Serializable
public data class SignInResponse(
    val redirect: Boolean,
    val token: String,
    val user: User,
    val url: String? = null,
)
