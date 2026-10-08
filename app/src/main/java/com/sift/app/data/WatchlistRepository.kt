package com.sift.app.data

/**
 * Pin result surfaced to the confirm screen.
 */
sealed interface PinOutcome {
    data class Pinned(val id: String, val alreadyPinned: Boolean) : PinOutcome
    data class TrialBlocked(val reason: TrialBlockReason) : PinOutcome
    data class Failed(val message: String) : PinOutcome
}

/**
 * Thin wrapper over [SiftApi.postWatchlist]. Phone-pinned rows ride the
 * normal price-update paths (extension re-pins, rescore) like any other row.
 */
class WatchlistRepository(private val api: SiftApi) {
    suspend fun pin(result: WatchlistResult): PinOutcome {
        return try {
            val resp = api.postWatchlist(WatchlistPostBody(result))
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body?.blocked == true) {
                    PinOutcome.TrialBlocked(body.reason?.toTrialBlockReason() ?: TrialBlockReason.UNKNOWN)
                } else {
                    PinOutcome.Pinned(id = body?.id ?: "", alreadyPinned = body?.alreadyPinned == true)
                }
            } else if (resp.code() == 403) {
                // Trial-limit 403s surface the existing upgrade nudge.
                PinOutcome.TrialBlocked(TrialBlockReason.UNKNOWN)
            } else {
                PinOutcome.Failed("Pin failed (${resp.code()})")
            }
        } catch (e: Exception) {
            PinOutcome.Failed(e.message ?: "Network error")
        }
    }
}
