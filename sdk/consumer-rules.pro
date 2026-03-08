# SDK consumer ProGuard rules
# Applied automatically to any app that uses this AAR

-keep class com.voicebanking.sdk.VoiceBankingSDK { *; }
-keep class com.voicebanking.sdk.models.** { *; }

# Retrofit / OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.gson.** { *; }
