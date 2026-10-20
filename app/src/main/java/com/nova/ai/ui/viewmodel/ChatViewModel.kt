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
import com.nova.ai.data.api.ApiException
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

    var proxyUrl by mutableStateOf(preferences.proxyUrl)
        private set

    fun updateProxyUrl(url: String) {
        val trimmed = url.trim()
        proxyUrl = trimmed
        preferences.proxyUrl = trimmed
        apiService.defaultBaseUrl = trimmed
    }

    // Smartwatch Mode (M99 1:1) state & public controls
    private val _isWatchMode = mutableStateOf(preferences.isWatchMode)
    val isWatchMode: Boolean
        get() = _isWatchMode.value

    fun setWatchMode(enabled: Boolean) {
        _isWatchMode.value = enabled
        preferences.isWatchMode = enabled
    }

    fun updateWatchMode(enabled: Boolean) {
        setWatchMode(enabled)
    }

    fun toggleWatchMode() {
        setWatchMode(!isWatchMode)
    }

    // Streaming mode state & public controls
    private val _isStreamingEnabled = mutableStateOf(preferences.isStreamingEnabled)
    val isStreamingEnabled: Boolean
        get() = _isStreamingEnabled.value

    fun setStreamingEnabled(enabled: Boolean) {
        _isStreamingEnabled.value = enabled
        preferences.isStreamingEnabled = enabled
    }

    fun setStreamMode(enabled: Boolean) {
        setStreamingEnabled(enabled)
    }

    fun toggleStreaming() {
        setStreamingEnabled(!isStreamingEnabled)
    }

    // Dynamic models state & public controls
    private val _isDynamicModelsEnabled = mutableStateOf(preferences.isDynamicModelsEnabled)
    val isDynamicModelsEnabled: Boolean
        get() = _isDynamicModelsEnabled.value

    fun setDynamicModelsEnabled(enabled: Boolean) {
        _isDynamicModelsEnabled.value = enabled
        preferences.isDynamicModelsEnabled = enabled
    }

    fun setUseDynamicModels(enabled: Boolean) {
        setDynamicModelsEnabled(enabled)
    }

    fun toggleDynamicModels() {
        setDynamicModelsEnabled(!isDynamicModelsEnabled)
    }

    val conversations = mutableStateListOf<Conversation>()
    var currentConversationId by mutableStateOf("")
        private set

    // Observable snapshot list of messages for the active conversation,
    // ensuring instantaneous token-by-token UI recomposition during streaming.
    val currentMessages = mutableStateListOf<ChatMessage>()

    fun syncCurrentMessages() {
        currentMessages.clear()
        currentConversation?.messages?.let { currentMessages.addAll(it) }
    }

    fun commitCurrentMessages() {
        currentConversation?.let { conv ->
            conv.messages.clear()
            conv.messages.addAll(currentMessages)
        }
    }

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
        apiService.defaultBaseUrl = preferences.proxyUrl
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
                val newConv = Conversation(title = "Диалог 1")
                conversations.add(newConv)
                currentConversationId = newConv.id
            }
            syncCurrentMessages()
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
            val result = apiService.fetchAvailableModels(apiKey, customBaseUrl = proxyUrl)
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
        syncCurrentMessages()
    }

    fun createNewConversation() {
        val newConv = Conversation(title = "Диалог ${conversations.size + 1}")
        conversations.add(0, newConv)
        currentConversationId = newConv.id
        syncCurrentMessages()
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
                syncCurrentMessages()
                saveConversations()
            }
        }
    }

    @JvmOverloads
    fun saveSettings(
        apiKey: String,
        model: String,
        systemInstruction: String,
        temperature: Float,
        isWatchMode: Boolean = this.isWatchMode,
        isStreamingEnabled: Boolean = this.isStreamingEnabled,
        isDynamicModelsEnabled: Boolean = this.isDynamicModelsEnabled,
        proxyUrl: String = this.proxyUrl
    ) {
        val keyChanged = this.apiKey != apiKey
        val proxyChanged = this.proxyUrl != proxyUrl.trim()
        this.apiKey = apiKey
        this.selectedModel = model
        this.systemInstruction = systemInstruction
        this.temperature = temperature
        this.proxyUrl = proxyUrl.trim()
        setWatchMode(isWatchMode)
        setStreamingEnabled(isStreamingEnabled)
        setDynamicModelsEnabled(isDynamicModelsEnabled)

        preferences.apiKey = apiKey
        preferences.selectedModel = model
        preferences.systemInstruction = systemInstruction
        preferences.temperature = temperature
        preferences.proxyUrl = proxyUrl.trim()
        apiService.defaultBaseUrl = proxyUrl.trim()

        if ((keyChanged || proxyChanged) && apiKey.isNotBlank() && this.isDynamicModelsEnabled) {
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
        currentMessages.add(userMessage)
        commitCurrentMessages()

        if (currentMessages.size == 1 || conv.title.startsWith("Диалог")) {
            val titleCandidate = textTrimmed.ifBlank { attachmentsToSend.firstOrNull()?.name ?: "Файл" }
            conv.title = titleCandidate.take(28) + if (titleCandidate.length > 28) "..." else ""
        }

        saveConversations()

        if (apiKey.isBlank()) {
            currentMessages.add(
                ChatMessage(
                    role = MessageRole.ERROR,
                    text = "API-ключ не настроен. Нажмите на иконку настроек вверху и введите ваш ключ Google Gemini."
                )
            )
            commitCurrentMessages()
            saveConversations()
            return
        }

        isLoading = true

        viewModelScope.launch {
            // Check if any attachments > 20MB need Files API upload
            val finalAttachments = mutableListOf<AttachmentInfo>()
            for (att in attachmentsToSend) {
                if (att.base64Data.isNullOrBlank() && att.remoteFileUri.isNullOrBlank() && !att.uriString.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(att.uriString)
                        val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val uploadResult = apiService.uploadFileToGemini(
                                apiKey = apiKey,
                                fileBytes = bytes,
                                mimeType = att.mimeType,
                                displayName = att.name,
                                customBaseUrl = proxyUrl
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
            val userMsgIndex = currentMessages.indexOfFirst { it.id == userMessage.id }
            if (userMsgIndex != -1) {
                currentMessages[userMsgIndex] = userMessage.copy(attachments = finalAttachments)
                commitCurrentMessages()
            }

            // Immediately create the assistant placeholder message so UI displays it with zero lag
            val assistantMessage = ChatMessage(
                role = MessageRole.MODEL,
                text = "",
                modelUsed = selectedModel,
                isPending = true
            )
            currentMessages.add(assistantMessage)
            commitCurrentMessages()

            var activeModel = selectedModel
            val accumulatedText = StringBuilder()
            var streamSuccess = false

            try {
                val allHistory = currentMessages.filter { it.id != assistantMessage.id }
                val history = if (allHistory.size > 15) {
                    val last15 = allHistory.takeLast(15)
                    last15.dropWhile { it.role != MessageRole.USER }.ifEmpty { last15 }
                } else {
                    allHistory
                }
                apiService.generateContentStream(
                    apiKey = apiKey,
                    model = activeModel,
                    conversationHistory = history,
                    systemInstruction = systemInstruction.ifBlank { null },
                    temperature = temperature,
                    customBaseUrl = proxyUrl
                ).collect { chunk ->
                    accumulatedText.append(chunk)
                    val idx = currentMessages.indexOfFirst { it.id == assistantMessage.id }
                    if (idx != -1) {
                        currentMessages[idx] = assistantMessage.copy(
                            text = accumulatedText.toString(),
                            modelUsed = activeModel,
                            isPending = true
                        )
                    }
                }
                streamSuccess = true
            } catch (e: Exception) {
                val isModelNotFoundError = (e is ApiException && e.code == 404) ||
                        e.message?.contains("не найдена", ignoreCase = true) == true

                // If selected model is completely missing/not found (404) and no text has been received, fallback to default model
                if (isModelNotFoundError && activeModel != UiGeminiModel.DEFAULT_MODEL_ID && accumulatedText.isEmpty()) {
                    activeModel = UiGeminiModel.DEFAULT_MODEL_ID
                    selectedModel = UiGeminiModel.DEFAULT_MODEL_ID
                    preferences.selectedModel = UiGeminiModel.DEFAULT_MODEL_ID

                    try {
                        val allHistory = currentMessages.filter { it.id != assistantMessage.id }
                        val history = if (allHistory.size > 15) {
                            val last15 = allHistory.takeLast(15)
                            last15.dropWhile { it.role != MessageRole.USER }.ifEmpty { last15 }
                        } else {
                            allHistory
                        }
                        apiService.generateContentStream(
                            apiKey = apiKey,
                            model = activeModel,
                            conversationHistory = history,
                            systemInstruction = systemInstruction.ifBlank { null },
                            temperature = temperature,
                            customBaseUrl = proxyUrl
                        ).collect { chunk ->
                            accumulatedText.append(chunk)
                            val idx = currentMessages.indexOfFirst { it.id == assistantMessage.id }
                            if (idx != -1) {
                                currentMessages[idx] = assistantMessage.copy(
                                    text = accumulatedText.toString(),
                                    modelUsed = activeModel,
                                    isPending = true
                                )
                            }
                        }
                        streamSuccess = true
                    } catch (retryError: Exception) {
                        handleStreamError(assistantMessage.id, retryError, accumulatedText)
                    }
                } else {
                    handleStreamError(assistantMessage.id, e, accumulatedText)
                }
            }

            if (streamSuccess) {
                val idx = currentMessages.indexOfFirst { it.id == assistantMessage.id }
                if (idx != -1) {
                    currentMessages[idx] = assistantMessage.copy(
                        text = accumulatedText.toString(),
                        modelUsed = activeModel,
                        isPending = false
                    )
                }
            }

            isLoading = false
            commitCurrentMessages()
            saveConversations()
        }
    }

    private fun handleStreamError(messageId: String, error: Throwable, partialText: StringBuilder) {
        val idx = currentMessages.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            val errorMessage = error.localizedMessage?.ifBlank { null }
                ?: error.message?.ifBlank { null }
                ?: "Неизвестная ошибка связи с Gemini API"
            if (partialText.isEmpty()) {
                currentMessages[idx] = ChatMessage(
                    role = MessageRole.ERROR,
                    text = errorMessage
                )
            } else {
                currentMessages[idx] = currentMessages[idx].copy(
                    isPending = false,
                    text = partialText.toString() + "\n\n[Прервано: $errorMessage]"
                )
            }
        }
    }

    fun retryLastMessage() {
        val lastUserMessage = currentMessages.lastOrNull { it.role == MessageRole.USER } ?: return

        if (currentMessages.lastOrNull()?.role == MessageRole.ERROR) {
            currentMessages.removeAt(currentMessages.size - 1)
            commitCurrentMessages()
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
