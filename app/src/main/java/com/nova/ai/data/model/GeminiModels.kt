package com.nova.ai.data.model

import com.google.gson.annotations.SerializedName
import java.util.UUID

// ==========================================
// Gemini REST API Data Models (v1beta)
// ==========================================

data class GeminiGenerateRequest(
    @SerializedName("contents") val contents: List<ContentItem>,
    @SerializedName("systemInstruction") val systemInstruction: SystemInstructionItem? = null,
    @SerializedName("generationConfig") val generationConfig: GenerationConfigItem? = null
)

data class ContentItem(
    @SerializedName("role") val role: String, // "user" or "model"
    @SerializedName("parts") val parts: List<PartItem>
)

data class InlineDataItem(
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("data") val data: String // Base64 encoded bytes
)

data class FileDataItem(
    @SerializedName("fileUri") val fileUri: String,
    @SerializedName("mimeType") val mimeType: String
)

data class PartItem(
    @SerializedName("text") val text: String? = null,
    @SerializedName("inlineData") val inlineData: InlineDataItem? = null,
    @SerializedName("fileData") val fileData: FileDataItem? = null,
    @SerializedName("thought") val thought: Boolean? = null
)

data class SystemInstructionItem(
    @SerializedName("parts") val parts: List<PartItem>
)

data class GenerationConfigItem(
    @SerializedName("temperature") val temperature: Float? = 0.7f,
    @SerializedName("topK") val topK: Int? = 40,
    @SerializedName("topP") val topP: Float? = 0.95f,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int? = 4096,
    @SerializedName("thinkingConfig") val thinkingConfig: ThinkingConfigItem? = null
)

data class ThinkingConfigItem(
    @SerializedName("thinkingBudget") val thinkingBudget: Int = 0
)

// ==========================================
// Gemini Files API Data Models
// ==========================================

data class GeminiFileUploadResponse(
    @SerializedName("file") val file: GeminiFileRemoteItem?
)

data class GeminiFileRemoteItem(
    @SerializedName("name") val name: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("mimeType") val mimeType: String?,
    @SerializedName("sizeBytes") val sizeBytes: String?,
    @SerializedName("uri") val uri: String?
)

// ==========================================
// Gemini Models List API Data Models
// ==========================================

data class GeminiListModelsResponse(
    @SerializedName("models") val models: List<GeminiRemoteModelItem>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class GeminiRemoteModelItem(
    @SerializedName("name") val name: String, // e.g. "models/gemini-2.5-flash"
    @SerializedName("baseModelId") val baseModelId: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("inputTokenLimit") val inputTokenLimit: Long? = null,
    @SerializedName("outputTokenLimit") val outputTokenLimit: Long? = null,
    @SerializedName("supportedGenerationMethods") val supportedGenerationMethods: List<String>? = null,
    @SerializedName("temperature") val temperature: Float? = null,
    @SerializedName("topP") val topP: Float? = null,
    @SerializedName("topK") val topK: Int? = null
) {
    val cleanId: String
        get() = name.removePrefix("models/").trim()
}

// ==========================================
// Gemini Response Data Models
// ==========================================

data class GeminiGenerateResponse(
    @SerializedName("candidates") val candidates: List<CandidateItem>?,
    @SerializedName("promptFeedback") val promptFeedback: PromptFeedbackItem?,
    @SerializedName("usageMetadata") val usageMetadata: UsageMetadataItem?
)

data class CandidateItem(
    @SerializedName("content") val content: ContentItem?,
    @SerializedName("finishReason") val finishReason: String?,
    @SerializedName("index") val index: Int?
)

data class PromptFeedbackItem(
    @SerializedName("blockReason") val blockReason: String?
)

data class UsageMetadataItem(
    @SerializedName("promptTokenCount") val promptTokenCount: Int?,
    @SerializedName("candidatesTokenCount") val candidatesTokenCount: Int?,
    @SerializedName("totalTokenCount") val totalTokenCount: Int?
)

data class GeminiApiErrorWrapper(
    @SerializedName("error") val error: GeminiApiErrorItem?
)

data class GeminiApiErrorItem(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?,
    @SerializedName("status") val status: String?
)

// ==========================================
// Internal Application UI Models
// ==========================================

enum class MessageRole {
    USER,
    MODEL,
    ERROR
}

data class AttachmentInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val uriString: String? = null,
    val base64Data: String? = null,
    val remoteFileUri: String? = null,
    val isImage: Boolean = mimeType.startsWith("image/")
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val isPending: Boolean = false,
    val attachments: List<AttachmentInfo> = emptyList()
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Новый диалог",
    val createdAt: Long = System.currentTimeMillis(),
    val messages: MutableList<ChatMessage> = mutableListOf()
)

