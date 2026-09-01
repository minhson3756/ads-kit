package com.xnetheron.adskit.core

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.xnetheron.adskit.AdSlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App-open ad. Shows when the app returns to the foreground — but NOT on cold
 * start, and NOT when the return is caused by an activity WE launched (picker,
 * scanner, share, permission dialog). Those callers fence the next foreground with
 * [suspendNextForeground].
 *
 * Foreground is detected via started-activity counting. Register once from the
 * Application. Disabled when [slot] is null.
 */
internal class AppOpenAdManager(
    private val context: Context,
    private val adMob: AdMobService,
    private val slot: AdSlot?,
) : Application.ActivityLifecycleCallbacks {

    private val scope = CoroutineScope(Dispatchers.Main)

    private var ad: AppOpenAd? = null
    private var loading = false
    private var loadedAtMs = 0L
    private var isShowing = false
    private var lastShownAtMs = 0L

    private var currentActivity: Activity? = null
    private var startedCount = 0
    private var isFirstForeground = true
    private var suspendNext = false

    private val allowed: Boolean
        get() = slot != null && adMob.canShow(slot)

    fun register(app: Application) {
        if (slot == null) return
        app.registerActivityLifecycleCallbacks(this)
        scope.launch { adMob.ready.collect { if (it) load() } }
    }

    fun suspendNextForeground() { suspendNext = true }

    private fun load() {
        val slot = slot ?: return
        if (loading || !allowed) return
        if (ad != null && !isExpired()) return
        loading = true
        AppOpenAd.load(
            context,
            adMob.unitId(slot),
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(loaded: AppOpenAd) {
                    ad = loaded
                    loadedAtMs = SystemClock.elapsedRealtime()
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    ad = null
                    loading = false
                }
            },
        )
    }

    private fun isExpired(): Boolean = SystemClock.elapsedRealtime() - loadedAtMs > EXPIRY_MS

    private fun showIfAvailable(activity: Activity) {
        if (isShowing || !allowed) { load(); return }
        val ready = ad
        if (ready == null || isExpired()) { ad = null; load(); return }
        if (SystemClock.elapsedRealtime() - lastShownAtMs < MIN_INTERVAL_MS) return

        isShowing = true
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                isShowing = false
                lastShownAtMs = SystemClock.elapsedRealtime()
                load()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                isShowing = false
                load()
            }
        }
        ready.show(activity)
    }

    private fun onForeground() {
        if (isFirstForeground) {          // cold start — never show; just warm up
            isFirstForeground = false
            load()
            return
        }
        if (suspendNext) {                // returned from an activity we launched
            suspendNext = false
            return
        }
        currentActivity?.let { showIfAvailable(it) }
    }

    override fun onActivityStarted(activity: Activity) {
        if (!isShowing) currentActivity = activity
        val wasBackground = startedCount == 0
        startedCount++
        if (wasBackground) onForeground()
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isShowing) currentActivity = activity
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val EXPIRY_MS = 4L * 60 * 60 * 1000   // AppOpenAd expires ~4h after load
        const val MIN_INTERVAL_MS = 60_000L          // cooldown between app-opens
    }
}
