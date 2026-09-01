package com.xnetheron.adskit.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.xnetheron.adskit.AdSlot
import com.xnetheron.adskit.AdsKit

/**
 * Anchored adaptive banner. Renders nothing unless ads are ready + the slot is
 * enabled + not Pro — safe to place unconditionally; it collapses to zero height
 * when ads are off. Give it a fixed slot with clear separation from interactive
 * controls so a late load never shifts content under a finger.
 */
@Composable
fun AdaptiveBannerAd(slot: AdSlot, modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current || !AdsKit.isInitialized) return
    val service = AdsKit.adMob
    val ready by service.ready.collectAsStateWithLifecycle()
    if (!ready || !service.canShow(slot)) return

    val widthDp = LocalConfiguration.current.screenWidthDp
    val unitId = service.unitId(slot)
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                adUnitId = unitId
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp),
                )
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}

/** Unwrap the hosting Activity from a Compose LocalContext (handy when showing a
 *  full-screen ad via [AdsKit.maybeShowInterstitial]). */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
