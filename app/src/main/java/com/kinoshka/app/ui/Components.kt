package com.kinoshka.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kinoshka.app.data.Movie

/**
 * Всё, что можно выбрать, заметно подсвечивается при фокусе:
 * на телевизоре управляют пультом, и пользователь должен видеть, где он.
 */
@Composable
fun Modifier.focusGlow(shape: RoundedCornerShape = RoundedCornerShape(10.dp), grow: Float = 1.06f): Modifier {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) grow else 1f, label = "focus")
    return this
        .scale(s)
        .onFocusChanged { focused = it.hasFocus }
        .border(if (focused) 3.dp else 0.dp, if (focused) Kino.Accent else Color.Transparent, shape)
}

@Composable
fun PosterCard(movie: Movie, width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.width(width)) {
        Box(
            Modifier
                .focusGlow(shape, 1.08f)
                .clip(shape)
                .background(Kino.SurfaceHi)
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            AsyncImage(
                model = movie.poster,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (movie.ratingKp > 0) {
                Text(
                    String.format(java.util.Locale.US, "%.1f", movie.ratingKp),
                    color = Kino.OnAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(ratingColor(movie.ratingKp), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(movie.name, color = Kino.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (movie.year > 0) Text("${movie.year}", color = Kino.TextDim, fontSize = 12.sp)
    }
}

fun ratingColor(r: Double) = when {
    r >= 7 -> Kino.Good
    r >= 5.5 -> Color(0xFFB0A8B9)
    else -> Kino.Bad
}

/** Кнопка, удобная и пальцем, и пультом. */
@Composable
fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .focusGlow(shape, 1.04f)
            .clip(shape)
            .background(if (primary) Kino.Accent else Kino.SurfaceHi)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val c = if (primary) Kino.OnAccent else if (enabled) Kino.Text else Kino.TextDim
        if (icon != null) {
            Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = c, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Kino.Text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 24.dp, top = 18.dp, bottom = 10.dp),
    )
}

fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return if (h > 0) "$h ч ${m} мин" else "$m мин"
}
