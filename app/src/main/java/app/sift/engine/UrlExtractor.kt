package app.sift.engine

/** Pulls URLs (including defanged and bare-domain ones) out of shared text. */
class UrlExtractor(private val psl: PublicSuffixList) {

    private val schemed = Regex(
        """(?i)(?<![\w])(?:https?|hxxps?|ftps?|fxp|wss?|file|intent|market|data|javascript|vbscript|tel|sms|smsto|mailto|content|blob|ms-[a-z-]+|search-ms|chrome|about|view-source)(?:\[?:\]?|\[:]|:)[^\s<>"'`]+""",
    )
    private val bare = Regex(
        """(?i)(?<![@\w.\-/])(?:[a-z0-9¡-￿](?:[a-z0-9¡-￿\-]{0,62})?(?:\.|\[\.]|\(\.\)|\[dot]))+[a-z¡-￿-]{2,63}(?::\d{1,5})?(?:[/?#][^\s<>"'`]*)?""",
    )

    fun extract(text: String, limit: Int = 20): List<String> {
        val out = LinkedHashSet<String>()
        val taken = mutableListOf<IntRange>()

        for (m in schemed.findAll(text)) {
            val v = trimTrailing(m.value)
            if (v.length < 4 || v.endsWith(":")) continue
            out += v
            taken += m.range
            if (out.size >= limit) return out.toList()
        }
        for (m in bare.findAll(text)) {
            if (taken.any { it.first <= m.range.last && m.range.first <= it.last }) continue
            val v = trimTrailing(m.value)
            val host = Defang.refang(v).substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
            val tld = host.substringAfterLast('.').lowercase()
            val asciiTld = runCatching { java.net.IDN.toASCII(tld, java.net.IDN.ALLOW_UNASSIGNED).lowercase() }.getOrDefault(tld)
            if (!psl.isKnownTld(asciiTld)) continue
            out += v
            if (out.size >= limit) break
        }
        if (out.isEmpty()) {
            val t = text.trim()
            if (t.isNotEmpty() && t.length < 8192 && t.none { it == ' ' || it == '\n' }) out += t
        }
        return out.toList()
    }

    private fun trimTrailing(s: String): String {
        var r = s
        while (r.isNotEmpty()) {
            val c = r.last()
            val drop = when (c) {
                '.', ',', ';', ':', '!', '?', '\'', '"', '*', '>', '…' -> true
                ')' -> r.count { it == '(' } < r.count { it == ')' }
                ']' -> r.count { it == '[' } < r.count { it == ']' }
                '}' -> r.count { it == '{' } < r.count { it == '}' }
                else -> false
            }
            if (!drop) break
            r = r.dropLast(1)
        }
        return r
    }
}
