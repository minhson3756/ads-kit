package com.xnetheron.adskit.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.xnetheron.adskit.AdsKit

/** Font from AdsConfig.fontRes (null → system default). */
internal fun adTypeface(context: Context): Typeface? =
    AdsKit.config.fontRes?.let { runCatching { ResourcesCompat.getFont(context, it) }.getOrNull() }

/** Rounded solid-accent background for a native CTA button. */
internal fun ctaBackground(accentColor: Int, cornerRadiusPx: Float): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerRadiusPx
        setColor(accentColor)
    }

/** A diagonal highlight band that sweeps across, on a theme-derived base tint. */
@Composable
internal fun rememberShimmerBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "adShimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val colors = listOf(
        cs.onSurface.copy(alpha = 0.06f),
        cs.onSurface.copy(alpha = 0.16f),
        cs.onSurface.copy(alpha = 0.06f),
    )
    val extent = 320f
    val x = progress * (extent * 2f) - extent
    return Brush.linearGradient(colors = colors, start = Offset(x, 0f), end = Offset(x + extent, extent))
}

@Composable
internal fun ShimmerBar(brush: Brush, widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(brush),
    )
}
