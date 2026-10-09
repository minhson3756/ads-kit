package com.xnetheron.adskit

import android.app.Activity
import android.app.Application
import android.content.Context
import com.xnetheron.adskit.core.AdMobService
import com.xnetheron.adskit.core.AdsRemoteConfig
import com.xnetheron.adskit.core.AppOpenAdManager
import com.xnetheron.adskit.core.ConsentManager
import com.xnetheron.adskit.core.InterstitialAdManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * True once consent allows ads, the SDK is initialized, `ADS_ACTIVE` is on and the user isn't
     * Pro. Collect it in custom placements (see [com.xnetheron.adskit.ui.rememberNativeAd]).
     */
    val ready: StateFlow<Boolean>
        get() = if (isInitialized) adMob.ready else notReady

    /** True when [slot] may show now: [ready] + the slot's Remote Config `enable` + not Pro. */
    fun canShow(slot: AdSlot): Boolean = isInitialized && adMob.canShow(slot)

    /** Ad unit id for [slot] (Remote Config JSON → host resource fallback). */
    fun unitId(slot: AdSlot): String = adMob.unitId(slot)

    /** EEA/UK users must be able to change consent: show a "Privacy options" entry while true. */
    val privacyOptionsRequired: StateFlow<Boolean>
        get() = if (isInitialized) consent.privacyOptionsRequired else notReady

    /** Re-open the UMP consent form (host's "Privacy options" row); ads re-evaluate afterwards. */
    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit = {}) {
        if (isInitialized) adMob.showPrivacyOptions(activity, onDone) else onDone()
    }

    private val notReady = MutableStateFlow(false)

    /** Call once from Application.onCreate. Safe to call again (no-op). */
    fun initialize(app: Application, config: AdsConfig) {
        if (isInitialized) return
        appContext = app.applicationContext
        this.config = config
        remoteConfig = AdsRemoteConfig(
            appContext, config.slots, config.interstitialMinIntervalSec, config.appOpenMinIntervalSec, config.defaultAdsActive,
        )
        consent = ConsentManager(appContext, config.consentDebug)
        adMob = AdMobService(appContext, consent, remoteConfig)
        interstitial = InterstitialAdManager(appContext, adMob, remoteConfig)
        val appOpenSlot = config.slots.firstOrNull { it.format == AdFormat.AppOpen }
        appOpen = AppOpenAdManager(appContext, adMob, remoteConfig, appOpenSlot)
        appOpen.register(app)
        isInitialized = true
    }

    /** Gather consent + init the SDK. Needs an Activity (consent form host). */
    fun start(activity: Activity) {
        if (isInitialized) adMob.start(activity)
    }

    /** Fence the NEXT return-to-foreground so it doesn't trigger an app-open ad.
     *  Call right before launching your own Activity (SAF picker, scanner, share,
     *  browser, billing, in-app update…), or once for all of them by overriding
     *  `startActivityForResult(Intent, Int, Bundle?)` in the host activity. The fence
     *  expires by itself if the launch never sends the app to the background. */
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
