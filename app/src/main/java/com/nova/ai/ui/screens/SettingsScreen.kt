package com.nova.ai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.ai.data.model.ModelFamily
import com.nova.ai.data.model.UiGeminiModel
import com.nova.ai.ui.theme.*
import com.nova.ai.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var apiKeyText by remember { mutableStateOf(viewModel.apiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(viewModel.selectedModel) }
    var systemInstructionText by remember { mutableStateOf(viewModel.systemInstruction) }
    var temperatureValue by remember { mutableFloatStateOf(viewModel.temperature) }
    var isWatchMode by remember { mutableStateOf(viewModel.isWatchMode) }
    var isStreamingEnabled by remember { mutableStateOf(viewModel.isStreamingEnabled) }
    var isDynamicModelsEnabled by remember { mutableStateOf(viewModel.isDynamicModelsEnabled) }
    var proxyUrlText by remember { mutableStateOf(viewModel.proxyUrl) }

    val scrollState = rememberScrollState()

    LaunchedEffect(viewModel.selectedModel) {
        selectedModel = viewModel.selectedModel
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки Nova AI", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.saveSettings(
                                apiKey = apiKeyText.trim(),
                                model = selectedModel,
                                systemInstruction = systemInstructionText.trim(),
                                temperature = temperatureValue,
                                isWatchMode = isWatchMode,
                                isStreamingEnabled = isStreamingEnabled,
                                isDynamicModelsEnabled = isDynamicModelsEnabled,
                                proxyUrl = proxyUrlText.trim()
                            )
                            Toast.makeText(context, "Настройки сохранены", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Сохранить",
                            tint = NovaPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Smartwatch Mode (M99 1:1) Switch Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isWatchMode) Color(0xFF132A4A) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (isWatchMode) androidx.compose.foundation.BorderStroke(1.5.dp, NovaPrimary) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (isWatchMode) NovaPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Watch,
                                    contentDescription = "Режим смарт-часов",
                                    tint = if (isWatchMode) NovaPrimary else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Режим смарт-часов",
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (isWatchMode) {
                                    Surface(
                                        color = NovaPrimary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "M99 1:1",
                                            color = NovaPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Ультра-компактная панель, оптимизация для квадратных экранов 1:1 и крупные кнопки для пальцев.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Switch(
                        checked = isWatchMode,
                        onCheckedChange = { checked ->
                            isWatchMode = checked
                            viewModel.setWatchMode(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NovaPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }

            // Additional Features (Streaming & Dynamic Models)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Streaming Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Потоковая генерация ответов",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Постепенный вывод текста по мере формирования ответа моделью",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isStreamingEnabled,
                            onCheckedChange = { checked ->
                                isStreamingEnabled = checked
                                viewModel.setStreamingEnabled(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = NovaPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }

                    HorizontalDivider(color = DarkBorder)

                    // Dynamic Models Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Динамическая подгрузка моделей",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Автоматическое получение доступных моделей через Gemini API",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isDynamicModelsEnabled,
                            onCheckedChange = { checked ->
                                isDynamicModelsEnabled = checked
                                viewModel.setDynamicModelsEnabled(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = NovaPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DarkSurfaceVariant
                            )
                        )
                    }
                }
            }

            // Proxy / Base URL Configuration Card (Bypass regional blocking without VPN)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (proxyUrlText.isNotBlank()) Color(0xFF132A4A) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(12.dp),
                border = if (proxyUrlText.isNotBlank()) androidx.compose.foundation.BorderStroke(1.5.dp, NovaPrimary) else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = if (proxyUrlText.isNotBlank()) NovaPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = "Прокси / Base URL",
                                    tint = if (proxyUrlText.isNotBlank()) NovaPrimary else TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Прокси / Base URL (работа без VPN)",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Обход региональных ограничений Gemini API",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text(
                        text = "Укажите адрес обратного прокси или шлюза (Cloudflare Worker, VPS, Nginx и др.). Если поле пустое — запросы отправляются напрямую на серверы Google.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = proxyUrlText,
                        onValueChange = { proxyUrlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                "Например: https://proxy.example.com",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        },
                        trailingIcon = {
                            if (proxyUrlText.isNotBlank()) {
                                IconButton(onClick = { proxyUrlText = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Очистить прокси",
                                        tint = TextMuted
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NovaPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    // Active connection indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val isProxyActive = proxyUrlText.isNotBlank()
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (isProxyActive) NovaPrimary else Color(0xFF10B981))
                        )
                        Text(
                            text = if (isProxyActive) {
                                "Маршрут: ${com.nova.ai.data.api.GeminiApiService.normalizeBaseUrl(proxyUrlText)}"
                            } else {
                                "Прямое соединение: generativelanguage.googleapis.com (по умолчанию)"
                            },
                            color = if (isProxyActive) NovaPrimary else TextMuted,
                            fontSize = 11.sp,
                            fontFamily = if (isProxyActive) androidx.compose.ui.text.font.FontFamily.Monospace else null
                        )
                    }
                }
            }

            // Security Warning Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Безопасность",
                        tint = NovaPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (proxyUrlText.isNotBlank()) {
                            "Ваш API-ключ хранится строго локально на устройстве и передаётся через указанный вами Base URL (${com.nova.ai.data.api.GeminiApiService.normalizeBaseUrl(proxyUrlText)})."
                        } else {
                            "Ваш API-ключ хранится строго локально на вашем смартфоне и отправляется исключительно напрямую на HTTPS REST сервер Google (generativelanguage.googleapis.com). Никаких сторонних серверов."
                        },
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            // Gemini API Key Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Google Gemini API-ключ",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Вставьте ключ AIzaSy...", color = TextMuted) },
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать ключ",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NovaPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
                Text(
                    text = "Получить бесплатный ключ можно в Google AI Studio (aistudio.google.com)",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // Models Management Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Выбор модели Gemini",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Модели фильтруются по флагу generateContent из GET /v1beta/models",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Refresh models button
                        FilledTonalButton(
                            onClick = {
                                if (apiKeyText.isNotBlank()) {
                                    viewModel.saveSettings(
                                        apiKey = apiKeyText.trim(),
                                        model = selectedModel,
                                        systemInstruction = systemInstructionText.trim(),
                                        temperature = temperatureValue,
                                        isWatchMode = isWatchMode,
                                        isStreamingEnabled = isStreamingEnabled,
                                        isDynamicModelsEnabled = isDynamicModelsEnabled
                                    )
                                }
                                viewModel.refreshModels()
                            },
                            enabled = !viewModel.isLoadingModels,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            if (viewModel.isLoadingModels) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NovaPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Загрузка...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Обновить",
                                    modifier = Modifier.size(16.dp),
                                    tint = NovaPrimary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Обновить", fontSize = 12.sp)
                            }
                        }

                        // Reset and Refresh models button
                        OutlinedButton(
                            onClick = {
                                if (apiKeyText.isNotBlank()) {
                                    viewModel.saveSettings(
                                        apiKey = apiKeyText.trim(),
                                        model = selectedModel,
                                        systemInstruction = systemInstructionText.trim(),
                                        temperature = temperatureValue,
                                        isWatchMode = isWatchMode,
                                        isStreamingEnabled = isStreamingEnabled,
                                        isDynamicModelsEnabled = isDynamicModelsEnabled
                                    )
                                }
                                viewModel.resetAndRefreshModels()
                                Toast.makeText(context, "Кэш моделей очищен, загружается свежий список", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !viewModel.isLoadingModels,
                            modifier = Modifier
                                .weight(1.3f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = NovaPrimary
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NovaPrimary.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = "Сбросить и обновить",
                                modifier = Modifier.size(16.dp),
                                tint = NovaPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сбросить и обновить", fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                // Error or cache notice
                if (viewModel.modelsErrorMessage != null) {
                    Surface(
                        color = Color(0xFF38151D),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF8599),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = viewModel.modelsErrorMessage ?: "",
                                color = Color(0xFFFF8599),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                } else if (viewModel.isModelsFromCache && viewModel.modelsLastUpdated > 0) {
                    val dateFormatted = remember(viewModel.modelsLastUpdated) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        sdf.format(Date(viewModel.modelsLastUpdated))
                    }
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = NovaPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Список из локального кэша ($dateFormatted). Нажмите «Обновить список» для проверки новых версий.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Render Models Grouped by Family
                val models = viewModel.modelsList
                val flashGroup = models.filter { it.family == ModelFamily.FLASH }
                val proGroup = models.filter { it.family == ModelFamily.PRO }
                val otherGroup = models.filter { it.family == ModelFamily.OTHER }

                if (flashGroup.isNotEmpty()) {
                    ModelFamilySection(
                        title = "Семейство Flash (Быстрые и эффективные)",
                        models = flashGroup,
                        selectedModel = selectedModel,
                        onSelect = { selectedModel = it }
                    )
                }

                if (proGroup.isNotEmpty()) {
                    ModelFamilySection(
                        title = "Семейство Pro (Сложная логика и анализ)",
                        models = proGroup,
                        selectedModel = selectedModel,
                        onSelect = { selectedModel = it }
                    )
                }

                if (otherGroup.isNotEmpty()) {
                    ModelFamilySection(
                        title = "Другие модели",
                        models = otherGroup,
                        selectedModel = selectedModel,
                        onSelect = { selectedModel = it }
                    )
                }
            }

            // Temperature Slider
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Температура генерации",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = String.format("%.1f", temperatureValue),
                        color = NovaPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = temperatureValue,
                    onValueChange = { temperatureValue = it },
                    valueRange = 0.0f..2.0f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = NovaPrimary,
                        activeTrackColor = NovaPrimary,
                        inactiveTrackColor = DarkBorder
                    )
                )
                Text(
                    text = "0.0 — точные факты и код; 1.0 — баланс; 2.0 — максимальная вариативность.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            // System Instruction (Prompt)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Системная инструкция (System Instruction)",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = systemInstructionText,
                    onValueChange = { systemInstructionText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    placeholder = {
                        Text(
                            "Например: Отвечай кратко на русском языке, форматируй код в markdown-блоки...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NovaPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    viewModel.saveSettings(
                        apiKey = apiKeyText.trim(),
                        model = selectedModel,
                        systemInstruction = systemInstructionText.trim(),
                        temperature = temperatureValue,
                        isWatchMode = isWatchMode,
                        isStreamingEnabled = isStreamingEnabled,
                        isDynamicModelsEnabled = isDynamicModelsEnabled,
                        proxyUrl = proxyUrlText.trim()
                    )
                    Toast.makeText(context, "Настройки успешно сохранены!", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NovaPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Сохранить настройки",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ModelFamilySection(
    title: String,
    models: List<UiGeminiModel>,
    selectedModel: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = NovaPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        models.forEach { model ->
            val isSelected = selectedModel == model.id
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelect(model.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF132A4A) else DarkSurface
                ),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, NovaPrimary) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = model.displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (model.isRecommended && model.id == UiGeminiModel.RECOMMENDED_MODEL_ID) {
                                Surface(
                                    color = NovaPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Рекомендуется",
                                        color = NovaPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Exact Model ID from API
                        Text(
                            text = model.id,
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = model.description,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = { onSelect(model.id) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = NovaPrimary,
                            unselectedColor = TextMuted
                        )
                    )
                }
            }
        }
    }
}
