package com.example.roboqwen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var backend: AssistantBackend

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                backend.startVoiceCapture()
            } else {
                statusText = "STATUS // MIC PERMISSION DENIED"
            }
        }

    private var statusText by mutableStateOf("SYSTEM READY // IDLE")
    private var chatMessages by mutableStateOf(listOf<String>())
    private var inputText by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        backend = AssistantBackend(
            context = this,
            onStatusUpdate = { statusText = it },
            onSpeechTranscribed = { text ->
                chatMessages = chatMessages + "User (Voice): $text"
            },
            onResponseReceived = { response ->
                chatMessages = chatMessages + "RoboQwen: $response"
            }
        )

        // Point backend to localhost over reverse USB port forwarding
        backend.updateNetworkEndpoint("http://127.0.0.1:8000")

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Status Header
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Chat Messages Display
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(chatMessages) { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Text Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type prompt...") }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(onClick = {
                    if (inputText.isNotBlank()) {
                        val promptToSend = inputText
                        chatMessages = chatMessages + "User: $promptToSend"
                        inputText = ""

                        coroutineScope.launch {
                            backend.queryQwenModel(promptToSend)
                        }
                    }
                }) {
                    Text("Send")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Microphone Capture Button
            Button(
                onClick = { triggerVoiceInput() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🎙️ Tap to Speak")
            }
        }
    }

    private fun triggerVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            backend.startVoiceCapture()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        backend.onDestroy()
    }
}
