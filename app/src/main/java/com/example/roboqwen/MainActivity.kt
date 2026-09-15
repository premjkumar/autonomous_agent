package com.example.roboqwen  

import android.Manifest  
import android.content.Context  
import android.content.Intent  
import android.content.pm.PackageManager  
import android.os.Bundle  
import android.widget.Toast  
import androidx.activity.ComponentActivity  
import androidx.activity.compose.setContent  
import androidx.activity.result.contract.ActivityResultContracts  
import androidx.compose.foundation.background  
import androidx.compose.foundation.border  
import androidx.compose.foundation.layout.*  
import androidx.compose.foundation.lazy.LazyColumn  
import androidx.compose.foundation.lazy.items  
import androidx.compose.foundation.shape.RoundedCornerShape  
import androidx.compose.foundation.text.BasicTextField  
import androidx.compose.material3.*  
import androidx.compose.runtime.*  
import androidx.compose.ui.Alignment  
import androidx.compose.ui.Modifier  
import androidx.compose.ui.graphics.Color  
import androidx.compose.ui.platform.LocalContext  
import androidx.compose.ui.text.TextStyle  
import androidx.compose.ui.text.font.FontFamily  
import androidx.compose.ui.text.font.FontWeight  
import androidx.compose.ui.unit.dp  
import androidx.compose.ui.unit.sp  
import androidx.core.content.ContextCompat  
import androidx.core.content.FileProvider  
import androidx.lifecycle.lifecycleScope  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.delay  
import kotlinx.coroutines.launch  
import org.json.JSONArray  
import org.json.JSONObject  
import java.io.File  
import java.io.FileWriter  

class MainActivity : ComponentActivity() {  

    private lateinit var backend: AssistantBackend  
    private lateinit var nsdHelper: NsdHelper  
    private lateinit var database: RoboDatabase  
      
    private val chatMessages = mutableStateListOf<Pair<String, String>>()  
    private var systemStatus by mutableStateOf("SCANNING FOR QWEN 3B HOST...")  
    private var uiLocked by mutableStateOf(true)  

    private var eyeStrainWarningVisible by mutableStateOf(false)  

    private val bgMain = Color(0xFF0B0F19)  
    private val bgCard = Color(0xFF161D30)  
    private val accentCyan = Color(0xFF06B6D4)  
    private val accentPurple = Color(0xFFA855F7)  
    private val accentGreen = Color(0xFF10B981)  

    override fun onCreate(savedInstanceState: Bundle?) {  
        super.onCreate(savedInstanceState)  
          
        database = RoboDatabase.getDatabase(this)  

        lifecycleScope.launch {  
            database.chatHistoryDao().getAllMessagesFlow().collect { entities ->  
                chatMessages.clear()  
                if (entities.isEmpty()) {  
                    chatMessages.add(Pair("System", "Robo-Qwen 3.0 Core Memory Arrays Active."))  
                } else {  
                    entities.forEach { chatMessages.add(Pair(it.sender, it.text)) }  
                }  
            }  
        }  

        lifecycleScope.launch {  
            delay(20.toLong() * 60 * 1000)  
            eyeStrainWarningVisible = true  
            backend.speak("Ambient eye strain alert. Please redirect your vision parameters away from the console screen.")  
            database.chatHistoryDao().insertMessage(MessageEntity(  
                sender = "System Alert",   
                text = "👁️ AMBIENT OPTICAL ALERT: 20 minutes of continuous screen focus detected. Look at an object 20 feet away for 20 seconds."  
            ))  
        }  

        val requestPermissionLauncher = registerForActivityResult(  
            ActivityResultContracts.RequestPermission()  
        ) { isGranted ->  
            if (!isGranted) systemStatus = "CRITICAL // MIC PERMISSION DENIED"  
        }  

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {  
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)  
        }  

        backend = AssistantBackend(  
            context = this,  
            onStatusUpdate = { systemStatus = it },  
            onSpeechTranscribed = { transcription -> persistAndExecute(transcription) },  
            onResponseReceived = { response ->  
                lifecycleScope.launch {  
                    database.chatHistoryDao().insertMessage(MessageEntity(sender = "Assistant", text = response))  
                    uiLocked = false  
                    systemStatus = "SYSTEM READY // IDLE"  
                }  
            }  
        )  

