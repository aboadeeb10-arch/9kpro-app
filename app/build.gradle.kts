plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "tv.ninekpro.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "tv.ninekpro.app"
        minSdk = 23
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.0"
        buildConfigField("String", "PANEL_URL", "\"https://9kpro-panel.vercel.app\"")
        vectorDrawables.useSupportLibrary = true
        // Phones + Android TV boxes are ARM. Dropping x86 halves the APK (libVLC ships one 40 MB blob per CPU).
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    // Two distributions of the same app: "sideload" = /app APK (Crash Fix visible); "play" = Google Play bundle (Crash Fix hidden)
    flavorDimensions += "dist"
    productFlavors {
        create("sideload") { dimension = "dist"; buildConfigField("boolean", "PLAY_BUILD", "false") }
        create("play") { dimension = "dist"; buildConfigField("boolean", "PLAY_BUILD", "true") }
    }
    signingConfigs {
        create("release") {
            val ks = System.getenv("KEYSTORE_PATH")
            if (ks != null) {
                storeFile = file(ks); storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS"); keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            // R8 shrinking ON (v0.8.3+). proguard-rules.pro keeps only our own entry points + libVLC's JNI classes (libraries ship consumer rules); both mapping files are attached to each build.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // On CI a release MUST be signed with the permanent key: a debug-signed APK/AAB would not install over the
            // customers' app and Play would reject it. Locally (no JDK/keystore) the debug key is still allowed for testing.
            val onCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") != null
            if (System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
            else if (onCi) error("release build without KEYSTORE_PATH — secrets KEYSTORE_B64 / KEYSTORE_PASSWORD missing in CI")
            else signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
        // Sideload APK: compress native libs inside the APK (much smaller download from the panel site).
        // The Play flavor overrides this below — Play serves split APKs already compressed, and legacy packaging would
        // make Android extract libVLC to disk (stored twice on the device).
        jniLibs.useLegacyPackaging = true
    }
}

androidComponents {
    onVariants(selector().withFlavor("dist" to "play")) { variant ->
        variant.packaging.jniLibs.useLegacyPackaging.set(false)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    implementation("org.videolan.android:libvlc-all:3.6.5")
    implementation("com.google.zxing:core:3.5.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
