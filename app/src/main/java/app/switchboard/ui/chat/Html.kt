package app.switchboard.ui.chat

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Patterns
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.core.text.HtmlCompat

/** Colors for rendering a message's text. */
data class TextColors(val text: Color, val link: Color, val mention: Color, val mentionMe: Color, val codeBg: Color, val quote: Color)

/** Turns a message (HTML or plain text) into styled, clickable text. */
object RichText {
    private val replyFallback = Regex("<mx-reply>.*?</mx-reply>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val matrixTo = Regex("https://matrix\\.to/#/([@!#][^/?\"'\\s]+)")

    fun render(plain: String, html: String?, colors: TextColors, ownUserId: String?, onLink: (String) -> Unit): AnnotatedString {
        if (html.isNullOrBlank()) return linkify(stripReplyFallback(plain), colors, onLink)
        val cleaned = html.replace(replyFallback, "")
            .replace("<code>", "<tt>").replace("</code>", "</tt>")
            .replace(Regex("<pre[^>]*>"), "<tt>").replace("</pre>", "</tt>")
            .replace("\n", "<br>")
            .replace(Regex("<br\\s*/?>\\s*</tt>"), "</tt>")
        val spanned = HtmlCompat.fromHtml(cleaned, HtmlCompat.FROM_HTML_MODE_COMPACT)
        return fromSpanned(spanned, colors, ownUserId, onLink)
    }

    private fun stripReplyFallback(body: String): String {
        if (!body.startsWith("> ")) return body
        val lines = body.lines()
        val idx = lines.indexOfFirst { !it.startsWith(">") }
        return if (idx < 0) body else lines.drop(idx).dropWhile { it.isBlank() }.joinToString("\n")
    }

    private fun fromSpanned(s: Spanned, colors: TextColors, ownUserId: String?, onLink: (String) -> Unit): AnnotatedString {
        val text = s.toString().trimEnd()
        val len = text.length
        return buildAnnotatedString {
            append(text)
            for (span in s.getSpans(0, s.length, Any::class.java)) {
                val start = s.getSpanStart(span).coerceIn(0, len)
                val end = s.getSpanEnd(span).coerceIn(0, len)
                if (start >= end) continue
                when (span) {
                    is StyleSpan -> when (span.style) {
                        Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                        Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                    }
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    is TypefaceSpan -> if (span.family == "monospace") addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.codeBg, fontSize = 0.92.em), start, end)
                    is RelativeSizeSpan -> addStyle(SpanStyle(fontSize = span.sizeChange.em), start, end)
                    is SuperscriptSpan -> addStyle(SpanStyle(baselineShift = BaselineShift.Superscript, fontSize = 0.8.em), start, end)
                    is SubscriptSpan -> addStyle(SpanStyle(baselineShift = BaselineShift.Subscript, fontSize = 0.8.em), start, end)
                    is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                    is QuoteSpan -> addStyle(SpanStyle(color = colors.quote, fontStyle = FontStyle.Italic), start, end)
                    is URLSpan -> {
                        val url = span.url
                        val mention = matrixTo.find(url)?.groupValues?.get(1)
                        if (mention != null && mention.startsWith("@")) {
                            val me = mention.substringBefore('?') == ownUserId
                            addStyle(SpanStyle(color = if (me) colors.mentionMe else colors.mention, fontWeight = FontWeight.Bold), start, end)
                            addLink(LinkAnnotation.Clickable(url) { onLink(url) }, start, end)
                        } else {
                            addLink(
                                LinkAnnotation.Clickable(url, TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))) { onLink(url) },
                                start, end,
                            )
                        }
                    }
                }
            }
        }
    }

    fun linkify(text: String, colors: TextColors, onLink: (String) -> Unit): AnnotatedString = buildAnnotatedString {
        append(text)
        val m = Patterns.WEB_URL.matcher(text)
        while (m.find()) {
            val raw = m.group()
            if (!raw.contains('.') || raw.startsWith("@")) continue
            val url = if (raw.startsWith("http", true)) raw else "https://$raw"
            addLink(
                LinkAnnotation.Clickable(url, TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline))) { onLink(url) },
                m.start(), m.end(),
            )
        }
    }
}
