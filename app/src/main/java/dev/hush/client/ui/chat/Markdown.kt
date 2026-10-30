package dev.hush.client.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.hush.client.discord.Cdn
import dev.hush.client.ui.Fmt
import dev.hush.client.ui.theme.LocalPalette
import dev.hush.client.ui.theme.LocalSettings

/** Looks up names for mentions inside message text. */
interface MdResolver {
    fun user(id: String): String?
    fun channel(id: String): String?
    fun role(id: String): String?
    val use24h: Boolean
}

sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String, val lang: String?) : MdBlock
}

data class EmojiRef(val id: String, val name: String, val animated: Boolean)

class MdStyles(
    val text: Color,
    val muted: Color,
    val link: Color,
    val mention: Color,
    val mentionBg: Color,
    val codeBg: Color,
    val spoilerBg: Color,
)

private val FENCE = Regex("```(?:([A-Za-z0-9_+\\-.#]{1,20})\\n)?([\\s\\S]*?)```")
private val USER_TAG = Regex("<@!?(\\d+)>")
private val ROLE_TAG = Regex("<@&(\\d+)>")
private val CHANNEL_TAG = Regex("<#(\\d+)>")
private val EMOJI_TAG = Regex("<(a?):([A-Za-z0-9_~]+):(\\d+)>")
private val TIME_TAG = Regex("<t:(-?\\d+)(?::([tTdDfFR]))?>")
private val COMMAND_TAG = Regex("</([^:>]+):\\d+>")
private val ANGLE_URL = Regex("<(https?://[^>\\s]+)>")
private const val ESCAPABLE = "\\*_~`|<>#[]-:"

fun parseBlocks(content: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    var last = 0
    for (m in FENCE.findAll(content)) {
        if (m.range.first > last) addTextBlocks(content.substring(last, m.range.first), out)
        val code = m.groupValues[2].trim('\n')
        if (code.isNotEmpty()) out += MdBlock.Code(code, m.groupValues[1].ifBlank { null })
        last = m.range.last + 1
    }
    if (last < content.length) addTextBlocks(content.substring(last), out)
    return out
}

private fun addTextBlocks(text: String, out: MutableList<MdBlock>) {
    val lines = text.split('\n')
    val para = ArrayList<String>()
    val quote = ArrayList<String>()
    fun flushPara() {
        val t = para.joinToString("\n").trim('\n')
        if (t.isNotBlank()) out += MdBlock.Paragraph(t)
        para.clear()
    }
    fun flushQuote() {
        if (quote.isNotEmpty()) out += MdBlock.Quote(quote.joinToString("\n").trimEnd('\n'))
        quote.clear()
    }
    for ((i, line) in lines.withIndex()) {
        if (line.startsWith(">>> ")) {
            flushPara()
            quote += line.removePrefix(">>> ")
            quote.addAll(lines.drop(i + 1))
            break
        } else if (line.startsWith("> ") || line == ">") {
            flushPara()
            quote += line.removePrefix(">").removePrefix(" ")
        } else {
            flushQuote()
            para += line
        }
    }
    flushPara()
    flushQuote()
}

/** True for messages that are only custom emoji (rendered large, like Discord). */
fun isEmojiOnly(content: String): Boolean {
    val count = EMOJI_TAG.findAll(content).count()
    return count in 1..27 && EMOJI_TAG.replace(content, "").isBlank()
}

