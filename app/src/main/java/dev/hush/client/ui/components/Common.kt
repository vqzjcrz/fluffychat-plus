package dev.hush.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.hush.client.data.AvatarShape
import dev.hush.client.discord.Cdn
import dev.hush.client.discord.User
import dev.hush.client.ui.theme.LocalPalette
import dev.hush.client.ui.theme.LocalSettings

@Composable
fun avatarShape(): Shape = when (LocalSettings.current.avatarShape) {
    AvatarShape.CIRCLE -> CircleShape
    AvatarShape.ROUNDED -> RoundedCornerShape(30)
    AvatarShape.SQUARE -> RoundedCornerShape(4.dp)
}

@Composable
fun UserAvatar(user: User?, size: Dp, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val animate = LocalSettings.current.animateMedia
    val px = with(LocalDensity.current) { size.roundToPx() }
    AsyncImage(
        model = user?.let { Cdn.avatar(it, px, animate) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(avatarShape()).background(p.elevated),
    )
}

/** An image with an initials fallback, used for servers and DM icons. */
@Composable
fun RemoteIcon(url: String?, fallback: String, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(modifier.size(size).clip(shape).background(p.elevated), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = fallback,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initials(fallback),
                color = p.text,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.3f).sp,
                maxLines = 1,
            )
        }
    }
}

fun initials(name: String): String =
    name.split(' ', '-', '_')
        .filter { it.isNotBlank() }
        .take(3)
        .joinToString("") { it.first().toString() }
        .ifEmpty { "?" }

@Composable
fun MentionBadge(count: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(p.danger)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
