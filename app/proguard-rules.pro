# ---- 9K Pro TV release rules (R8) ----
# Only the app's own entry points and models are kept here. Libraries (Media3, Cast/GMS, MediaRouter, OkHttp, Coil,
# Compose, coroutines) ship their own consumer rules inside their AARs — library-wide "-keep ... { *; }" lines just
# bloat the APK and defeat shrinking. Add a keep ONLY for something the app itself looks up by name / reflection.

# Our data models / JSON helpers whole (Parcelable + classes referenced from the manifest must stay named)
-keep class tv.ninekpro.app.data.** { *; }
-keep class tv.ninekpro.app.** extends android.content.BroadcastReceiver { *; }
-keep class tv.ninekpro.app.** extends android.app.Service { *; }
-keep class tv.ninekpro.app.** extends android.appwidget.AppWidgetProvider { *; }
# Looked up by name from the manifest <meta-data> by the Cast SDK
-keep class tv.ninekpro.app.CastOptionsProvider { *; }
-keep class tv.ninekpro.app.MainActivity { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# libVLC: native code calls back into org.videolan.libvlc Java classes by name (JNI GetMethodID), and the AAR's
# consumer rules are not guaranteed. The Java side is tiny (the 40 MB is the .so, untouched by R8) — keep it whole.
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.**

# Media3 / ExoPlayer (consumer rules in the AAR)
-dontwarn androidx.media3.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coil
-dontwarn coil.**

# Google Cast / MediaRouter / TV provider (consumer rules in the AARs; our OptionsProvider is kept above)
-dontwarn com.google.android.gms.**
-dontwarn androidx.mediarouter.**
-dontwarn androidx.tvprovider.**

# ZXing (pure Java, no reflection)
-dontwarn com.google.zxing.**

# Kotlin / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Compose
-dontwarn androidx.compose.**
