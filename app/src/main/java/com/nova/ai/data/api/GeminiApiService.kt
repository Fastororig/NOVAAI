package com.nova.ai.data.api

import com.google.gson.Gson
import com.nova.ai.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
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
            return@withContext Result.failure(IllegalArgumentException("API-ключ не указан."))
        }

        try {
            // Step 1: Initiate resumable upload
            val metadataJson = "{\"file\": {\"displayName\": \"${displayName.replace("\"", "\\\"")}\"}}"
            val initRequest = Request.Builder()
                .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
                .post(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", fileBytes.size.toString())
                .header("X-Goog-Upload-Header-Content-Type", mimeType)
                .build()

            val initResponse = client.newCall(initRequest).execute()
            if (!initResponse.isSuccessful) {
                val err = parseApiError(initResponse.code, initResponse.body?.string() ?: "")
                return@withContext Result.failure(Exception("Ошибка инициализации загрузки файла: $err"))
            }

            val uploadUrl = initResponse.header("X-Goog-Upload-URL")
                ?: return@withContext Result.failure(Exception("Не получен URL загрузки от Files API"))

            // Step 2: Upload bytes and finalize
            val mediaType = mimeType.toMediaType()
            val uploadBody = fileBytes.toRequestBody(mediaType)
            val uploadRequest = Request.Builder()
                .url(uploadUrl)
                .post(uploadBody)
                .header("Content-Length", fileBytes.size.toString())
                .header("X-Goog-Upload-Offset", "0")
                .header("X-Goog-Upload-Command", "upload, finalize")
                .build()

            val uploadResponse = client.newCall(uploadRequest).execute()
            val uploadResponseBody = uploadResponse.body?.string() ?: ""

            if (!uploadResponse.isSuccessful) {
                val err = parseApiError(uploadResponse.code, uploadResponseBody)
                return@withContext Result.failure(Exception("Ошибка передачи файла: $err"))
            }

            val parsedResponse = gson.fromJson(uploadResponseBody, GeminiFileUploadResponse::class.java)
            val fileUri = parsedResponse.file?.uri

            if (!fileUri.isNullOrBlank()) {
                Result.success(fileUri)
            } else {
                Result.failure(Exception("Files API не вернул URI загруженного файла"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Сбой отправки файла в Gemini Files API: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    /**
     * Fetches all models from Google Gemini API with pagination support (nextPageToken),
     * filters by supportedGenerationMethods containing "generateContent",
     * and categorizes into Flash, Pro, and Other families.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<UiGeminiModel>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("API-ключ не указан. Введите ваш ключ для загрузки списка моделей.")
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

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .get()
                    .build()

                val response = client.newCall(request).execute()
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

                // Guard against infinite loop
                if (pageCount > 10) break
            } while (!pageToken.isNullOrBlank())

            // Filter models that support generateContent
            val validModels = allCollectedRemoteModels.filter { model ->
                model.supportedGenerationMethods?.contains("generateContent") == true
            }

            if (validModels.isEmpty()) {
                return@withContext Result.failure(
                    Exception("Не найдено доступных моделей с методом generateContent для данного ключа.")
                )
            }

            // Check if gemini-3.8-flash exists in the real returned models
            val hasGemini38Flash = validModels.any { it.cleanId == "gemini-3.8-flash" }

            // Map to UI models with exact clean IDs from API
            val uiModels = validModels.map { remoteItem ->
                val cleanId = remoteItem.cleanId
                val isRecommended = if (hasGemini38Flash) {
                    cleanId == "gemini-3.8-flash"
                } else {
                    cleanId == "gemini-2.5-flash" || cleanId == "gemini-1.5-flash"
                }
                UiGeminiModel.fromRemote(remoteItem, isRecommended)
            }

            Result.success(uiModels)
        } catch (e: IOException) {
            Result.failure(Exception("Ошибка сети при загрузке моделей: ${e.localizedMessage ?: "Timeout"}"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка обработки списка моделей: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    suspend fun generateContent(
        apiKey: String,
        model: String,
        conversationHistory: List<ChatMessage>,
        systemInstruction: String? = null,
        temperature: Float = 0.7f
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("API-ключ не указан. Перейдите в настройки и введите ваш ключ Google Gemini.")
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
        val cleanModel = model.removePrefix("models/").trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMessage = parseApiError(response.code, responseBody)
                return@withContext Result.failure(Exception(errorMessage))
            }

            val parsedResponse = gson.fromJson(responseBody, GeminiGenerateResponse::class.java)
            val candidateText = parsedResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

            if (candidateText != null) {
                Result.success(candidateText)
            } else {
                val blockReason = parsedResponse.promptFeedback?.blockReason
                if (blockReason != null) {
                    Result.failure(Exception("Ответ заблокирован фильтрами безопасности Google: $blockReason"))
                } else {
                    Result.failure(Exception("Получен пустой ответ от модели Gemini."))
                }
            }
        } catch (e: IOException) {
            Result.failure(Exception("Ошибка сети: не удалось подключиться к Google Gemini API. Проверьте интернет: ${e.localizedMessage ?: "Timeout"}"))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка обработки ответа: ${e.localizedMessage ?: e.javaClass.simpleName}"))
        }
    }

    private fun parseApiError(statusCode: Int, responseBody: String): String {
        return try {
            val errorResponse = gson.fromJson(responseBody, GeminiApiErrorWrapper::class.java)
            val serverMessage = errorResponse?.error?.message

            when (statusCode) {
                400 -> {
                    if (serverMessage?.contains("API key", ignoreCase = true) == true) {
                        "Недействительный API-ключ Gemini (HTTP 400). Проверьте ключ в настройках."
                    } else {
                        "Ошибка запроса к API (HTTP 400): ${serverMessage ?: "Неверные параметры запроса"}"
                    }
                }
                403 -> "Доступ запрещен (HTTP 403). Убедитесь, что Gemini API включен для вашего ключа в Google Cloud Console."
                404 -> "Модель не найдена в API (HTTP 404). Выберите другую модель в настройках приложения."
                413 -> "Файл слишком велик для передачи (HTTP 413 Payload Too Large)."
                429 -> "Превышен лимит запросов (Rate Limit / Quota Exceeded, HTTP 429). Подождите несколько секунд или используйте модель Flash."
                500, 503 -> "Серверы Google Gemini временно перегружены (HTTP $statusCode). Повторите попытку через мгновение."
                else -> "Ошибка Gemini API (HTTP $statusCode): ${serverMessage ?: responseBody.take(150)}"
            }
        } catch (e: Exception) {
            "Ошибка сервера Gemini (HTTP $statusCode): $responseBody"
        }
    }
}
