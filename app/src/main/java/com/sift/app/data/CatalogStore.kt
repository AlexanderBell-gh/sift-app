package com.sift.app.data

import android.content.Context
import com.sift.app.lib.CatalogEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Phase 2 catalog loader: reads the bundled `uk-*.json` assets
 * (copies of `Sift/src/data/`, refreshed via `sync-assets`) into
 * [CatalogEntry] rows for [com.sift.app.lib.matchCatalog].
 *
 * Android-only (`AssetManager`) by design — the matcher itself stays pure
 * Kotlin/JVM-testable. Owned by `AppContainer`; nothing reads [entries]
 * yet (confirm-screen suggestions land in Phase 3).
 */
class CatalogStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val entries: StateFlow<List<CatalogEntry>> = _entries.asStateFlow()

    /** Load (or reload) all catalog assets off the main thread. */
    suspend fun load() = withContext(Dispatchers.IO) {
        val all = mutableListOf<CatalogEntry>()
        for ((category, file) in FILES) {
            val text = context.assets.open(file).bufferedReader().use { it.readText() }
            val rows = json.decodeFromString<List<NameRow>>(text)
            rows.forEachIndexed { i, row ->
                all += CatalogEntry(id = "$category#$i", name = row.name, category = category)
            }
        }
        _entries.value = all
    }

    @Serializable
    private data class NameRow(val name: String)

    companion object {
        /** Category slug to asset file. Order is load order only. */
        val FILES = listOf(
            "bakery" to "uk-bakery.json",
            "cupboard" to "uk-cupboard.json",
            "dairy" to "uk-dairy.json",
            "drinks" to "uk-drinks.json",
            "frozen" to "uk-frozen.json",
            "meat-fish" to "uk-meat-fish.json",
            "produce" to "uk-produce.json",
        )
    }
}
