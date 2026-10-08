package com.sift.app.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StoresTest {

    @Test
    fun `eleven stores shipped`() {
        assertEquals(11, STORES.size)
    }

    @Test
    fun `mands asset name matches bundled png`() {
        val mands = storeById("marksandspencer")
        assertNotNull(mands)
        assertEquals("mands", mands!!.assetName)
        assertEquals("M&S", mands.name)
    }

    @Test
    fun `store lookup by display name`() {
        assertEquals("tesco", storeByName("Tesco")?.id)
        assertEquals("sainsburys", storeByName("Sainsbury's")?.id)
        assertNull(storeByName("No Such Store"))
    }

    @Test
    fun `host inference covers all eleven`() {
        val cases = mapOf(
            "https://www.tesco.com/groceries/x" to "tesco",
            "https://www.sainsburys.co.uk/gol-ui/x" to "sainsburys",
            "https://groceries.asda.com/search/milk" to "asda",
            "https://groceries.morrisons.com/search?q=milk" to "morrisons",
            "https://www.marksandspencer.com/food/x" to "marksandspencer",
            "https://www.aldi.co.uk/search?q=milk" to "aldi",
            "https://www.lidl.co.uk/h/search?q=milk" to "lidl",
            "https://www.coop.co.uk/search?q=milk" to "coop",
            "https://www.waitrose.com/search?searchTerm=milk" to "waitrose",
            "https://www.iceland.co.uk/search?q=milk" to "iceland",
            "https://www.ocado.com/search?q=milk" to "ocado",
        )
        cases.forEach { (url, expected) ->
            assertEquals(expected, storeIdForUrl(url))
        }
    }
}
