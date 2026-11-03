package app.sift.engine

/**
 * A deliberately lenient URL splitter. Real parsers reject (or silently "fix") the malformed
 * input attackers use, so we split it ourselves and report the oddities instead.
 */
data class ParsedUrl(
    /** Lowercase scheme. If none was given this is "https" and [schemeAssumed] is true. */
    val scheme: String,
    val schemeAssumed: Boolean,
    val hierarchical: Boolean,
    /** The run of '/' and '\' between "scheme:" and the authority. */
    val slashes: String,
    val userInfo: String?,
    val rawHost: String,
    val port: String?,
    val path: String,
    val query: String?,
    val fragment: String?,
    /** For non-hierarchical URLs (javascript:, data:, tel:) — everything after "scheme:". */
    val opaque: String?,
)

object UrlParser {
    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")

    private val HIERARCHICAL = setOf(
        "http", "https", "ftp", "ftps", "ws", "wss", "sftp", "ssh", "smb", "file", "rtsp", "git", "irc", "ircs",
        "gopher", "telnet", "ldap", "ldaps", "nntp", "svn", "webdav", "dav", "afp", "nfs",
    )

    /** Always treated as opaque, even when followed by "//" (javascript://%0aalert(1), intent://…). */
    private val OPAQUE = setOf("javascript", "vbscript", "livescript", "data", "intent", "tel", "sms", "smsto", "mms", "mailto", "blob", "about", "market")

    fun parse(input: String): ParsedUrl {
        var scheme: String? = null
        var rest = input
        val m = SCHEME.find(input)
        if (m != null) {
            val candidate = m.groupValues[1].lowercase()
            val after = input.substring(m.range.last + 1)
            val digits = after.takeWhile { it.isDigit() }
            val afterDigits = after.substring(digits.length)
            val looksLikeHostPort = (candidate.contains('.') || candidate == "localhost") &&
                digits.isNotEmpty() && (afterDigits.isEmpty() || afterDigits[0] in "/?#\\")
            if (!looksLikeHostPort) {
                scheme = candidate
                rest = after
            }
        }
        val assumed = scheme == null
        val hierarchical = scheme !in OPAQUE && (scheme == null || scheme in HIERARCHICAL ||
            rest.startsWith("//") || rest.startsWith("\\\\") || rest.startsWith("/\\") || rest.startsWith("\\/"))

        if (!hierarchical) {
            return ParsedUrl(scheme!!, false, false, "", null, "", null, "", null, null, rest)
        }

        val slashes = rest.takeWhile { it == '/' || it == '\\' }
        rest = rest.substring(slashes.length)

        val authEnd = rest.indexOfFirst { it == '/' || it == '\\' || it == '?' || it == '#' }
            .let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authEnd)
        var tail = rest.substring(authEnd)

        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at) else null
        val hostPort = if (at >= 0) authority.substring(at + 1) else authority

        var host: String
        var port: String? = null
        if (hostPort.startsWith("[")) {
            val close = hostPort.indexOf(']')
            if (close > 0) {
                host = hostPort.substring(0, close + 1)
                val r = hostPort.substring(close + 1)
                if (r.startsWith(":")) port = r.substring(1)
            } else {
                host = hostPort
            }
        } else {
            val c = hostPort.lastIndexOf(':')
            if (c >= 0) {
                host = hostPort.substring(0, c)
                port = hostPort.substring(c + 1)
            } else {
                host = hostPort
            }
        }

        val hash = tail.indexOf('#')
        val fragment = if (hash >= 0) tail.substring(hash + 1) else null
        if (hash >= 0) tail = tail.substring(0, hash)
        val q = tail.indexOf('?')
        val query = if (q >= 0) tail.substring(q + 1) else null
        val path = if (q >= 0) tail.substring(0, q) else tail

        return ParsedUrl(
            scheme = scheme ?: "https",
            schemeAssumed = assumed,
            hierarchical = true,
            slashes = slashes,
            userInfo = userInfo,
            rawHost = host,
            port = port?.ifEmpty { null },
            path = path,
            query = query,
            fragment = fragment,
            opaque = null,
        )
    }

    /** Splits a query string into decoded (key, value) pairs. Keys are lowercased. */
    fun queryParams(query: String?): List<Pair<String, String>> {
        if (query.isNullOrEmpty()) return emptyList()
        return query.split('&', ';').filter { it.isNotEmpty() }.map { part ->
            val eq = part.indexOf('=')
            val k = if (eq >= 0) part.substring(0, eq) else part
            val v = if (eq >= 0) part.substring(eq + 1) else ""
            Text.pctDecode(k, plusIsSpace = true).lowercase() to Text.pctDecode(v, plusIsSpace = true)
        }
    }
}

object Defang {
    private val REFANG_RULES = listOf(
        Regex("(?i)\\bhxxp(s?)") to "http$1",
        Regex("(?i)\\bfxp\\b") to "ftp",
        Regex("\\[\\s*:\\s*/\\s*/\\s*]") to "://",
        Regex("\\[\\s*://\\s*]") to "://",
        Regex("\\[\\s*:\\s*]") to ":",
        Regex("\\[\\s*\\.\\s*]|\\(\\s*\\.\\s*\\)|\\{\\s*\\.\\s*\\}") to ".",
        Regex("(?i)\\[\\s*dot\\s*]|\\(\\s*dot\\s*\\)|\\{\\s*dot\\s*\\}") to ".",
        Regex("\\[\\s*/\\s*]") to "/",
        Regex("\\[\\s*@\\s*]|(?i)\\[\\s*at\\s*]") to "@",
    )

    fun refang(s: String): String {
        var r = s
        for ((re, rep) in REFANG_RULES) r = re.replace(r, rep)
        return r
    }

    /** hxxps[://]evil[.]example/path — safe to paste into reports and chats. */
    fun defang(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd < 0) {
            val colon = url.indexOf(':')
            return if (colon > 0) url.substring(0, colon) + "[:]" + url.substring(colon + 1) else url.replace(".", "[.]")
        }
        val scheme = url.substring(0, schemeEnd).replace(Regex("(?i)^http"), "hxxp").replace(Regex("(?i)^ftp"), "fxp")
        val rest = url.substring(schemeEnd + 3)
        val authEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authEnd).replace(".", "[.]").replace("@", "[@]")
        return "$scheme[://]$authority${rest.substring(authEnd)}"
    }
}
