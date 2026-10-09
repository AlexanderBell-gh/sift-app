package com.sift.app

import android.content.Context
import com.sift.app.data.AuthStore
import com.sift.app.data.CatalogStore
import com.sift.app.data.SiftApi
import com.sift.app.data.WatchlistRepository
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual DI container (no Hilt/Koin in v1 — see AGENTS.md).
 * Single Retrofit + OkHttp pair; auth header injected from [AuthStore].
 * Revisit Hilt only when the graph outgrows ~6 bindings.
 */
class AppContainer(context: Context, val apiBase: String) {
    val authStore = AuthStore(context.applicationContext)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val authInterceptor = Interceptor { chain ->
        val token = authStore.currentToken()
        val req = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        }
        chain.proceed(req)
    }

    // Authorization header is redacted by the logging interceptor by default.
    private val logInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logInterceptor)
        .build()

    val api: SiftApi = Retrofit.Builder()
        .baseUrl(apiBase.trimEnd('/') + "/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SiftApi::class.java)

    val watchlistRepository = WatchlistRepository(api)

    // Phase 2 catalog (bundled uk-*.json). Nothing reads it yet —
    // confirm-screen suggestions land in Phase 3.
    val catalogStore = CatalogStore(context.applicationContext)
}
