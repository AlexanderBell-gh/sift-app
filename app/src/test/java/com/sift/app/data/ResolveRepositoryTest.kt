package com.sift.app.data

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class ResolveRepositoryTest {

    private class FakeApi(
        val resolveResult: Result<Response<InheritedFacts>>,
    ) : SiftApi {
        override suspend fun login(body: LoginRequest): LoginResponse = throw UnsupportedOperationException()
        override suspend fun postWatchlist(body: WatchlistPostBody): Response<WatchlistPostResponse> =
            throw UnsupportedOperationException()
        override suspend fun resolve(body: ResolveRequest): Response<InheritedFacts> =
            resolveResult.getOrThrow()
    }

    private val facts = InheritedFacts(
        imageUrl = "https://example.com/img.jpg",
        normalPrice = 2.5,
        loyaltyPrice = 2.0,
        offerDeal = "2 for £4",
        category = "Dairy",
        productUrl = "https://www.tesco.com/x",
        unit = "500ml",
    )

    @Test
    fun `resolve returns facts on success`() = runTest {
        val repo = WatchlistRepository(FakeApi(Result.success(Response.success(facts))))
        assertEquals(facts, repo.resolve("Tesco", "Milk"))
    }

    @Test
    fun `resolve returns null on transport failure`() = runTest {
        val repo = WatchlistRepository(FakeApi(Result.failure(IOException("down"))))
        assertNull(repo.resolve("Tesco", "Milk"))
    }

    @Test
    fun `resolve returns null on error code`() = runTest {
        val error = Response.error<InheritedFacts>(
            429,
            "Too many attempts".toResponseBody("text/plain".toMediaType()),
        )
        val repo = WatchlistRepository(FakeApi(Result.success(error)))
        assertNull(repo.resolve("Tesco", "Milk"))
    }

    @Test
    fun `resolve returns null on empty body`() = runTest {
        val repo = WatchlistRepository(FakeApi(Result.success(Response.success(null))))
        assertNull(repo.resolve("Tesco", "Milk"))
    }
}
