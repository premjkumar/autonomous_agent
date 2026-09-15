package com.example.roboqwen  

import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import java.net.HttpURLConnection  
import java.net.URL  
import java.net.URLDecoder  
import java.util.regex.Pattern  

class YoutubeExtractor {  
    private val playerResponsePattern = Pattern.compile("var\\s+ytInitialPlayerResponse\\s*=\\s*(\\{.*?\\});")  

    suspend fun extractAudioUrl(searchQuery: String): Pair<String, String>? = withContext(Dispatchers.IO) {  
        try {  
            val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")  
            val connection = URL("https://youtube.com").openConnection() as HttpURLConnection  
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")  
              
            val html = connection.inputStream.bufferedReader().use { it.readText() }  
            val videoIdMatch = Regex("/watch\\?v=([a-zA-Z0-9_-]{11})").find(html) ?: return@withContext null  
              
            return@withContext fetchStreamInfo(videoIdMatch.groupValues[1])  
        } catch (e: Exception) { null }  
    }  

    private fun fetchStreamInfo(videoId: String): Pair<String, String>? {  
        try {  
            val conn = URL("https://youtube.com/watch?v=$videoId").openConnection() as HttpURLConnection  
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")  
            val html = conn.inputStream.bufferedReader().use { it.readText() }  
              
            val matcher = playerResponsePattern.matcher(html)  
            if (matcher.find()) {  
                val json = matcher.group(1)  
                  
                val urlMatch = Regex("\"url\":\"(https://[^\"]*mime=audio[^\"]*)\"").find(json)  
                val titleMatch = Regex("\"title\":\"([^\"]*)\"").find(json)  
                  
                if (urlMatch != null) {  
                    val directUrl = URLDecoder.decode(urlMatch.groupValues[1].replace("\\u0026", "&"), "UTF-8")  
                    val title = titleMatch?.groupValues?.get(1) ?: "Ad-Free Streaming Track"  
                    return Pair(title, directUrl)  
                }  
            }  
        } catch (e: Exception) {}  
        return null  
    }  
}
