package com.sift.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * API models. Field names mirror the Cloudflare Worker contract
 * (`Sift/workers/index.js`) exactly — the worker is plain JS with no
 * shared schema, so this file is the Android-side mirror. Keep in sync
 * manually when the worker changes.
 */

// --- Auth ---

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class SiftUser(
    val id: String,
    val email: String,
    val username: String,
    val role: String = "user",
    val isTrial: Boolean = false,
    val trialExpiresAt: Long? = null,
)

@Serializable
data class LoginResponse(
    val user: SiftUser,
    val token: String,
)

// --- Watchlist pin (POST /api/watchlist { result }) ---

@Serializable
data class PriceBlock(
    val normal: Double? = null,
    val loyalty: Double? = null,
    @SerialName("unit_price") val unitPrice: Double? = null,
    val currency: String = "GBP",
)

@Serializable
data class CategorySignals(
    val title: String? = null,
    val brand: String? = null,
    val store: String? = null,
)

/**
 * Phone pins send title/brand/store signals only (title-first scoring
 * server-side). Never send a guessed `category` — the worker owns taxonomy.
 */
@Serializable
data class WatchlistResult(
    val id: String,
    val name: String,
    val store: String,
    @SerialName("store_logo") val storeLogo: String = "",
    @SerialName("image_url") val imageUrl: String = "",
    val unit: String? = null,
    val prices: PriceBlock? = null,
    @SerialName("loyalty_type") val loyaltyType: String? = null,
    @SerialName("offer_expires_at") val offerExpiresAt: String? = null,
    @SerialName("offer_deal") val offerDeal: String? = null,
    @SerialName("product_url") val productUrl: String = "",
    @SerialName("is_on_offer") val isOnOffer: Boolean = false,
    @SerialName("category_signals") val categorySignals: CategorySignals? = null,
    val notes: String? = null,
)

@Serializable
data class WatchlistPostBody(
    val result: WatchlistResult,
)

@Serializable
data class WatchlistPostResponse(
    val id: String? = null,
    @SerialName("already_pinned") val alreadyPinned: Boolean = false,
    val blocked: Boolean = false,
    val reason: String? = null,
)

/** Trial-limit 403 reasons surfaced as the existing upgrade nudge. */
enum class TrialBlockReason { TRIAL_EXPIRED, WATCHLIST_LIMIT, UNKNOWN }

fun String.toTrialBlockReason(): TrialBlockReason = when (this) {
    "trial_expired" -> TrialBlockReason.TRIAL_EXPIRED
    "watchlist_limit" -> TrialBlockReason.WATCHLIST_LIMIT
    else -> TrialBlockReason.UNKNOWN
}

// --- Future: POST /api/import/resolve (Phase 1, worker-side) ---

@Serializable
data class ResolveRequest(
    val store: String,
    val name: String,
)

@Serializable
data class InheritedFacts(
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("normal_price") val normalPrice: Double? = null,
    @SerialName("loyalty_price") val loyaltyPrice: Double? = null,
    @SerialName("offer_deal") val offerDeal: String? = null,
    @SerialName("offer_expires_at") val offerExpiresAt: String? = null,
    val category: String? = null,
    @SerialName("taxonomy_version") val taxonomyVersion: Int = 0,
    @SerialName("product_url") val productUrl: String = "",
    val unit: String? = null,
)

/**
 * Stable client-side pin identity: m_<hash(store|slug-or-name)>.
 * Same URL shared again -> same id (per-user dedup); same product shared
 * differently -> separate rows (accepted; inheritance still matches by name).
 */
fun productIdFor(store: String, slugOrName: String): String {
    val key = "$store|$slugOrName".lowercase()
    val h = key.hashCode().toUInt().toString(36)
    return "m_$h"
}
