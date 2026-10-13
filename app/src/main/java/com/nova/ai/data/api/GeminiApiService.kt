package com.nova.ai.data.api

import com.google.gson.Gson
import com.nova.ai.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiApiService {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Configured with standard timeouts and connection specs for Android 7.0 (API 24)
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
        .build()

    /**
     * Executes HTTP requests with automatic retry and exponential backoff
     * for HTTP 503 (Model Overloaded / Service Unavailable) and 429 (Resource Exhausted).
     * Delays: 1.5s -> 3.0s (up to 3 attempts total).
     */
    private suspend fun executeWithRetry(
        requestBuilder: () -> Request
    ): Response {
        val retryDelaysMs = listOf(1500L, 3000L)
        var attempt = 0

        while (true) {
            attempt++
            try {
                val request = requestBuilder()
                val response = client.newCall(request).execute()

                // Check for 503 or 429 retry condition
                if ((response.code == 503 || response.code == 429) && attempt <= retryDelaysMs.size) {
                    val delayMs = retryDelaysMs[attempt - 1]
                    response.close()
                    delay(delayMs)
                    continue
                }

                return response
            } catch (e: IOException) {
                // Retry transient network dropouts once
                if (attempt <= retryDelaysMs.size) {
                    val delayMs = retryDelaysMs[attempt - 1]
                    delay(delayMs)
                    continue
                }
                throw e
            }
        }
    }

    /**
     * Uploads large files (> 20MB) to Google Gemini Files API.
     * Returns the file URI (e.g. "https://generativelanguage.googleapis.com/v1beta/files/...").
     */
    suspend fun uploadFileToGemini(
        apiKey: String,
        fileBytes: ByteArray,
        mimeType: String,
        displayName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках."))
        }

        try {
            // Step 1: Initiate resumable upload with retry
            val metadataJson = "{\"file\": {\"displayName\": \"${displayName.replace("\"", "\\\"")}\"}}"
            val initResponse = executeWithRetry {
                Request.Builder()
                    .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
                    .post(metadataJson.toRequestBody(jsonMediaType))
                    .header("X-Goog-Upload-Protocol", "resumable")
                    .header("X-Goog-Upload-Command", "start")
                    .header("X-Goog-Upload-Header-Content-Length", fileBytes.size.toString())
                    .header("X-Goog-Upload-Header-Content-Type", mimeType)
                    .build()
            }

            if (!initResponse.isSuccessful) {
                val responseBody = initResponse.body?.string() ?: ""
                val err = parseApiError(initResponse.code, responseBody)
                return@withContext Result.failure(Exception(err))
            }

            val uploadUrl = initResponse.header("X-Goog-Upload-URL")
                ?: return@withContext Result.failure(Exception("Не получен URL загрузки от Files API"))

            // Step 2: Upload bytes and finalize with retry
            val mediaType = mimeType.toMediaType()
            val uploadBody = fileBytes.toRequestBody(mediaType)
            val uploadResponse = executeWithRetry {
                Request.Builder()
                    .url(uploadUrl)
                    .post(uploadBody)
                    .header("Content-Length", fileBytes.size.toString())
                    .header("X-Goog-Upload-Offset", "0")
                    .header("X-Goog-Upload-Command", "upload, finalize")
                    .build()
            }

            val uploadResponseBody = uploadResponse.body?.string() ?: ""

            if (!uploadResponse.isSuccessful) {
                val err = parseApiError(uploadResponse.code, uploadResponseBody)
                return@withContext Result.failure(Exception(err))
            }

            val parsedResponse = gson.fromJson(uploadResponseBody, GeminiFileUploadResponse::class.java)
            val fileUri = parsedResponse.file?.uri

            if (!fileUri.isNullOrBlank()) {
                Result.success(fileUri)
            } else {
                Result.failure(Exception("Files API не вернул URI загруженного файла"))
            }
        } catch (e: IOException) {
            Result.failure(Exception("Нет подключения к интернету или превышено время ожидания."))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка отправки файла: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    /**
     * Fetches all models from Google Gemini API with pagination support (nextPageToken),
     * filters by supportedGenerationMethods containing "generateContent",
     * removes "models/" prefix, and categorizes into Flash, Pro, and Other families.
     * Retries automatically on 503 and 429.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<UiGeminiModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках.")
            )
        }

        val allCollectedRemoteModels = mutableListOf<GeminiRemoteModelItem>()
        var pageToken: String? = null
        var pageCount = 0

        try {
            do {
                val urlBuilder = StringBuilder("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                if (!pageToken.isNullOrBlank()) {
                    urlBuilder.append("&pageToken=$pageToken")
                }

                val currentUrl = urlBuilder.toString()
                val response = executeWithRetry {
                    Request.Builder()
                        .url(currentUrl)
                        .get()
                        .build()
                }

                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorMessage = parseApiError(response.code, responseBody)
                    return@withContext Result.failure(Exception(errorMessage))
                }

                val parsed = gson.fromJson(responseBody, GeminiListModelsResponse::class.java)
                val pageModels = parsed?.models ?: emptyList()

                allCollectedRemoteModels.addAll(pageModels)
                pageToken = parsed?.nextPageToken
                pageCount++

                // Guard against infinite pagination loop
                if (pageCount > 10) break
            } while (!pageToken.isNullOrBlank())

            // Filter models that support generateContent
            val validModels = allCollectedRemoteModels.filter { model ->
                model.supportedGenerationMethods?.contains("generateContent") == true
            }

            if (validModels.isEmpty()) {
                // If API returned 0 valid models, return fallback list
                return@withContext Result.success(UiGeminiModel.INITIAL_FALLBACK)
            }

            // Map to UI models with exact clean IDs (without models/ prefix).
            // Бейдж «Рекомендуется» отображается строго у одной стабильной модели (gemini-3.1-flash-lite),
            // а у остальных версий и Preview-моделей скрыт.
            val uiModels = validModels.map { remoteItem ->
                val cleanId = remoteItem.cleanId
                val isRecommended = cleanId == UiGeminiModel.RECOMMENDED_MODEL_ID
                UiGeminiModel.fromRemote(remoteItem, isRecommended)
            }

            Result.success(uiModels)
        } catch (e: IOException) {
            Result.failure(Exception("Нет подключения к интернету или превышено время ожидания."))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка обработки списка моделей: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    /**
     * Sends a chat prompt and conversation history to Gemini generateContent.
     * Includes automatic retry with exponential backoff on 503 and 429,
     * and returns user-friendly error messages.
     */
    suspend fun generateContent(
        apiKey: String,
        model: String,
        conversationHistory: List<ChatMessage>,
        systemInstruction: String? = null,
        temperature: Float = 0.7f
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках.")
            )
        }

        // Map chat history to Gemini ContentItems (supporting text, inlineData, and fileData)
        val contents = mutableListOf<ContentItem>()

        for (msg in conversationHistory) {
            if (msg.role == MessageRole.ERROR) continue

            val parts = mutableListOf<PartItem>()

            // If user message has attachments, add them as parts
            if (msg.role == MessageRole.USER && msg.attachments.isNotEmpty()) {
                for (att in msg.attachments) {
                    if (!att.remoteFileUri.isNullOrBlank()) {
                        parts.add(
                            PartItem(
                                fileData = FileDataItem(
                                    fileUri = att.remoteFileUri,
                                    mimeType = att.mimeType
                                )
                            )
                        )
                    } else if (!att.base64Data.isNullOrBlank()) {
                        parts.add(
                            PartItem(
                                inlineData = InlineDataItem(
                                    mimeType = att.mimeType,
                                    data = att.base64Data
                                )
                            )
                        )
                    }
                }
            }

            // Add text part if text is present or as fallback
            if (msg.text.isNotBlank()) {
                parts.add(PartItem(text = msg.text))
            } else if (parts.isEmpty()) {
                parts.add(PartItem(text = " "))
            }

            contents.add(
                ContentItem(
                    role = if (msg.role == MessageRole.USER) "user" else "model",
                    parts = parts
                )
            )
        }

        if (contents.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("История сообщений пуста."))
        }

        val systemInst = if (!systemInstruction.isNullOrBlank()) {
            SystemInstructionItem(parts = listOf(PartItem(text = systemInstruction.trim())))
        } else null

        val requestPayload = GeminiGenerateRequest(
            contents = contents,
            systemInstruction = systemInst,
            generationConfig = GenerationConfigItem(
                temperature = temperature.coerceIn(0.0f, 2.0f),
                maxOutputTokens = 4096
            )
        )

        val jsonBody = gson.toJson(requestPayload)
        val requestBody = jsonBody.toRequestBody(jsonMediaType)

        // Ensure clean model ID without redundant models/ prefix
        val cleanModel = model.removePrefix("models/").trim().ifBlank { UiGeminiModel.DEFAULT_MODEL_ID }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        try {
            val response = executeWithRetry {
                Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .build()
            }

            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMessage = parseApiError(response.code, responseBody)
                return@withContext Result.failure(ApiException(response.code, errorMessage))
            }

            val parsedResponse = gson.fromJson(responseBody, GeminiGenerateResponse::class.java)
            val candidate = parsedResponse.candidates?.firstOrNull()
            val candidateText = candidate?.content?.parts?.firstOrNull()?.text

            if (!candidateText.isNullOrBlank()) {
                Result.success(candidateText)
            } else {
                val blockReason = parsedResponse.promptFeedback?.blockReason
                val finishReason = candidate?.finishReason
                if (blockReason != null || finishReason == "SAFETY") {
                    Result.failure(Exception("Ответ или прикреплённое фото заблокированы фильтрами безопасности Google."))
                } else {
                    Result.failure(Exception("Получен пустой ответ от модели Gemini."))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception("Нет подключения к интернету или превышено время ожидания."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Streaming generateContent with automatic retry on initial connection failure (503/429).
     * Emits chunks of text in a Kotlin Flow.
     */
    fun generateContentStream(
        apiKey: String,
        model: String,
        conversationHistory: List<ChatMessage>,
        systemInstruction: String? = null,
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках.")
        }

        val contents = mutableListOf<ContentItem>()
        for (msg in conversationHistory) {
            if (msg.role == MessageRole.ERROR) continue
            val parts = mutableListOf<PartItem>()
            if (msg.role == MessageRole.USER && msg.attachments.isNotEmpty()) {
                for (att in msg.attachments) {
                    if (!att.remoteFileUri.isNullOrBlank()) {
                        parts.add(PartItem(fileData = FileDataItem(fileUri = att.remoteFileUri, mimeType = att.mimeType)))
                    } else if (!att.base64Data.isNullOrBlank()) {
                        parts.add(PartItem(inlineData = InlineDataItem(mimeType = att.mimeType, data = att.base64Data)))
                    }
                }
            }
            if (msg.text.isNotBlank()) parts.add(PartItem(text = msg.text))
            else if (parts.isEmpty()) parts.add(PartItem(text = " "))

            contents.add(ContentItem(role = if (msg.role == MessageRole.USER) "user" else "model", parts = parts))
        }

        val systemInst = if (!systemInstruction.isNullOrBlank()) {
            SystemInstructionItem(parts = listOf(PartItem(text = systemInstruction.trim())))
        } else null

        val requestPayload = GeminiGenerateRequest(
            contents = contents,
            systemInstruction = systemInst,
            generationConfig = GenerationConfigItem(
                temperature = temperature.coerceIn(0.0f, 2.0f),
                maxOutputTokens = 4096
            )
        )

        val cleanModel = model.removePrefix("models/").trim().ifBlank { UiGeminiModel.DEFAULT_MODEL_ID }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:streamGenerateContent?alt=sse&key=$apiKey"
        val requestBody = gson.toJson(requestPayload).toRequestBody(jsonMediaType)

        val response = executeWithRetry {
            Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .build()
        }

        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw ApiException(response.code, parseApiError(response.code, errBody))
        }

        val body = response.body ?: throw IOException("Нет подключения к интернету или превышено время ожидания.")
        val reader = BufferedReader(InputStreamReader(body.byteStream()))
        try {
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.startsWith("data: ")) {
                    val dataJson = currentLine.removePrefix("data: ").trim()
                    if (dataJson.isNotEmpty() && dataJson != "[DONE]") {
                        try {
                            val chunk = gson.fromJson(dataJson, GeminiGenerateResponse::class.java)
                            val text = chunk.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (!text.isNullOrEmpty()) {
                                emit(text)
                            }
                        } catch (_: Exception) {
                            // Skip parse errors on intermediate SSE events
                        }
                    }
                }
            }
        } finally {
            reader.close()
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Formats error codes according to user specifications.
     */
    fun parseApiError(statusCode: Int, responseBody: String): String {
        return when (statusCode) {
            503 -> "Серверы Google сейчас перегружены. Подождите несколько секунд и попробуйте снова или временно выберите другую модель."
            429 -> "Превышен лимит бесплатных запросов. Подождите 1–2 минуты."
            401, 403 -> "Недействительный API-ключ. Проверьте правильность ключа в настройках."
            400, 404 -> "Выбранная модель недоступна или запрос составлен некорректно."
            500, 502, 504 -> "Серверы Google сейчас перегружены. Подождите несколько секунд и попробуйте снова или временно выберите другую модель."
            else -> {
                try {
                    val errorResponse = gson.fromJson(responseBody, GeminiApiErrorWrapper::class.java)
                    val serverMessage = errorResponse?.error?.message
                    if (!serverMessage.isNullOrBlank()) {
                        "Ошибка Gemini API (HTTP $statusCode): $serverMessage"
                    } else {
                        "Ошибка Gemini API (HTTP $statusCode)."
                    }
                } catch (_: Exception) {
                    "Ошибка Gemini API (HTTP $statusCode)."
                }
            }
        }
    }
}

class ApiException(val code: Int, message: String) : Exception(message)
