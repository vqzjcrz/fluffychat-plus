package app.sift.engine

import java.net.IDN

/**
 * Local domain/URL blocklist. Accepts hosts files ("0.0.0.0 evil.com"), plain domain lists,
 * AdBlock-style "||evil.com^" rules, and full URLs (one per line).
 */
class Blocklist(
    val domains: Set<String>,
    val urls: Set<String>,
) {
    val size: Int get() = domains.size + urls.size

    /** Returns the blocklisted domain that matched [host] or one of its parents. */
    fun matchDomain(host: String): String? {
        if (domains.isEmpty()) return null
        var h = host.lowercase().trimEnd('.')
        while (true) {
            if (h in domains) return h
            val i = h.indexOf('.')
            if (i < 0) return null
            h = h.substring(i + 1)
        }
    }

    fun matchUrl(url: String): Boolean = urls.isNotEmpty() && normalizeUrl(url) in urls

    companion object {
        val EMPTY = Blocklist(emptySet(), emptySet())

        private val SKIP = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0", "127.0.0.1", "::1", "ip6-localhost", "ip6-loopback")
        private val IP_PREFIX = Regex("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1|::1?|::)\\s+")
        private val DOMAIN = Regex("^[a-z0-9_]([a-z0-9_-]{0,62}\\.)*[a-z0-9_-]{1,63}$")

        fun normalizeUrl(u: String): String {
            var s = u.trim().substringBefore('#')
            val schemeEnd = s.indexOf("://")
            if (schemeEnd > 0) {
                val authEnd = s.indexOf('/', schemeEnd + 3).let { if (it < 0) s.length else it }
                s = s.substring(0, authEnd).lowercase() + s.substring(authEnd)
            }
            return s.trimEnd('/')
        }

        fun parse(text: String): Blocklist = parse(text.lineSequence())

        fun parse(lines: Sequence<String>): Blocklist {
            val domains = HashSet<String>()
            val urls = HashSet<String>()
            for (raw in lines) {
                var line = raw.trim()
                if (line.isEmpty() || line[0] == '#' || line[0] == '!' || line.startsWith("//") || line[0] == '[') continue
                val hash = line.indexOf(" #")
                if (hash > 0) line = line.substring(0, hash).trim()

                if (line.contains("://")) {
                    urls += normalizeUrl(line)
                    continue
                }
                line = IP_PREFIX.replace(line, "")
                if (line.startsWith("||")) line = line.removePrefix("||").substringBefore('^').substringBefore('$')
                if (line.startsWith("*.")) line = line.substring(2)
                if (line.startsWith(".")) line = line.substring(1)
                line = line.split(' ', '\t').first().trimEnd('.').lowercase()
                if (line in SKIP || line.isEmpty()) continue
                val ascii = runCatching { IDN.toASCII(line, IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull() ?: continue
                if (DOMAIN.matches(ascii)) domains += ascii
            }
            return Blocklist(domains, urls)
        }

        fun merge(lists: Collection<Blocklist>): Blocklist =
            Blocklist(lists.flatMapTo(HashSet()) { it.domains }, lists.flatMapTo(HashSet()) { it.urls })
    }
}
