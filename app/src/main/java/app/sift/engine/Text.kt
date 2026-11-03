package app.sift.engine

import java.io.ByteArrayOutputStream
import java.util.Base64

internal object Text {

    /** Lenient percent-decoding: invalid escapes are left as-is, bytes are decoded as UTF-8. */
    fun pctDecode(s: String, plusIsSpace: Boolean = false): String {
        if ('%' !in s && !(plusIsSpace && '+' in s)) return s
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length && isHex(s[i + 1]) && isHex(s[i + 2])) {
                out.write(Character.digit(s[i + 1], 16) * 16 + Character.digit(s[i + 2], 16))
                i += 3
                continue
            }
            if (c == '+' && plusIsSpace) {
                out.write(' '.code); i++; continue
            }
            val cp = s.codePointAt(i)
            val bytes = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8)
            out.write(bytes, 0, bytes.size)
            i += Character.charCount(cp)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Decode repeatedly until stable (max [rounds]). */
    fun deepDecode(s: String, rounds: Int = 4, plusIsSpace: Boolean = false): String {
        var cur = s
        repeat(rounds) {
            val next = pctDecode(cur, plusIsSpace)
            if (next == cur) return cur
            cur = next
        }
        return cur
    }

    private fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private val ENTITY = Regex("&#(x[0-9a-fA-F]{1,6}|[0-9]{1,7});?")
    private val NAMED = mapOf("&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&amp;" to "&", "&sol;" to "/", "&colon;" to ":", "&lpar;" to "(", "&rpar;" to ")")

    fun htmlDecode(s: String): String {
        if ('&' !in s) return s
        var r = ENTITY.replace(s) { m ->
            val v = m.groupValues[1]
            val cp = if (v.startsWith("x") || v.startsWith("X")) v.substring(1).toIntOrNull(16) else v.toIntOrNull()
            if (cp != null && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else m.value
        }
        for ((k, v) in NAMED) r = r.replace(k, v, ignoreCase = true)
        return r
    }

    private val B64 = Regex("^[A-Za-z0-9+/_-]{16,}={0,2}$")

    /** Returns decoded text if [s] is base64 that decodes to mostly-printable text, else null. */
    fun tryBase64(s: String): String? {
        val t = s.trim()
        if (!B64.matches(t)) return null
        if (t.all { it.isLetter() } && t.length < 24) return null // plain words
        val bytes = runCatching {
            if ('-' in t || '_' in t) Base64.getUrlDecoder().decode(t.padEnd((t.length + 3) / 4 * 4, '='))
            else Base64.getDecoder().decode(t.padEnd((t.length + 3) / 4 * 4, '='))
        }.getOrNull() ?: return null
        if (bytes.size < 6) return null
        val str = String(bytes, Charsets.UTF_8)
        val printable = str.count { it.code in 0x20..0x7e || it == '\n' || it == '\r' || it == '\t' }
        return if (printable >= str.length * 0.9) str else null
    }

    fun shannonEntropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        val counts = s.groupingBy { it }.eachCount()
        var e = 0.0
        for (n in counts.values) {
            val p = n.toDouble() / s.length
            e -= p * Math.log(p) / Math.log(2.0)
        }
        return e
    }

    /** Shorten long evidence strings and make control/invisible characters visible. */
    fun evidence(s: String, max: Int = 90): String {
        val visible = buildString {
            for (ch in s) {
                when {
                    ch == '\n' -> append("\\n")
                    ch == '\r' -> append("\\r")
                    ch == '\t' -> append("\\t")
                    Character.getType(ch) == Character.FORMAT.toInt() || ch.code < 0x20 ->
                        append("<U+%04X>".format(ch.code))
                    else -> append(ch)
                }
            }
        }
        return if (visible.length <= max) visible else visible.take(max - 1) + "…"
    }

    fun snippetAround(hay: String, range: IntRange, pad: Int = 24): String {
        val start = (range.first - pad).coerceAtLeast(0)
        val end = (range.last + 1 + pad).coerceAtMost(hay.length)
        val pre = if (start > 0) "…" else ""
        val post = if (end < hay.length) "…" else ""
        return evidence(pre + hay.substring(start, end) + post, 120)
    }

    fun maskEmail(e: String): String {
        val at = e.indexOf('@')
        if (at <= 1) return e
        return e[0] + "•".repeat((at - 1).coerceAtMost(6)) + e.substring(at)
    }
}
