package com.xnetheron.adskit.core

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.xnetheron.adskit.AdSlot

/**
 * Loads + shows an interstitial, frequency-capped. [preload] fetches one ahead of
 * the show point; [maybeShow] shows it if ready AND at least
 * `interstitialMinIntervalSec` has passed — otherwise it just runs `onDone` so the
 * caller's navigation never blocks. A failed/slow load never blocks either.
 */
internal class InterstitialAdManager(
    private val context: Context,
    private val adMob: AdMobService,
    private val remoteConfig: AdsRemoteConfig,
) {
    private var ad: InterstitialAd? = null
    private var loadedSlotKey: String? = null
    private var loading = false
    private var lastShownAtMs = 0L

    fun preload(slot: AdSlot) {
        if (loading || !adMob.canShow(slot)) return
        if (ad != null && loadedSlotKey == slot.rcKey) return
        loading = true
        InterstitialAd.load(
            context,
            adMob.unitId(slot),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    ad = loaded
                    loadedSlotKey = slot.rcKey
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                }
            },
        )
    }

    fun maybeShow(slot: AdSlot, activity: Activity?, onDone: () -> Unit) {
        val ready = ad
        val capOk = SystemClock.elapsedRealtime() - lastShownAtMs >=
            remoteConfig.interstitialMinIntervalSec * 1000L
        if (activity == null || ready == null || loadedSlotKey != slot.rcKey || !capOk || !adMob.canShow(slot)) {
            preload(slot)
            onDone()
            return
        }
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                lastShownAtMs = SystemClock.elapsedRealtime()
                preload(slot)
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                preload(slot)
                onDone()
            }
        }
        ready.show(activity)
    }
}
