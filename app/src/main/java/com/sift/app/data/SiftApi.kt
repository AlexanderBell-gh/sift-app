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

    // Phase 1 (worker-side, not yet implemented): uncomment when live.
    // @POST("/api/import/resolve")
    // suspend fun resolve(@Body body: ResolveRequest): Response<InheritedFacts>
}
