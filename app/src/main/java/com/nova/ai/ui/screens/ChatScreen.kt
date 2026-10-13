package com.nova.ai.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.data.model.AttachmentInfo
import com.nova.ai.data.model.ChatMessage
import com.nova.ai.data.model.MessageRole
import com.nova.ai.ui.components.MarkdownViewer
import com.nova.ai.ui.theme.*
import com.nova.ai.ui.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val currentConversation = viewModel.currentConversation
    val messages = currentConversation?.messages ?: emptyList()
    val isLoading = viewModel.isLoading
    val currentModel = viewModel.selectedModel
    val isKeyConfigured = viewModel.apiKey.isNotBlank()
    val attachedFiles = viewModel.attachedFiles

    var inputText by remember { mutableStateOf("") }

    // System File Picker for multi-file selection (Android 7.0+ compatible via GetMultipleContents)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addFiles(context, uris)
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentConversation?.title ?: "Nova AI",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = currentModel,
                            fontSize = 12.sp,
                            color = NovaPrimary,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Меню диалогов",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.createNewConversation() }) {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = "Новый диалог",
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Missing API Key Notification Banner
            if (!isKeyConfigured) {
                Surface(
                    color = Color(0xFF3B1E28),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "API-ключ не настроен",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        TextButton(
                            onClick = onOpenSettings,
                            colors = ButtonDefaults.textButtonColors(contentColor = NovaPrimary)
                        ) {
                            Text("Ввести ключ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Message List
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = NovaPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = NovaPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "Чем я могу помочь вам сегодня?",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Отправляйте запросы и прикрепляйте файлы (изображения, PDF, документы, аудио, видео). Все запросы идут напрямую в Google Gemini API.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Nova AI", message.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                            },
                            onRetry = {
                                viewModel.retryLastMessage()
                            }
                        )
                    }

                    if (isLoading) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = DarkSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = NovaPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = NovaPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Nova думает...",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Attachment Error Banner
            viewModel.attachmentError?.let { err ->
                Surface(
                    color = Color(0xFF38151D),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = err,
                            color = Color(0xFFFF8599),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.clearAttachments() },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = Color(0xFFFF8599),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Attached Files Horizontal Preview List
            if (attachedFiles.isNotEmpty()) {
                Surface(
                    color = Color(0xFF0F1523),
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        attachedFiles.forEach { file ->
                            AttachmentChip(
                                file = file,
                                onRemove = { viewModel.removeAttachment(file.id) }
                            )
                        }
                    }
                }
            }

            // Bottom Input Bar
            Surface(
                color = DarkSurface,
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Paperclip Attachment Button
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        enabled = !isLoading,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0D1322))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Прикрепить файлы",
                            tint = if (attachedFiles.isNotEmpty()) NovaPrimary else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp, max = 140.dp),
                        placeholder = { Text("Спросите что-нибудь...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NovaPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = Color(0xFF0D1322),
                            unfocusedContainerColor = Color(0xFF0D1322)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 5
                    )

                    val canSend = (inputText.isNotBlank() || attachedFiles.isNotEmpty()) && !isLoading

                    IconButton(
                        onClick = {
                            if (canSend) {
                                val textToSend = inputText.trim()
                                inputText = ""
                                viewModel.sendMessage(textToSend)
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (canSend) NovaPrimary else DarkSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = if (canSend) Color.Black else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(
    file: AttachmentInfo,
    onRemove: () -> Unit
) {
    Surface(
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (file.isImage && !file.base64Data.isNullOrBlank()) {
                val imageBitmap = remember(file.base64Data) {
                    try {
                        val bytes = android.util.Base64.decode(file.base64Data, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = file.name,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = NovaPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = when {
                        file.isImage -> Icons.Default.Image
                        file.mimeType.startsWith("audio/") -> Icons.Default.Audiotrack
                        file.mimeType.startsWith("video/") -> Icons.Default.VideoFile
                        file.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.Description
                    },
                    contentDescription = null,
                    tint = NovaPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(modifier = Modifier.widthIn(max = 120.dp)) {
                Text(
                    text = file.name,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(file.sizeBytes),
                    fontSize = 9.sp,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Удалить файл",
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onRetry: () -> Unit
) {
    val isUser = message.role == MessageRole.USER
    val isError = message.role == MessageRole.ERROR

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.88f else 0.96f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Surface(
                    shape = CircleShape,
                    color = if (isError) ErrorColor.copy(alpha = 0.2f) else DarkSurfaceVariant,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(top = 2.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isError) Icons.Default.ErrorOutline else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isError) ErrorColor else NovaPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f, fill = false)) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            )
                        )
                        .background(
                            when {
                                isError -> Color(0xFF38151D)
                                isUser -> UserBubbleColor
                                else -> AiBubbleColor
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = when {
                                isError -> ErrorColor.copy(alpha = 0.5f)
                                isUser -> Color.Transparent
                                else -> DarkBorder
                            },
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isUser) 16.dp else 4.dp,
                                bottomEnd = if (isUser) 4.dp else 16.dp
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Display attachments in user message
                        if (message.attachments.isNotEmpty()) {
                            Row(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .horizontalScroll(rememberScrollState()),
                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                message.attachments.forEach { att ->
                                    Surface(
                                        color = Color(0x33000000),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (att.isImage && !att.base64Data.isNullOrBlank()) {
                                                val bubbleImg = remember(att.base64Data) {
                                                    try {
                                                        val bytes = android.util.Base64.decode(att.base64Data, android.util.Base64.DEFAULT)
                                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                                    } catch (e: Exception) { null }
                                                }
                                                if (bubbleImg != null) {
                                                    Image(
                                                        bitmap = bubbleImg,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(imageVector = Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = if (att.isImage) Icons.Default.Image else Icons.Default.AttachFile,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = att.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (isUser) {
                            if (message.text.isNotBlank()) {
                                Text(
                                    text = message.text,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        } else if (isError) {
                            Text(
                                text = message.text,
                                color = Color(0xFFFF8599),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorColor),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Повторить",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Повторить", fontSize = 12.sp, color = Color.White)
                            }
                        } else {
                            MarkdownViewer(text = message.text)
                        }
                    }
                }

                if (!isUser && !isError) {
                    Row(
                        modifier = Modifier
                            .padding(top = 4.dp, start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onCopy,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Копировать",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        if (message.modelUsed != null) {
                            Text(
                                text = message.modelUsed,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format("%.1f МБ", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "${bytes / 1024} КБ"
        else -> "$bytes Б"
    }
}
