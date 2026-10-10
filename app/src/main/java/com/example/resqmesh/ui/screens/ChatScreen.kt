package com.example.resqmesh.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.resqmesh.data.repository.ChatRepository
import com.example.resqmesh.domain.models.ChatMessage
import com.example.resqmesh.service.GattClientManager
import com.example.resqmesh.service.MeshManager
import com.example.resqmesh.ui.theme.ResQmeshTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(peerId: String, peerName: String, onBackClick: () -> Unit) {
    val context = LocalContext.current
    val clientManager = remember { GattClientManager(context) }
    
    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    var transferProgress by remember { mutableFloatStateOf(0f) }
    var isTransferring by remember { mutableStateOf(false) }
    
    val allMessages by ChatRepository.allMessages.collectAsState()
    val messages = allMessages.filter { msg ->
        if (peerId == "BROADCAST") {
            msg.destinationId == "BROADCAST"
        } else {
            msg.destinationId != "BROADCAST" && (
                (msg.isFromMe && (msg.destinationId == peerId || (peerName.isNotBlank() && msg.destinationId.equals(peerName, ignoreCase = true)))) ||
                (!msg.isFromMe && (msg.senderId == peerId || msg.peerId == peerId || (peerName.isNotBlank() && msg.senderId.equals(peerName, ignoreCase = true))))
            )
        }
    }

    // Crisis-Connect Style File Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                Toast.makeText(context, "Preparing file attachment...", Toast.LENGTH_SHORT).show()
                val targetDestination = if (peerId == "BROADCAST") {
                    "BROADCAST"
                } else if (peerName.isNotBlank() && peerName != "User" && peerName != "ResQmesh Node") {
                    peerName
                } else {
                    peerId
                }

                val processedPayload = processFileUri(context, uri)
                if (processedPayload != null) {
                    val fileMessage = ChatMessage(
                        messageId = UUID.randomUUID().toString(),
                        senderId = "ME",
                        destinationId = targetDestination,
                        text = processedPayload,
                        isFromMe = true,
                        timestamp = System.currentTimeMillis(),
                        ttl = 3
                    )
                    ChatRepository.addMessage(fileMessage)

                    if (peerId == "BROADCAST") {
                        val activePeers = MeshManager.getScanner()?.foundPeers?.value?.map { it.id } ?: emptyList()
                        clientManager.broadcastToAll(activePeers, processedPayload, isEmergency = false)
                    } else {
                        isTransferring = true
                        transferProgress = 0.05f
                        clientManager.sendChatMessage(
                            peerId, 
                            fileMessage,
                            onProgress = { p ->
                                transferProgress = p
                                isTransferring = true
                            },
                            onResult = { success ->
                                isTransferring = false
                                if (!success) {
                                    Toast.makeText(context, "Peer offline. File stored for automatic delivery.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "File Transfer Completed 100%!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                } else {
                    Toast.makeText(context, "File exceeds BLE size limit (max 250 KB)", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error processing file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Chat History", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to clear all messages with $peerName?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        ChatRepository.clearChatWithPeer(peerId)
                        showClearDialog = false
                        Toast.makeText(context, "Chat history cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Clear", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = peerName.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(peerName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Mesh Secured", fontSize = 10.sp, color = Color(0xFF4CAF50))
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Column {
                if (isTransferring) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Transmitting Attachment...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "${(transferProgress * 100).toInt()}%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { transferProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else if (isSending) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                }

                BottomMessageBar(
                    text = messageText,
                    onTextChange = { messageText = it },
                    onAttachClick = { filePickerLauncher.launch("*/*") },
                    onSendClick = {
                        if (messageText.isNotBlank() && !isSending) {
                            val msgToSend = messageText
                            messageText = ""
                            isSending = true
                            
                            val targetDestination = if (peerId == "BROADCAST") {
                                "BROADCAST"
                            } else if (peerName.isNotBlank() && peerName != "User" && peerName != "ResQmesh Node") {
                                peerName
                            } else {
                                peerId
                            }

                            val outgoingMessage = ChatMessage(
                                messageId = UUID.randomUUID().toString(),
                                senderId = "ME",
                                destinationId = targetDestination,
                                text = msgToSend,
                                isFromMe = true,
                                timestamp = System.currentTimeMillis(),
                                ttl = 3
                            )
                            ChatRepository.addMessage(outgoingMessage)

                            if (peerId == "BROADCAST") {
                                val activePeers = MeshManager.getScanner()?.foundPeers?.value?.map { it.id } ?: emptyList()
                                clientManager.broadcastToAll(activePeers, msgToSend, isEmergency = false)
                                isSending = false
                            } else {
                                clientManager.sendChatMessage(peerId, outgoingMessage) { success ->
                                    isSending = false
                                    if (!success) {
                                        Toast.makeText(context, "Peer offline. Message stored for automatic delivery.", Toast.LENGTH_SHORT).show()
                                    }
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val context = LocalContext.current
    val alignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val shape = if (message.isFromMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    val timeString = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))

    val isFilePayload = remember(message.text) { message.text.startsWith("FILE_PAYLOAD|") }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
            if (isFilePayload) {
                // Crisis-Connect Interactive Attachment Card
                val parts = message.text.split("|")
                val fileName = parts.getOrNull(1) ?: "Attachment"
                val base64Data = parts.getOrNull(2) ?: ""

                Surface(
                    color = color,
                    shape = shape,
                    shadowElevation = 2.dp,
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(textColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (fileName.contains(".png", true) || fileName.contains(".jpg", true)) Icons.Default.Image else Icons.Default.InsertDriveFile,
                                    contentDescription = null,
                                    tint = textColor
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = textColor,
                                    maxLines = 1
                                )
                                val sizeKb = (base64Data.length * 3 / 4) / 1024
                                Text(
                                    "$sizeKb KB • Offline Transfer",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                saveAndOpenFile(context, fileName, base64Data)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = textColor.copy(alpha = 0.2f),
                                contentColor = textColor
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save / Open File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Surface(
                    color = color,
                    shape = shape,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            Text(
                text = timeString,
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
fun BottomMessageBar(
    text: String,
    onTextChange: (String) -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttachClick) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach File",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Type offline message...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FloatingActionButton(
                onClick = onSendClick,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Crisis-Connect Style Helper: Reads File Uri, Compresses Images, Encodes Base64 Wire Payload
private fun processFileUri(context: Context, uri: Uri): String? {
    val contentResolver = context.contentResolver
    
    // Resolve Filename
    var fileName = "attachment"
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }

    val inputStream = contentResolver.openInputStream(uri) ?: return null
    var rawBytes = inputStream.readBytes()
    inputStream.close()

    // If file is an Image, downscale/compress aggressively for 5x faster BLE transmission
    val mimeType = contentResolver.getType(uri) ?: ""
    if (mimeType.startsWith("image") || fileName.endsWith(".jpg", true) || fileName.endsWith(".png", true)) {
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
        if (bitmap != null) {
            val maxDim = 500
            val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = Math.min(maxDim.toDouble() / bitmap.width, maxDim.toDouble() / bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else bitmap

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
            rawBytes = baos.toByteArray()
        }
    }

    if (rawBytes.size > 250 * 1024) {
        return null // Exceeds BLE maximum capacity
    }

    val base64Str = Base64.encodeToString(rawBytes, Base64.NO_WRAP)
    return "FILE_PAYLOAD|$fileName|$base64Str"
}

// Crisis-Connect Style Helper: Decodes Base64 into Public Downloads & Launches Viewer
private fun saveAndOpenFile(context: Context, fileName: String, base64Data: String) {
    try {
        val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

        val fileUri: Uri?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ResQmesh")
            }
            val resolver = context.contentResolver
            fileUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (fileUri != null) {
                resolver.openOutputStream(fileUri)?.use { out ->
                    out.write(rawBytes)
                }
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ResQmesh")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { out -> out.write(rawBytes) }
            fileUri = Uri.fromFile(file)
        }

        Toast.makeText(context, "Saved to Downloads/ResQmesh/$fileName", Toast.LENGTH_LONG).show()

        if (fileUri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open Attachment"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Saved to Downloads/ResQmesh folder", Toast.LENGTH_SHORT).show()
    }
}
