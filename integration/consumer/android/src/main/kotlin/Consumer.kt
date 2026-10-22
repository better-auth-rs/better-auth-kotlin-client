package io.github.betterauth.consumer

import android.content.Context
import io.github.betterauth.client.BetterAuthClient
import io.github.betterauth.client.android.androidClient

fun createAuth(context: Context): BetterAuthClient =
    BetterAuthClient {
        baseUrl = "https://auth.example.com/api/auth"
        plugins(androidClient(context) { scheme = "consumer" })
    }
