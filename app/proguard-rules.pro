-keepclassmembers class * {  
    @androidx.compose.runtime.Composable *;  
    @androidx.compose.runtime.ReadOnlyComposable *;  
}  
-keep class androidx.compose.runtime.Recomposer { *; }  

-keep class org.json.JSONObject { *; }  
-keep class org.json.JSONArray { *; }  

-keep class androidx.media3.exoplayer.** { *; }  
-keep class androidx.media3.common.** { *; }  
-dontwarn androidx.media3.**  

-keep class android.speech.SpeechRecognizer { *; }  
-keep class android.speech.tts.TextToSpeech { *; }