enum class ModelFamily(val title: String) {
    FLASH("Семейство Flash"),
    PRO("Семейство Pro"),
    OTHER("Другие модели")
}

data class UiGeminiModel(
    val id: String, // clean identifier e.g. "gemini-2.5-flash"
    val displayName: String,
    val description: String,
    val family: ModelFamily,
    val inputTokenLimit: Long? = null,
    val outputTokenLimit: Long? = null,
    val isRecommended: Boolean = false
) {
    companion object {
        const val RECOMMENDED_MODEL_ID = "gemini-3.1-flash-lite"
        const val DEFAULT_MODEL_ID = "gemini-2.5-flash"

        fun fromRemote(item: GeminiRemoteModelItem, isRecommended: Boolean = false): UiGeminiModel {
            val id = item.cleanId
            val nameLower = (item.displayName ?: id).lowercase()
            val family = when {
                nameLower.contains("flash") -> ModelFamily.FLASH
                nameLower.contains("pro") -> ModelFamily.PRO
                else -> ModelFamily.OTHER
            }
            // Strict recommendation check: only gemini-3.1-flash-lite can have isRecommended = true
            val strictlyRecommended = isRecommended && id == RECOMMENDED_MODEL_ID
            return UiGeminiModel(
                id = id,
                displayName = item.displayName ?: id,
                description = item.description ?: "Модель Google Gemini API ($id)",
                family = family,
                inputTokenLimit = item.inputTokenLimit,
                outputTokenLimit = item.outputTokenLimit,
                isRecommended = strictlyRecommended
            )
        }

        val INITIAL_FALLBACK = listOf(
            UiGeminiModel(
                id = RECOMMENDED_MODEL_ID,
                displayName = "Gemini 3.1 Flash-Lite",
                description = "Рекомендуемая стабильная модель. Высокая скорость генерации, экономичность и надежность",
                family = ModelFamily.FLASH,
                isRecommended = true
            ),
            UiGeminiModel(
                id = DEFAULT_MODEL_ID,
                displayName = "Gemini 2.5 Flash",
                description = "Базовая стабильная модель по умолчанию. Новейшая быстрая и умная для повседневных задач",
                family = ModelFamily.FLASH,
                isRecommended = false
            ),
            UiGeminiModel(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                description = "Максимальная логика, рассуждения и анализ сложных текстов",
                family = ModelFamily.PRO,
                isRecommended = false
            ),
            UiGeminiModel(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash",
                description = "Высокая скорость, оптимизирована для мобильных устройств",
                family = ModelFamily.FLASH,
                isRecommended = false
            ),
            UiGeminiModel(
                id = "gemini-1.5-flash",
                displayName = "Gemini 1.5 Flash",
                description = "Проверенная легкая модель с длинным контекстом",
                family = ModelFamily.FLASH,
                isRecommended = false
            ),
            UiGeminiModel(
                id = "gemini-1.5-pro",
                displayName = "Gemini 1.5 Pro",
                description = "Глубокий анализ кода и документов",
                family = ModelFamily.PRO,
                isRecommended = false
            )
        )
    }
}
