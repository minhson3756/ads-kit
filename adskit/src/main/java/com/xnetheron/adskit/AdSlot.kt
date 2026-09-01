package com.xnetheron.adskit

import androidx.annotation.StringRes

/**
 * A single ad slot in the HOST app. The host implements this — typically as an
 * enum — giving each placement:
 *
 *  - [rcKey]         the Remote Config param name. Its value is JSON
 *                    `{"id": "...", "enable": true}`; the id/enable live together
 *                    so a slot can be re-pointed or killed without a release.
 *  - [fallbackResId] a per-flavor string resource (dev = Google test id,
 *                    prod = real id) used when the RC value is absent/blank/bad.
 *  - [format]        which [AdFormat] this slot renders as.
 *
 * Example (host):
 * ```
 * enum class AppAds(
 *     override val rcKey: String,
 *     override val fallbackResId: Int,
 *     override val format: AdFormat,
 * ) : AdSlot {
 *     Home("AD_NATIVE_HOME", R.string.ad_unit_native_home, AdFormat.NativeCard),
 *     AppOpen("AD_APP_OPEN", R.string.ad_unit_app_open, AdFormat.AppOpen),
 * }
 * ```
 */
interface AdSlot {
    val rcKey: String

    @get:StringRes
    val fallbackResId: Int

    val format: AdFormat
}
