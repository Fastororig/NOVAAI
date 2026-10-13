package com.nova.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.nova.ai.ui.screens.ChatScreen
import com.nova.ai.ui.screens.ConversationsDrawerSheet
import com.nova.ai.ui.screens.SettingsScreen
import com.nova.ai.ui.theme.NovaAITheme
import com.nova.ai.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

enum class CurrentScreen {
    CHAT,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NovaAITheme {
                var currentScreen by remember { mutableStateOf(CurrentScreen.CHAT) }
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val coroutineScope = rememberCoroutineScope()

                // Handle back press inside settings or open drawer
                BackHandler(enabled = currentScreen == CurrentScreen.SETTINGS || drawerState.isOpen) {
                    if (drawerState.isOpen) {
                        coroutineScope.launch { drawerState.close() }
                    } else if (currentScreen == CurrentScreen.SETTINGS) {
                        currentScreen = CurrentScreen.CHAT
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        CurrentScreen.CHAT -> {
                            ModalNavigationDrawer(
                                drawerState = drawerState,
                                drawerContent = {
                                    ConversationsDrawerSheet(
                                        conversations = chatViewModel.conversations,
                                        currentConversationId = chatViewModel.currentConversationId,
                                        onSelectConversation = { id ->
                                            chatViewModel.selectConversation(id)
                                            coroutineScope.launch { drawerState.close() }
                                        },
                                        onNewChat = {
                                            chatViewModel.createNewConversation()
                                            coroutineScope.launch { drawerState.close() }
                                        },
                                        onDeleteConversation = { id ->
                                            chatViewModel.deleteConversation(id)
                                        },
                                        onOpenSettings = {
                                            coroutineScope.launch { drawerState.close() }
                                            currentScreen = CurrentScreen.SETTINGS
                                        }
                                    )
                                }
                            ) {
                                ChatScreen(
                                    viewModel = chatViewModel,
                                    onOpenDrawer = {
                                        coroutineScope.launch { drawerState.open() }
                                    },
                                    onOpenSettings = {
                                        currentScreen = CurrentScreen.SETTINGS
                                    }
                                )
                            }
                        }
                        CurrentScreen.SETTINGS -> {
                            SettingsScreen(
                                viewModel = chatViewModel,
                                onBack = { currentScreen = CurrentScreen.CHAT }
                            )
                        }
                    }
                }
            }
        }
    }
}
