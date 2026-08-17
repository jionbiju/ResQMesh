package com.example.resqmesh.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.service.GattClientManager
import com.example.resqmesh.security.CryptoHelper
import com.example.resqmesh.ui.theme.ResQmeshTheme
import com.google.gson.Gson
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(peerId: String, peerName: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val clientManager = remember { GattClientManager(context) }
    val cryptoHelper = remember { CryptoHelper() }
    val gson = remember { Gson() }
    
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    
    val allMessages by ChatRepository.allMessages.collectAsState()
    val messages = allMessages.filter { it.peerId == peerId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(peerName, fontWeight = FontWeight.Bold)
                        Text("Secure Connection", fontSize = 10.sp, color = Color(0xFF4CAF50))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Column {
                if (isSending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                BottomMessageBar(
                    text = messageText,
                    onTextChange = { messageText = it },
                    onSendClick = {
                        if (messageText.isNotBlank() && !isSending) {
                            val originalMsg = messageText
                            
                            // 1. ENCRYPT before sending
                            // Note: For now, we use a fixed shared secret for testing. 
                            // Tomorrow we link it to the actual QR handshake secret.
                            val dummySecret = "ResQmeshSecretKey123456789012345".toByteArray()
                            val encryptedMsg = cryptoHelper.encrypt(originalMsg, dummySecret)
                            
                            // 2. Wrap in ChatMessage for the mesh protocol
                            val meshMessage = ChatMessage(
                                senderId = "Me", // In real use, this would be our local device ID
                                destinationId = peerId,
                                text = encryptedMsg,
                                isFromMe = true
                            )
                            val jsonPayload = gson.toJson(meshMessage)
                            
                            isSending = true
                            clientManager.sendMessage(peerId, jsonPayload) { success ->
                                isSending = false
                                if (success) {
                                    ChatRepository.addMessage(
                                        ChatMessage(
                                            senderId = "Me",
                                            destinationId = peerId,
                                            text = originalMsg, // Add the plaintext locally
                                            isFromMe = true
                                        )
                                    )
                                    messageText = ""
                                } else {
                                    Toast.makeText(context, "Delivery failed. Peer offline.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    val timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
            Surface(
                color = color,
                shape = shape,
                tonalElevation = 2.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    color = textColor,
                    fontSize = 15.sp
                )
            }
            Text(
                text = timeString,
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun BottomMessageBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type securely...") },
                maxLines = 3,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSendClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatScreenPreview() {
    ResQmeshTheme {
        ChatScreen(peerId = "00:11:22:33:44:55", peerName = "Test Peer", onBackClick = {})
    }
}
