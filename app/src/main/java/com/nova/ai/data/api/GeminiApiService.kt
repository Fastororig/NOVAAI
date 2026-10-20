package com.nova.ai.data.api

import android.util.Log
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

    companion object {
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com"

        /**
         * Normalizes user-specified Base URL / Proxy:
         * - If blank or null, returns the default Gemini API base URL.
         * - Guarantees scheme (https:// if not specified).
         * - Strips trailing slashes and accidental /v1beta suffixes.
         */
        fun normalizeBaseUrl(url: String?): String {
            if (url.isNullOrBlank()) return DEFAULT_BASE_URL
            var clean = url.trim()
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                clean = "https://$clean"
            }
            clean = clean.trimEnd('/')
            if (clean.endsWith("/v1beta")) {
                clean = clean.substringBeforeLast("/v1beta").trimEnd('/')
            }
            return clean.ifBlank { DEFAULT_BASE_URL }
        }
    }

    var defaultBaseUrl: String = DEFAULT_BASE_URL

    fun getEffectiveBaseUrl(customBaseUrl: String? = null): String {
        return normalizeBaseUrl(if (!customBaseUrl.isNullOrBlank()) customBaseUrl else defaultBaseUrl)
    }

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
     * for HTTP 503 (Model Overloaded / Service Unavailable), 429 (Resource Exhausted),
     * and transient server/network errors. Total of 3 attempts.
     */
    private suspend fun executeWithRetry(
        requestBuilder: () -> Request
    ): Response {
        val maxAttempts = 3
        val retryDelaysMs = listOf(1500L, 3000L)
        var attempt = 0

        while (true) {
            attempt++
            try {
                val request = requestBuilder()
                val response = client.newCall(request).execute()

                // Check for 503, 429, 502, 504 retry condition
                val code = response.code
                val isRetryableCode = code == 503 || code == 429 || code == 502 || code == 504
                if (isRetryableCode && attempt < maxAttempts) {
                    val delayMs = retryDelaysMs[attempt - 1]
                    response.close()
                    delay(delayMs)
                    continue
                }

                return response
            } catch (e: IOException) {
                // Retry transient network dropouts up to maxAttempts
                if (attempt < maxAttempts) {
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
        displayName: String,
        customBaseUrl: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках."))
        }

        try {
            val baseUrl = getEffectiveBaseUrl(customBaseUrl)
            // Step 1: Initiate resumable upload with retry
            val metadataJson = "{\"file\": {\"displayName\": \"${displayName.replace("\"", "\\\"")}\"}}"
            val initResponse = executeWithRetry {
                Request.Builder()
                    .url("$baseUrl/upload/v1beta/files?key=$apiKey")
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
    suspend fun fetchAvailableModels(
        apiKey: String,
        customBaseUrl: String? = null
    ): Result<List<UiGeminiModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках.")
            )
        }

        val baseUrl = getEffectiveBaseUrl(customBaseUrl)
        val allCollectedRemoteModels = mutableListOf<GeminiRemoteModelItem>()
        var pageToken: String? = null
        var pageCount = 0

        try {
            do {
                val urlBuilder = StringBuilder("$baseUrl/v1beta/models?key=$apiKey")
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

            // Filter models: keep only stable release flash & pro versions, exclude preview & customtools
            val validModels = allCollectedRemoteModels.filter { model ->
                val id = model.cleanId
                val name = model.name ?: ""
                val supportsGen = model.supportedGenerationMethods?.contains("generateContent") == true
                val isFlashOrPro = id.contains("flash", ignoreCase = true) || id.contains("pro", ignoreCase = true) ||
                                  name.contains("flash", ignoreCase = true) || name.contains("pro", ignoreCase = true)
                val isExcluded = id.contains("preview", ignoreCase = true) || id.contains("customtools", ignoreCase = true) ||
                                 name.contains("preview", ignoreCase = true) || name.contains("customtools", ignoreCase = true)

                supportsGen && isFlashOrPro && !isExcluded
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
        temperature: Float = 0.7f,
        customBaseUrl: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках.")
            )
        }

        // 3. Optimize history context: limit to last 15 messages (trim context)
        val trimmedHistory = if (conversationHistory.size > 15) {
            val last15 = conversationHistory.takeLast(15)
            last15.dropWhile { it.role != MessageRole.USER }.ifEmpty { last15 }
        } else {
            conversationHistory
        }

        // Map chat history to Gemini ContentItems (supporting text, inlineData, and fileData)
        val contents = mutableListOf<ContentItem>()

        for (msg in trimmedHistory) {
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

        // 2. Disable thinking delay with thinkingBudget = 0 in generationConfig
        val requestPayload = GeminiGenerateRequest(
            contents = contents,
            systemInstruction = systemInst,
            generationConfig = GenerationConfigItem(
                temperature = temperature.coerceIn(0.0f, 2.0f),
                maxOutputTokens = 4096,
                thinkingConfig = ThinkingConfigItem(thinkingBudget = 0)
            )
        )

        val jsonBody = gson.toJson(requestPayload)
        val requestBody = jsonBody.toRequestBody(jsonMediaType)

        val baseUrl = getEffectiveBaseUrl(customBaseUrl)
        // Ensure clean model ID without redundant models/ prefix
        val cleanModel = model.removePrefix("models/").trim().ifBlank { UiGeminiModel.DEFAULT_MODEL_ID }
        val url = "$baseUrl/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        // 4. Measure timings using System.currentTimeMillis() and Logcat tag "GeminiPerf"
        val requestStartTime = System.currentTimeMillis()
        Log.i("GeminiPerf", "[$cleanModel] 1. Request sent at: $requestStartTime ms (history count: ${trimmedHistory.size})")

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
                val errorTime = System.currentTimeMillis()
                Log.e("GeminiPerf", "[$cleanModel] Request failed at: $errorTime ms (duration: ${errorTime - requestStartTime} ms, HTTP ${response.code})")
                val errorDetails = parseApiErrorDetails(response.code, responseBody)
                return@withContext Result.failure(
                    ApiException(
                        code = response.code,
                        message = errorDetails.userFriendlyMessage,
                        status = errorDetails.status,
                        isOverloaded = errorDetails.isOverloaded,
                        isResourceExhausted = errorDetails.isResourceExhausted
                    )
                )
            }

            val parsedResponse = gson.fromJson(responseBody, GeminiGenerateResponse::class.java)
            val candidate = parsedResponse.candidates?.firstOrNull()
            val candidateText = candidate?.content?.parts?.firstOrNull()?.text

            val completionTime = System.currentTimeMillis()
            val totalDuration = completionTime - requestStartTime
            Log.i("GeminiPerf", "[$cleanModel] 3. Response completed at: $completionTime ms. Total duration: ${totalDuration} ms (TTFT: ${totalDuration} ms)")

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
            val errorTime = System.currentTimeMillis()
            Log.e("GeminiPerf", "[$cleanModel] Network error after ${errorTime - requestStartTime} ms: ${e.message}")
            Result.failure(Exception("Нет подключения к интернету или превышено время ожидания."))
        } catch (e: Exception) {
            val errorTime = System.currentTimeMillis()
            Log.e("GeminiPerf", "[$cleanModel] Error after ${errorTime - requestStartTime} ms: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Streaming generateContent with automatic retry (up to 3 attempts total)
     * on connection failure or stream errors caused by 503, UNAVAILABLE, overloaded, or resource exhausted.
     * Emits chunks of text in real time via Kotlin Flow directly as SSE lines arrive.
     */
    fun generateContentStream(
        apiKey: String,
        model: String,
        conversationHistory: List<ChatMessage>,
        systemInstruction: String? = null,
        temperature: Float = 0.7f,
        customBaseUrl: String? = null
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("Недействительный API-ключ. Проверьте правильность ключа в настройках.")
        }

        // 3. Optimize history context: limit to last 15 messages (trim context)
        val trimmedHistory = if (conversationHistory.size > 15) {
            val last15 = conversationHistory.takeLast(15)
            last15.dropWhile { it.role != MessageRole.USER }.ifEmpty { last15 }
        } else {
            conversationHistory
        }

        val contents = mutableListOf<ContentItem>()
        for (msg in trimmedHistory) {
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

        // 2. Disable thinking delay with thinkingBudget = 0 in generationConfig
        val requestPayload = GeminiGenerateRequest(
            contents = contents,
            systemInstruction = systemInst,
            generationConfig = GenerationConfigItem(
                temperature = temperature.coerceIn(0.0f, 2.0f),
                maxOutputTokens = 4096,
                thinkingConfig = ThinkingConfigItem(thinkingBudget = 0)
            )
        )

        val baseUrl = getEffectiveBaseUrl(customBaseUrl)
        val cleanModel = model.removePrefix("models/").trim().ifBlank { UiGeminiModel.DEFAULT_MODEL_ID }
        // 1. Ensure streaming endpoint :streamGenerateContent?alt=sse
        val url = "$baseUrl/v1beta/models/$cleanModel:streamGenerateContent?alt=sse&key=$apiKey"
        val jsonPayload = gson.toJson(requestPayload)

        val maxAttempts = 3
        val retryDelaysMs = listOf(1500L, 3000L)
        var attempt = 0
        var hasEmittedAnyText = false

        // 4. Measure timings using System.currentTimeMillis() and Logcat tag "GeminiPerf"
        val requestStartTime = System.currentTimeMillis()
        var firstChunkTime: Long? = null
        Log.i("GeminiPerf", "[$cleanModel] 1. Request sent at: $requestStartTime ms (history count: ${trimmedHistory.size})")

        while (attempt < maxAttempts) {
            attempt++
            val requestBody = jsonPayload.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .build()

            var response: Response? = null
            var streamError: Throwable? = null

            try {
                response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val errorDetails = parseApiErrorDetails(response.code, errBody)
                    val apiException = ApiException(
                        code = response.code,
                        message = errorDetails.userFriendlyMessage,
                        status = errorDetails.status,
                        isOverloaded = errorDetails.isOverloaded,
                        isResourceExhausted = errorDetails.isResourceExhausted
                    )

                    // Retry up to 3 attempts on retryable errors
                    if (errorDetails.isRetryable && attempt < maxAttempts) {
                        response.close()
                        delay(retryDelaysMs[attempt - 1])
                        continue
                    } else {
                        throw apiException
                    }
                }

                val body = response.body ?: throw IOException("Пустой ответ от сервера")
                // Read stream directly line-by-line without buffering entire body into memory
                val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8), 1024)

                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.startsWith("data:")) {
                            val dataJson = currentLine.substringAfter("data:").trim()
                            if (dataJson.isNotEmpty() && dataJson != "[DONE]") {
                                if (dataJson.startsWith("{\"error\":")) {
                                    val errDetails = parseApiErrorDetails(response.code, dataJson)
                                    throw ApiException(
                                        code = errDetails.code,
                                        message = errDetails.userFriendlyMessage,
                                        status = errDetails.status,
                                        isOverloaded = errDetails.isOverloaded,
                                        isResourceExhausted = errDetails.isResourceExhausted
                                    )
                                }

                                val chunk = gson.fromJson(dataJson, GeminiGenerateResponse::class.java)
                                val blockReason = chunk.promptFeedback?.blockReason
                                val candidate = chunk.candidates?.firstOrNull()
                                val finishReason = candidate?.finishReason

                                if (blockReason != null || finishReason == "SAFETY") {
                                    throw Exception("Ответ или прикреплённое фото заблокированы фильтрами безопасности Google.")
                                }

                                val parts = candidate?.content?.parts
                                if (!parts.isNullOrEmpty()) {
                                    for (part in parts) {
                                        val partText = part.text
                                        if (!partText.isNullOrEmpty()) {
                                            // 4. Log TTFT (Time To First Token) upon receiving first chunk
                                            if (firstChunkTime == null) {
                                                firstChunkTime = System.currentTimeMillis()
                                                val ttft = firstChunkTime!! - requestStartTime
                                                Log.i("GeminiPerf", "[$cleanModel] 2. First chunk received (TTFT): ${ttft} ms (at $firstChunkTime ms)")
                                            }
                                            hasEmittedAnyText = true
                                            // 1. Emit chunk immediately to UI
                                            emit(partText)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    try { reader.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                streamError = e
            } finally {
                try { response?.close() } catch (_: Exception) {}
            }

            if (streamError != null) {
                val isRetryable = when (streamError) {
                    is ApiException -> streamError.isRetryable
                    is IOException -> true
                    else -> false
                }

                // Retry if no partial tokens were emitted to prevent duplicated messages
                if (!hasEmittedAnyText && isRetryable && attempt < maxAttempts) {
                    delay(retryDelaysMs[attempt - 1])
                    continue
                }
                val errorTime = System.currentTimeMillis()
                Log.e("GeminiPerf", "[$cleanModel] Stream failed at $errorTime ms (duration: ${errorTime - requestStartTime} ms): ${streamError.message}")
                throw streamError
            }

            if (!hasEmittedAnyText) {
                val errorTime = System.currentTimeMillis()
                Log.e("GeminiPerf", "[$cleanModel] Stream ended without tokens after ${errorTime - requestStartTime} ms")
                throw Exception("Получен пустой ответ от модели Gemini.")
            }

            // 4. Log full completion time
            val completionTime = System.currentTimeMillis()
            val totalDuration = completionTime - requestStartTime
            val ttftDuration = if (firstChunkTime != null) firstChunkTime!! - requestStartTime else totalDuration
            Log.i("GeminiPerf", "[$cleanModel] 3. Stream completed at: $completionTime ms. Total duration: ${totalDuration} ms, TTFT: ${ttftDuration} ms")

            return@flow
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Parses error response from Gemini API into structured details,
     * identifying genuine overload, quota exhaustion, or distinct client/server errors
     * instead of masking non-overload errors.
     */
    fun parseApiErrorDetails(statusCode: Int, responseBody: String): ApiErrorDetails {
        var parsedCode = statusCode
        var serverMessage: String? = null
        var errorStatus: String? = null

        if (responseBody.isNotBlank()) {
            try {
                val errorWrapper = gson.fromJson(responseBody, GeminiApiErrorWrapper::class.java)
                errorWrapper?.error?.let { err ->
                    if (err.code != null && err.code != 0) parsedCode = err.code
                    serverMessage = err.message
                    errorStatus = err.status
                }
            } catch (_: Exception) {
                if (responseBody.length < 200 && !responseBody.contains("<html", ignoreCase = true)) {
                    serverMessage = responseBody.trim()
                }
            }
        }

        val msgLower = (serverMessage ?: "").lowercase()
        val statusUpper = (errorStatus ?: "").uppercase()

        // 1. Check for genuine model overload
        val isOverloaded = parsedCode == 503 ||
                statusUpper == "UNAVAILABLE" ||
                msgLower.contains("overload") ||
                msgLower.contains("unavailable") ||
                msgLower.contains("перегруж")

        // 2. Check for resource / quota exhaustion
        val isResourceExhausted = parsedCode == 429 ||
                statusUpper == "RESOURCE_EXHAUSTED" ||
                msgLower.contains("resource exhausted") ||
                msgLower.contains("resource_exhausted") ||
                msgLower.contains("quota") ||
                msgLower.contains("rate limit")

        val userFriendlyMessage = when {
            isOverloaded -> {
                val detail = if (!serverMessage.isNullOrBlank()) " ($serverMessage)" else ""
                "Серверы модели временно перегружены запросами (HTTP 503 UNAVAILABLE)$detail. Подождите несколько секунд и попробуйте снова или временно выберите другую модель."
            }
            isResourceExhausted -> {
                val detail = if (!serverMessage.isNullOrBlank()) " ($serverMessage)" else ""
                "Превышен лимит запросов к Gemini API (HTTP 429 Resource Exhausted)$detail. Подождите 1–2 минуты или используйте другой API-ключ."
            }
            parsedCode == 401 || parsedCode == 403 || statusUpper == "PERMISSION_DENIED" || statusUpper == "UNAUTHENTICATED" -> {
                val detail = if (!serverMessage.isNullOrBlank()) " Детали: $serverMessage." else ""
                "Ошибка авторизации (HTTP $parsedCode): Недействительный API-ключ или нет доступа.$detail Проверьте правильность ключа в настройках."
            }
            parsedCode == 404 || statusUpper == "NOT_FOUND" -> {
                val detail = if (!serverMessage.isNullOrBlank()) ": $serverMessage" else ""
                "Выбранная модель не найдена в Gemini API (HTTP 404)$detail. Попробуйте выбрать другую модель в заголовке чата."
            }
            parsedCode == 400 || statusUpper == "INVALID_ARGUMENT" || statusUpper == "FAILED_PRECONDITION" -> {
                if (msgLower.contains("location")) {
                    "Доступ к Gemini API ограничен в вашем регионе (HTTP 400): ${serverMessage ?: "User location is not supported"}."
                } else {
                    val detail = if (!serverMessage.isNullOrBlank()) ": $serverMessage" else ""
                    "Некорректный запрос к Gemini API (HTTP 400$statusUpper)$detail."
                }
            }
            parsedCode == 500 || statusUpper == "INTERNAL" -> {
                val detail = if (!serverMessage.isNullOrBlank()) ": $serverMessage" else ""
                "Внутренняя ошибка сервиса Google Gemini (HTTP 500 INTERNAL)$detail. Попробуйте повторить запрос позже."
            }
            parsedCode == 502 || parsedCode == 504 -> {
                "Сетевой шлюз Google временно недоступен (HTTP $parsedCode). Повторите попытку через несколько секунд."
            }
            else -> {
                if (!serverMessage.isNullOrBlank()) {
                    "Ошибка Gemini API (HTTP $parsedCode${if (errorStatus != null) " $errorStatus" else ""}): $serverMessage"
                } else {
                    "Ошибка связи с Gemini API (HTTP $parsedCode)."
                }
            }
        }

        return ApiErrorDetails(
            code = parsedCode,
            serverMessage = serverMessage,
            status = errorStatus,
            isOverloaded = isOverloaded,
            isResourceExhausted = isResourceExhausted,
            userFriendlyMessage = userFriendlyMessage
        )
    }

    /**
     * Formats error codes according to user specifications.
     */
    fun parseApiError(statusCode: Int, responseBody: String): String {
        return parseApiErrorDetails(statusCode, responseBody).userFriendlyMessage
    }
}

data class ApiErrorDetails(
    val code: Int,
    val serverMessage: String?,
    val status: String?,
    val isOverloaded: Boolean,
    val isResourceExhausted: Boolean,
    val userFriendlyMessage: String
) {
    val isRetryable: Boolean
        get() = isOverloaded || isResourceExhausted || code in listOf(429, 502, 503, 504)
}

class ApiException(
    val code: Int,
    message: String,
    val status: String? = null,
    val isOverloaded: Boolean = false,
    val isResourceExhausted: Boolean = false
) : Exception(message) {
    val isRetryable: Boolean
        get() = isOverloaded || isResourceExhausted || code in listOf(429, 502, 503, 504)
}
