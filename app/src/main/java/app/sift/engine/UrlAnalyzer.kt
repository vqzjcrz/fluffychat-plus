package app.sift.engine

import java.net.IDN
import java.text.Normalizer

/**
 * Static, fully on-device URL analysis. Nothing here opens a socket: the link is never visited,
 * resolved, or sent anywhere. Everything is decided from the text of the URL itself plus the
 * bundled Public Suffix List and whatever local blocklist the user loaded.
 */
class UrlAnalyzer(
    private val psl: PublicSuffixList,
    private val blocklist: Blocklist = Blocklist.EMPTY,
) {
    fun analyze(input: String): Report = analyze(input, depth = 0)

    // ---------------------------------------------------------------------------------------------

    private class Ctx(val input: String) {
        val findings = ArrayList<Finding>()
        val nested = ArrayList<Report>()
        fun add(sev: Severity, cat: Category, title: String, detail: String, evidence: String? = null) {
            if (findings.none { it.title == title }) findings += Finding(sev, cat, title, detail, evidence)
        }
    }

    private fun analyze(input: String, depth: Int): Report {
        val c = Ctx(input)
        var s = input.trim()

        // --- 1. Characters that don't belong in a URL ---------------------------------------
        checkHiddenCharacters(s, c)
        s = HIDDEN.replace(s, "")
        val stripped = s.replace(Regex("[\\t\\r\\n]"), "")
        if (stripped != s) {
            c.add(Severity.MEDIUM, Category.OBFUSCATION, "Tabs or line breaks inside the link",
                "Browsers silently delete these, so the link a filter sees is different from the one you'd open.")
            s = stripped
        }
        val refanged = Defang.refang(s)
        if (refanged != s) {
            c.add(Severity.INFO, Category.STRUCTURE, "Link was defanged",
                "It was written in safe form (hxxp / [.]) — Sift re-assembled it to analyze. Usually means someone already flagged it as malicious.")
            s = refanged
        }
        if (s.length > 2000) {
            c.add(Severity.MEDIUM, Category.STRUCTURE, "Extremely long link (${s.length} chars)",
                "Very long URLs are used to push the real destination or a payload out of view.")
        } else if (s.length > 250) {
            c.add(Severity.LOW, Category.STRUCTURE, "Long link (${s.length} chars)",
                "Long links can hide the important part off-screen. Check the domain carefully.")
        }

        val p = UrlParser.parse(s)
        var asciiHost: String? = null
        var unicodeHost: String? = null
        var registrable: String? = null
        var suffix: String? = null
        var portNum: Int? = null

        // --- 2. Scheme ------------------------------------------------------------------------
        if (!p.hierarchical) {
            checkOpaqueScheme(p, c, depth)
        } else {
            checkHierarchicalScheme(p, c)

            // --- 3. Authority: user info, host, port -----------------------------------------
            val hostInfo = analyzeHost(p, c)
            asciiHost = hostInfo.ascii
            unicodeHost = hostInfo.unicode?.takeIf { it != hostInfo.ascii }
            registrable = hostInfo.registrable
            suffix = hostInfo.suffix
            portNum = checkPort(p, c)
            checkUserInfo(p, asciiHost, c)

            // --- 4. Path / query / fragment ---------------------------------------------------
            checkPathAndQuery(p, asciiHost, registrable, c)
            checkDownload(p, registrable, c)
            if (depth < 2) checkRedirects(p, asciiHost, registrable, c, depth)
            checkCredentialWords(p, asciiHost, registrable, c)

            // --- 5. Blocklist ------------------------------------------------------------------
            if (asciiHost != null) {
                val h: String = asciiHost
                IP_LOGGERS_ASCII.firstOrNull { h == it || h.endsWith(".$it") }?.let {
                    c.add(Severity.HIGH, Category.BLOCKLIST, "Known IP logger",
                        "$it exists to record the IP address, location, and device of whoever opens the link.")
                }
                blocklist.matchDomain(h)?.let {
                    c.add(Severity.CRITICAL, Category.BLOCKLIST, "On your blocklist",
                        "The domain $it appears in your loaded threat list.", it)
                }
            }
            if (blocklist.matchUrl(s)) {
                c.add(Severity.CRITICAL, Category.BLOCKLIST, "Exact URL is on your blocklist",
                    "This exact link appears in your loaded threat list.")
            }
        }

        val findings = c.findings.sortedByDescending { it.severity.ordinal }
        val score = findings.sumOf { it.severity.weight }.coerceAtMost(100)
        val verdict = when {
            findings.any { it.severity == Severity.CRITICAL } || score >= 80 -> Verdict.DANGEROUS
            score >= 40 -> Verdict.SUSPICIOUS
            score >= 15 -> Verdict.CAUTION
            else -> Verdict.LOOKS_CLEAN
        }
        val display = if (p.hierarchical && p.schemeAssumed) "https://$s" else s
        return Report(
            input = input,
            analyzed = display,
            scheme = p.scheme,
            host = asciiHost,
            unicodeHost = unicodeHost,
            registrableDomain = registrable,
            publicSuffix = suffix,
            port = portNum,
            path = p.path,
            findings = findings,
            nested = c.nested,
            score = score,
            verdict = verdict,
            defanged = Defang.defang(display),
        )
    }

    // ============================================================================== characters

    private fun checkHiddenCharacters(s: String, c: Ctx) {
        val bidi = s.filter { it in '\u202A'..'\u202E' || it in '\u2066'..'\u2069' || it == '\u200E' || it == '\u200F' || it == '\u061C' }
        if (bidi.isNotEmpty()) {
            c.add(Severity.CRITICAL, Category.OBFUSCATION, "Right-to-left override characters",
                "Invisible direction-control characters reverse how text is displayed, so 'gpj.exe' can show up as 'exe.jpg'. There's no innocent reason for these in a link.",
                Text.evidence(s))
        }
        val zw = s.filter { it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\u2060' || it == '\uFEFF' || it == '\u00AD' || it == '\u180E' }
        if (zw.isNotEmpty()) {
            c.add(Severity.HIGH, Category.OBFUSCATION, "Invisible characters",
                "Contains ${zw.length} zero-width/invisible character(s). Used to make a malicious link look identical to a real one or to slip past filters.",
                Text.evidence(s))
        }
        val ctrl = s.count { it.code < 0x20 && it != '\t' && it != '\n' && it != '\r' }
        if (ctrl > 0) {
            c.add(Severity.MEDIUM, Category.OBFUSCATION, "Control characters",
                "Contains $ctrl raw control character(s), which have no place in a normal link.")
        }
    }

    // ================================================================================== scheme

    private fun checkHierarchicalScheme(p: ParsedUrl, c: Ctx) {
        if (p.schemeAssumed) {
            c.add(Severity.INFO, Category.SCHEME, "No scheme given", "Analyzed as https://.")
        }
        when (p.scheme) {
            "https", "wss" -> {}
            "http" -> c.add(Severity.LOW, Category.SCHEME, "Not encrypted (http)",
                "Anything you type on this page can be read or modified by anyone on the network path — public Wi-Fi, ISP, etc.")
            "ws" -> c.add(Severity.LOW, Category.SCHEME, "Unencrypted WebSocket (ws)", "Traffic is not encrypted.")
            "ftp" -> c.add(Severity.LOW, Category.SCHEME, "FTP link", "Unencrypted, and modern browsers no longer open FTP. Unusual to receive one.")
            "file" -> c.add(Severity.HIGH, Category.SCHEME, "Opens a local or network file (file://)",
                "Points at a file on your device or a network share. On Windows, opening a file:// link to a remote share can leak your login hash.")
            "smb" -> c.add(Severity.HIGH, Category.SCHEME, "Windows network share (smb://)",
                "Connecting to an attacker-controlled share can leak credentials.")
            else -> c.add(Severity.LOW, Category.SCHEME, "Unusual scheme: ${p.scheme}://", "Not a normal web link.")
        }
        if (!p.schemeAssumed) {
            val norm = p.slashes == "//"
            if ('\\' in p.slashes) {
                c.add(Severity.MEDIUM, Category.OBFUSCATION, "Backslashes after the scheme",
                    "Browsers treat '\\' like '/', but many link checkers don't. A trick to sneak past filters.", Text.evidence(p.scheme + ":" + p.slashes))
            } else if (!norm && p.scheme in setOf("http", "https")) {
                c.add(Severity.LOW, Category.OBFUSCATION, "Malformed slashes after ${p.scheme}:",
                    "Expected '//', found '${p.slashes}'. Browsers auto-correct this; filters may not.")
            }
        }
    }

    private fun checkOpaqueScheme(p: ParsedUrl, c: Ctx, depth: Int) {
        val body = p.opaque.orEmpty()
        val decoded = Text.deepDecode(body)
        when (p.scheme) {
            "javascript", "livescript" -> c.add(Severity.CRITICAL, Category.SCHEME, "JavaScript link",
                "Opening this runs code in whatever page you're on. Real links are never javascript:.", Text.evidence(decoded))
            "vbscript" -> c.add(Severity.CRITICAL, Category.SCHEME, "VBScript link", "Runs script code. Only ever used for attacks.", Text.evidence(decoded))
            "data" -> {
                val mime = decoded.substringBefore(',').substringBefore(';').lowercase().trim()
                when {
                    mime.contains("html") || mime.contains("xml") && !mime.contains("svg") ->
                        c.add(Severity.CRITICAL, Category.SCHEME, "Embedded web page (data:$mime)",
                            "The whole page is packed inside the link, so it has no real web address. Classic way to show a fake login page that no filter can block.")
                    mime.contains("svg") -> c.add(Severity.HIGH, Category.SCHEME, "Embedded SVG (data:image/svg+xml)",
                        "SVG images can contain scripts and links.")
                    mime.startsWith("application") || mime.contains("javascript") ->
                        c.add(Severity.HIGH, Category.SCHEME, "Embedded file (data:$mime)", "A file packed inside the link — opening it can trigger a download.")
                    else -> c.add(Severity.MEDIUM, Category.SCHEME, "Data link (data:${mime.ifEmpty { "text/plain" }})",
                        "Content is embedded inside the link rather than hosted anywhere.")
                }
                val payload = decoded.substringAfter(',', "")
                val b64 = if (decoded.substringBefore(',').contains("base64", true)) Text.tryBase64(payload.trim()) else null
                scanInjection((b64 ?: payload).take(20_000), c)
            }
            "intent" -> checkIntent(body, c, depth)
            "tel", "callto" -> {
                val num = decoded.substringBefore('?')
                if (num.contains('*') || num.contains('#')) {
                    c.add(Severity.HIGH, Category.SCHEME, "Dials a USSD / MMI code",
                        "Codes with * or # talk to your carrier or phone firmware — they can set up call forwarding or change settings. Don't dial unless you know exactly what it does.",
                        Text.evidence(num))
                } else {
                    c.add(Severity.INFO, Category.SCHEME, "Phone number", "Opens the dialer with $num.")
                }
            }
            "sms", "smsto", "mms", "mmsto" -> {
                val num = decoded.substringBefore('?').substringBefore(';').replace(Regex("[^0-9+]"), "")
                if (num.length in 4..6) {
                    c.add(Severity.MEDIUM, Category.SCHEME, "Texts a short code ($num)",
                        "Short codes can be premium-rate or subscribe you to paid services.")
                } else {
                    c.add(Severity.INFO, Category.SCHEME, "Composes a text message", "Opens your messaging app addressed to ${num.ifEmpty { "(none)" }}.")
                }
            }
            "mailto" -> c.add(Severity.INFO, Category.SCHEME, "Email link", "Opens a new email to ${Text.evidence(decoded.substringBefore('?'), 60)}.")
            "market" -> c.add(Severity.INFO, Category.SCHEME, "Play Store link",
                "Opens the Play Store${Regex("id=([\\w.]+)").find(decoded)?.let { " to " + it.groupValues[1] } ?: ""}. Check the developer before installing.")
            "content" -> c.add(Severity.MEDIUM, Category.SCHEME, "Android content:// URI",
                "Points at data exposed by an app on your phone. Odd thing to receive as a link.")
            "blob", "filesystem" -> c.add(Severity.MEDIUM, Category.SCHEME, "Browser-internal URL (${p.scheme}:)",
                "Only meaningful inside the web page that created it. Phishing kits use these to hide the real page source.")
            "about", "chrome", "chrome-extension", "edge", "view-source", "opera", "brave" -> c.add(Severity.MEDIUM, Category.SCHEME,
                "Browser-internal page (${p.scheme}:)", "Not a website. Links to browser settings/extensions are a social-engineering red flag.")
            "ms-msdt" -> c.add(Severity.CRITICAL, Category.SCHEME, "ms-msdt: link (Follina)",
                "This Windows handler was exploited for remote code execution (CVE-2022-30190).")
            "search-ms", "search" -> c.add(Severity.HIGH, Category.SCHEME, "Windows search link",
                "Abused to show attacker-hosted files in Explorer so they look local.")
            "ms-officecmd", "ms-excel", "ms-word", "ms-powerpoint", "ms-access", "ms-visio", "ms-project", "ms-publisher", "ms-infopath", "ms-spd" ->
                c.add(Severity.HIGH, Category.SCHEME, "Opens an Office app directly (${p.scheme}:)",
                    "Office protocol handlers have been abused to open remote documents with macros or exploits.")
            "jar", "phar", "php", "expect", "gopher", "dict", "ldap", "rmi", "jndi", "netdoc" ->
                c.add(Severity.HIGH, Category.SCHEME, "Exploit-associated scheme (${p.scheme}:)",
                    "This scheme shows up mostly in server-side exploit payloads (SSRF / deserialization / LFI).")
            else -> c.add(Severity.LOW, Category.SCHEME, "Opens an app (${p.scheme}:)",
                "Custom scheme that hands the link to whatever app registered '${p.scheme}'. Make sure you expected that app to open.")
        }
        // embedded web links inside opaque schemes (e.g. data:text/html with a form action)
        if (depth < 2 && p.scheme !in setOf("intent")) {
            for (m in EMBEDDED_URL.findAll(decoded).take(2)) {
                val target = m.value.trimEnd('"', '\'', ')', ';', '>')
                nestAnalyze(target, depth, c, "Contains a link to")
            }
        }
    }

    private fun checkIntent(body: String, c: Ctx, depth: Int) {
        val d = Text.pctDecode(body)
        val extras = d.substringAfter("#Intent;", "").substringBefore(";end").split(';')
        fun extra(key: String) = extras.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')
        val pkg = extra("package") ?: extra("component")?.substringBefore('/')
        val action = extra("action")
        val scheme = extra("scheme")
        val fallback = extra("S.browser_fallback_url")?.let { Text.pctDecode(it) }
        c.add(Severity.MEDIUM, Category.SCHEME, "Launches an Android app${pkg?.let { " ($it)" } ?: ""}",
            "intent:// links open apps directly from the browser" +
                (action?.let { ", here with action '$it'" } ?: "") + (scheme?.let { " and scheme '$it'" } ?: "") + ".")
        if (action != null && DANGEROUS_INTENT_ACTIONS.any { action.contains(it, ignoreCase = true) }) {
            c.add(Severity.HIGH, Category.SCHEME, "Sensitive Android action", "The intent asks for '$action', which can place calls, install apps, or change settings.")
        }
        if (fallback != null && depth < 2) nestAnalyze(fallback, depth, c, "Fallback page is")
    }

    // ==================================================================================== host

    private data class HostInfo(val ascii: String?, val unicode: String?, val registrable: String?, val suffix: String?)

    private fun analyzeHost(p: ParsedUrl, c: Ctx): HostInfo {
        var raw = p.rawHost
        if (raw.isEmpty()) {
            if (p.scheme in setOf("http", "https")) {
                c.add(Severity.MEDIUM, Category.HOST, "No host name", "The link has no destination host — malformed or deliberately broken.")
            }
            return HostInfo(null, null, null, null)
        }
        if ('%' in raw) {
            c.add(Severity.MEDIUM, Category.OBFUSCATION, "Percent-encoded host name",
                "The domain itself is URL-encoded ('${Text.evidence(raw, 40)}'). Normal links never do this; it hides the domain from people and filters.")
            raw = Text.pctDecode(raw)
        }
        val fullwidth = raw.any { it in '\uFF01'..'\uFF5E' || it == '\u3002' || it == '\uFF61' }
        val host = raw.lowercase().trimEnd('.', '。', '．', '｡')

        // IPv6 literal
        if (host.startsWith("[")) {
            val inner = host.trim('[', ']').substringBefore('%')
            c.add(Severity.MEDIUM, Category.HOST, "Raw IPv6 address", "No domain name — legit services almost never send links to bare IP addresses.", inner)
            val mapped = Regex("^::ffff:(\\d+\\.\\d+\\.\\d+\\.\\d+)$").find(inner)?.groupValues?.get(1)
            val isLocal = inner == "::1" || inner == "::" || inner.startsWith("fe8") || inner.startsWith("fe9") ||
                inner.startsWith("fea") || inner.startsWith("feb") || inner.startsWith("fc") || inner.startsWith("fd") ||
                (mapped != null && parseIpv4(mapped)?.let { isPrivateV4(it.first) } == true)
            if (isLocal) localTarget(c, inner)
            return HostInfo(host, null, null, null)
        }

        // IPv4 (including decimal / hex / octal disguises)
        val v4 = parseIpv4(host)
        if (v4 != null) {
            val (value, nonStandard) = v4
            val dotted = "${value shr 24 and 255}.${value shr 16 and 255}.${value shr 8 and 255}.${value and 255}"
            if (nonStandard) {
                c.add(Severity.HIGH, Category.OBFUSCATION, "Disguised IP address",
                    "'$host' is an IP address written in decimal/hex/octal form to look like something else. It really points to $dotted.", host)
            }
            c.add(Severity.MEDIUM, Category.HOST, "Raw IP address ($dotted)",
                "No domain name. Legit services almost never send links to bare IP addresses; it's common for malware hosts and phishing kits.")
            if (dotted == "169.254.169.254") {
                c.add(Severity.HIGH, Category.HOST, "Cloud metadata address", "169.254.169.254 is the cloud instance metadata endpoint — a classic SSRF target for stealing server credentials.")
            } else if (isPrivateV4(value)) localTarget(c, dotted)
            return HostInfo(dotted, null, null, null)
        }

        // Domain name
        val ascii = try {
            IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).lowercase()
        } catch (e: Exception) {
            c.add(Severity.MEDIUM, Category.HOST, "Invalid host name", "The host isn't a valid domain name (${e.message ?: "IDN error"}).", Text.evidence(host, 60))
            return HostInfo(host, null, null, null)
        }
        val unicode = runCatching { IDN.toUnicode(ascii, IDN.ALLOW_UNASSIGNED) }.getOrDefault(ascii)
        if (fullwidth) {
            c.add(Severity.LOW, Category.OBFUSCATION, "Full-width characters in domain",
                "Written with full-width letters that browsers silently convert to '$ascii'. Used to dodge filters.")
        }
        if (!Regex("^[a-z0-9.-]+$").matches(ascii)) {
            c.add(Severity.LOW, Category.HOST, "Unusual characters in host", "Host contains characters not allowed in normal domain names.", Text.evidence(ascii, 60))
        }
        if (ascii.split('.').any { it.startsWith("xn--") } && unicode == ascii) {
            c.add(Severity.MEDIUM, Category.HOST, "Broken punycode", "Contains an 'xn--' label that doesn't decode properly.", ascii)
        }

        // Local-only names
        when {
            ascii == "localhost" || ascii.endsWith(".localhost") -> {
                c.add(Severity.HIGH, Category.HOST, "Points at your own device (localhost)",
                    "A web page or app sending you to localhost may be trying to reach a service running on your phone.")
                return HostInfo(ascii, unicode, null, null)
            }
            !ascii.contains('.') -> {
                c.add(Severity.MEDIUM, Category.HOST, "Single-word host name ('$ascii')",
                    "Resolves only on a local/corporate network. Targets something on the network you're connected to.")
                return HostInfo(ascii, unicode, null, null)
            }
            LOCAL_SUFFIXES.any { ascii.endsWith(it) } -> {
                localTarget(c, ascii)
            }
            ascii.endsWith(".onion") -> c.add(Severity.MEDIUM, Category.HOST, "Tor hidden service (.onion)",
                "Only reachable through Tor. Anonymous hosting is popular with scams and malware.")
        }

        val match = psl.publicSuffix(ascii)
        val suffix = match.suffix
        val registrable = psl.registrableDomain(ascii)
        val tld = ascii.substringAfterLast('.')

        if (!psl.isKnownTld(tld)) {
            c.add(Severity.MEDIUM, Category.DOMAIN, "Unknown top-level domain (.$tld)",
                "'.$tld' isn't a real public TLD, so this won't resolve on the normal internet. Typo, internal name, or a filename pretending to be a link.")
        }

        if (unicode != ascii) analyzeIdn(ascii, unicode, c)

        if (registrable != null) {
            analyzeDomainShape(ascii, registrable, suffix, tld, c)
            checkHosting(ascii, registrable, suffix, match.isPrivate, c)
            checkImpersonation(ascii, registrable, suffix, c, viaLookalike = false)
        }
        return HostInfo(ascii, unicode, registrable, suffix)
    }

    private fun localTarget(c: Ctx, what: String) {
        c.add(Severity.HIGH, Category.HOST, "Targets your local network",
            "$what is a private/local address — your router, a printer, or other devices on your Wi-Fi. Links like this are used to attack routers (change DNS settings, etc.) from your browser.")
    }

    private fun analyzeIdn(ascii: String, unicode: String, c: Ctx) {
        c.add(Severity.LOW, Category.HOST, "International (Unicode) domain",
            "Displays as '$unicode' but its real name is '$ascii'. Legit for non-English sites; also the basis of lookalike attacks.")

        // Mixed scripts within a single label = textbook homograph.
        for (label in unicode.split('.')) {
            val scripts = label.codePoints().toArray()
                .filter { Character.isLetter(it) }
                .map { Character.UnicodeScript.of(it) }
                .filter { it != Character.UnicodeScript.COMMON && it != Character.UnicodeScript.INHERITED }
                .toSet()
            if (scripts.size > 1 && !ALLOWED_SCRIPT_MIXES.any { it.containsAll(scripts) }) {
                c.add(Severity.HIGH, Category.IMPERSONATION, "Mixed alphabets in one word",
                    "'$label' mixes ${scripts.joinToString(" + ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }} letters. Real words don't do that; lookalike phishing domains do.",
                    label)
                break
            }
        }

        // Lookalike skeleton (e.g. Cyrillic "аррӏе.com" → "apple.com")
        val usesForeignConfusables = unicode.any { ch ->
            ch in Data.CONFUSABLES && Character.UnicodeScript.of(ch.code) != Character.UnicodeScript.LATIN
        }
        val skel = skeleton(unicode)
        if (skel != ascii && Regex("^[a-z0-9.-]+$").matches(skel)) {
            val skelReg = psl.registrableDomain(skel)
            val official = skelReg != null && skelReg in Data.ALL_OFFICIAL
            when {
                official -> c.add(Severity.CRITICAL, Category.IMPERSONATION, "Lookalike of $skelReg",
                    "Uses look-alike foreign or accented characters to imitate $skelReg. Real address: $ascii.", "$unicode → $skel")
                usesForeignConfusables -> c.add(Severity.HIGH, Category.IMPERSONATION, "Disguised as '$skel'",
                    "Letters from another alphabet are used to look like the plain domain '$skel'. Real address: $ascii.", "$unicode → $skel")
            }
            if (!official && skelReg != null) {
                // Run brand checks on what the user *sees*.
                checkImpersonation(skel, skelReg, psl.publicSuffix(skel).suffix, c, viaLookalike = true)
            }
        }
    }

    private fun analyzeDomainShape(ascii: String, registrable: String, suffix: String, tld: String, c: Ctx) {
        when (tld) {
            in Data.FILENAME_TLDS -> c.add(Severity.MEDIUM, Category.DOMAIN, ".$tld domain looks like a filename",
                "'$registrable' looks like ${Data.FILENAME_TLDS[tld]} but it's a website. Used to trick people into thinking they're opening a file.")
            in Data.VERY_ABUSED_TLDS -> c.add(Severity.MEDIUM, Category.DOMAIN, "High-abuse TLD (.$tld)",
                "Cheap or free domains under .$tld are disproportionately used for phishing and malware.")
            in Data.ABUSED_TLDS -> c.add(Severity.LOW, Category.DOMAIN, "Often-abused TLD (.$tld)",
                ".$tld has an above-average share of malicious registrations. Fine on its own; adds up with other signals.")
        }

        val label = runCatching { IDN.toUnicode(registrable.removeSuffix(".$suffix"), IDN.ALLOW_UNASSIGNED) }
            .getOrDefault(registrable.removeSuffix(".$suffix"))
        val subs = ascii.removeSuffix(registrable).trimEnd('.').split('.').filter { it.isNotEmpty() }
        if (subs.size >= 4) {
            c.add(Severity.LOW, Category.DOMAIN, "Many subdomains (${subs.size})",
                "Deeply nested hosts push the real domain ($registrable) to the far right, where people don't look.")
        }
        if (ascii.length > 60) {
            c.add(Severity.LOW, Category.DOMAIN, "Very long host name (${ascii.length} chars)", "Long hosts are hard to read and often hide the real domain.")
        }
        val hyphens = label.count { it == '-' }
        if (hyphens >= 3) {
            c.add(Severity.LOW, Category.DOMAIN, "Lots of hyphens in domain", "'$label' has $hyphens hyphens — typical of throwaway phishing domains like secure-account-verify-login.")
        }
        val core = label.replace("-", "")
        if (core.length >= 12 && core.all { it.code < 128 }) {
            val ent = Text.shannonEntropy(core)
            val vowels = core.count { it in "aeiou" }.toDouble() / core.length
            val digits = core.count { it.isDigit() }.toDouble() / core.length
            if ((ent > 3.6 && vowels < 0.25) || (digits > 0.3 && core.any { it.isLetter() })) {
                c.add(Severity.LOW, Category.DOMAIN, "Random-looking domain",
                    "'$label' looks machine-generated. Malware and throwaway phishing campaigns often use generated names.")
            }
        }
    }

    private fun checkHosting(ascii: String, registrable: String, suffix: String, privateSuffix: Boolean, c: Ctx) {
        fun inSet(set: Set<String>) = set.firstOrNull { ascii == it || ascii.endsWith(".$it") }
        val shortener = inSet(Data.SHORTENERS)
        if (shortener != null) {
            val official = shortener in Data.ALL_OFFICIAL
            if (official) {
                c.add(Severity.INFO, Category.REDIRECT, "Official short link ($shortener)", "Brand-owned link shortener. Destination is hidden but should stay on the brand's sites.")
            } else {
                c.add(Severity.MEDIUM, Category.REDIRECT, "Shortened link ($shortener)",
                    "The real destination is hidden. Sift never visits links, so it can't see where this goes — treat it as unknown. Many shorteners have a preview page (e.g. add '+' to a bit.ly link).")
            }
        }
        inSet(Data.TUNNEL_DDNS)?.let {
            c.add(Severity.MEDIUM, Category.DOMAIN, "Tunnel / dynamic-DNS host ($it)",
                "Points at someone's personal machine through $it. Popular for short-lived phishing and malware command servers.")
        }
        val free = inSet(Data.FREE_HOSTING)
        if (free != null) {
            c.add(Severity.LOW, Category.DOMAIN, "Free hosting platform ($free)",
                "Anyone can publish a page here in minutes. The platform is legit — the page may not be. Judge by '${registrable}', not '$free'.")
        } else if (privateSuffix && shortener == null && inSet(Data.TUNNEL_DDNS) == null) {
            c.add(Severity.INFO, Category.DOMAIN, "Shared platform ($suffix)",
                "Sites under $suffix belong to individual users, not to the platform itself.")
        }
        inSet(Data.FORM_HOSTS)?.let {
            c.add(Severity.LOW, Category.DOMAIN, "Online form ($it)", "Fine for surveys. Never enter passwords, codes, or card numbers into a form like this.")
        }
        if (ascii == "docs.google.com" || ascii == "sites.google.com" || ascii == "drive.google.com") {
            c.add(Severity.INFO, Category.DOMAIN, "User content on Google", "Anyone can share docs/sites/forms from Google. The domain is real; the content is from whoever made it.")
        }
    }

    // ========================================================================== impersonation

    private fun checkImpersonation(host: String, registrable: String, suffix: String, c: Ctx, viaLookalike: Boolean) {
        if (registrable in Data.ALL_OFFICIAL) return
        val label = registrable.removeSuffix(".$suffix")
        val subs = host.removeSuffix(registrable).trimEnd('.').split('.').filter { it.isNotEmpty() }
        val where = if (viaLookalike) " (as displayed)" else ""
        var reported = 0
        // Brands sharing official domains (Apple / Apple ID / iCloud) are reported once.
        val covered = HashSet<String>()
        fun claim(b: Brand): Boolean {
            if (b.official.any { it in covered }) return false
            covered += b.official
            return true
        }

        // paypal.com.secure-login.net / amazon.com-verify.xyz
        for (d in Data.ALL_OFFICIAL) {
            if (d.length < 6 || !d.contains('.')) continue
            val embedded = host.startsWith("$d.") || host.contains(".$d.") || host.contains("$d-") || host.startsWith("${d}_")
            if (embedded && !host.endsWith(".$d") && host != d) {
                Data.BRANDS.filter { d in it.official }.forEach { covered += it.official }
                val dotted = host.startsWith("$d.") || host.contains(".$d.")
                c.add(if (dotted) Severity.CRITICAL else Severity.HIGH, Category.IMPERSONATION, "Fake '$d' prefix$where",
                    "Starts with '$d' so it looks legit at a glance, but the real domain is $registrable — not $d.", host)
                reported++
                break
            }
        }

        for (brand in Data.BRANDS) {
            if (reported >= 3) break
            if (isOfficial(brand, registrable, label, suffix)) continue
            if (brand.official.any { it in covered }) continue
            val k = brand.keyword
            val name = brand.display
            when {
                label == k || label.replace("-", "") == k -> {
                    claim(brand)
                    c.add(if (brand.commonWord) Severity.MEDIUM else Severity.HIGH, Category.IMPERSONATION, "$name name on a non-$name domain$where",
                        "$registrable uses the name '$k' but isn't one of $name's domains (${brand.official.take(3).joinToString()}).", registrable)
                    reported++; continue
                }
                labelContainsBrand(label, k) -> {
                    claim(brand)
                    c.add(Severity.HIGH, Category.IMPERSONATION, "Uses $name's name$where",
                        "'$registrable' combines '$k' with other words. $name doesn't own it — classic phishing domain pattern.", registrable)
                    reported++; continue
                }
                isVisualTypo(label, k) || label.split('-').any { it != label && isVisualTypo(it, k) } -> {
                    claim(brand)
                    c.add(Severity.HIGH, Category.IMPERSONATION, "Typo of $name$where",
                        "'$label' is one look-alike character away from '$k'. Typosquatted domain.", registrable)
                    reported++; continue
                }
            }
            val sub = subs.firstOrNull {
                (it == k && !brand.commonWord) || labelContainsBrand(it, k) || (k.length >= 5 && it != k && it.split('-').contains(k))
            }
            if (sub != null && claim(brand)) {
                c.add(Severity.HIGH, Category.IMPERSONATION, "$name in subdomain of unrelated site$where",
                    "'$sub' is just a label on $registrable, which has nothing to do with $name.", host)
                reported++
            }
        }
    }

    private fun isOfficial(b: Brand, registrable: String, label: String, suffix: String): Boolean =
        registrable in b.official || (b.anyCountryTld && label == b.keyword && suffix.substringAfterLast('.').length == 2)

    private fun labelContainsBrand(label: String, k: String): Boolean {
        val tokens = label.split('-', '_').filter { it.isNotEmpty() }
        if (tokens.size > 1 && k in tokens) return true
        if (k.length < 5) return false
        val candidates = (tokens + label.replace("-", "")).toSet()
        for (tok in candidates) {
            var idx = tok.indexOf(k)
            while (idx >= 0) {
                val pre = tok.substring(0, idx)
                val post = tok.substring(idx + k.length)
                if ((pre.isNotEmpty() || post.isNotEmpty()) && isFiller(pre) && isFiller(post)) return true
                idx = tok.indexOf(k, idx + 1)
            }
        }
        return false
    }

    /** True if [s] can be split entirely into phishing filler words and/or digits. */
    private fun isFiller(s: String): Boolean {
        if (s.isEmpty()) return true
        val ok = BooleanArray(s.length + 1).also { it[0] = true }
        for (i in 1..s.length) {
            for (j in 0 until i) {
                if (!ok[j]) continue
                val w = s.substring(j, i)
                if (w in Data.PHISH_WORDS || w.all { it.isDigit() }) { ok[i] = true; break }
            }
        }
        return ok[s.length]
    }

    private fun normalizeTypo(s: String, one: Char): String = s.lowercase()
        .replace("rn", "m").replace("vv", "w").replace("-", "")
        .replace('0', 'o').replace('1', one).replace('3', 'e').replace('4', 'a').replace('5', 's')
        .replace('7', 't').replace('8', 'b').replace('@', 'a').replace('$', 's').replace('|', 'l')

    private fun isVisualTypo(label: String, k: String): Boolean {
        if (k.length < 5 || label == k || label.length < 4) return false
        if (normalizeTypo(label, 'l') == k || normalizeTypo(label, 'i') == k) return true
        val a = normalizeTypo(label, 'l')
        if (kotlin.math.abs(a.length - k.length) > 1) return false
        if (a.length == k.length) {
            val diffs = a.indices.filter { a[it] != k[it] }
            if (diffs.size == 1) return similar(a[diffs[0]], k[diffs[0]])
            if (diffs.size == 2 && diffs[1] == diffs[0] + 1 && a[diffs[0]] == k[diffs[1]] && a[diffs[1]] == k[diffs[0]]) return true
            return false
        }
        val (long, short) = if (a.length > k.length) a to k else k to a
        val i = long.indices.firstOrNull { it >= short.length || long[it] != short[it] } ?: return false
        if (long.removeRange(i, i + 1) != short) return false
        return (i > 0 && long[i] == long[i - 1]) || (i + 1 < long.length && long[i] == long[i + 1])
    }

    private fun similar(x: Char, y: Char): Boolean = "$x$y" in SIMILAR_PAIRS || "$y$x" in SIMILAR_PAIRS

    private fun skeleton(s: String): String {
        val nfd = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        for (ch in nfd) {
            if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
            sb.append(Data.CONFUSABLES[ch] ?: ch)
        }
        return sb.toString()
    }

    // ============================================================================== authority

    private fun checkPort(p: ParsedUrl, c: Ctx): Int? {
        val raw = p.port ?: return null
        val n = raw.toIntOrNull()
        if (n == null || n !in 0..65535) {
            c.add(Severity.MEDIUM, Category.STRUCTURE, "Invalid port", "'$raw' isn't a valid port number.")
            return null
        }
        val default = (p.scheme == "http" && n == 80) || (p.scheme == "https" && n == 443)
        if (!default) {
            if (n in SUSPICIOUS_PORTS) {
                c.add(Severity.MEDIUM, Category.STRUCTURE, "Suspicious port ($n)", "Port $n is commonly used by hacking tools, backdoors, or IRC botnets.")
            } else {
                c.add(Severity.LOW, Category.STRUCTURE, "Non-standard port ($n)", "Normal websites use 443. Odd ports point to test servers, admin panels, or self-hosted kits.")
            }
        }
        return n
    }

    private fun checkUserInfo(p: ParsedUrl, host: String?, c: Ctx) {
        val ui = p.userInfo ?: return
        val decoded = Text.deepDecode(ui)
        val fakeHost = Regex("(?i)([a-z0-9-]+\\.)+[a-z]{2,}").find(decoded)?.value
        if (fakeHost != null) {
            c.add(Severity.CRITICAL, Category.OBFUSCATION, "Fake destination before '@'",
                "Everything before '@' is a username, not the address. It looks like you're going to $fakeHost, but you'd actually land on ${host ?: "a different host"}.",
                Text.evidence(decoded + "@" + (host ?: "")))
        } else {
            c.add(Severity.HIGH, Category.OBFUSCATION, "Username/password embedded in link",
                "The '…@' part is ignored by most browsers and is used to bury the real host further to the right.", Text.evidence(decoded))
        }
    }

    // ============================================================================ path/query

    private fun checkPathAndQuery(p: ParsedUrl, host: String?, registrable: String?, c: Ctx) {
        val raw = p.path + (p.query?.let { "?$it" } ?: "") + (p.fragment?.let { "#$it" } ?: "")
        if (raw.isEmpty()) return
        val d1 = Text.pctDecode(raw)
        val d2 = Text.deepDecode(d1, plusIsSpace = true)

        if (DOUBLE_ENC.containsMatchIn(raw) && d2 != d1) {
            c.add(Severity.MEDIUM, Category.OBFUSCATION, "Double-encoded characters",
                "Text is URL-encoded twice (%25xx). A technique for slipping payloads past filters that only decode once.", Text.evidence(raw.take(80)))
        }
        val pointless = POINTLESS_ENC.findAll(raw).count()
        if (pointless >= 6) {
            c.add(Severity.MEDIUM, Category.OBFUSCATION, "Encodes ordinary letters",
                "$pointless plain letters/digits are percent-encoded for no reason except to hide what the link says.")
        }
        if ("%00" in raw.lowercase()) {
            c.add(Severity.MEDIUM, Category.INJECTION, "Null byte (%00)", "Used to truncate file names in path-traversal and upload exploits.")
        }

        val hay = Text.htmlDecode(d2)
        scanInjection(hay, c)

        // Email addresses / personalization
        val email = EMAIL.find(Text.pctDecode(p.query.orEmpty(), true) + " " + Text.pctDecode(p.fragment.orEmpty()))?.value
        if (email != null) {
            val inFragment = p.fragment?.let { EMAIL.containsMatchIn(Text.pctDecode(it)) } == true
            c.add(if (inFragment) Severity.MEDIUM else Severity.LOW, Category.PRIVACY, "Contains an email address",
                "Includes ${Text.maskEmail(email)}. Personalized links pre-fill fake login pages and confirm your address is live." +
                    if (inFragment) " Putting it after '#' keeps it out of server logs — a phishing-kit habit." else "")
        } else {
            for ((_, v) in UrlParser.queryParams(p.query) + listOfNotNull(p.fragment?.let { "#" to it })) {
                val b = Text.tryBase64(v) ?: continue
                val e = EMAIL.find(b)?.value ?: continue
                c.add(Severity.MEDIUM, Category.PRIVACY, "Hidden email address",
                    "A base64-encoded email (${Text.maskEmail(e)}) is tucked into the link. Typical of targeted phishing — the page greets you by name to look legit.")
                break
            }
        }

        val tracking = UrlParser.queryParams(p.query).map { it.first }.filter { k -> TRACKING.any { t -> if (t.endsWith("*")) k.startsWith(t.dropLast(1)) else k == t } }
        if (tracking.isNotEmpty()) {
            c.add(Severity.INFO, Category.PRIVACY, "Tracking parameters",
                "${tracking.distinct().take(6).joinToString()} — used to track who clicked. Use 'Copy clean link' to strip them.")
        }

        if (p.path.contains("/ipfs/") || p.path.contains("/ipns/")) {
            c.add(Severity.MEDIUM, Category.DOMAIN, "IPFS content",
                "Served from IPFS, where content can't be taken down. Phishing kits moved here for exactly that reason.")
        }

        // Brand mentioned in path on an unrelated site
        if (registrable != null && registrable !in Data.ALL_OFFICIAL) {
            val pathLower = Text.deepDecode(p.path).lowercase()
            val segs = pathLower.split('/', '-', '_', '.').filter { it.isNotEmpty() }.toSet()
            val brand = Data.BRANDS.firstOrNull { b -> (b.keyword.length >= 5 && pathLower.contains(b.keyword)) || b.keyword in segs }
            if (brand != null && segs.any { it in Data.CREDENTIAL_WORDS }) {
                c.add(Severity.MEDIUM, Category.IMPERSONATION, "${brand.display} login page on another site",
                    "The path mentions ${brand.display} and a login/verify word, but the site is $registrable.", Text.evidence(p.path, 80))
            }
        }
    }

    private fun scanInjection(hay: String, c: Ctx) {
        if (hay.isBlank()) return
        val lower = hay.lowercase()
        for (rule in INJECTION_RULES) {
            val m = rule.regex.find(lower) ?: continue
            c.add(rule.severity, Category.INJECTION, rule.title, rule.detail, Text.snippetAround(hay, m.range))
        }
    }

    private fun checkDownload(p: ParsedUrl, registrable: String?, c: Ctx) {
        val last = Text.deepDecode(p.path).substringAfterLast('/').substringAfterLast('\\').lowercase().trimEnd('.', ' ')
        val candidates = mutableListOf(last)
        for ((k, v) in UrlParser.queryParams(p.query)) {
            if (k in setOf("file", "filename", "name", "download", "attachment", "f", "fn")) candidates += v.lowercase().substringAfterLast('/')
        }
        for (name in candidates) {
            if (!name.contains('.')) continue
            val parts = name.split('.')
            val ext = parts.last()
            val (sev, why) = Data.DANGEROUS_EXTENSIONS[ext] ?: continue
            if (parts.size >= 3 && parts[parts.size - 2] in Data.DECOY_EXTENSIONS && sev >= Severity.MEDIUM) {
                c.add(Severity.CRITICAL, Category.DOWNLOAD, "Double file extension",
                    "'$name' is dressed up as a .${parts[parts.size - 2]} but is actually a .$ext file. $why", name)
                return
            }
            val trustedApk = ext in setOf("apk", "xapk", "apks", "apkm") && registrable in TRUSTED_APK_SOURCES
            c.add(if (trustedApk) Severity.MEDIUM else sev, Category.DOWNLOAD, "Downloads a .$ext file", why, name)
            return
        }
    }

    private fun checkRedirects(p: ParsedUrl, host: String?, registrable: String?, c: Ctx, depth: Int) {
        val targets = LinkedHashSet<String>()
        for ((k, v) in UrlParser.queryParams(p.query)) {
            val vv = Text.deepDecode(v).trim()
            val looksUrl = vv.matches(Regex("(?is)^(https?:|ftp:|javascript:|data:|vbscript:|intent:)?[/\\\\]{2}.+")) ||
                vv.matches(Regex("(?is)^(javascript|data|vbscript|intent):.+"))
            when {
                looksUrl && (k in Data.REDIRECT_PARAMS || vv.startsWith("http", true) || vv.startsWith("javascript", true)) -> targets += vv
                else -> Text.tryBase64(v)?.trim()?.takeIf { it.matches(Regex("(?i)^https?://\\S+$")) }?.let { targets += it }
            }
        }
        // URL glued into the path: https://evil.example/https://paypal.com/...
        EMBEDDED_URL.find(Text.deepDecode(p.path).drop(1))?.let { targets += it.value }
        p.fragment?.let { f -> EMBEDDED_URL.find(Text.deepDecode(f))?.let { targets += it.value } }

        val viaKnown = host != null && Data.KNOWN_REDIRECTORS.any { (h, path) -> (host == h || host.endsWith(".$h")) && p.path.startsWith(path) }
        for (t in targets.take(3)) {
            nestAnalyze(t, depth, c, if (viaKnown) "Unwrapped redirect to" else "Redirects to", registrable)
        }
    }

    private fun nestAnalyze(target: String, depth: Int, c: Ctx, verb: String, parentRegistrable: String? = null) {
        val r = analyze(target, depth + 1)
        if (parentRegistrable != null && r.registrableDomain == parentRegistrable && r.verdict == Verdict.LOOKS_CLEAN) return
        c.nested += r
        val dest = r.displayHost ?: r.scheme ?: "?"
        val sev = when (r.verdict) {
            Verdict.DANGEROUS -> Severity.CRITICAL
            Verdict.SUSPICIOUS -> Severity.HIGH
            Verdict.CAUTION -> Severity.MEDIUM
            Verdict.LOOKS_CLEAN -> Severity.LOW
        }
        val openRedirect = parentRegistrable != null && parentRegistrable in Data.ALL_OFFICIAL && r.registrableDomain !in Data.ALL_OFFICIAL
        val finalSev = when {
            openRedirect && sev < Severity.MEDIUM -> Severity.MEDIUM
            r.verdict == Verdict.LOOKS_CLEAN && r.registrableDomain in Data.ALL_OFFICIAL -> Severity.INFO
            else -> sev
        }
        c.add(finalSev, Category.REDIRECT, "$verb $dest",
            (if (openRedirect) "Uses a trusted domain ($parentRegistrable) to bounce you somewhere else — a common way to get phishing links past filters. " else "") +
                "The destination was analyzed too: ${r.verdict.label.lowercase()}" +
                (r.findings.firstOrNull { it.severity >= Severity.MEDIUM }?.let { " (${it.title})" } ?: "") + ".",
            Text.evidence(target, 100))
    }

    private fun checkCredentialWords(p: ParsedUrl, host: String?, registrable: String?, c: Ctx) {
        if (host == null || registrable in Data.ALL_OFFICIAL) return
        val words = (host.split('.', '-') + Text.deepDecode(p.path).lowercase().split('/', '.', '-', '_', '?', '=', '&'))
            .filter { it in Data.CREDENTIAL_WORDS }.distinct()
        if (words.isEmpty()) return
        val amplified = c.findings.any {
            it.category in setOf(Category.IMPERSONATION, Category.HOST) && it.severity >= Severity.MEDIUM ||
                it.title.startsWith("Free hosting") || it.title.startsWith("Tunnel") || it.title.contains("TLD")
        }
        c.add(if (amplified) Severity.MEDIUM else Severity.LOW, Category.IMPERSONATION, "Login / verification wording",
            "Mentions ${words.take(4).joinToString()}. " +
                if (amplified) "Combined with the other signals here, this looks like a credential-harvesting page." else "Only enter credentials if you navigated to this site yourself.")
    }

    // ================================================================================ helpers

    /** WHATWG-style lenient IPv4 parse. Returns (value, nonStandardNotation). */
    private fun parseIpv4(h: String): Pair<Long, Boolean>? {
        val parts = h.split('.').let { if (it.size > 1 && it.last().isEmpty()) it.dropLast(1) else it }
        if (parts.isEmpty() || parts.size > 4 || parts.any { it.isEmpty() }) return null
        val nums = parts.map { parseIpPart(it) ?: return null }
        for (i in 0 until nums.size - 1) if (nums[i] > 255) return null
        val lastMax = 1L shl (8 * (5 - nums.size))
        if (nums.last() >= lastMax) return null
        var v = nums.last()
        for (i in 0 until nums.size - 1) v += nums[i] shl (8 * (3 - i))
        val nonStd = parts.size != 4 || parts.any { it.startsWith("0x", true) || (it.length > 1 && it.startsWith("0")) }
        return v to nonStd
    }

    private fun parseIpPart(p: String): Long? {
        if (p.length > 12) return null
        return when {
            p.startsWith("0x", true) -> if (p.length == 2) 0L else p.substring(2).toLongOrNull(16)
            p.length > 1 && p.startsWith("0") -> if (p.all { it in '0'..'7' }) p.toLong(8) else null
            p.all { it.isDigit() } -> p.toLong()
            else -> null
        }
    }

    private fun isPrivateV4(v: Long): Boolean {
        val a = (v shr 24) and 255
        val b = (v shr 16) and 255
        return a == 10L || a == 127L || a == 0L || (a == 169L && b == 254L) || (a == 172L && b in 16L..31L) ||
            (a == 192L && b == 168L) || (a == 100L && b in 64L..127L)
    }

    private class InjectionRule(val severity: Severity, val title: String, val detail: String, pattern: String) {
        val regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    }

    companion object {
        private val HIDDEN = Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF\\u00AD\\u061C\\u180E]")
        private val DOUBLE_ENC = Regex("%25[0-9a-fA-F]{2}")
        private val POINTLESS_ENC = Regex("%(3[0-9]|4[1-9a-fA-F]|5[0-9aA]|6[1-9a-fA-F]|7[0-9aA])")
        private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val EMBEDDED_URL = Regex("(?i)(https?|ftp)://[^\\s\"'<>]+")
        private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal", ".intranet", ".corp", ".home", ".localdomain", ".router", ".gateway")
        private val ALLOWED_SCRIPT_MIXES = listOf(
            setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA),
            setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.HAN, Character.UnicodeScript.BOPOMOFO),
            setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.HAN, Character.UnicodeScript.HANGUL),
        )
        private val SIMILAR_PAIRS = setOf("il", "l1", "i1", "o0", "mn", "uv", "ce", "gq", "ij", "vy", "rn")
        private val SUSPICIOUS_PORTS = setOf(1337, 31337, 4444, 4445, 5555, 6666, 6667, 6668, 6669, 7777, 8888, 9999, 12345, 54321, 1234, 3389, 5900, 23, 21, 2222, 4443)
        private val TRUSTED_APK_SOURCES = setOf("f-droid.org", "github.com", "githubusercontent.com", "gitlab.com", "codeberg.org", "apkmirror.com")
        private val DANGEROUS_INTENT_ACTIONS = listOf("CALL", "INSTALL_PACKAGE", "DELETE", "SETTINGS", "DEVICE_ADMIN", "ACCESSIBILITY", "REQUEST_INSTALL", "VIEW_DOWNLOADS", "SEND_MULTIPLE")
        private val TRACKING = listOf(
            "utm_*", "fbclid", "gclid", "gclsrc", "dclid", "msclkid", "mc_eid", "mc_cid", "_hsenc", "_hsmi", "mkt_tok",
            "igshid", "yclid", "twclid", "ttclid", "li_fat_id", "oly_enc_id", "oly_anon_id", "vero_id", "wickedid",
            "__s", "s_cid", "ef_id", "srsltid", "si", "_ga", "_gl", "rb_clickid", "trk", "trkcampaign", "sc_campaign",
        )
        val TRACKING_PARAMS get() = TRACKING
        private val IP_LOGGERS_ASCII: Set<String> by lazy {
            Data.IP_LOGGERS.mapNotNull { runCatching { IDN.toASCII(it, IDN.ALLOW_UNASSIGNED).lowercase() }.getOrNull() }.toSet()
        }

        private val INJECTION_RULES = listOf(
            InjectionRule(Severity.CRITICAL, "Log4Shell / JNDI payload",
                "A \${jndi:…} string triggers remote code execution on servers running vulnerable Log4j (CVE-2021-44228).",
                "\\$\\{[^}]{0,40}(jndi|j\\}|\\$\\{(lower|upper|env|sys|::-))"),
            InjectionRule(Severity.HIGH, "Cross-site scripting (XSS) payload",
                "Contains HTML/script that would run in the target site's page — used to steal sessions or show fake login prompts on a real domain.",
                "<\\s*/?\\s*(script|iframe|svg|img|body|object|embed|math|details|video|audio|marquee|style|base|form|input|isindex|frameset|link|meta)\\b[^>]*(>|on\\w+\\s*=|src|href)|\\bon(error|load|mouseover|mouseenter|focus|focusin|click|toggle|begin|animationstart|pointerover|wheel|beforeprint|pageshow)\\s*=|(^|[=\"'(\\s])javascript\\s*:|document\\.(cookie|domain|write)|\\b(alert|prompt|confirm)\\s*[(`]|\\beval\\s*\\(|string\\.fromcharcode|srcdoc\\s*=|\\bexpression\\s*\\("),
            InjectionRule(Severity.HIGH, "SQL injection payload",
                "Contains database query fragments aimed at the site's backend.",
                "\\bunion\\b[\\s/*+()]+(all[\\s/*+]+)?(distinct[\\s/*+]+)?select\\b|'\\s*(or|and|\\|\\|)\\s*['\\d(][^&#]{0,20}(=|\\blike\\b|<|>)|\\bor\\s+1\\s*=\\s*1\\b|;\\s*(drop|delete|insert|truncate|alter|shutdown|exec)\\s|\\b(sleep|pg_sleep|benchmark)\\s*\\(\\s*\\d|waitfor\\s+delay|\\binformation_schema\\b|\\bxp_cmdshell\\b|@@version|\\bload_file\\s*\\(|into\\s+(out|dump)file|\\b(extractvalue|updatexml)\\s*\\(|'\\s*(--|#|/\\*)|\\bselect\\s+[\\w*,.\\s()]{1,80}\\s+from\\s+[\\w.]+(\\s+where\\b|\\s*(--|#|;))"),
            InjectionRule(Severity.HIGH, "Command injection payload",
                "Contains shell commands meant to run on a server.",
                "[;|`\\n]\\s*(cat|wget|curl|nc|ncat|netcat|bash|sh|zsh|rm|chmod|python3?|perl|php|ruby|powershell|pwsh|cmd|whoami|uname|ping|nslookup|id|busybox|tftp)\\b(?!\\s*=)|\\$\\((cat|wget|curl|id|whoami|uname|nslookup|ping)\\b|`(id|whoami|uname|cat\\s)|\\bcmd(\\.exe)?\\s*/[ck]\\b|/bin/(ba|z|da)?sh\\b|\\bpowershell(\\.exe)?\\s+-(e|enc|encodedcommand|nop|w)\\b|\\(\\s*\\)\\s*\\{\\s*:\\s*;\\s*\\}"),
            InjectionRule(Severity.HIGH, "Sensitive file requested",
                "Tries to read a well-known secrets/system file from a server.",
                "/etc/(passwd|shadow|hosts|group)\\b|\\bwin\\.ini\\b|\\bboot\\.ini\\b|/proc/self/(environ|cmdline|maps)|\\bweb\\.config\\b|/\\.env\\b|/\\.git/(config|head)|\\bwp-config\\.php\\b|\\.htpasswd\\b|\\bid_rsa\\b|/\\.aws/credentials|/\\.ssh/"),
            InjectionRule(Severity.MEDIUM, "Path traversal",
                "'../' sequences climb out of the intended folder to reach other files on the server.",
                "(\\.\\.[/\\\\])|([/\\\\]\\.\\.([/\\\\]|$))|%c0%ae|%e0%80%ae|\\.\\.%2f|\\.\\.%5c"),
            InjectionRule(Severity.HIGH, "Template / expression injection",
                "Server-side template syntax used to execute code (SSTI / OGNL / EL).",
                "\\{\\{[^}]{0,60}(\\d+\\s*\\*\\s*\\d+|config|__class__|__globals__|__builtins__|self\\.|constructor|request\\.|lipsum|cycler|joiner)[^}]{0,60}\\}\\}|\\$\\{\\s*\\d+\\s*\\*\\s*\\d+\\s*\\}|#\\{\\s*\\d+\\s*\\*\\s*\\d+\\s*\\}|<%=?.{0,60}%>|%\\{.{0,80}(#context|@java|#_memberaccess|ognl|#cmd)|class\\.module\\.classloader|\\bt\\(java\\.lang"),
            InjectionRule(Severity.HIGH, "Cloud metadata / SSRF target",
                "Points a server at the cloud metadata service or exotic protocols to pivot into internal systems (SSRF).",
                "169\\.254\\.169\\.254|metadata\\.google\\.internal|100\\.100\\.100\\.200|\\b(gopher|dict|ldap|tftp|jar|netdoc)://"),
            InjectionRule(Severity.MEDIUM, "Points a server at localhost or local files",
                "A parameter references localhost or file://. Normal for some developer logins (OAuth callbacks), but also how SSRF / local-file attacks start.",
                "[=/:](localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|\\[::1?\\])([:/?&]|$)|\\bfile://"),
            InjectionRule(Severity.HIGH, "PHP wrapper / file-inclusion payload",
                "php://, phar://, expect:// and data:// wrappers are used for local/remote file inclusion attacks.",
                "\\b(php|phar|expect|zip|glob|ogg|rar|zlib|compress\\.zlib)://|\\bdata://text/plain"),
            InjectionRule(Severity.HIGH, "Header / CRLF injection",
                "Line breaks injected to forge HTTP headers (set cookies, redirect, split responses).",
                "[\\r\\n]\\s*(set-cookie|location|content-type|content-length|http/1\\.)"),
            InjectionRule(Severity.MEDIUM, "Prototype pollution",
                "Tries to modify JavaScript object prototypes on the site.",
                "__proto__|constructor\\[prototype\\]|constructor\\.prototype"),
            InjectionRule(Severity.MEDIUM, "NoSQL injection",
                "MongoDB-style operators injected into parameters.",
                "\\[\\$(ne|gt|gte|lt|lte|regex|where|exists|nin|in|or|and)\\]|[?&]\\\$where="),
            InjectionRule(Severity.HIGH, "Serialized object payload",
                "Encoded Java/PHP/.NET serialized objects are used for deserialization exploits.",
                "\\bro0ab[a-z0-9+/=]{10,}|\\bo:\\d+:\"[a-z_\\\\]+\":\\d+:\\{|aced0005|\\\$type\"?\\s*:\\s*\"system\\."),
            InjectionRule(Severity.MEDIUM, "XML external entity (XXE)",
                "XML DOCTYPE/ENTITY declarations used to read server files.",
                "<!(doctype|entity)\\s+[^>]*(system|public)\\b"),
        )
    }
}
