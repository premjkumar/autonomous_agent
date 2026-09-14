# app/proguard-rules.pro
# ProGuard rules for code shrinking and obfuscation.
# These rules ensure that specific classes and structures are not stripped or obfuscated,
# especially those related to Compose, JSON handling, Media3, and Speech frameworks.

# Keep androidx.compose.runtime elements intact for Compose runtime stability
-keep class androidx.compose.runtime.** { *; }

# Keep JSON related classes intact
-keep class org.json.** { *; }

# Keep Media3 class structures intact
-keep class androidx.media3.** { *; }

# Keep Android Speech peripheral frameworks intact
-keep class android.speech.** { *; }
