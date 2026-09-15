package com.example.roboqwen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var locationManager: LocationManager? = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val youtubeExtractor = YoutubeExtractor()
    private val audioPlayer = RoboAudioPlayer(context)

    private val agentGraph = RoboAgentGraph(youtubeExtractor, audioPlayer, onStatusUpdate) { alarmLabel, durationMinutes ->
        createNewHardwareAlarm(alarmLabel, durationMinutes)
    }

    var voiceSpeed: Float = 1.0f
    var voiceGenderLocale: Locale = Locale.US
    private var isListening = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = voiceGenderLocale
                tts?.setSpeechRate(voiceSpeed)
            }
        }

        initSpeechRecognizer()
        initializeLocationUpdates()
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                setupSpeechListener()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeLocationUpdates() {
        try {
            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            lastKnown?.let { agentGraph.updateHardwareLocation(it.latitude, it.longitude) }

            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                10f,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        agentGraph.updateHardwareLocation(location.latitude, location.longitude)
                    }
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNewHardwareAlarm(label: String, durationInMinutes: Int) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
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
        agentGraph.setPersona(persona)
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
        mainHandler.post {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                }
                onStatusUpdate("STATUS // LISTENING...")
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                isListening = true
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                initSpeechRecognizer()
                onStatusUpdate("SYSTEM READY // IDLE")
            }
        }
    }

    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun setupSpeechListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {
                onStatusUpdate("STATUS // RECORDING AUDIO...")
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                onStatusUpdate("STATUS // PROCESSING SPEECH...")
            }
            override fun onError(error: Int) {
                isListening = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    else -> "Voice recognition reset"
                }
                onStatusUpdate("SYSTEM READY // $errorMsg")
                initSpeechRecognizer()
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val transcribedText = matches[0]
                    onSpeechTranscribed(transcribedText)
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        queryQwenModel(transcribedText)
                    }
                } else {
                    onStatusUpdate("SYSTEM READY // IDLE")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        audioPlayer.releasePlayer()
    }
}
