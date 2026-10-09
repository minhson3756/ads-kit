plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "com.xnetheron.adskit"
    compileSdk = 37

    defaultConfig {
        minSdk = 24

        // Default AdMob App ID (Google TEST id) so the library builds standalone.
        // Consumer apps OVERRIDE this with their real App ID:
        //   defaultConfig { manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXX~YYYY" }
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // Expose a single `release` variant (with sources) for publishing (JitPack).
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Firebase Remote Config — reads the host's Firebase config (host must have
    // google-services.json). Gates ADS_ACTIVE + per-slot {id,enable}.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)

    // Google Mobile Ads SDK + UMP consent. GMA is `api`: custom native placements take
    // NativeAd / NativeAdView / MediaView in the public API.
    api(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
}

// JitPack publishes com.github.<user>:ads-kit:<tag> (artifact = repo name). group/version are injected by
// JitPack from the repo + git tag; the defaults below make `publishToMavenLocal`
// usable during local development (./gradlew :adskit:publishToMavenLocal).
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.xnetheron"
                artifactId = "adskit"
                version = (project.findProperty("VERSION_NAME") as String?) ?: "0.2.0"
            }
        }
    }
}
