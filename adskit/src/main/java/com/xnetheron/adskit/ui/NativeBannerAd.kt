package com.xnetheron.adskit.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.xnetheron.adskit.AdSlot
import com.xnetheron.adskit.AdsKit
import com.xnetheron.adskit.R

/**
 * Small banner-height native ad (icon + headline · advertiser + CTA, no MediaView)
 * for slim foot-of-screen slots. Shimmers while loading; renders nothing when ads
 * aren't ready / the slot is disabled / Pro, or when the load fails.
 */
@Composable
fun NativeBannerAd(slot: AdSlot, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current || !AdsKit.isInitialized) return
    val service = AdsKit.adMob
    val ready by service.ready.collectAsStateWithLifecycle()
    if (!ready || !service.canShow(slot)) return

    val ctx = LocalContext.current
    var nativeAd by remember(slot) { mutableStateOf<NativeAd?>(null) }
    var failed by remember(slot) { mutableStateOf(false) }

    DisposableEffect(slot, ready) {
        failed = false
        val loader = AdLoader.Builder(ctx, service.unitId(slot))
            .forNativeAd { loaded ->
                nativeAd?.destroy()
                nativeAd = loaded
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    failed = true
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd
    if (ad == null) {
        if (!failed) NativeBannerPlaceholder(modifier)
        return
    }

    val cs = MaterialTheme.colorScheme
    val surface = cs.surface.toArgb()
    val onSurface = cs.onSurface.toArgb()
    val onSurfaceVariant = cs.onSurfaceVariant.toArgb()
    val outline = cs.outlineVariant.toArgb()
    val cardRadiusPx = with(LocalDensity.current) { 14.dp.toPx() }
    val ctaRadiusPx = with(LocalDensity.current) { 11.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 1.dp.toPx() }.toInt()
    val accent = AdsKit.config.accentColor
    val typeface = remember(ctx) { adTypeface(ctx) }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.native_ad_banner, null) as NativeAdView
        },
        update = { view ->
            bindNativeBanner(view, ad, surface, onSurface, onSurfaceVariant, outline, cardRadiusPx, strokePx, accent, ctaRadiusPx, typeface)
        },
    )
}

@Composable
private fun NativeBannerPlaceholder(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val brush = rememberShimmerBrush()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surface)
            .border(1.dp, cs.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(brush),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 10.dp),
        ) {
            ShimmerBar(brush, widthFraction = 0.9f, height = 12.dp)
            Spacer(Modifier.height(5.dp))
            ShimmerBar(brush, widthFraction = 0.5f, height = 10.dp)
        }
        Box(
            Modifier
                .width(72.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(brush),
        )
    }
}

private fun bindNativeBanner(
    adView: NativeAdView,
    ad: NativeAd,
    surface: Int,
    onSurface: Int,
    onSurfaceVariant: Int,
    outline: Int,
    cardRadiusPx: Float,
    strokePx: Int,
    accent: Int,
    ctaRadiusPx: Float,
    typeface: Typeface?,
) {
    adView.findViewById<LinearLayout>(R.id.ad_card).background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(surface)
        cornerRadius = cardRadiusPx
        setStroke(strokePx, outline)
    }

    val headline = adView.findViewById<TextView>(R.id.ad_headline)
    headline.text = ad.headline
    headline.setTextColor(onSurface)
    typeface?.let { headline.typeface = it }
    adView.headlineView = headline

    val icon = adView.findViewById<ImageView>(R.id.ad_icon)
    val iconDrawable = ad.icon?.drawable
    if (iconDrawable != null) {
        icon.setImageDrawable(iconDrawable)
        icon.visibility = View.VISIBLE
        adView.iconView = icon
    } else {
        icon.visibility = View.GONE
    }

    val advertiser = adView.findViewById<TextView>(R.id.ad_advertiser)
    val rating = ad.starRating
    val ratingPart = if (rating != null) "★ %.1f".format(rating) else null
    val advertiserPart = ad.advertiser ?: ad.store ?: ad.body
    advertiser.text = listOfNotNull(ratingPart, advertiserPart).joinToString(" · ")
    advertiser.setTextColor(onSurfaceVariant)
    typeface?.let { advertiser.typeface = it }
    advertiser.visibility = if (advertiser.text.isNullOrBlank()) View.GONE else View.VISIBLE
    if (advertiserPart != null) adView.advertiserView = advertiser
    if (rating != null) adView.starRatingView = advertiser

    val cta = adView.findViewById<TextView>(R.id.ad_cta)
    val callToAction = ad.callToAction
    if (callToAction != null) {
        cta.text = callToAction
        cta.background = ctaBackground(accent, ctaRadiusPx)
        typeface?.let { cta.typeface = it }
        cta.visibility = View.VISIBLE
        adView.callToActionView = cta
    } else {
        cta.visibility = View.GONE
    }

    adView.setNativeAd(ad)
}
