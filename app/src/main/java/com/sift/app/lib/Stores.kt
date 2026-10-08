package com.sift.app.lib

/**
 * Canonical UK store list. Mirrors the web's `STORES` in `Sift/src/lib/stores.ts`
 * and the extension's per-store config — keep ids/names identical so
 * `POST /api/watchlist` rows from the phone match extension rows by
 * (store, product_name) for inheritance (`POST /api/import/resolve`).
 *
 * Logo drawables ship as APK assets (see SIFTPHONEAPP.md § Logos):
 * `res/drawable-nodpi/<assetName>.png`, never fetched from network.
 * `storeLogoFor` semantics: local asset wins, inherited `store_logo`
 * (DB value, may be stale/remote) is the fallback, hide when both empty.
 */
data class Store(
    val id: String,
    val name: String,
    /** drawable asset name, e.g. "tesco" -> R.drawable.tesco */
    val assetName: String,
    val searchUrl: (String) -> String,
)

val STORES: List<Store> = listOf(
    Store("tesco", "Tesco", "tesco") { q -> "https://www.tesco.com/groceries/en-GB/search?query=$q" },
    Store("sainsburys", "Sainsbury's", "sainsburys") { q -> "https://www.sainsburys.co.uk/gol-ui/SearchResults/$q" },
    Store("asda", "ASDA", "asda") { q -> "https://groceries.asda.com/search/$q" },
    Store("morrisons", "Morrisons", "morrisons") { q -> "https://groceries.morrisons.com/search?q=$q" },
    // Web id is "marksandspencer" with logo file "mands.png"; asset stays "mands".
    Store("marksandspencer", "M&S", "mands") { q -> "https://www.marksandspencer.com/food/search?referrer=food-catalogue&searchTerm=$q" },
    Store("aldi", "Aldi", "aldi") { q -> "https://www.aldi.co.uk/search?q=$q" },
    Store("lidl", "Lidl", "lidl") { q -> "https://www.lidl.co.uk/h/search?q=$q" },
    Store("coop", "Co-op", "coop") { q -> "https://www.coop.co.uk/search?q=$q" },
    Store("waitrose", "Waitrose", "waitrose") { q -> "https://www.waitrose.com/search?searchTerm=$q" },
    Store("iceland", "Iceland", "iceland") { q -> "https://www.iceland.co.uk/search?q=$q" },
    Store("ocado", "Ocado", "ocado") { q -> "https://www.ocado.com/search?q=$q" },
)

/** Resolve a store by display name (matches web `storeLogoFor` lookup key). */
fun storeByName(name: String): Store? = STORES.find { it.name == name }

/** Resolve a store by id. */
fun storeById(id: String): Store? = STORES.find { it.id == id }

/** Host -> store id map for ACTION_SEND URL inference (Phase 0, no network). */
val STORE_HOSTS: Map<String, String> = mapOf(
    "tesco.com" to "tesco",
    "sainsburys.co.uk" to "sainsburys",
    "groceries.asda.com" to "asda",
    "asda.com" to "asda",
    "groceries.morrisons.com" to "morrisons",
    "morrisons.com" to "morrisons",
    "marksandspencer.com" to "marksandspencer",
    "aldi.co.uk" to "aldi",
    "lidl.co.uk" to "lidl",
    "coop.co.uk" to "coop",
    "waitrose.com" to "waitrose",
    "iceland.co.uk" to "iceland",
    "ocado.com" to "ocado",
)

/** Infer a store id from a shared URL's host. Returns null when unknown. */
fun storeIdForUrl(url: String): String? {
    val host = Regex("https?://([^/\\s]+)").find(url)?.groupValues?.get(1)?.lowercase() ?: return null
    // Longest-suffix match so groceries.asda.com wins over asda.com correctly.
    return STORE_HOSTS.entries
        .filter { (suffix, _) -> host == suffix || host.endsWith(".$suffix") }
        .maxByOrNull { (suffix, _) -> suffix.length }
        ?.value
}
