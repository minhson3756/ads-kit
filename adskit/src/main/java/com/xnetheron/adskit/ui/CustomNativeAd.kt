package com.xnetheron.adskit.ui

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.xnetheron.adskit.AdSlot
import com.xnetheron.adskit.AdsKit

/** State of a native ad requested with [rememberNativeAd]. */
sealed interface NativeAdLoad {
    /** Ads aren't ready, the slot is disabled, the caller disabled it, or the user is Pro. */
    data object Hidden : NativeAdLoad

    data object Loading : NativeAdLoad

    data class Loaded(val ad: NativeAd) : NativeAdLoad

    data object Failed : NativeAdLoad
}

/** The loaded ad, or null for every other state. */
val NativeAdLoad.adOrNull: NativeAd? get() = (this as? NativeAdLoad.Loaded)?.ad

/**
 * Request options for a custom native placement.
 *
 * @param adChoicesPlacement corner for the SDK-rendered AdChoices icon (`NativeAdOptions.ADCHOICES_*`).
 * @param mediaAspectRatio preferred media shape (`NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_*`).
 */
data class NativeAdRequest(
    val adChoicesPlacement: Int = NativeAdOptions.ADCHOICES_TOP_RIGHT,
    val mediaAspectRatio: Int = NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY,
    val startVideoMuted: Boolean = true,
)

/**
 * Loads one native ad for [slot] while this call stays in the composition, gated by consent,
 * `ADS_ACTIVE`, the slot's `enable` flag and Pro (it switches to [NativeAdLoad.Hidden] and
 * destroys the ad as soon as any of them turns off). The ad is destroyed on dispose.
 *
 * Hoist it to the screen level when the placement lives inside a lazy list, so scrolling the
 * item away doesn't reload the ad; render it with [NativeAdHost].
 */
@Composable
fun rememberNativeAd(
    slot: AdSlot,
    enabled: Boolean = true,
    request: NativeAdRequest = NativeAdRequest(),
): NativeAdLoad {
    if (LocalInspectionMode.current || !AdsKit.isInitialized) return NativeAdLoad.Hidden
    val ready by AdsKit.ready.collectAsStateWithLifecycle()
    val active = enabled && ready && AdsKit.canShow(slot)
    val context = LocalContext.current
    var state by remember(slot) { mutableStateOf<NativeAdLoad>(NativeAdLoad.Loading) }

    DisposableEffect(slot, active, request) {
        if (!active) return@DisposableEffect onDispose { }
        var disposed = false
        state = NativeAdLoad.Loading
        AdLoader.Builder(context, AdsKit.unitId(slot))
            .forNativeAd { ad ->
                if (disposed) {
                    ad.destroy()
                } else {
                    state.adOrNull?.destroy()
                    state = NativeAdLoad.Loaded(ad)
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (!disposed) state = NativeAdLoad.Failed
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(request.adChoicesPlacement)
                    .setMediaAspectRatio(request.mediaAspectRatio)
                    .setVideoOptions(VideoOptions.Builder().setStartMuted(request.startVideoMuted).build())
                    .build(),
            )
            .build()
            .loadAd(AdRequest.Builder().build())
        onDispose {
            disposed = true
            state.adOrNull?.destroy()
            state = NativeAdLoad.Loading
        }
    }
    return if (active) state else NativeAdLoad.Hidden
}

/**
 * Renders an already-loaded [ad] in a host-built [NativeAdView]. [factory] creates the view tree
 * once (XML inflate or code); [bind] fills it and must end with [NativeAdView.setNativeAd],
 * typically through [bindNativeAd]. [bind] re-runs on recomposition, so theme colors can be
 * applied there.
 */
@Composable
fun NativeAdHost(
    ad: NativeAd,
    factory: (Context) -> NativeAdView,
    modifier: Modifier = Modifier,
    bind: (NativeAdView, NativeAd) -> Unit,
) {
    AndroidView(factory = factory, modifier = modifier, update = { view -> bind(view, ad) })
}

/**
 * One-call custom placement: [rememberNativeAd] + [NativeAdHost]. Shows [placeholder] while
 * loading and nothing when hidden or failed.
 */
@Composable
fun CustomNativeAd(
    slot: AdSlot,
    factory: (Context) -> NativeAdView,
    bind: (NativeAdView, NativeAd) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    request: NativeAdRequest = NativeAdRequest(),
    placeholder: (@Composable () -> Unit)? = null,
) {
    when (val load = rememberNativeAd(slot, enabled, request)) {
        is NativeAdLoad.Loaded -> NativeAdHost(load.ad, factory, modifier, bind)
        NativeAdLoad.Loading -> placeholder?.invoke()
        NativeAdLoad.Hidden, NativeAdLoad.Failed -> Unit
    }
}

/**
 * Fills and registers the asset views of a custom layout, then calls [NativeAdView.setNativeAd].
 * Assets the ad doesn't carry are hidden (GONE) and left unregistered; text and images are shown
 * as delivered, as AdMob policy requires. A [starRating] view may be a [RatingBar] or a [TextView]
 * (formatted "4.5").
 */
fun NativeAdView.bindNativeAd(
    ad: NativeAd,
    headline: TextView,
    body: TextView? = null,
    callToAction: TextView? = null,
    icon: ImageView? = null,
    media: MediaView? = null,
    advertiser: TextView? = null,
    store: TextView? = null,
    price: TextView? = null,
    starRating: View? = null,
) {
    headline.text = ad.headline
    headlineView = headline
    bodyView = body?.takeIfAsset(ad.body)
    callToActionView = callToAction?.takeIfAsset(ad.callToAction)
    advertiserView = advertiser?.takeIfAsset(ad.advertiser)
    storeView = store?.takeIfAsset(ad.store)
    priceView = price?.takeIfAsset(ad.price)
    iconView = icon?.let { view ->
        val drawable = ad.icon?.drawable
        view.setImageDrawable(drawable)
        view.visibility = if (drawable != null) View.VISIBLE else View.GONE
        view.takeIf { drawable != null }
    }
    mediaView = media?.also { view -> ad.mediaContent?.let { view.mediaContent = it } }
    starRatingView = starRating?.let { view ->
        val rating = ad.starRating
        view.visibility = if (rating != null) View.VISIBLE else View.GONE
        when {
            rating == null -> null
            view is RatingBar -> view.also { it.rating = rating.toFloat() }
            view is TextView -> view.also { it.text = "%.1f".format(rating) }
            else -> view
        }
    }
    setNativeAd(ad)
}

private fun TextView.takeIfAsset(value: String?): TextView? {
    text = value
    visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    return takeUnless { value.isNullOrBlank() }
}
