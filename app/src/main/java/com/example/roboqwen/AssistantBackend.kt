package com.example.roboqwen  

import android.annotation.SuppressLint  
import android.content.Context  
import android.content.Intent  
import android.os.Bundle  
import android.provider.AlarmClock  
import android.speech.RecognitionListener  
import android.speech.RecognizerIntent  
import android.speech.SpeechRecognizer  
import android.speech.tts.TextToSpeech  
import android.location.LocationManager  
import android.location.Location  
import android.location.LocationListener  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import java.util.Locale  

class AssistantBackend(  
    private val context: Context,  
    private val onStatusUpdate: (String) -> Unit,  
    private val onSpeechTranscribed: (String) -> Unit,  
    private val onResponseReceived: (String) -> Unit  
) {  
    private var tts: TextToSpeech? = null  
    private var speechRecognizer: SpeechRecognizer? = null  
    private var locationManager: LocationManager? = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager  
      
    private val youtubeExtractor = YoutubeExtractor()  
    private val audioPlayer = RoboAudioPlayer(context)  
      
    private val agentGraph = RoboAgentGraph(youtubeExtractor, audioPlayer, onStatusUpdate) { alarmLabel, durationMinutes ->  
        createNewHardwareAlarm(alarmLabel, durationMinutes)  
    }  

    var voiceSpeed: Float = 1.0f  
    var voiceGenderLocale: Locale = Locale.US  

    init {  
        tts = TextToSpeech(context) { status ->  
            if (status == TextToSpeech.SUCCESS) {  
                tts?.language = voiceGenderLocale  
                tts?.setSpeechRate(voiceSpeed)  
            }  
        }  
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)  
        setupSpeechListener()  
        initializeLocationUpdates()  
    }  

    @SuppressLint("MissingPermission")  
    private fun initializeLocationUpdates() {  
        try {  
            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)  
            lastKnown?.let { agentGraph.updateHardwareLocation(it.latitude, it.longitude) }  
              
            locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, object : LocationListener {  
                override fun onLocationChanged(location: Location) {  
                    agentGraph.updateHardwareLocation(location.latitude, location.longitude)  
                }  
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}  
                override fun onProviderEnabled(p: String) {}  
                override fun onProviderDisabled(p: String) {}  
            })  
        } catch (e: Exception) { e.printStackTrace() }  
    }  

    private fun createNewHardwareAlarm(label: String, durationInMinutes: Int) {  
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {  
            if (label.contains("Health Notifier")) {  
                putExtra(AlarmClock.EXTRA_MESSAGE, "$label (Drink Water / Stand Up!)")  
            } else {  
                putExtra(AlarmClock.EXTRA_MESSAGE, "Robo-Qwen Memory Alert: $label")  
            }  
            putExtra(AlarmClock.EXTRA_LENGTH, durationInMinutes * 60)  
            putExtra(AlarmClock.EXTRA_MINUTES, durationInMinutes)  
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)  
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)  
        }  
        context.startActivity(intent)  
    }  

    fun updateNetworkEndpoint(url: String) { agentGraph.updateTargetEndpoint(url) }  
    fun resetTurnCounter() { agentGraph.resetInteractionTurnCount() }  

    fun updateVoiceSettings(gender: String, speed: String, persona: String) {  
        val configuredPersona = if (persona == "Friendly Robot") {  
            "You are a warm, helpful assistant. Keep your responses short and concise since they are spoken aloud."  
        } else {  
            "You are a sharp, analytical robotic unit. Keep responses concise and focused on telemetry data logs."  
        }  
        agentGraph.setPersona(configuredPersona)  
          
        voiceSpeed = when (speed) {  
            "Slow" -> 0.7f  
            "Fast" -> 1.4f  
            else -> 1.0f  
        }  
        tts?.setSpeechRate(voiceSpeed)  
        voiceGenderLocale = if (gender == "Female Voice") Locale.UK else Locale.US  
        tts?.language = voiceGenderLocale  
    }  

    suspend fun queryQwenModel(prompt: String) = withContext(Dispatchers.IO) {  
        val finalResponseText = agentGraph.compileAndRunGraph(prompt)  
        withContext(Dispatchers.Main) {  
            onResponseReceived(finalResponseText)  
            speak(finalResponseText)  
        }  
    }  

    fun startVoiceCapture() {  
        onStatusUpdate("STATUS // ENGAGING MICROPHONE SENSORS...")  
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {  
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)  
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())  
        }  
        speechRecognizer?.startListening(intent)  
    }  

    fun speak(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) }  

    private fun setupSpeechListener() {  
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {  
            override fun onReadyForSpeech(params: Bundle?) {}  
            override fun onBeginningOfSpeech() {}  
            override fun onRmsChanged(rmsdB: Float) {}  
            override fun onBufferReceived(buffer: ByteArray?) {}  
            override fun onEndOfSpeech() {}  
            override fun onError(error: Int) { onStatusUpdate("SYSTEM READY // IDLE") }  
            override fun onResults(results: Bundle?) {  
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)  
                if (!matches.isNullOrEmpty()) onSpeechTranscribed(matches[0])  
            }  
            override fun onPartialResults(p0: Bundle?) {}  
            override fun onEvent(p0: Int, p1: Bundle?) {}  
        })  
    }  

    fun onDestroy() {  
        tts?.stop()  
        tts?.shutdown()  
        speechRecognizer?.destroy()  
        audioPlayer.releasePlayer()  
    }  
}
