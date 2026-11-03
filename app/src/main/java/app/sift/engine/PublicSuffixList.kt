package app.sift.engine

import java.net.IDN

/**
 * Minimal implementation of the Public Suffix List algorithm (https://publicsuffix.org/list/).
 * The list itself ships inside the APK, so lookups never touch the network.
 */
class PublicSuffixList(lines: Sequence<String>) {
    private val rules = HashSet<String>(16_000)
    private val wildcards = HashSet<String>(512)
    private val exceptions = HashSet<String>(64)
    private val privateRules = HashSet<String>(4_000)

    init {
        var inPrivate = false
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("// ===BEGIN PRIVATE DOMAINS")) inPrivate = true
            if (line.isEmpty() || line.startsWith("//")) continue
            val rule = line.split(' ', '\t').first()
            when {
                rule.startsWith("!") -> exceptions += ascii(rule.substring(1)) ?: continue
                rule.startsWith("*.") -> {
                    val r = ascii(rule.substring(2)) ?: continue
                    wildcards += r
                    if (inPrivate) privateRules += "*.$r"
                }
                else -> {
                    val r = ascii(rule) ?: continue
                    rules += r
                    if (inPrivate) privateRules += r
                }
            }
        }
    }

    val size: Int get() = rules.size + wildcards.size + exceptions.size

    fun isKnownTld(tld: String): Boolean = tld.lowercase() in rules

    data class Match(val suffix: String, val isPrivate: Boolean)

    /** [host] must be ASCII (punycode), lowercase, no trailing dot. */
    fun publicSuffix(host: String): Match {
        val labels = host.split('.')
        for (i in labels.indices) {
            val candidate = labels.subList(i, labels.size).joinToString(".")
            if (candidate in exceptions) {
                val s = labels.subList(i + 1, labels.size).joinToString(".")
                return Match(s, false)
            }
            if (candidate in rules) return Match(candidate, candidate in privateRules)
            if (i + 1 < labels.size) {
                val parent = labels.subList(i + 1, labels.size).joinToString(".")
                if (parent in wildcards) return Match(candidate, "*.$parent" in privateRules)
            }
        }
        return Match(labels.last(), false)
    }

    /** eTLD+1, or null when the host *is* a public suffix. */
    fun registrableDomain(host: String): String? {
        val suffix = publicSuffix(host).suffix
        if (host == suffix) return null
        val prefix = host.removeSuffix(".$suffix")
        if (prefix == host) return null
        return prefix.substringAfterLast('.') + "." + suffix
    }

    private fun ascii(s: String): String? = try {
        IDN.toASCII(s, IDN.ALLOW_UNASSIGNED).lowercase()
    } catch (_: Exception) {
        null
    }
}
