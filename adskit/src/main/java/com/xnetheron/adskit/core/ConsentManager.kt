package com.xnetheron.adskit.core

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.xnetheron.adskit.ConsentDebug
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UMP (Google User Messaging Platform) consent gate. Gathers GDPR/EEA consent
 * BEFORE any ad is requested. Where a form isn't required, [canRequestAds] is
 * already true and no form is shown.
 */
internal class ConsentManager(private val context: Context, private val debug: ConsentDebug? = null) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean get() = consentInformation.canRequestAds()

    private var debugResetDone = false

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** True when the user must be able to revisit consent (EEA/UK): show a "Privacy options" entry. */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private fun refreshPrivacyStatus() {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /** Re-open the consent form (the host's "Privacy options" row), then [onDone]. */
    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            refreshPrivacyStatus()
            onDone()
        }
    }

    /**
     * Refresh consent info + show the form if required, then invoke [onResolved]
     * (on both success and failure — [canRequestAds] may already be true outside
     * the EEA).
     */
    fun gather(activity: Activity, onResolved: () -> Unit) {
        val params = ConsentRequestParameters.Builder()
            .apply { debug?.let { setConsentDebugSettings(it.toSettings()) } }
            .build()
        // Once per process: start() runs again whenever the launcher activity is recreated.
        if (debug?.resetOnStart == true && !debugResetDone) {
            debugResetDone = true
            consentInformation.reset()
        }
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    refreshPrivacyStatus()
                    onResolved()
                }
            },
            { _ ->
                refreshPrivacyStatus()
                onResolved()
            },
        )
    }

    private fun ConsentDebug.toSettings(): ConsentDebugSettings =
        ConsentDebugSettings.Builder(context)
            .setDebugGeography(
                when (geography) {
                    ConsentDebug.Geography.EEA -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
                    ConsentDebug.Geography.RegulatedUsState -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
                    ConsentDebug.Geography.Other -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
                },
            )
            // Debug geography normally needs the device's hashed id; force testing on any device.
            .setForceTesting(true)
            .build()
}
