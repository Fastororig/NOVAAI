package com.nova.ai.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.ai.data.api.GeminiApiService
import com.nova.ai.data.model.*
import com.nova.ai.data.storage.ApiKeyPreferences
import com.nova.ai.data.storage.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = ApiKeyPreferences(application)
    private val repository = ChatRepository(application)
    private val apiService = GeminiApiService()

    var apiKey by mutableStateOf(preferences.apiKey)
        private set

    var selectedModel by mutableStateOf(preferences.selectedModel)
        private set

    var systemInstruction by mutableStateOf(preferences.systemInstruction)
        private set

    var temperature by mutableStateOf(preferences.temperature)
        private set

    var isWatchMode by mutableStateOf(preferences.isWatchMode)
        private set

    fun setWatchMode(enabled: Boolean) {
        isWatchMode = enabled
        preferences.isWatchMode = enabled
    }

    val conversations = mutableStateListOf<Conversation>()
    var currentConversationId by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    // Attached files for the current outgoing message
    val attachedFiles = mutableStateListOf<AttachmentInfo>()
    var attachmentError by mutableStateOf<String?>(null)
        private set

    // Dynamic Models State
    val modelsList = mutableStateListOf<UiGeminiModel>()
    var isLoadingModels by mutableStateOf(false)
        private set
    var modelsErrorMessage by mutableStateOf<String?>(null)
        private set
    var isModelsFromCache by mutableStateOf(false)
        private set
    var modelsLastUpdated by mutableStateOf(preferences.modelsCacheTimestamp)
        private set

    val currentConversation: Conversation?
        get() = conversations.find { it.id == currentConversationId } ?: conversations.firstOrNull()

    init {
        loadData()
        loadInitialModels()
    }

    private fun loadData() {
        viewModelScope.launch {
            val list = repository.loadConversations()
            conversations.clear()
            conversations.addAll(list)
            if (conversations.isNotEmpty()) {
                currentConversationId = conversations.first().id
            } else {
                createNewConversation()
            }
        }
    }

    private fun loadInitialModels() {
        val cached = preferences.getCachedModels()
        if (!cached.isNullOrEmpty()) {
            val sanitized = cached.map {
                it.copy(isRecommended = (it.id == UiGeminiModel.RECOMMENDED_MODEL_ID))
            }
            modelsList.clear()
            modelsList.addAll(sanitized)
            isModelsFromCache = true
            modelsLastUpdated = preferences.modelsCacheTimestamp
        } else {
            modelsList.clear()
            modelsList.addAll(UiGeminiModel.INITIAL_FALLBACK)
            isModelsFromCache = true
        }

        // If saved model is not in the list, auto switch to default stable model
        if (modelsList.none { it.id == selectedModel }) {
            selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
            preferences.selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
        }

        if (apiKey.isNotBlank()) {
            refreshModels()
        }
    }

    fun refreshModels() {
        if (apiKey.isBlank()) {
            modelsErrorMessage = "Недействительный API-ключ. Проверьте правильность ключа в настройках."
            return
        }

        isLoadingModels = true
        modelsErrorMessage = null

        viewModelScope.launch {
            val result = apiService.fetchAvailableModels(apiKey)
            isLoadingModels = false

            result.onSuccess { remoteModels ->
                if (remoteModels.isNotEmpty()) {
                    modelsList.clear()
                    modelsList.addAll(remoteModels)
                    preferences.saveCachedModels(remoteModels)
                    isModelsFromCache = false
                    modelsLastUpdated = preferences.modelsCacheTimestamp
                    modelsErrorMessage = null

                    // If saved model is absent from the loaded list, auto switch to default stable model
                    val modelExists = remoteModels.any { it.id == selectedModel }
                    if (!modelExists) {
                        val fallback = remoteModels.find { it.id == UiGeminiModel.DEFAULT_MODEL_ID }
                            ?: remoteModels.find { it.isRecommended }
                            ?: remoteModels.first()
                        selectedModel = fallback.id
                        preferences.selectedModel = fallback.id
                    }
                }
            }.onFailure { error ->
                isModelsFromCache = true
                if (modelsList.isEmpty()) {
                    modelsList.addAll(UiGeminiModel.INITIAL_FALLBACK)
                }
                if (modelsList.none { it.id == selectedModel }) {
                    selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
                    preferences.selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
                }
                modelsErrorMessage = error.localizedMessage ?: "Не удалось загрузить список. Используется резервный список моделей."
            }
        }
    }

    /**
     * Clears local models cache in preferences and re-fetches a clean list from API.
     */
    fun resetAndRefreshModels() {
        preferences.clearCachedModels()
        modelsList.clear()
        modelsList.addAll(UiGeminiModel.INITIAL_FALLBACK)
        isModelsFromCache = true
        modelsLastUpdated = 0L
        modelsErrorMessage = null
        selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
        preferences.selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
        refreshModels()
    }

    // ==========================================
    // Attachments Handling
    // ==========================================

    fun addFiles(context: Context, uris: List<Uri>) {
        attachmentError = null
        viewModelScope.launch(Dispatchers.IO) {
            val newAttachments = mutableListOf<AttachmentInfo>()

            for (uri in uris) {
                try {
                    var displayName = "file_${System.currentTimeMillis()}"
                    var sizeBytes = 0L

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: displayName
                            if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }

                    var mimeType = context.contentResolver.getType(uri) ?: detectMimeType(displayName)

                    // Validate supported types
                    if (!isSupportedMimeType(mimeType)) {
                        withContext(Dispatchers.Main) {
                            attachmentError = "Неподдерживаемый тип файла: $displayName ($mimeType). Поддерживаются изображения, PDF, TXT, CSV, JSON, MP3, WAV, MP4."
                        }
                        continue
                    }

                    // Read bytes
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: continue

                    if (sizeBytes == 0L) {
                        sizeBytes = bytes.size.toLong()
                    }

                    // 100MB max limit
                    if (sizeBytes > 100 * 1024 * 1024) {
                        withContext(Dispatchers.Main) {
                            attachmentError = "Файл $displayName превышает допустимый лимит (100 МБ)."
                        }
                        continue
                    }

                    // If <= 20MB, prepare base64 for inlineData
                    val base64Data = if (sizeBytes <= 20 * 1024 * 1024) {
                        Base64.encodeToString(bytes, Base64.NO_WRAP)
                    } else null

                    val isImage = mimeType.startsWith("image/")

                    newAttachments.add(
                        AttachmentInfo(
                            name = displayName,
                            mimeType = mimeType,
                            sizeBytes = sizeBytes,
                            uriString = uri.toString(),
                            base64Data = base64Data,
                            isImage = isImage
                        )
                    )
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        attachmentError = "Не удалось прочитать файл: ${e.localizedMessage}"
                    }
                }
            }

            withContext(Dispatchers.Main) {
                attachedFiles.addAll(newAttachments)
            }
        }
    }

    fun removeAttachment(id: String) {
        val index = attachedFiles.indexOfFirst { it.id == id }
        if (index != -1) {
            attachedFiles.removeAt(index)
        }
    }

    fun clearAttachments() {
        attachedFiles.clear()
        attachmentError = null
    }

    private fun detectMimeType(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun isSupportedMimeType(mime: String): Boolean {
        return mime.startsWith("image/") ||
                mime == "application/pdf" ||
                mime.startsWith("text/") ||
                mime == "application/json" ||
                mime == "text/csv" ||
                mime.startsWith("audio/") ||
                mime == "video/mp4"
    }

    // ==========================================
    // Chat Actions
    // ==========================================

    fun selectConversation(id: String) {
        currentConversationId = id
    }

    fun createNewConversation() {
        val newConv = Conversation(title = "Диалог ${conversations.size + 1}")
        conversations.add(0, newConv)
        currentConversationId = newConv.id
        saveConversations()
    }

    fun deleteConversation(id: String) {
        if (conversations.size > 1) {
            val index = conversations.indexOfFirst { it.id == id }
            if (index != -1) {
                conversations.removeAt(index)
                if (currentConversationId == id) {
                    currentConversationId = conversations.first().id
                }
                saveConversations()
            }
        }
    }

    fun saveSettings(
        apiKey: String,
        model: String,
        systemInstruction: String,
        temperature: Float,
        isWatchMode: Boolean = this.isWatchMode
    ) {
        val keyChanged = this.apiKey != apiKey
        this.apiKey = apiKey
        this.selectedModel = model
        this.systemInstruction = systemInstruction
        this.temperature = temperature
        this.isWatchMode = isWatchMode

        preferences.apiKey = apiKey
        preferences.selectedModel = model
        preferences.systemInstruction = systemInstruction
        preferences.temperature = temperature
        preferences.isWatchMode = isWatchMode

        if (keyChanged && apiKey.isNotBlank()) {
            refreshModels()
        }
    }

    fun sendMessage(promptText: String) {
        val conv = currentConversation ?: return
        val textTrimmed = promptText.trim()
        if (textTrimmed.isBlank() && attachedFiles.isEmpty()) return
        if (isLoading) return

        // Take snapshot of attachments and clear UI list immediately
        val attachmentsToSend = attachedFiles.toList()
        attachedFiles.clear()
        attachmentError = null

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            text = textTrimmed,
            attachments = attachmentsToSend
        )
        conv.messages.add(userMessage)

        if (conv.messages.size == 1 || conv.title.startsWith("Диалог")) {
            val titleCandidate = textTrimmed.ifBlank { attachmentsToSend.firstOrNull()?.name ?: "Файл" }
            conv.title = titleCandidate.take(28) + if (titleCandidate.length > 28) "..." else ""
        }

        saveConversations()

        if (apiKey.isBlank()) {
            conv.messages.add(
                ChatMessage(
                    role = MessageRole.ERROR,
                    text = "API-ключ не настроен. Нажмите на иконку настроек вверху и введите ваш ключ Google Gemini."
                )
            )
            saveConversations()
            return
        }

        isLoading = true

        viewModelScope.launch {
            // Check if any attachments > 20MB need Files API upload
            val finalAttachments = mutableListOf<AttachmentInfo>()
            for (att in attachmentsToSend) {
                if (att.base64Data.isNullOrBlank() && att.remoteFileUri.isNullOrBlank() && !att.uriString.isNullOrBlank()) {
                    // Upload via Files API
                    try {
                        val uri = Uri.parse(att.uriString)
                        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val uploadResult = apiService.uploadFileToGemini(
                                apiKey = apiKey,
                                fileBytes = bytes,
                                mimeType = att.mimeType,
                                displayName = att.name
                            )
                            if (uploadResult.isSuccess) {
                                finalAttachments.add(att.copy(remoteFileUri = uploadResult.getOrNull()))
                            } else {
                                finalAttachments.add(att)
                            }
                        } else {
                            finalAttachments.add(att)
                        }
                    } catch (e: Exception) {
                        finalAttachments.add(att)
                    }
                } else {
                    finalAttachments.add(att)
                }
            }

            // Update user message with uploaded URIs if changed
            val msgIndex = conv.messages.indexOfFirst { it.id == userMessage.id }
            if (msgIndex != -1) {
                conv.messages[msgIndex] = userMessage.copy(attachments = finalAttachments)
            }

            var result = apiService.generateContent(
                apiKey = apiKey,
                model = selectedModel,
                conversationHistory = conv.messages,
                systemInstruction = systemInstruction.ifBlank { null },
                temperature = temperature
            )

            // If model returned an error (e.g. 404/400 model unavailable) and is not already the default model,
            // automatically switch to the default stable model (gemini-2.5-flash) and retry
            if (result.isFailure && selectedModel != UiGeminiModel.DEFAULT_MODEL_ID) {
                val ex = result.exceptionOrNull()
                val isModelError = (ex is com.nova.ai.data.api.ApiException && (ex.code == 404 || ex.code == 400)) ||
                        ex?.message?.contains("недоступна", ignoreCase = true) == true
                if (isModelError) {
                    selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
                    preferences.selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
                    result = apiService.generateContent(
                        apiKey = apiKey,
                        model = selectedModel,
                        conversationHistory = conv.messages,
                        systemInstruction = systemInstruction.ifBlank { null },
                        temperature = temperature
                    )
                }
            }

            isLoading = false

            result.onSuccess { responseText ->
                conv.messages.add(
                    ChatMessage(
                        role = MessageRole.MODEL,
                        text = responseText,
                        modelUsed = selectedModel
                    )
                )
            }.onFailure { error ->
                conv.messages.add(
                    ChatMessage(
                        role = MessageRole.ERROR,
                        text = error.localizedMessage ?: "Неизвестная ошибка связи с Gemini API"
                    )
                )
            }

            saveConversations()
        }
    }

    fun retryLastMessage() {
        val conv = currentConversation ?: return
        val lastUserMessage = conv.messages.lastOrNull { it.role == MessageRole.USER } ?: return

        if (conv.messages.lastOrNull()?.role == MessageRole.ERROR) {
            conv.messages.removeAt(conv.messages.size - 1)
        }

        // Re-attach last attachments if any
        attachedFiles.clear()
        attachedFiles.addAll(lastUserMessage.attachments)
        sendMessage(lastUserMessage.text)
    }

    private fun saveConversations() {
        viewModelScope.launch {
            repository.saveConversations(conversations)
        }
    }
}
