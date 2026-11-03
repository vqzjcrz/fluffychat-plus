package app.sift.engine

enum class Severity(val weight: Int, val label: String) {
    INFO(0, "Info"),
    LOW(8, "Low"),
    MEDIUM(22, "Medium"),
    HIGH(45, "High"),
    CRITICAL(100, "Critical"),
}

enum class Category(val label: String) {
    SCHEME("Scheme"),
    HOST("Host"),
    DOMAIN("Domain"),
    IMPERSONATION("Impersonation"),
    OBFUSCATION("Obfuscation"),
    INJECTION("Attack payload"),
    REDIRECT("Redirect"),
    DOWNLOAD("Download"),
    BLOCKLIST("Blocklist"),
    STRUCTURE("Structure"),
    PRIVACY("Privacy"),
}

data class Finding(
    val severity: Severity,
    val category: Category,
    val title: String,
    val detail: String,
    /** Optional snippet of the URL that triggered the finding (already sanitized for display). */
    val evidence: String? = null,
)

enum class Verdict(val label: String, val blurb: String) {
    LOOKS_CLEAN("No red flags", "Nothing suspicious found in the link itself. That isn't a guarantee the site is safe."),
    CAUTION("Caution", "Minor issues. Probably fine, but double-check where it's taking you."),
    SUSPICIOUS("Suspicious", "Several warning signs. Don't log in or download anything from this link."),
    DANGEROUS("Dangerous", "Strong indicators of phishing, an exploit, or malware. Don't open it."),
}

data class Report(
    val input: String,
    /** Cleaned URL as it was analyzed (invisible characters removed, refanged). */
    val analyzed: String,
    val scheme: String?,
    /** Host as ASCII / punycode, lowercase. */
    val host: String?,
    /** Host rendered in Unicode, if it differs from [host]. */
    val unicodeHost: String?,
    val registrableDomain: String?,
    val publicSuffix: String?,
    val port: Int?,
    val path: String,
    val findings: List<Finding>,
    val nested: List<Report>,
    val score: Int,
    val verdict: Verdict,
    val defanged: String,
) {
    val worst: Severity get() = findings.maxOfOrNull { it.severity } ?: Severity.INFO
    val displayHost: String? get() = unicodeHost ?: host
}
