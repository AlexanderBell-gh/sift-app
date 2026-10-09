package com.sift.app.lib

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

class CatalogMatcherTest {

    private val cheddar = listOf(
        CatalogEntry("dairy#1", "Cheddar", "dairy"),
        CatalogEntry("dairy#2", "Mature Cheddar Cheese", "dairy"),
        CatalogEntry("dairy#3", "Mild Cheddar Cheese", "dairy"),
        CatalogEntry("dairy#4", "Extra Mature Cheddar", "dairy"),
        CatalogEntry("dairy#5", "Red Leicester", "dairy"),
    )

    @Test
    fun `exact match ranks first with full score`() {
        val matches = matchCatalog("Cheddar", cheddar)
        assertEquals("dairy#1", matches.first().entry.id)
        assertEquals(1.0, matches.first().score, 0.0)
    }

    @Test
    fun `partial query returns top 3 tightest names first`() {
        val matches = matchCatalog("cheddar", cheddar, limit = 3)
        assertEquals(3, matches.size)
        assertEquals(
            listOf("dairy#1", "dairy#4", "dairy#2"),
            matches.map { it.entry.id },
        )
    }

    @Test
    fun `price and quantity noise does not break the match`() {
        val entries = listOf(
            CatalogEntry("dairy#1", "Whole Milk 4Pint", "dairy"),
            CatalogEntry("dairy#2", "Whole Milk", "dairy"),
            CatalogEntry("dairy#3", "Semi-Skimmed Milk", "dairy"),
        )
        val matches = matchCatalog("Whole Milk 4Pint £2.50", entries)
        assertEquals("dairy#1", matches.first().entry.id)
    }

    @Test
    fun `query with no usable tokens matches nothing`() {
        assertTrue(matchCatalog("£2.50 !!!", cheddar).isEmpty())
        assertTrue(matchCatalog("", cheddar).isEmpty())
        assertTrue(matchCatalog("cheddar", cheddar, limit = 0).isEmpty())
        assertTrue(matchCatalog("cheddar", emptyList()).isEmpty())
    }

    @Test
    fun `unrelated names never surface`() {
        val matches = matchCatalog("cheddar", cheddar, limit = 10)
        assertTrue(matches.none { it.entry.id == "dairy#5" })
    }

    @Serializable
    private data class NameRow(val name: String)

    private val rowsJson = Json { ignoreUnknownKeys = true }

    @Test
    fun `real dairy asset resolves a staple to the dairy category`() {
        // JVM unit tests run with the module dir as cwd, so the bundled
        // assets are readable directly. Skip outside Gradle (e.g. IDE run
        // configs with a different working directory).
        val asset = java.io.File("src/main/assets/uk-dairy.json")
        Assume.assumeTrue(asset.exists())
        val rows = rowsJson.decodeFromString<List<NameRow>>(asset.readText())
        Assume.assumeTrue(rows.isNotEmpty())
        val entries = rows.mapIndexed { i, r ->
            CatalogEntry("dairy#$i", r.name, "dairy")
        }
        val matches = matchCatalog("Semi-Skimmed Milk", entries)
        assertEquals("dairy", matches.first().entry.category)
        assertEquals(1.0, matches.first().score, 0.0)
    }
}
