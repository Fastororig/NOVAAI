package com.nova.ai.data.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.nova.ai.data.model.ChatMessage
import com.nova.ai.data.model.Conversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ChatRepository(private val context: Context) {

    private val gson = Gson()
    private val storageFile: File
        get() = File(context.filesDir, "nova_conversations.json")

    suspend fun loadConversations(): MutableList<Conversation> = withContext(Dispatchers.IO) {
        try {
            if (!storageFile.exists()) {
                val defaultList = mutableListOf(Conversation(title = "Диалог 1"))
                saveConversations(defaultList)
                return@withContext defaultList
            }
            val json = storageFile.readText()
            val type = object : TypeToken<MutableList<Conversation>>() {}.type
            val list: MutableList<Conversation>? = gson.fromJson(json, type)
            if (list.isNullOrEmpty()) {
                mutableListOf(Conversation(title = "Диалог 1"))
            } else {
                list
            }
        } catch (e: Exception) {
            mutableListOf(Conversation(title = "Диалог 1"))
        }
    }

    suspend fun saveConversations(conversations: List<Conversation>) = withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(conversations)
            storageFile.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            if (storageFile.exists()) {
                storageFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
