# adskit

A small, reusable Android ads library (Google Mobile Ads + UMP consent + Firebase
Remote Config gating) extracted from PDF Toolkit. Hilt-free, Compose-first.

**Formats:** adaptive banner · compact native card · banner-height native ·
interstitial · app-open. Each slot is gated by a Remote Config master switch
(`ADS_ACTIVE`) plus a per-slot `{"id": "...", "enable": true}` JSON param, with a
per-flavor resource fallback. Themeable (accent color + font) from any host app.

## Install (JitPack)

`settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google(); mavenCentral()
        maven("https://jitpack.io")
    }
}
```
App `build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.minhson3756:adskit:<tag>")
}
android {
    defaultConfig {
        // your real AdMob App ID
        manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"
    }
}
```
The host app must have Firebase configured (a `google-services.json`) — the library
reads Remote Config through it.

## Use

Define your slots (ids stay in the host as per-flavor string resources):
```kotlin
enum class AppAds(
    override val rcKey: String,
    override val fallbackResId: Int,
    override val format: AdFormat,
) : AdSlot {
    HomeNative("AD_NATIVE_HOME", R.string.ad_unit_native_home, AdFormat.NativeCard),
    EditBanner("AD_NATIVE_EDIT", R.string.ad_unit_native_edit, AdFormat.NativeBanner),
    HomeBanner("AD_BANNER_HOME", R.string.ad_unit_banner_home, AdFormat.Banner),
    ToolDone("AD_INTERSTITIAL_DONE", R.string.ad_unit_interstitial_done, AdFormat.Interstitial),
    AppOpen("AD_APP_OPEN", R.string.ad_unit_app_open, AdFormat.AppOpen),
}
```
Wire it up:
```kotlin
// Application.onCreate
AdsKit.initialize(this, AdsConfig(
    slots = AppAds.entries.toList(),
    accentColor = 0xFFF43F5E.toInt(),          // optional
    fontRes = R.font.my_font,                  // optional (null = system)
))

// launcher Activity.onCreate
AdsKit.start(this)

// Compose
NativeAdCard(slot = AppAds.HomeNative)
NativeBannerAd(slot = AppAds.EditBanner)
AdaptiveBannerAd(slot = AppAds.HomeBanner)

// Interstitial (never blocks navigation)
AdsKit.preloadInterstitial(AppAds.ToolDone)
AdsKit.maybeShowInterstitial(AppAds.ToolDone, activity) { navigateOn() }

// Before launching your own Activity (SAF picker / scanner / share / permission)
AdsKit.suspendNextForeground()

// Pro removes ads
AdsKit.isPro = billing.isSubscribed
```

## Remote Config

Master switch `ADS_ACTIVE` (BOOLEAN, default OFF). One JSON param per slot, keyed
by `rcKey`:
```json
{ "id": "ca-app-pub-XXXX/YYYY", "enable": true }
```
Plus `ADS_INTERSTITIAL_MIN_INTERVAL_SEC` (NUMBER). Ads stay hidden until you flip
`ADS_ACTIVE` on.

## Notes

- Adding the ads SDK re-adds the `AD_ID` permission — declare **Advertising ID =
  Yes** + update Data safety in Play Console for each consumer app.
- The banner-height native has no MediaView, so video-only mediation won't fill it
  (fine for AdMob-direct).
