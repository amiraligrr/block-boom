# Myket / Adivery keep rules for production (R8 fullMode)
-keep class ir.myket.billingclient.** { *; }
-keep class com.adivery.sdk.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }
# Keep data/domain models for serialization / prefs reflection
-keep class ir.amiraligrr.blockboom.data.** { *; }
-keep class ir.amiraligrr.blockboom.domain.** { *; }
-keep class ir.amiraligrr.blockboom.utils.** { *; }
-keep class androidx.navigation.** { *; }

# Keep BuildConfig for IAB key (used at runtime)
-keep class ir.amiraligrr.blockboom.BuildConfig { *; }

# Compose & Kotlin metadata
-keep class androidx.compose.** { *; }
-keep class kotlin.** { *; }
-keep class kotlin.jvm.functions.Function1 { *; }
-keep class kotlinx.coroutines.** { *; }

# Optimizations
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-optimizationpasses 5
-dontpreverify
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Suppress noisy warnings (Adivery pulls old GMS deps)
-dontwarn com.adivery.sdk.**
-dontwarn ir.myket.billingclient.**
-dontwarn com.google.android.gms.**
-dontwarn androidx.lifecycle.**
-dontwarn kotlin.**

# Keep native methods & enums
-keepclassmembers,allowshrinking,allowobfuscation class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}