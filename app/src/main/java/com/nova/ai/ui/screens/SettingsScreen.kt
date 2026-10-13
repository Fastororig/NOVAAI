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

    val scrollState = rememberScrollState()

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
                                temperature = temperatureValue
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
                        text = "Ваш API-ключ хранится строго локально на вашем смартфоне и отправляется исключительно напрямую на HTTPS REST сервер Google (generativelanguage.googleapis.com). Никаких сторонних прокси или серверов.",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Выбор модели Gemini",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Загружаются из GET /v1beta/models",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    // Refresh models button
                    FilledTonalButton(
                        onClick = {
                            if (apiKeyText.isNotBlank()) {
                                viewModel.saveSettings(
                                    apiKey = apiKeyText.trim(),
                                    model = selectedModel,
                                    systemInstruction = systemInstructionText.trim(),
                                    temperature = temperatureValue
                                )
                            }
                            viewModel.refreshModels()
                        },
                        enabled = !viewModel.isLoadingModels,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обновить список", fontSize = 12.sp)
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
                        temperature = temperatureValue
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
                            if (model.isRecommended) {
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
