package com.nova.ai.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nova.ai.data.model.UiGeminiModel

class ApiKeyPreferences(context: Context) {

    private val gson = Gson()

    private val prefs: SharedPreferences = try {
        // Use EncryptedSharedPreferences if available on device (API 23+)
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "nova_ai_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for older Android 7.0 devices where hardware Keystore crypto may fail
        context.getSharedPreferences("nova_ai_prefs", Context.MODE_PRIVATE)
    }

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var selectedModel: String
        get() = prefs.getString(KEY_MODEL, "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    var systemInstruction: String
        get() = prefs.getString(KEY_SYSTEM_INSTRUCTION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SYSTEM_INSTRUCTION, value).apply()

    var temperature: Float
        get() = prefs.getFloat(KEY_TEMPERATURE, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_TEMPERATURE, value).apply()

    var modelsCacheTimestamp: Long
        get() = prefs.getLong(KEY_MODELS_CACHE_TIME, 0L)
        private set(value) = prefs.edit().putLong(KEY_MODELS_CACHE_TIME, value).apply()

    fun saveCachedModels(models: List<UiGeminiModel>) {
        val json = gson.toJson(models)
        prefs.edit()
            .putString(KEY_MODELS_CACHE_JSON, json)
            .putLong(KEY_MODELS_CACHE_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getCachedModels(): List<UiGeminiModel>? {
        val json = prefs.getString(KEY_MODELS_CACHE_JSON, null) ?: return null
        return try {
            val type = object : TypeToken<List<UiGeminiModel>>() {}.type
            gson.fromJson<List<UiGeminiModel>>(json, type)
        } catch (e: Exception) {
            null
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_MODEL = "gemini_model"
        private const val KEY_SYSTEM_INSTRUCTION = "system_instruction"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_MODELS_CACHE_JSON = "models_cache_json"
        private const val KEY_MODELS_CACHE_TIME = "models_cache_time"
    }
}
