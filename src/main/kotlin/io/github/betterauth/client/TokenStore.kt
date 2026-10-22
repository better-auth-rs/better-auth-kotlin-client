package io.github.betterauth.client

/** Stores the signed bearer credential from the server's set-auth-token header. */
public interface TokenStore {
    public suspend fun read(): String?

    public suspend fun write(token: String?)
}

public class InMemoryTokenStore(
    initialToken: String? = null,
) : TokenStore {
    @Volatile
    private var token: String? = initialToken

    override suspend fun read(): String? = token

    override suspend fun write(token: String?) {
        this.token = token
    }
}
