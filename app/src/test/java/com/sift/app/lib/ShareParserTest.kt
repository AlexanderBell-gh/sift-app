package com.sift.app.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareParserTest {

    @Test
    fun `extracts url and infers tesco store`() {
        val parsed = parseShareText("Tesco Whole Milk https://www.tesco.com/groceries/en-GB/products/12345")
        assertEquals("https://www.tesco.com/groceries/en-GB/products/12345", parsed.url)
        assertEquals("tesco", parsed.storeId)
    }

    @Test
    fun `strips price fragments from title`() {
        val parsed = parseShareText("Sainsbury's Cheddar 500g Was £3.50 Now £2.50 https://www.sainsburys.co.uk/x")
        assertEquals("sainsburys", parsed.storeId)
        assert(!parsed.title.contains("£"))
        assert(!parsed.title.contains("Was", ignoreCase = true))
    }

    @Test
    fun `infers asda from groceries subdomain`() {
        val parsed = parseShareText("Milk https://groceries.asda.com/product/1")
        assertEquals("asda", parsed.storeId)
    }

    @Test
    fun `unknown host yields null store`() {
        val parsed = parseShareText("Something https://example.com/product/1")
        assertNull(parsed.storeId)
        assertEquals("https://example.com/product/1", parsed.url)
    }

    @Test
    fun `plain text with no url`() {
        val parsed = parseShareText("Co-op Irresistible Bananas")
        assertNull(parsed.url)
        assertNull(parsed.storeId)
        assertEquals("Co-op Irresistible Bananas", parsed.title)
    }

    @Test
    fun `slug fallback when title is thin`() {
        val parsed = parseShareText("https://www.ocado.com/products/tesco-whole-milk-123456")
        assertEquals("ocado", parsed.storeId)
        assert(parsed.title.isNotBlank())
    }

    @Test
    fun `strips multibuy and percent fragments`() {
        val title = cleanTitle("Any 3 for £12 Finest Sausages 20% off")
        assert(!title.contains("£"))
        assert(!title.contains("%"))
        assert(title.contains("Sausages"))
    }
}
