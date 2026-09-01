package com.xnetheron.adskit.core

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.xnetheron.adskit.AdSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Firebase Remote Config gate. `ADS_ACTIVE` is the master kill-switch (default
 * OFF), and **each [AdSlot] is ONE JSON param** `{"id": "...", "enable": true}` —
 * so a slot's unit id and its own on/off toggle live together and change without
 * a release. Falls back to the host's per-flavor resource id + enabled when the RC
 * value is missing/blank/malformed. Requires the host to have Firebase configured;
 * if `FirebaseRemoteConfig.getInstance()` is unavailable everything falls back.
 */
internal class AdsRemoteConfig(
    private val context: Context,
    private val slots: List<AdSlot>,
    private val defaultInterstitialMinIntervalSec: Long,
) {
    private val rc: FirebaseRemoteConfig? =
        runCatching { FirebaseRemoteConfig.getInstance() }.getOrNull()

    private val _configLoaded = MutableStateFlow(false)
    /** Flips true once the first fetch+activate completes (or immediately if
     *  Firebase isn't configured). AdMobService re-evaluates init on this. */
    val configLoaded: StateFlow<Boolean> = _configLoaded.asStateFlow()

    /** Resolved config for one slot: which unit id + whether it's enabled. */
    data class AdConfig(val id: String, val enable: Boolean)

    init {
        val remote = rc
        if (remote != null) {
            val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            remote.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (debuggable) 0L else MIN_FETCH_INTERVAL_SEC
                },
            )
            remote.setDefaultsAsync(buildDefaults())
            remote.fetchAndActivate().addOnCompleteListener { _configLoaded.value = true }
        } else {
            _configLoaded.value = true
        }
    }

    val adsActive: Boolean get() = rc?.getBoolean(KEY_ADS_ACTIVE) ?: false
    val interstitialMinIntervalSec: Long
        get() = rc?.getLong(KEY_INT_MIN_INTERVAL) ?: defaultInterstitialMinIntervalSec

    fun configFor(slot: AdSlot): AdConfig {
        val fallbackId = context.getString(slot.fallbackResId)
        val raw = rc?.getString(slot.rcKey)?.takeIf { it.isNotBlank() }
            ?: return AdConfig(fallbackId, enable = true)
        return runCatching {
            val json = JSONObject(raw)
            AdConfig(
                id = json.optString("id").takeIf { it.isNotBlank() } ?: fallbackId,
                enable = json.optBoolean("enable", true),
            )
        }.getOrDefault(AdConfig(fallbackId, enable = true))
    }

    fun unitId(slot: AdSlot): String = configFor(slot).id

    private fun buildDefaults(): Map<String, Any> = buildMap {
        put(KEY_ADS_ACTIVE, false)
        put(KEY_INT_MIN_INTERVAL, defaultInterstitialMinIntervalSec)
        slots.forEach { slot ->
            put(
                slot.rcKey,
                JSONObject()
                    .put("id", context.getString(slot.fallbackResId))
                    .put("enable", true)
                    .toString(),
            )
        }
    }

    companion object {
        const val KEY_ADS_ACTIVE = "ADS_ACTIVE"
        const val KEY_INT_MIN_INTERVAL = "ADS_INTERSTITIAL_MIN_INTERVAL_SEC"
        private const val MIN_FETCH_INTERVAL_SEC = 3600L
    }
}
