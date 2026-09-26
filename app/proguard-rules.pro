# ---- 9K Pro TV release rules (R8) ----
# Keep our data models / JSON helpers whole (read with reflection-free code, but Parcelable/receivers must stay named)
-keep class tv.ninekpro.app.data.** { *; }
-keep class tv.ninekpro.app.** extends android.content.BroadcastReceiver { *; }
-keep class tv.ninekpro.app.** extends android.app.Service { *; }
-keep class tv.ninekpro.app.** extends android.appwidget.AppWidgetProvider { *; }
-keep class tv.ninekpro.app.CastOptionsProvider { *; }
-keep class tv.ninekpro.app.MainActivity { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# libVLC talks to Java through JNI — keep it whole
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }

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

# Google Cast / MediaRouter (OptionsProvider is looked up by name from the manifest)
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn com.google.android.gms.**
-keep class androidx.mediarouter.** { *; }

# TV provider (home-screen channels)
-keep class androidx.tvprovider.** { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Kotlin / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Compose
-dontwarn androidx.compose.**
