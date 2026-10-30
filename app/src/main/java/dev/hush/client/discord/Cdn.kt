package dev.hush.client.discord

object Cdn {
    private const val BASE = "https://cdn.discordapp.com"

    /** Discord only serves power-of-two sizes. */
    fun size(px: Int): Int = listOf(16, 32, 64, 128, 256, 512).firstOrNull { it >= px } ?: 512

    fun avatar(user: User, px: Int, animate: Boolean): String {
        val hash = user.avatar ?: return defaultAvatar(user)
        val ext = if (animate && hash.startsWith("a_")) "gif" else "png"
        return "$BASE/avatars/${user.id}/$hash.$ext?size=${size(px)}"
    }

    fun defaultAvatar(user: User): String {
        val disc = user.discriminator
        val index = if (disc.isNullOrEmpty() || disc == "0") {
            ((user.id.toLongOrNull() ?: 0L) ushr 22) % 6
        } else {
            ((disc.toIntOrNull() ?: 0) % 5).toLong()
        }
        return "$BASE/embed/avatars/$index.png"
    }

    fun guildIcon(guildId: String, hash: String?): String? =
        hash?.let { "$BASE/icons/$guildId/$it.png?size=128" }

    fun channelIcon(channelId: String, hash: String): String =
        "$BASE/channel-icons/$channelId/$hash.png?size=64"

    fun emoji(id: String, animated: Boolean, animate: Boolean): String =
        "$BASE/emojis/$id.${if (animated && animate) "gif" else "png"}?size=64"

    fun sticker(id: String, formatType: Int): String? = when (formatType) {
        1, 2 -> "https://media.discordapp.net/stickers/$id.png?size=160"
        4 -> "https://media.discordapp.net/stickers/$id.gif?size=160"
        else -> null // Lottie stickers aren't supported
    }

    /** Ask Discord's media proxy for a resized image when possible. */
    fun sized(url: String, widthPx: Int, heightPx: Int): String {
        if (!url.contains("media.discordapp.net") && !url.contains("images-ext")) return url
        val sep = if (url.contains('?')) '&' else '?'
        return "$url${sep}width=$widthPx&height=$heightPx"
    }
}
