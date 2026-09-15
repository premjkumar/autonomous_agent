package com.example.roboqwen

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RoboAgentGraph(
    private val youtubeExtractor: YoutubeExtractor,
    private val audioPlayer: RoboAudioPlayer,
    private val onStatusUpdate: (String) -> Unit,
    private val onTriggerAlarm: (String, Int) -> Unit
) {
    private var targetEndpoint = "http://127.0.0.1:8000/chat"
    private var systemPersona = ""
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    fun updateTargetEndpoint(url: String) {
        val trimmed = url.trim().removeSuffix("/")
        targetEndpoint = if (trimmed.endsWith("/chat")) trimmed else "$trimmed/chat"
    }

    fun setPersona(persona: String) { systemPersona = persona }
    fun updateHardwareLocation(lat: Double, lon: Double) {
        latitude = lat
        longitude = lon
    }
    fun resetInteractionTurnCount() {}

    suspend fun compileAndRunGraph(userPrompt: String): String = withContext(Dispatchers.IO) {
        try {
            onStatusUpdate("STATUS // SENDING PROMPT...")
            val url = URL(targetEndpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 60000
            conn.doOutput = true

            val payload = JSONObject().apply {
                put("prompt", userPrompt)
                if (systemPersona.isNotBlank()) put("persona", systemPersona)
            }

            OutputStreamWriter(conn.outputStream).use { os ->
                os.write(payload.toString())
                os.flush()
            }

            if (conn.responseCode == 200) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)
                val responseText = json.optString("response", "No response text received.")
                val action = json.optString("action", "NONE")

                onStatusUpdate("SYSTEM READY // IDLE")

                if (action == "SET_ALARM") {
                    val duration = json.optInt("duration", 5)
                    val label = json.optString("label", "RoboQwen Alarm")
                    withContext(Dispatchers.Main) {
                        onTriggerAlarm(label, duration)
                    }
                }

                responseText
            } else {
                onStatusUpdate("STATUS // ERROR ${conn.responseCode}")
                "Server returned HTTP error code ${conn.responseCode}"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onStatusUpdate("STATUS // CONNECTION FAILED")
            "Unable to reach host backend. Ensure ADB reverse tunnel is active."
        }
    }
}
