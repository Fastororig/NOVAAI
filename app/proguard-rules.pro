# Proguard rules for Nova AI
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.nova.ai.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Jetpack Compose
-keepclassmembers class * extends androidx.compose.runtime.State { *; }
