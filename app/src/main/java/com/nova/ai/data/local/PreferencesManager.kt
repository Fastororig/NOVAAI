package com.nova.ai.data.local

import android.content.Context
import com.nova.ai.data.storage.ApiKeyPreferences

/**
 * Backward-compatible delegating alias to ApiKeyPreferences
 * for local preferences management including Watch Mode.
 */
class PreferencesManager(context: Context) {
    val delegate = ApiKeyPreferences(context)

    var apiKey: String
        get() = delegate.apiKey
        set(value) { delegate.apiKey = value }

    var selectedModel: String
        get() = delegate.selectedModel
        set(value) { delegate.selectedModel = value }

    var systemInstruction: String
        get() = delegate.systemInstruction
        set(value) { delegate.systemInstruction = value }

    var temperature: Float
        get() = delegate.temperature
        set(value) { delegate.temperature = value }

    var isWatchMode: Boolean
        get() = delegate.isWatchMode
        set(value) { delegate.isWatchMode = value }

    var isStreamingEnabled: Boolean
        get() = delegate.isStreamingEnabled
        set(value) { delegate.isStreamingEnabled = value }

    var isDynamicModelsEnabled: Boolean
        get() = delegate.isDynamicModelsEnabled
        set(value) { delegate.isDynamicModelsEnabled = value }

    fun clearCachedModels() = delegate.clearCachedModels()
    fun clearAll() = delegate.clearAll()
}
