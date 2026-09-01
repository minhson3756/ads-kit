package com.xnetheron.adskit

import android.app.Activity
import android.app.Application
import android.content.Context
import com.xnetheron.adskit.core.AdMobService
import com.xnetheron.adskit.core.AdsRemoteConfig
import com.xnetheron.adskit.core.AppOpenAdManager
import com.xnetheron.adskit.core.ConsentManager
import com.xnetheron.adskit.core.InterstitialAdManager

/**
 * Public entry point to the ads library — a Hilt-free singleton facade so any app
 * (Hilt or not) can use it.
 *
 * Wiring:
 * ```
 * // Application.onCreate
 * AdsKit.initialize(this, AdsConfig(slots = AppAds.entries.toList(), accentColor = ..., fontRes = ...))
 * // launcher Activity.onCreate
 * AdsKit.start(this)
 * // Compose
 * NativeAdCard(slot = AppAds.Home)
 * // before launching your own Activity (picker/scanner/share/permission)
 * AdsKit.suspendNextForeground()
 * ```
 */
object AdsKit {

    @Volatile
    var isInitialized: Boolean = false
        private set

    private lateinit var appContext: Context
    internal lateinit var config: AdsConfig
        private set

    internal lateinit var remoteConfig: AdsRemoteConfig
    internal lateinit var consent: ConsentManager
    internal lateinit var adMob: AdMobService
    internal lateinit var interstitial: InterstitialAdManager
    internal lateinit var appOpen: AppOpenAdManager

    /** Pro removes ads. Set from the host's billing state. */
    var isPro: Boolean
        get() = if (isInitialized) adMob.isPro else false
        set(value) { if (isInitialized) adMob.isPro = value }

    /** Call once from Application.onCreate. Safe to call again (no-op). */
    fun initialize(app: Application, config: AdsConfig) {
        if (isInitialized) return
        appContext = app.applicationContext
        this.config = config
        remoteConfig = AdsRemoteConfig(appContext, config.slots, config.interstitialMinIntervalSec)
        consent = ConsentManager(appContext)
        adMob = AdMobService(appContext, consent, remoteConfig)
        interstitial = InterstitialAdManager(appContext, adMob, remoteConfig)
        val appOpenSlot = config.slots.firstOrNull { it.format == AdFormat.AppOpen }
        appOpen = AppOpenAdManager(appContext, adMob, appOpenSlot)
        appOpen.register(app)
        isInitialized = true
    }

    /** Gather consent + init the SDK. Needs an Activity (consent form host). */
    fun start(activity: Activity) {
        if (isInitialized) adMob.start(activity)
    }

    /** Fence the NEXT return-to-foreground so it doesn't trigger an app-open ad.
     *  Call right before launching your own Activity (SAF picker, scanner, share,
     *  permission dialog, in-app update…). */
    fun suspendNextForeground() {
        if (isInitialized) appOpen.suspendNextForeground()
    }

    /** Preload the interstitial for [slot] ahead of the show point. */
    fun preloadInterstitial(slot: AdSlot) {
        if (isInitialized) interstitial.preload(slot)
    }

    /** Show the interstitial for [slot] if ready + frequency cap allows, then run
     *  [onDone]; otherwise run [onDone] immediately (never blocks navigation). */
    fun maybeShowInterstitial(slot: AdSlot, activity: Activity?, onDone: () -> Unit) {
        if (isInitialized) interstitial.maybeShow(slot, activity, onDone) else onDone()
    }
}
