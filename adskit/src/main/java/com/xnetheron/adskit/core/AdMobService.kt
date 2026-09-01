package com.xnetheron.adskit.core

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.xnetheron.adskit.AdSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Central ads gate. Google Mobile Ads is initialized exactly once, AFTER UMP
 * consent AND once [AdsRemoteConfig.adsActive] is on. Every placement checks
 * [canShow], which folds in the per-slot `enable` (its RC JSON) + the Pro flag.
 *
 * Consent and the Remote Config fetch resolve asynchronously in any order, so init
 * is re-evaluated whenever either lands.
 */
internal class AdMobService(
    private val context: Context,
    private val consent: ConsentManager,
    private val remoteConfig: AdsRemoteConfig,
) {
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _ready = MutableStateFlow(false)
    /** Emits true once the SDK is initialized and consent + ADS_ACTIVE allow ads. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile
    var isPro: Boolean = false

    private var consentResolved = false
    private var initialized = false

    init {
        scope.launch { remoteConfig.configLoaded.collect { evaluate() } }
    }

    /** Call from the launcher Activity. Gathers consent, then evaluates init. */
    fun start(activity: Activity) {
        consent.gather(activity) {
            consentResolved = true
            evaluate()
        }
    }

    private fun evaluate() {
        if (!initialized && consentResolved && consent.canRequestAds && remoteConfig.adsActive && !isPro) {
            initialized = true
            // MobileAds.initialize() does first-run disk + binder work that BLOCKS
            // the calling thread — on a fresh install long enough to ANR. Initialize
            // off the main thread; the SDK posts its callback back to main.
            scope.launch(Dispatchers.IO) {
                MobileAds.initialize(context) { refreshReady() }
            }
        }
        refreshReady()
    }

    private fun refreshReady() {
        _ready.value = initialized && remoteConfig.adsActive && !isPro
    }

    /** True when [slot] may show now: SDK ready + slot enabled + not Pro. */
    fun canShow(slot: AdSlot): Boolean =
        _ready.value && !isPro && remoteConfig.configFor(slot).enable

    /** Ad unit id for [slot] (Remote Config JSON → host resource fallback). */
    fun unitId(slot: AdSlot): String = remoteConfig.unitId(slot)
}
