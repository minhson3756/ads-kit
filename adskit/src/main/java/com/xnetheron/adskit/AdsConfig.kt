package com.xnetheron.adskit

import androidx.annotation.ColorInt
import androidx.annotation.FontRes

/**
 * Host-supplied ads configuration, passed to [AdsKit.initialize].
 *
 * @param slots every ad slot the host uses (pass e.g. `AppAds.entries.toList()`).
 *   Used to seed Remote Config defaults and to find the app-open slot. The slot
 *   whose [AdSlot.format] is [AdFormat.AppOpen] (if any) drives the app-open ad.
 * @param accentColor ARGB color for the native CTA button + accents. Default rose.
 * @param fontRes font applied to native-ad text; null → the system default.
 * @param interstitialMinIntervalSec minimum seconds between interstitials.
 */
data class AdsConfig(
    val slots: List<AdSlot>,
    @get:ColorInt val accentColor: Int = DEFAULT_ACCENT,
    @get:FontRes val fontRes: Int? = null,
    val interstitialMinIntervalSec: Long = 60L,
) {
    companion object {
        /** Rose (#F43F5E). */
        const val DEFAULT_ACCENT: Int = 0xFFF43F5E.toInt()
    }
}
