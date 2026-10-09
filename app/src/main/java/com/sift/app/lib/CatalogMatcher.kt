package com.sift.app.lib

/**
 * Phase 2 on-device catalog matcher (portable, zero-dep, no network).
 *
 * Scores the bundled `uk-*.json` catalog against a share-derived title with
 * normalized token overlap and returns the top-N candidates with catalog ids.
 * Mirrors the web's Fuse-over-names autocomplete (`Sift/src/lib/api.ts`)
 * deliberately simply: exact token overlap ranks first, noise already
 * stripped by [cleanTitle] never participates.
 *
 * Pure Kotlin (no Android APIs) so it runs in local JVM unit tests verbatim.
 * Callers pass in entries — asset loading lives in `data/CatalogStore`
 * (Android `AssetManager`), keeping this file JVM-testable.
 */
data class CatalogEntry(
    /** Stable within a catalog copy: `"<category>#<index>"` (e.g. `dairy#42`). */
    val id: String,
    /** Display name as shipped in the catalog JSON. */
    val name: String,
    /** Asset slug without prefix/suffix (e.g. `dairy` for `uk-dairy.json`). */
    val category: String,
)

data class CatalogMatch(
    val entry: CatalogEntry,
    /** Fraction of query tokens covered by the entry (0..1]. */
    val score: Double,
)

private data class Scored(val entry: CatalogEntry, val precision: Double, val recall: Double)

/** Normalize a title to matchable tokens: clean, lowercase, split on non-alphanumerics. */
fun normalizeTokens(title: String): List<String> =
    cleanTitle(title).lowercase()
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }

/**
 * Return up to [limit] catalog candidates for [query], best first.
 *
 * Ranking: query-token coverage (precision) desc, then entry-token coverage
 * (recall, prefers the tighter name) desc, then category + name for a
 * deterministic order on ties. Queries with no usable tokens match nothing.
 */
fun matchCatalog(query: String, entries: List<CatalogEntry>, limit: Int = 3): List<CatalogMatch> {
    val q = normalizeTokens(query).toSet()
    if (q.isEmpty() || entries.isEmpty() || limit <= 0) return emptyList()
    return entries.mapNotNull { e ->
        val eToks = normalizeTokens(e.name).toSet()
        if (eToks.isEmpty()) return@mapNotNull null
        val inter = (q intersect eToks).size
        if (inter == 0) return@mapNotNull null
        Scored(e, inter.toDouble() / q.size, inter.toDouble() / eToks.size)
    }.sortedWith(
        compareByDescending<Scored> { it.precision }
            .thenByDescending { it.recall }
            .thenBy { it.entry.category }
            .thenBy { it.entry.name },
    ).take(limit).map { CatalogMatch(it.entry, it.precision) }
}
