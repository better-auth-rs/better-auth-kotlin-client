package io.github.betterauth.client

public class BetterAuthException(
    public val status: Int,
    public val code: String?,
    message: String,
) : Exception(message)
