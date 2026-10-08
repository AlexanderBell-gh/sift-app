package com.sift.app.lib

/**
 * Phase 0 share-text parser (portable, zero-dep, no network).
 *
 * Mirrors the planned `workers/lib/`-style parser from SIFTPHONEAPP.md § Phase 0:
 * strips price/promo/quantity fragments from shared text, extracts any URL for
 * store inference ([storeIdForUrl]) + slug-to-title fallback.
 *
 * Pure Kotlin (no Android APIs) so it runs in local JVM unit tests verbatim.
 */
data class ParsedShare(
    /** Cleaned product title candidate. */
    val title: String,
    /** First URL found in the shared text, if any. */
    val url: String?,
    /** Store id inferred from the URL host, if any. */
    val storeId: String?,
)

private val URL_REGEX = Regex("https?://[^\\s)\"'<>]+")

// £1.50, 50p, £12, 3 for £12, BOGOF-adjacent fragments live in offer text, not titles.
private val PRICE_FRAGMENT = Regex(
    """(?ix)
    £\s*\d+(?:\.\d{1,2})?      # £1.50 / £12
    | \b\d+(?:\.\d{1,2})?\s*p\b # 50p / 75p
    | \b\d+\s*for\s*£?\s*\d+(?:\.\d{1,2})?  # 3 for £12 / any 3 for 2
    | \bbuy\s+one\s+get\s+one\b  # BOGOF spelled out
    | \bbogof\b
    | \bwas\s*£?\s*\d+(?:\.\d{1,2})?   # Was £2.00
    | \bnow\s*£?\s*\d+(?:\.\d{1,2})?   # Now £1.50
    | \b\d+\s*%\s*off\b         # 20% off
    | \bhalf\s+price\b
    """
)

// Trailing quantity/size fragments: 500g, 1kg, 2x200ml, 4 pack, 1L…
private val QTY_FRAGMENT = Regex(
    """(?ix)
    \(?\s*\d+\s*[x×]\s*\d+(?:\.\d+)?\s*(?:g|kg|ml|l|cl)\s*\)?  # 2x200ml
    | \(?\s*\d+(?:\.\d+)?\s*(?:g|kg|ml|l|cl|oz)\s*\)?          # 500g / 1.5kg / 1L
    | \b\d+\s*pack\b                                           # 4 pack
    """
)

/** Strip price/promo/quantity fragments and surrounding noise from shared text. */
fun cleanTitle(raw: String): String {
    var s = raw
    s = URL_REGEX.replace(s, " ")
    s = PRICE_FRAGMENT.replace(s, " ")
    s = QTY_FRAGMENT.replace(s, " ")
    // Collapse separators/pipes left over from share sheets ("Tesco | Milk | ...").
    s = s.replace(Regex("[|•·–—]+"), " ")
    s = s.replace(Regex("\\s+"), " ").trim()
    // Drop empty parens/brackets left by fragment removal.
    s = s.replace(Regex("\\(\\s*\\)"), "").replace(Regex("\\[\\s*]"), "")
    s = s.replace(Regex("\\s+"), " ").trim(' ', '-', '–', ':', ',')
    return s.trim()
}

/** Extract the first URL from shared text, if any. */
fun extractUrl(text: String): String? =
    URL_REGEX.find(text)?.value?.trimEnd('.', ',', '!', '?', ')')

/**
 * Slug-to-title fallback: last meaningful path segment of a product URL
 * turned into a human title (".../tesco-whole-milk-2l-12345" -> "Tesco Whole Milk").
 */
fun slugToTitle(url: String): String? {
    val path = url.substringAfter("://").substringAfter("/", "").substringBefore("?").substringBefore("#")
    if (path.isBlank()) return null
    val segment = path.split("/").lastOrNull { it.isNotBlank() } ?: return null
    var slug = segment
    // Drop trailing numeric SKU tails ("...-milk-2l-123456" -> "...-milk-2l").
    slug = slug.replace(Regex("-\\d{4,}$"), "")
    val words = slug.split(Regex("[-_]+")).filter { it.isNotBlank() && !it.all(Char::isDigit) }
    if (words.isEmpty()) return null
    return words.joinToString(" ") { w ->
        w.lowercase().replaceFirstChar { c -> c.uppercaseChar() }
    }
}

/** Full parse: clean title, URL, store inference, slug fallback when title is thin. */
fun parseShareText(text: String): ParsedShare {
    val url = extractUrl(text)
    val storeId = url?.let(::storeIdForUrl)
    var title = cleanTitle(text)
    if (title.length < 3 && url != null) {
        title = slugToTitle(url) ?: title
    }
    return ParsedShare(title = title, url = url, storeId = storeId)
}
