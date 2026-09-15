package com.example.roboqwen  

import android.util.Log  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import org.json.JSONArray  
import org.json.JSONObject  
import java.net.HttpURLConnection  
import java.net.URL  
import java.net.URLEncoder  

data class AgentState(  
    val messages: MutableList<JSONObject> = mutableListOf(),  
    var currentLatitude: Double = 0.0,  
    var currentLongitude: Double = 0.0  
)  

class RoboAgentGraph(  
    private val youtubeExtractor: YoutubeExtractor,  
    private val audioPlayer: RoboAudioPlayer,  
    private val onStatusChange: (String) -> Unit,  
    private val onAlarmTriggered: (String, Int) -> Unit  
) {  
    private var currentPersona: String = "You are a warm, helpful, and friendly robotic companion."  
    private var targetEndpoint: String = "http://10.0.2.2:11434/api/chat"  
      
    private var cachedLat: Double = 0.0  
    private var cachedLon: Double = 0.0  
      
    private var interactionTurnCount: Int = 0  
    private val maxConversationWindow = 10   

    fun setPersona(persona: String) { currentPersona = persona }  
    fun updateTargetEndpoint(url: String) { targetEndpoint = url }  
    fun updateHardwareLocation(lat: Double, lon: Double) {  
        cachedLat = lat  
        cachedLon = lon  
    }  
      
    fun resetInteractionTurnCount() {  
        interactionTurnCount = 0  
        Log.d("ROBO_AGENT_THOUGHT", "[WELLNESS] Fatigue monitoring tracker reset.")  
    }  

    private suspend fun callModel(state: AgentState): AgentState = withContext(Dispatchers.IO) {  
        if (interactionTurnCount >= 5) {  
            Log.d("ROBO_AGENT_THOUGHT", "[INTERCEPT] Fatigue limits breached ($interactionTurnCount steps).")  
            onStatusChange("STATUS // WELLNESS INTERCEPT ACTIVE...")  
              
            val fatigueInterceptMessage = JSONObject().apply {  
                put("role", "assistant")  
                put("content", "⚠️ ROBO-QWEN WELLNESS INTERCEPT: Systematic scan indicates you have submitted more than 5 consecutive text queries without a resting window. Shifting track. Stand up, stretch, look away from the screen, and let your cognitive fields rest for 5 minutes.")  
            }  
            state.messages.add(fatigueInterceptMessage)  
              
            withContext(Dispatchers.Main) { onAlarmTriggered("Health Notifier: Forced Micro-Break", 5) }  
            resetInteractionTurnCount()  
            return@withContext state  
        }  

        onStatusChange("STATUS // EXECUTING INFERENCE PATHWAY...")  
        trimContextState(state)  
          
        try {  
            val connection = URL(targetEndpoint).openConnection() as HttpURLConnection  
            connection.requestMethod = "POST"  
            connection.setRequestProperty("Content-Type", "application/json")  
            connection.doOutput = true  

            val messagesArray = JSONArray()  
            messagesArray.put(JSONObject().put("role", "system").put("content", currentPersona))  
            state.messages.forEach { messagesArray.put(it) }  

            val toolsArray = JSONArray().apply {  
                put(createCloudToolSchema("live_web_search", "Searches the live web for general information."))  
                put(createCloudToolSchema("play_youtube_audio", "Streams music audio parameters from YouTube."))  
                put(createCloudToolSchema("get_nearby_coffee", "Finds the closest coffee spots based on your active GPS coordinates."))  
                put(createCloudToolSchema("check_rain_alert", "Queries weather status to see if rain is forecasted."))  
                put(createToolWithArgsSchema("register_device_alarm", "Sets a cooking, sleeping, or memory alarm reminder.",   
                    listOf("label" to "Type of alarm", "minutes" to "Duration in minutes")))  
                put(createToolWithArgsSchema("register_health_notifier", "Schedules wellness reminders.",   
                    listOf("type" to "The health action type", "interval_minutes" to "Time in minutes before reminder")))  
            }  

            val payload = JSONObject().apply {  
                put("model", "qwen2.5:3b")  
                put("messages", messagesArray)  
                put("tools", toolsArray)  
                put("stream", false)  
            }  

            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }  
            val rawResponse = connection.inputStream.bufferedReader().use { it.readText() }  
            val messageOutput = JSONObject(rawResponse).getJSONObject("message")  
              
            state.messages.add(messageOutput)  
        } catch (e: Exception) {  
            state.messages.add(JSONObject().put("role", "assistant").put("content", "Neural link transport window dropped out."))  
        }  
        return@withContext state  
    }  

    private suspend fun executeTools(state: AgentState): AgentState = withContext(Dispatchers.IO) {  
        val lastMessage = state.messages.lastOrNull() ?: return@withContext state  
        if (!lastMessage.has("tool_calls")) return@withContext state  

        val toolCalls = lastMessage.getJSONArray("tool_calls")  
        for (i in 0 until toolCalls.length()) {  
            val call = toolCalls.getJSONObject(i)  
            val function = call.getJSONObject("function")  
            val toolName = function.getString("name")  
            val args = function.optJSONObject("arguments") ?: JSONObject()  

            val toolResult = when (toolName) {  
                "get_nearby_coffee" -> runCoffeeFinderTool(cachedLat, cachedLon)  
                "check_rain_alert" -> runRainCheckTool(cachedLat, cachedLon)  
                "register_device_alarm" -> {  
                    val label = args.optString("label", "General Alert")  
                    val minutes = args.optInt("minutes", 5)  
                    withContext(Dispatchers.Main) { onAlarmTriggered(label, minutes) }  
                    "System Confirmation: Native Android $label alarm set for $minutes minutes."  
                }  
                "register_health_notifier" -> {  
                    val healthType = args.optString("type", "Wellness Alert")  
                    val interval = args.optInt("interval_minutes", 45)  
                    Log.d("ROBO_AGENT_THOUGHT", "[HEALTH] Intercepted wellness intent. Action: $healthType")  
                    withContext(Dispatchers.Main) { onAlarmTriggered("Health Notifier: $healthType", interval) }  
                    "System Confirmation: Scheduled '$healthType' reminder for $interval minutes."  
                }  
                "live_web_search" -> runWebSearchTool(args.optString("query"))  
                "play_youtube_audio" -> runYoutubeTool(args.optString("query"))  
                else -> "Unknown hardware target endpoint."  
            }  

            state.messages.add(JSONObject().apply {  
                put("role", "tool")  
                put("name", toolName)  
                put("content", toolResult)  
            })  
        }  
        return@withContext state  
    }  

    suspend fun compileAndRunGraph(userPrompt: String): String {  
        interactionTurnCount++  
        Log.d("ROBO_AGENT_THOUGHT", "[METRICS] Active conversation turn count: $interactionTurnCount")  

        var state = AgentState(currentLatitude = cachedLat, currentLongitude = cachedLon)  
        val contextualizedPrompt = "Current GPS context: [Lat: $cachedLat, Lon: $cachedLon]. User query: $userPrompt"  
        state.messages.add(JSONObject().put("role", "user").put("content", contextualizedPrompt))  

        var loops = 0  
        while (loops < 4) {  
            state = callModel(state)  
            val lastMsg = state.messages.last()  
            if (lastMsg.has("tool_calls") && lastMsg.getJSONArray("tool_calls").length() > 0) {  
                state = executeTools(state)  
                loops++  
            } else {  
                break  
            }  
        }  
        return state.messages.lastOrNull()?.optString("content") ?: "Runtime sensor logic fault."  
    }  

    private fun runCoffeeFinderTool(lat: Double, lon: Double): String {  
        if (lat == 0.0 || lon == 0.0) return "GPS location data is currently unavailable."  
        return try {  
            val query = "[out:json];node(around:1500,$lat,$lon)[amenity=cafe];out 3;"  
            val encodedQuery = URLEncoder.encode(query, "UTF-8")  
            val conn = URL("https://overpass-api.de/api/interpreter?data=$encodedQuery").openConnection() as HttpURLConnection  
            val response = conn.inputStream.bufferedReader().use { it.readText() }  
            val elements = JSONObject(response).getJSONArray("elements")  
            if (elements.length() == 0) return "No coffee spots found on this route within a 1.5km range."  
            val resultBuilder = StringBuilder("Found nearby coffee spots on your route:\n")  
            for (i in 0 until minOf(elements.length(), 3)) {  
                val tags = elements.getJSONObject(i).optJSONObject("tags")  
                resultBuilder.append("- ${tags?.optString("name", "Café")}\n")  
            }  
            resultBuilder.toString()  
        } catch (e: Exception) { "Failed to parse OpenStreetMap records." }  
    }  

    private fun runRainCheckTool(lat: Double, lon: Double): String {  
        return try {  
            val urlConn = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=precipitation").openConnection() as HttpURLConnection  
            val rawJson = urlConn.inputStream.bufferedReader().use { it.readText() }  
            val precipitation = JSONObject(rawJson).getJSONObject("current").getDouble("precipitation")  
            if (precipitation > 0.1) "⚠️ ALERT: Live precipitation indicates active rain ($precipitation mm) at your coordinates." else "No active rain alerts."  
        } catch (e: Exception) { "Weather connection failed." }  
    }  

    private fun runWebSearchTool(query: String): String {  
        return try {  
            val encoded = URLEncoder.encode(query, "UTF-8")  
            val html = URL("https://html.duckduckgo.com/html/?q=$encoded").openConnection().apply { setRequestProperty("User-Agent", "Mozilla/5.0") }.inputStream.bufferedReader().use { it.readText() }  
            val plainText = html.replace(Regex("<[^>]*>"), " ").split(Regex("\\s+")).take(35).joinToString(" ")  
            "Search trace: $plainText"  
        } catch (e: Exception) { "Scraper connection failed." }  
    }  

    private suspend fun runYoutubeTool(query: String): String {  
        val result = youtubeExtractor.extractAudioUrl(query) ?: return "Media unavailable."  
        withContext(Dispatchers.Main) { audioPlayer.playAudioStream(result.second) }  
        return "Streaming track '${result.first}' without ads."  
    }  

    private fun trimContextState(state: AgentState) {  
        if (state.messages.size > maxConversationWindow) {  
            val rawMessagesToKeep = state.messages.takeLast(maxConversationWindow - 2)  
            state.messages.clear()  
            state.messages.addAll(rawMessagesToKeep)  
        }  
    }  

    private fun createCloudToolSchema(name: String, description: String): JSONObject {  
        return JSONObject().put("name", name).put("description", description).put("parameters", JSONObject().put("type", "object").put("properties", JSONObject().put("query", JSONObject().put("type", "string").put("description", "Query string"))))  
    }  

    private fun createToolWithArgsSchema(name: String, description: String, args: List<Pair<String, String>>): JSONObject {  
        val props = JSONObject()  
        val req = JSONArray()  
        args.forEach { (argName, argDesc) ->  
            props.put(argName, JSONObject().put("type", "string").put("description", argDesc))  
            req.put(argName)  
        }  
        return JSONObject().put("name", name).put("description", description).put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", req))  
    }  
}