private class InlineRenderer(
    private val st: MdStyles,
    private val r: MdResolver,
    private val spoilersRevealed: Boolean,
    private val onRevealSpoiler: (() -> Unit)?,
) {
    val emojis = LinkedHashMap<String, EmojiRef>()

    private class Delim(val token: String, val style: SpanStyle?)

    private val delims = listOf(
        Delim("**", SpanStyle(fontWeight = FontWeight.Bold)),
        Delim("__", SpanStyle(textDecoration = TextDecoration.Underline)),
        Delim("~~", SpanStyle(textDecoration = TextDecoration.LineThrough)),
        Delim("||", null),
        Delim("*", SpanStyle(fontStyle = FontStyle.Italic)),
        Delim("_", SpanStyle(fontStyle = FontStyle.Italic)),
    )

    fun paragraph(text: String): AnnotatedString = buildAnnotatedString {
        text.split('\n').forEachIndexed { index, raw ->
            if (index > 0) append('\n')
            var line = raw
            val style: SpanStyle? = when {
                line.startsWith("### ") -> {
                    line = line.substring(4)
                    SpanStyle(fontSize = 1.1.em, fontWeight = FontWeight.Bold)
                }
                line.startsWith("## ") -> {
                    line = line.substring(3)
                    SpanStyle(fontSize = 1.25.em, fontWeight = FontWeight.Bold)
                }
                line.startsWith("# ") -> {
                    line = line.substring(2)
                    SpanStyle(fontSize = 1.45.em, fontWeight = FontWeight.Bold)
                }
                line.startsWith("-# ") -> {
                    line = line.substring(3)
                    SpanStyle(fontSize = 0.8.em, color = st.muted)
                }
                else -> null
            }
            if (line.startsWith("- ") || line.startsWith("* ")) {
                append("  •  ")
                line = line.substring(2)
            }
            if (style != null) withStyle(style) { renderInline(this, line) } else renderInline(this, line)
        }
    }

    private fun renderInline(b: AnnotatedString.Builder, s: String) {
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) {
                b.append(plain.toString())
                plain.setLength(0)
            }
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]

            // Escapes
            if (c == '\\' && i + 1 < s.length && s[i + 1] in ESCAPABLE) {
                plain.append(s[i + 1])
                i += 2
                continue
            }

            // Inline code
            if (c == '`') {
                val end = s.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush()
                    b.withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = st.codeBg, fontSize = 0.9.em)) {
                        append(s.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // <@user>, <#channel>, <:emoji:id>, <t:...>, <https://...>
            if (c == '<') {
                val end = s.indexOf('>', i)
                if (end > i) {
                    flush()
                    if (tag(b, s.substring(i, end + 1))) {
                        i = end + 1
                        continue
                    }
                }
            }

            // @everyone / @here
            if (c == '@' && (s.startsWith("@everyone", i) || s.startsWith("@here", i))) {
                val word = if (s.startsWith("@everyone", i)) "@everyone" else "@here"
                flush()
                mention(b, word)
                i += word.length
                continue
            }

            // Bare URLs
            if (c == 'h' && (s.startsWith("https://", i) || s.startsWith("http://", i)) &&
                (i == 0 || !s[i - 1].isLetterOrDigit())
            ) {
                var end = i
                while (end < s.length && !s[end].isWhitespace()) end++
                val url = trimUrl(s.substring(i, end))
                if (url.length > 8) {
                    flush()
                    link(b, url, url)
                    i += url.length
                    continue
                }
            }

            // [label](https://url)
            if (c == '[') {
                val close = s.indexOf("](", i)
                if (close > i + 1) {
                    val endParen = s.indexOf(')', close + 2)
                    if (endParen > close + 2) {
                        val url = s.substring(close + 2, endParen).trim().removeSurrounding("<", ">")
                        if (url.startsWith("http://") || url.startsWith("https://")) {
                            flush()
                            link(b, url, s.substring(i + 1, close))
                            i = endParen + 1
                            continue
                        }
                    }
                }
            }

            // Emphasis, underline, strikethrough, spoilers
            val d = delims.firstOrNull { s.startsWith(it.token, i) }
            if (d != null) {
                val start = i + d.token.length
                val end = findClose(s, d.token, start)
                if (end > start && s[start] != ' ' && boundaryOk(d.token, s, i, end)) {
                    flush()
                    val inner = s.substring(start, end)
                    if (d.style == null) spoiler(b, inner) else b.withStyle(d.style) { renderInline(this, inner) }
                    i = end + d.token.length
                    continue
                }
            }

            plain.append(c)
            i++
        }
        flush()
    }

    private fun findClose(s: String, token: String, from: Int): Int {
        var j = s.indexOf(token, from)
        while (j >= 0) {
            // A single * or _ shouldn't close on the first half of ** or __.
            if (token.length == 1 && j + 1 < s.length && s[j + 1] == token[0]) {
                j = s.indexOf(token, j + 2)
                continue
            }
            return j
        }
        return -1
    }

    private fun boundaryOk(token: String, s: String, open: Int, close: Int): Boolean {
        if (token != "_") return true
        val before = open == 0 || !s[open - 1].isLetterOrDigit()
        val after = close + 1 >= s.length || !s[close + 1].isLetterOrDigit()
        return before && after
    }

    private fun trimUrl(url: String): String {
        var u = url
        while (u.isNotEmpty()) {
            val ch = u.last()
            u = when {
                ch in ".,:;!?\"'*_~>" -> u.dropLast(1)
                ch == ')' && u.count { it == '(' } < u.count { it == ')' } -> u.dropLast(1)
                else -> break
            }
        }
        return u
    }

    private fun link(b: AnnotatedString.Builder, url: String, label: String) {
        b.withLink(LinkAnnotation.Url(url, TextLinkStyles(style = SpanStyle(color = st.link)))) {
            append(label)
        }
    }

    private fun mention(b: AnnotatedString.Builder, label: String) {
        b.withStyle(SpanStyle(color = st.mention, background = st.mentionBg, fontWeight = FontWeight.Medium)) {
            append(label)
        }
    }

    private fun spoiler(b: AnnotatedString.Builder, inner: String) {
        val reveal = onRevealSpoiler
        if (spoilersRevealed || reveal == null) {
            b.withStyle(SpanStyle(background = st.spoilerBg.copy(alpha = 0.35f))) { renderInline(this, inner) }
        } else {
            val hidden = TextLinkStyles(style = SpanStyle(color = Color.Transparent, background = st.spoilerBg))
            b.withLink(LinkAnnotation.Clickable("spoiler", hidden, LinkInteractionListener { reveal() })) { append(inner) }
        }
    }

    private fun tag(b: AnnotatedString.Builder, t: String): Boolean {
        USER_TAG.matchEntire(t)?.let {
            mention(b, "@" + (r.user(it.groupValues[1]) ?: "unknown-user"))
            return true
        }
        ROLE_TAG.matchEntire(t)?.let {
            mention(b, "@" + (r.role(it.groupValues[1]) ?: "unknown-role"))
            return true
        }
        CHANNEL_TAG.matchEntire(t)?.let {
            mention(b, "#" + (r.channel(it.groupValues[1]) ?: "unknown"))
            return true
        }
        EMOJI_TAG.matchEntire(t)?.let {
            val key = "emoji${emojis.size}"
            emojis[key] = EmojiRef(id = it.groupValues[3], name = it.groupValues[2], animated = it.groupValues[1] == "a")
            b.appendInlineContent(key, ":${it.groupValues[2]}:")
            return true
        }
        TIME_TAG.matchEntire(t)?.let {
            val seconds = it.groupValues[1].toLongOrNull() ?: return false
            b.withStyle(SpanStyle(background = st.codeBg)) {
                append(Fmt.discordTimestamp(seconds * 1000, it.groupValues[2].ifEmpty { null }, r.use24h))
            }
            return true
        }
        COMMAND_TAG.matchEntire(t)?.let {
            mention(b, "/" + it.groupValues[1])
            return true
        }
        ANGLE_URL.matchEntire(t)?.let {
            link(b, it.groupValues[1], it.groupValues[1])
            return true
        }
        return false
    }
}