        nsdHelper = NsdHelper(this) { resolvedEndpointUrl ->  
            backend.updateNetworkEndpoint(resolvedEndpointUrl)  
            systemStatus = "NEURAL LINK ESTABLISHED // QWEN 3B READY"  
            uiLocked = false  
        }  
        nsdHelper.startDiscovery()  

        setContent {  
            MaterialTheme {  
                Surface(modifier = Modifier.fillMaxSize(), color = bgMain) {  
                    AssistantScreen()  
                }  
            }  
        }  
    }  

    private fun persistAndExecute(prompt: String) {  
        uiLocked = true  
        lifecycleScope.launch {  
            database.chatHistoryDao().insertMessage(MessageEntity(sender = "You", text = prompt))  
            backend.queryQwenModel(prompt)  
        }  
    }  

    @Composable  
    fun AssistantScreen() {  
        var textInput by remember { mutableStateOf("") }  
        var selectedGender by remember { mutableStateOf("Male Voice") }  
        var selectedSpeed by remember { mutableStateOf("Normal") }  
        var selectedPersona by remember { mutableStateOf("Friendly Robot") }  
          
        var showPurgeDialog by remember { mutableStateOf(false) }  
        val context = LocalContext.current  

        if (showPurgeDialog) {  
            AlertDialog(  
                onDismissRequest = { showPurgeDialog = false },  
                title = { Text("Purge Neural Core Memory?", color = Color.White, fontWeight = FontWeight.Bold) },  
                text = { Text("This will permanently erase all local conversation text history tables from the SQLite matrix.", color = Color(0xFF94A3B8)) },  
                containerColor = bgCard,  
                confirmButton = {  
                    Button(  
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),  
                        onClick = {  
                            lifecycleScope.launch {  
                                database.chatHistoryDao().purgeAllHistory()  
                                showPurgeDialog = false  
                                Toast.makeText(context, "Memory cleared completely.", Toast.LENGTH_SHORT).show()  
                            }  
                        }  
                    ) { Text("Confirm Clear", color = Color.White) }  
                },  
                dismissButton = {  
                    TextButton(onClick = { showPurgeDialog = false }) {  
                        Text("Cancel", color = accentCyan)  
                    }  
                }  
            )  
        }  

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {  
            if (eyeStrainWarningVisible) {  
                Card(  
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),  
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7C2D12)),  
                    shape = RoundedCornerShape(8.dp)  
                ) {  
                    Row(  
                        modifier = Modifier.fillMaxWidth().padding(12.dp),  
                        horizontalArrangement = Arrangement.SpaceBetween,  
                        verticalAlignment = Alignment.CenterVertically  
                    ) {  
                        Text("👁️ OPTICAL FATIGUE ALERT: REST YOUR EYES NOW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)  
                        Button(  
                            onClick = {   
                                eyeStrainWarningVisible = false  
                                backend.resetTurnCounter()   
                            },  
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),  
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)  
                        ) { Text("DISMISS", fontSize = 10.sp, color = Color.White) }  
                    }  
                }  
            }  

            Column(  
                modifier = Modifier  
                    .fillMaxWidth()  
                    .background(bgCard, RoundedCornerShape(8.dp))  
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))  
                    .padding(12.dp)  
            ) {  
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {  
                    Text("SYSTEM COGNITIVE PARAMETERS", color = accentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)  
                    Row {  
                        Button(  
                            onClick = { exportAndShareChatHistory(context, database) },  
                            colors = ButtonDefaults.buttonColors(containerColor = accentGreen),  
                            modifier = Modifier.padding(end = 4.dp),  
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)  
                        ) { Text("SHARE JSON", fontSize = 9.sp, color = Color.White) }  

                        Button(  
                            onClick = { showPurgeDialog = true },  
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),  
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)  
                        ) { Text("PURGE DATA", fontSize = 9.sp, color = Color.White) }  
                    }  
                }  

                Spacer(modifier = Modifier.height(8.dp))  
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {  
                    Button(onClick = {  
                        selectedGender = if (selectedGender == "Male Voice") "Female Voice" else "Male Voice"  
                        backend.updateVoiceSettings(selectedGender, selectedSpeed, selectedPersona)  
                    }, colors = ButtonDefaults.buttonColors(containerColor = bgMain)) {  
                        Text(selectedGender, color = Color.White, fontSize = 11.sp)  
                    }  
                    Button(onClick = {  
                        selectedSpeed = when (selectedSpeed) {  
                            "Normal" -> "Fast"  
                            "Fast" -> "Slow"  
                            else -> "Normal"  
                        }  
                        backend.updateVoiceSettings(selectedGender, selectedSpeed, selectedPersona)  
                    }, colors = ButtonDefaults.buttonColors(containerColor = bgMain)) {  
                        Text("Tempo: $selectedSpeed", color = Color.White, fontSize = 11.sp)  
                    }  
                    Button(onClick = {  
                        selectedPersona = if (selectedPersona == "Friendly Robot") "Sharp Unit" else "Friendly Robot"  
                        backend.updateVoiceSettings(selectedGender, selectedSpeed, selectedPersona)  
                    }, colors = ButtonDefaults.buttonColors(containerColor = bgMain)) {  
                        Text(selectedPersona, color = Color.White, fontSize = 11.sp)  
                    }  
                }  
            }  

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp).background(bgCard, RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp)).padding(8.dp)) {  
                items(chatMessages) { message ->  
                    val nameColor = when (message.first) {  
                        "You" -> accentCyan  
                        "Assistant" -> accentPurple  
                        "System Alert" -> Color(0xFFF97316)  
                        else -> Color.Gray  
                    }  
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {  
                        Text("⚡ [${message.first}]:", color = nameColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 13.sp)  
                        Text(message.second, color = Color(0xFFE2E8F0), fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp, top = 2.dp))  
                    }  
                }  
            }  

            Text(systemStatus, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))  

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {  
                BasicTextField(  
                    value = textInput,  
                    onValueChange = { if (!uiLocked) textInput = it },  
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),  
                    modifier = Modifier.weight(1f).background(Color(0xFF0D1324), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(4.dp)).padding(12.dp),  
                    enabled = !uiLocked  
                )  
                Spacer(modifier = Modifier.width(8.dp))  
                Button(onClick = { backend.startVoiceCapture() }, enabled = !uiLocked, colors = ButtonDefaults.buttonColors(containerColor = accentGreen), shape = RoundedCornerShape(4.dp)) {  
                    Text("🎤", color = Color.White)  
                }  
                Spacer(modifier = Modifier.width(4.dp))  
                Button(onClick = {  
                    if (textInput.isNotBlank()) {  
                        persistAndExecute(textInput)  
                        textInput = ""  
                    }  
                }, enabled = !uiLocked, colors = ButtonDefaults.buttonColors(containerColor = accentCyan), shape = RoundedCornerShape(4.dp)) {  
                    Text("SEND", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)  
                }  
            }  
        }  
    }  

    private fun exportAndShareChatHistory(context: Context, database: RoboDatabase) {  
        lifecycleScope.launch(Dispatchers.IO) {  
            try {  
                val messages = database.chatHistoryDao().getAllMessagesStatic()  
                if (messages.isEmpty()) return@launch  
                val jsonArray = JSONArray()  
                messages.forEach { msg ->  
                    jsonArray.put(JSONObject().apply {  
                        put("sender", msg.sender)  
                        put("text", msg.text)  
                        put("timestamp", msg.timestamp)  
                    })  
                }  
                val targetDirectory = File(context.getExternalFilesDir(null), "RoboQwenBackups")  
                if (!targetDirectory.exists()) targetDirectory.mkdirs()  
                val exportFile = File(targetDirectory, "robo_chat_export_${System.currentTimeMillis()}.json")  
                FileWriter(exportFile).use { writer -> writer.write(jsonArray.toString(4)) }  
                val fileUri = FileProvider.getUriForFile(context, "com.example.roboqwen.fileprovider", exportFile)  
                val shareIntent = Intent(Intent.ACTION_SEND).apply {  
                    type = "application/json"  
                    putExtra(Intent.EXTRA_STREAM, fileUri)  
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  
                }  
                launch(Dispatchers.Main) { context.startActivity(Intent.createChooser(shareIntent, "Export via:")) }  
            } catch (e: Exception) { }  
        }  
    }  

    override fun onDestroy() {  
        super.onDestroy()  
        nsdHelper.stopDiscovery()  
        backend.onDestroy()  
    }  
}
