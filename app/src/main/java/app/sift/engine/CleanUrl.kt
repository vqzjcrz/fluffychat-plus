package app.sift.engine

/** Strips tracking parameters (utm_*, fbclid, gclid, …) while leaving the rest of the link intact. */
object CleanUrl {
    fun clean(url: String): String {
        val hashIdx = url.indexOf('#')
        val beforeHash = if (hashIdx >= 0) url.substring(0, hashIdx) else url
        val fragment = if (hashIdx >= 0) url.substring(hashIdx) else ""
        val q = beforeHash.indexOf('?')
        if (q < 0) return url
        val base = beforeHash.substring(0, q)
        val kept = beforeHash.substring(q + 1).split('&').filter { part ->
            if (part.isEmpty()) return@filter false
            val k = Text.pctDecode(part.substringBefore('=')).lowercase()
            UrlAnalyzer.TRACKING_PARAMS.none { t -> if (t.endsWith("*")) k.startsWith(t.dropLast(1)) else k == t }
        }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }
}
