package com.xnetheron.adskit.core

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * UMP (Google User Messaging Platform) consent gate. Gathers GDPR/EEA consent
 * BEFORE any ad is requested. Where a form isn't required, [canRequestAds] is
 * already true and no form is shown.
 */
internal class ConsentManager(private val context: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    val canRequestAds: Boolean get() = consentInformation.canRequestAds()

    /**
     * Refresh consent info + show the form if required, then invoke [onResolved]
     * (on both success and failure — [canRequestAds] may already be true outside
     * the EEA).
     */
    fun gather(activity: Activity, onResolved: () -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    onResolved()
                }
            },
            { _ -> onResolved() },
        )
    }
}
