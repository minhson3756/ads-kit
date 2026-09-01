package com.xnetheron.adskit

/** The ad format a slot renders as. */
enum class AdFormat {
    /** Anchored adaptive banner. */
    Banner,

    /** Compact in-feed native card (120dp media + headline · rating · CTA). */
    NativeCard,

    /** Banner-height native (icon + headline + CTA, no MediaView). */
    NativeBanner,

    /** Full-screen interstitial. */
    Interstitial,

    /** App-open ad (foreground return). At most one slot should use this. */
    AppOpen,
}
