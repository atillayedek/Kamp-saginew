package com.kampusagi.android.presentation.common.avatar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Provided by the signed-in part of the app; screens outside it show initials only. */
val LocalAvatarLoader = staticCompositionLocalOf<AvatarLoader?> { null }

// Blue-leaning gradients that read well with white letters in both themes.
private val palette = listOf(
    Color(0xFF1D6FE8) to Color(0xFF5B9CFF),
    Color(0xFF0EA5E9) to Color(0xFF38BDF8),
    Color(0xFF4F46E5) to Color(0xFF818CF8),
    Color(0xFF0D9488) to Color(0xFF2DD4BF),
    Color(0xFF7C3AED) to Color(0xFFA78BFA),
    Color(0xFFDB2777) to Color(0xFFF472B6),
    Color(0xFFEA580C) to Color(0xFFFB923C),
    Color(0xFF16A34A) to Color(0xFF4ADE80),
)

/** Profile photo, or an initials circle in a colour tied to the person. */
@Composable
fun UserAvatar(
    userId: String,
    fullName: String?,
    username: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val loader = LocalAvatarLoader.current
    LaunchedEffect(loader, userId) { loader?.request(userId) }
    val image = loader?.image(userId)
    Box(modifier = modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            val (start, end) = palette[AvatarInitials.colorIndex(userId, palette.size)]
            Box(
                modifier = Modifier.size(size).background(Brush.linearGradient(listOf(start, end))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    AvatarInitials.of(fullName, username),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.38f).sp,
                )
            }
        }
    }
}
