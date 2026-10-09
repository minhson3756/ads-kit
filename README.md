# adskit

A small, reusable Android ads library (Google Mobile Ads + UMP consent + Firebase
Remote Config gating) extracted from PDF Toolkit. Hilt-free, Compose-first.

**Formats:** adaptive banner · compact native card · banner-height native ·
interstitial · app-open. Each slot is gated by a Remote Config master switch
(`ADS_ACTIVE`) plus a per-slot `{"id": "...", "enable": true}` JSON param, with a
per-flavor resource fallback. Themeable (accent color + font) from any host app, or
bring your own native layout (custom native placements, since 0.2.0).

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
Firebase is optional. With a `google-services.json`, Remote Config drives `ADS_ACTIVE`
and the per-slot ids. Without one, every slot uses its resource id and `ADS_ACTIVE`
is `AdsConfig.defaultAdsActive` (default false, so set it to true for apps that ship
without Firebase).

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

// Pro removes ads; placements disappear (or come back) as soon as it changes
AdsKit.isPro = billing.isSubscribed

// Debug only: simulate an EEA user to see the consent form + Privacy options
// AdsConfig(..., consentDebug = if (BuildConfig.DEBUG) ConsentDebug(ConsentDebug.Geography.EEA, resetOnStart = true) else null)

// EEA/UK: show a "Privacy options" row while required
val required by AdsKit.privacyOptionsRequired.collectAsStateWithLifecycle()
if (required) SettingsRow("Privacy options") { AdsKit.showPrivacyOptions(activity) }
```

### Custom native layouts

Build your own `NativeAdView` (XML or code) and let adskit handle loading, consent,
Remote Config and Pro gating:
```kotlin
CustomNativeAd(
    slot = AppAds.Feed,
    factory = { ctx -> LayoutInflater.from(ctx).inflate(R.layout.my_native, null) as NativeAdView },
    bind = { view, ad ->
        // fills + registers the asset views, hides missing ones, then setNativeAd(ad)
        view.bindNativeAd(
            ad,
            headline = view.findViewById(R.id.headline),
            body = view.findViewById(R.id.body),
            callToAction = view.findViewById(R.id.cta),
            icon = view.findViewById(R.id.icon),
            media = view.findViewById(R.id.media),
        )
    },
    request = NativeAdRequest(mediaAspectRatio = NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE),
    placeholder = { MyShimmer() },
)
```
Inside a lazy list, hoist the load to the screen so scrolling doesn't reload the ad:
```kotlin
val ad = rememberNativeAd(AppAds.Feed).adOrNull     // NativeAdLoad: Hidden / Loading / Loaded / Failed
LazyColumn {
    // …
    if (ad != null) item { NativeAdHost(ad, factory = ::buildMyAdView) { view, a -> bindMyAd(view, a) } }
}
```
`play-services-ads` is an `api` dependency, so `NativeAd` / `NativeAdView` / `MediaView`
are available to the host without declaring it.

## Remote Config

Master switch `ADS_ACTIVE` (BOOLEAN, default `AdsConfig.defaultAdsActive`, OFF unless set). One JSON param per slot, keyed
by `rcKey`:
```json
{ "id": "ca-app-pub-XXXX/YYYY", "enable": true }
```
Plus `ADS_INTERSTITIAL_MIN_INTERVAL_SEC` (NUMBER). Ads stay hidden until you flip
`ADS_ACTIVE` on (its default is `AdsConfig.defaultAdsActive`).

## Notes

- Adding the ads SDK re-adds the `AD_ID` permission — declare **Advertising ID =
  Yes** + update Data safety in Play Console for each consumer app.
- The banner-height native has no MediaView, so video-only mediation won't fill it
  (fine for AdMob-direct).
