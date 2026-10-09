package com.sift.app.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit service. Paths mirror `Sift/workers/index.js` route table.
 * Authenticated calls carry `Authorization: Bearer <JWT>` via the OkHttp
 * interceptor in [AppContainer] (token from [AuthStore]).
 */
interface SiftApi {
    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @POST("/api/watchlist")
    suspend fun postWatchlist(@Body body: WatchlistPostBody): Response<WatchlistPostResponse>

    // Phase 1 inheritance: best product-level facts from Sift's own DB
    // (200 with empty facts on no match, never 404).
    @POST("/api/import/resolve")
    suspend fun resolve(@Body body: ResolveRequest): Response<InheritedFacts>
}
