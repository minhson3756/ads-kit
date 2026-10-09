package com.xnetheron.adskit

/**
 * Debug-only UMP testing: pretend the device is in [geography] so the consent form and the
 * "Privacy options" entry can be checked from anywhere. Pass it only in debug builds
 * (e.g. `consentDebug = if (BuildConfig.DEBUG) ConsentDebug(ConsentDebug.Geography.EEA) else null`).
 *
 * @param resetOnStart clear the stored consent once per app process, so the form shows on every launch.
 */
data class ConsentDebug(
    val geography: Geography,
    val resetOnStart: Boolean = false,
) {
    enum class Geography { EEA, RegulatedUsState, Other }
}