@Composable
private fun mdStyles(): MdStyles {
    val p = LocalPalette.current
    return remember(p) {
        MdStyles(
            text = p.text,
            muted = p.muted,
            link = p.link,
            mention = p.accent,
            mentionBg = p.accent.copy(alpha = 0.18f),
            codeBg = p.elevated,
            spoilerBg = p.muted,
        )
    }
}

/** Renders Discord-flavored markdown: code blocks, quotes, emphasis, mentions, links and custom emoji. */
@Composable
fun MessageBody(
    content: String,
    edited: Boolean,
    resolver: MdResolver,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    val blocks = remember(content) { parseBlocks(content) }
    val jumbo = remember(content) { isEmojiOnly(content) }
    var revealed by remember(content) { mutableStateOf(false) }
    val styles = mdStyles()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEachIndexed { index, block ->
            val last = index == blocks.lastIndex
            when (block) {
                is MdBlock.Paragraph -> RichText(
                    text = block.text,
                    styles = styles,
                    resolver = resolver,
                    textStyle = textStyle,
                    jumbo = jumbo,
                    edited = edited && last,
                    revealed = revealed,
                    onReveal = { revealed = true },
                )
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(p.muted.copy(alpha = 0.45f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    RichText(
                        text = block.text,
                        styles = styles,
                        resolver = resolver,
                        textStyle = textStyle,
                        jumbo = false,
                        edited = edited && last,
                        revealed = revealed,
                        onReveal = { revealed = true },
                    )
                }
                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(p.elevated)
                        .border(1.dp, p.divider, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                ) {
                    Text(
                        text = block.text,
                        style = textStyle.copy(fontFamily = FontFamily.Monospace, fontSize = textStyle.fontSize * 0.88f),
                        color = p.text,
                        softWrap = false,
                    )
                }
            }
        }
        if (edited && blocks.lastOrNull() is MdBlock.Code) {
            Text("(edited)", color = p.muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun RichText(
    text: String,
    styles: MdStyles,
    resolver: MdResolver,
    textStyle: TextStyle,
    jumbo: Boolean,
    edited: Boolean,
    revealed: Boolean,
    onReveal: () -> Unit,
) {
    val p = LocalPalette.current
    val animate = LocalSettings.current.animateMedia
    val rendered = remember(text, styles, resolver, revealed, edited) {
        val renderer = InlineRenderer(styles, resolver, revealed, onReveal)
        var annotated = renderer.paragraph(text)
        if (edited) {
            annotated += buildAnnotatedString {
                withStyle(SpanStyle(color = styles.muted, fontSize = 0.7.em)) { append("  (edited)") }
            }
        }
        annotated to renderer.emojis.toMap()
    }
    val emojiSize = if (jumbo) 44.sp else textStyle.fontSize * 1.35f
    val inline = remember(rendered.second, emojiSize, animate) {
        rendered.second.mapValues { (_, e) ->
            InlineTextContent(Placeholder(emojiSize, emojiSize, PlaceholderVerticalAlign.TextCenter)) {
                AsyncImage(
                    model = Cdn.emoji(e.id, e.animated, animate),
                    contentDescription = e.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
    Text(text = rendered.first, style = textStyle, color = p.text, inlineContent = inline)
}
