package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.database.TranscriptEntity
import com.example.data.youtube.YouTubeTranscriptExtractor
import com.example.ui.MainViewModel
import com.example.ui.ProcessState
import com.example.utils.VoiceRecorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val processState by viewModel.processState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val currentViewingTranscript by viewModel.currentViewingTranscript.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()

    val recorder = remember { VoiceRecorder(context) }

    // Dialog & Input triggers
    var showYoutubeDialog by remember { mutableStateOf(false) }
    var youtubeUrlInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<TranscriptEntity?>(null) }

    // File selection picker intent
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.processLocalAudio(context, it)
        }
    }

    // RECORD_AUDIO raw permission launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording(recorder)
        } else {
            Toast.makeText(context, "Для записи звука требуется доступ к микрофону", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Audio Scribe",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                ),
                actions = {
                    // Settings Button
                    var showSettingsDialog by remember { mutableStateOf(false) }
                    val activeEngine by viewModel.activeEngine.collectAsStateWithLifecycle()
                    val openaiKey by viewModel.openaiKey.collectAsStateWithLifecycle()
                    val hfToken by viewModel.hfToken.collectAsStateWithLifecycle()

                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки движка",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Render Settings Dialog if triggered
                    if (showSettingsDialog) {
                        var expanded by remember { mutableStateOf(false) }
                        var editedOpenaiKey by remember { mutableStateOf(openaiKey) }
                        var editedHfToken by remember { mutableStateOf(hfToken) }
                        
                        val isModelDownloaded by viewModel.isModelDownloaded.collectAsStateWithLifecycle()
                        val localModelSize by viewModel.localModelSize.collectAsStateWithLifecycle()
                        val isDownloading by viewModel.isDownloadingModel.collectAsStateWithLifecycle()
                        val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

                        AlertDialog(
                            onDismissRequest = { showSettingsDialog = false },
                            title = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Настройки распознавания") 
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Выберите движок для распознавания файлов и микрофона:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { expanded = true },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = when (activeEngine) {
                                                        "HF" -> "🤗 Hugging Face (Бесплатно)"
                                                        "OPENAI" -> "🔑 OpenAI Whisper (Платно)"
                                                        "LOCAL_WHISPER" -> "📱 Локальный Whisper на телефоне"
                                                        else -> "✨ Gemini 1.5 Flash (Бесплатно, топ)"
                                                    },
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                            }
                                        }
                                        DropdownMenu(
                                            expanded = expanded,
                                            onDismissRequest = { expanded = false },
                                            modifier = Modifier.fillMaxWidth(0.8f)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("✨ Gemini 1.5 Flash (Бесплатно, топ)") },
                                                onClick = {
                                                    viewModel.setEngine("GEMINI")
                                                    expanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("🤗 Hugging Face Whisper-L3 (Бесплатно)") },
                                                onClick = {
                                                    viewModel.setEngine("HF")
                                                    expanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("🔑 OpenAI Whisper API (Платно)") },
                                                onClick = {
                                                    viewModel.setEngine("OPENAI")
                                                    expanded = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("📱 Локальный Whisper на телефоне") },
                                                onClick = {
                                                    viewModel.setEngine("LOCAL_WHISPER")
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
 
                                    Spacer(modifier = Modifier.height(4.dp))
 
                                    when (activeEngine) {
                                        "GEMINI" -> {
                                            Text(
                                                text = "Использует официальный ключ Gemini API Key из AI Studio Secrets panel. 100% бесплатно (до 1500 запросов в день), работает мгновенно в облаке Google с высочайшей точностью.",
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        "HF" -> {
                                            Text(
                                                text = "Использует открытую модель Whisper-Large-V3 на серверах Hugging Face. Ключ не обязателен, но при наличии лимитов вы можете указать свой HF Token:",
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            OutlinedTextField(
                                                value = editedHfToken,
                                                onValueChange = { editedHfToken = it },
                                                label = { Text("Hugging Face API Token") },
                                                placeholder = { Text("hf_...") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        "OPENAI" -> {
                                            Text(
                                                text = "Использует официальный Whisper API за ваш счет ($0.006/мин). Требуется ваш личный OpenAI API Key:",
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            OutlinedTextField(
                                                value = editedOpenaiKey,
                                                onValueChange = { editedOpenaiKey = it },
                                                label = { Text("OpenAI API Key") },
                                                placeholder = { Text("sk-...") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        "LOCAL_WHISPER" -> {
                                            Text(
                                                text = "Использует модель Whisper на вашем Android-устройстве без доступа к Интернету. Модель сохраняется на флеш-памяти телефона для оффлайн работы.",
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = "Размер Whisper модели:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val sizes = listOf(
                                                    "tiny" to "Tiny (~75M)",
                                                    "base" to "Base (~145M)",
                                                    "small" to "Small (~460M)"
                                                )
                                                sizes.forEach { (sizeId, label) ->
                                                    val isSelected = localModelSize == sizeId
                                                    Button(
                                                        onClick = { 
                                                            if (!isDownloading) {
                                                                viewModel.setLocalModelSize(sizeId) 
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                                        enabled = !isDownloading
                                                    ) {
                                                        Text(label, fontSize = 10.sp, maxLines = 1)
                                                    }
                                                }
                                            }

                                            val sizeLabel = when (localModelSize) {
                                                "tiny" -> "Tiny (Быстрая, низкая точность)"
                                                "base" -> "Base (Сбалансированная)"
                                                else -> "Small (Высокая точность, рекомендуется для русского языка)"
                                            }

                                            Text(
                                                text = "Выбрано: $sizeLabel",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            if (isModelDownloaded) {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.CheckCircle,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Text(
                                                                text = "Модель Whisper-${localModelSize.uppercase()} успешно загружена на телефон! Можно пользоваться оффлайн.",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                                            )
                                                        }
                                                        
                                                        Button(
                                                            onClick = { viewModel.deleteLocalModel() },
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                            ),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Удалить модель с диска Android", fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            } else {
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                                    )
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Warning,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                            Text(
                                                                text = "Модель Whisper-${localModelSize.uppercase()} не установлена! Локальное распознавание временно заблокировано.",
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                        
                                                        if (isDownloading) {
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                                            ) {
                                                                LinearProgressIndicator(
                                                                    progress = { downloadProgress },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    trackColor = MaterialTheme.colorScheme.primaryContainer
                                                                )
                                                                Text(
                                                                    text = "Скачивание весов модели... ${(downloadProgress * 100).toInt()}%",
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        } else {
                                                            Button(
                                                                onClick = {
                                                                    viewModel.downloadModel()
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                                                 Spacer(modifier = Modifier.width(6.dp))
                                                                Text("Скачать модель Whisper-${localModelSize.uppercase()}", fontSize = 11.sp)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    if (activeEngine == "OPENAI") {
                                        viewModel.setOpenaiKey(editedOpenaiKey)
                                    } else if (activeEngine == "HF") {
                                        viewModel.setHfToken(editedHfToken)
                                    }
                                    showSettingsDialog = false
                                }) {
                                    Text("Применить")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showSettingsDialog = false }) {
                                    Text("Отмена")
                                }
                            }
                        )
                    }

                    // Quick stats/API warning indicator if key is placeholder
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (activeEngine == "GEMINI" && (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY")) {
                        IconButton(onClick = {
                            android.widget.Toast.makeText(context, "Внимание: Введите GEMINI_API_KEY в панели Secrets", android.widget.Toast.LENGTH_LONG).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "No API Key Warning",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main Content Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Section 1: Welcome Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Конвертируйте речь в текст с легкостью!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Выберите источник звука ниже: загрузите локальный файл, укажите ссылку на Youtube или запишите звук в реальном времени.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                WhisperModelStatusIndicator(viewModel = viewModel)

                // Section 2: Input Options Cards
                Text(
                    text = "Новая расшифровка",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option 1: YouTube Link Click Card
                    InteractiveSourceCard(
                        title = "Ссылка YouTube",
                        description = "Субтитры и таймкоды",
                        icon = Icons.Default.PlayCircle,
                        tintColor = Color(0xFFFF4D4D),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("youtube_card_btn"),
                        onClick = {
                            youtubeUrlInput = ""
                            showYoutubeDialog = true
                        }
                    )

                    // Option 2: Local Upload Card
                    InteractiveSourceCard(
                        title = "Аудиофайл",
                        description = "Загрузить mp3, wav...",
                        icon = Icons.Default.AudioFile,
                        tintColor = Color(0xFF4DA6FF),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("upload_file_btn"),
                        onClick = {
                            filePickerLauncher.launch("audio/*")
                        }
                    )
                }

                // Option 3: Live Recorder Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isRecording) Color.Red else MaterialTheme.colorScheme.outlineVariant
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRecording) Color(0x11FF0000) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isRecording) "Идет запись аудио..." else "Диктофон",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isRecording) "Нажмите на круг снизу, чтобы остановить" else "Запись лекции или мысли в реальном времени",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            modifier = Modifier
                                .size(54.dp)
                                .background(
                                    color = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                                .testTag("record_mic_btn"),
                            onClick = {
                                if (isRecording) {
                                    viewModel.stopAndProcessRecording(recorder)
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Mic Trigger",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Section 3: History List
                Text(
                    text = "История расшифровок",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Empty History",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = "Список пуст. Добавьте файлы или ссылки для автоматической обработки.",
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        history.forEach { item ->
                            HistoryItemCard(
                                item = item,
                                onClick = {
                                    viewModel.viewTranscript(item)
                                },
                                onDelete = {
                                    showDeleteConfirmDialog = item
                                }
                            )
                        }
                    }
                }
                
                // Extra margin at the bottom
                Spacer(modifier = Modifier.height(60.dp))
            }

            // Foreground Overlays based on state changes
            
            // 1. Process loading states with retro terminal log details
            if (processState is ProcessState.Loading) {
                val state = processState as ProcessState.Loading
                val loadingMsg = state.message
                val logs = state.logs
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(enabled = false) {}, // Scrim protection
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .fillMaxHeight(0.85f)
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                        border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // Terminal Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Three window control dots (retro style)
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF5F56), CircleShape))
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFFBD2E), CircleShape))
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF27C93F), CircleShape))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "console_output.log",
                                        color = Color(0xFFE0E0E0),
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF00FF66),
                                    strokeWidth = 2.dp
                                )
                            }
                            
                            Divider(color = Color(0xFF00FF66).copy(alpha = 0.2f), thickness = 1.dp)
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Headline status
                            Text(
                                text = "СТАТУС: $loadingMsg",
                                color = Color(0xFF00FF66),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            // Console Log list (Auto scroll)
                            val listState = rememberLazyListState()
                            
                            // Always scroll to end when log size changes
                            LaunchedEffect(logs.size) {
                                if (logs.isNotEmpty()) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color(0xFF070707))
                                    .border(BorderStroke(0.5.dp, Color(0xFF333333)))
                                    .padding(8.dp)
                            ) {
                                if (logs.isEmpty()) {
                                    Text(
                                        text = "Ожидание запуска вычислительных потоков...\n_ ",
                                        color = Color.LightGray.copy(alpha = 0.6f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                } else {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(logs) { logLine ->
                                            Text(
                                                text = if (logLine.startsWith("[ОШИБКА]")) "❌ $logLine" else if (logLine.startsWith("[УСПЕХ]") || logLine.contains("Успешно")) "✅ $logLine" else "  $logLine",
                                                color = if (logLine.startsWith("[ОШИБКА]")) Color(0xFFFF5252) else if (logLine.startsWith("[УСПЕХ]")) Color(0xFF00FF66) else Color(0xFFECEFF1),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                        item {
                                            // blinking cursor at the very bottom
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "  sh-4.4$ ",
                                                    color = Color(0xFF00FF66).copy(alpha = 0.7f),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                BlinkingCursor()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Choice of caption tracks language dialog for YouTube
            if (processState is ProcessState.ChooseYouTubeLanguage) {
                val videoInfo = (processState as ProcessState.ChooseYouTubeLanguage).videoInfo
                AlertDialog(
                    onDismissRequest = { viewModel.setIdle() },
                    title = { Text("Выберите язык субтитров") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            videoInfo.captionTracks.forEach { track ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.fetchAndProcessYouTubeTranscript(videoInfo, track)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = "Lang",
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = track.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (track.isAutoGenerated) {
                                                Text(
                                                    text = "Автоматические субтитры",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { viewModel.setIdle() }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            // 3. Error state Dialog info box
            if (processState is ProcessState.Error) {
                val errorState = processState as ProcessState.Error
                val errMsg = errorState.message
                val logs = errorState.logs
                AlertDialog(
                    onDismissRequest = { viewModel.setIdle() },
                    icon = { Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red) },
                    title = { Text("Ошибка") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(errMsg, style = MaterialTheme.typography.bodyMedium)
                            
                            if (logs.isNotEmpty()) {
                                Text(
                                    text = "Пошаговый отладочный лог:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 160.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    color = Color(0xFF0F0F0F),
                                    border = BorderStroke(1.dp, Color(0xFFFF5F56).copy(alpha = 0.3f))
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(logs) { logLine ->
                                            Text(
                                                text = if (logLine.startsWith("[ОШИБКА]")) "❌ $logLine" else "  $logLine",
                                                color = if (logLine.startsWith("[ОШИБКА]")) Color(0xFFFF5252) else if (logLine.contains("Успешно") || logLine.startsWith("[УСПЕХ]")) Color(0xFF00FF66) else Color(0xFFECEFF1),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.setIdle() }) {
                            Text("Закрыть")
                        }
                    }
                )
            }

            // 4. Detail Viewer sliding sheet/overlay
            AnimatedVisibility(
                visible = currentViewingTranscript != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                currentViewingTranscript?.let { transcript ->
                    TranscriptDetailView(
                        transcript = transcript,
                        onBack = { viewModel.clearViewingTranscript() },
                        onDelete = {
                            showDeleteConfirmDialog = transcript
                        }
                    )
                }
            }
        }
    }

    // Modal dialogs templates

    // 1. Paste Youtube Link Dialog inputs
    if (showYoutubeDialog) {
        AlertDialog(
            onDismissRequest = { showYoutubeDialog = false },
            title = { Text("Укажите ссылку YouTube") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Вставьте ссылку на полное видео или Shorts:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = youtubeUrlInput,
                        onValueChange = { youtubeUrlInput = it },
                        placeholder = { Text("https://www.youtube.com/watch?v=...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("youtube_url_field"),
                        singleLine = true,
                        trailingIcon = {
                            if (youtubeUrlInput.isNotEmpty()) {
                                IconButton(onClick = { youtubeUrlInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (youtubeUrlInput.trim().isNotEmpty()) {
                            showYoutubeDialog = false
                            viewModel.processYouTubeUrl(youtubeUrlInput.trim())
                        } else {
                            Toast.makeText(context, "Введите ссылку!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("youtube_confirm_btn")
                ) {
                    Text("Обработать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showYoutubeDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // 2. Clear Database Entry Confirmation Dialog
    if (showDeleteConfirmDialog != null) {
        val entry = showDeleteConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = "Delete", tint = Color.Red) },
            title = { Text("Удалить расшифровку?") },
            text = {
                Text("Вы действительно хотите удалить '${entry.title}'? Это действие нельзя будет отменить.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTranscript(entry.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun InteractiveSourceCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tintColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tintColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tintColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    item: TranscriptEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateStr = remember(item.timestamp) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }

    val icon = when (item.sourceType) {
        "YOUTUBE" -> Icons.Default.PlayCircle
        "LOCAL_AUDIO" -> Icons.Default.AudioFile
        else -> Icons.Default.Mic
    }

    val color = when (item.sourceType) {
        "YOUTUBE" -> Color(0xFFFF4D4D)
        "LOCAL_AUDIO" -> Color(0xFF4DA6FF)
        else -> Color(0xFF33CC33)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = item.sourceType,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = dateStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = when(item.sourceType) {
                            "YOUTUBE" -> "YouTube"
                            "LOCAL_AUDIO" -> "Файл"
                            else -> "Запись"
                        },
                        fontSize = 11.sp,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            IconButton(
                onClick = { onDelete() },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove Entry",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Detailed Pane Viewer with clean Material tabs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptDetailView(
    transcript: TranscriptEntity,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val contentResolver = context.contentResolver

    var activeTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Субтитры (SRT)", "Таймкоды", "Чистый текст")

    val activeTextContent = when (activeTab) {
        0 -> transcript.srtText
        1 -> transcript.chaptersText
        else -> transcript.plainText
    }

    // SAF Document Savers launchers
    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        uri?.let {
            writeStringUri(contentResolver, it, transcript.srtText, context, "Субтитры успешно сохранены!")
        }
    }

    val chaptersPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            writeStringUri(contentResolver, it, transcript.chaptersText, context, "Таймкоды успешно сохранены!")
        }
    }

    val plainPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            writeStringUri(contentResolver, it, transcript.plainText, context, "Текст успешно сохранен!")
        }
    }

    val dateStr = remember(transcript.timestamp) {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(transcript.timestamp))
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false) {}, // Scrim input absorber
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = transcript.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = dateStr,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Displays Thumbnail if from Youtube
            if (transcript.sourceType == "YOUTUBE") {
                val videoId = remember(transcript.sourceUrl) {
                    YouTubeTranscriptExtractor.extractVideoId(transcript.sourceUrl) ?: ""
                }
                if (videoId.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(Color.Black)
                    ) {
                        AsyncImage(
                            model = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                            contentDescription = "Video Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(54.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(transcript.sourceUrl))
                                    context.startActivity(intent)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Video",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            // Material 3 Tabs Selector
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                tabTitles.forEachIndexed { i, title ->
                    Tab(
                        selected = activeTab == i,
                        onClick = { activeTab = i },
                        text = { Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                    )
                }
            }

            // Scrollable transcript viewer field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = activeTextContent.ifEmpty { "Пустой результат." },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontFamily = if (activeTab == 0) FontFamily.Monospace else FontFamily.SansSerif,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Bottom Action buttons panel (Copy, Share, Save)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Action 1: Save SAF file download!
                    Button(
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("export_document_btn"),
                        onClick = {
                            val cleanTitle = transcript.title.filter { it.isLetterOrDigit() || it in " ._-" }.trim()
                            when (activeTab) {
                                0 -> srtPickerLauncher.launch("${cleanTitle}.srt")
                                1 -> chaptersPickerLauncher.launch("${cleanTitle}_таймкоды.txt")
                                else -> plainPickerLauncher.launch("${cleanTitle}_текст.txt")
                            }
                        }
                    ) {
                        Icon(Icons.Default.SaveAlt, contentDescription = "Save", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Скачать", fontSize = 13.sp)
                    }

                    // Action 2: Copy Clipboard
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("copy_text_btn"),
                        onClick = {
                            val clip = ClipData.newPlainText("Formatted text", activeTextContent)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Скопировано в буфер!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Копировать", fontSize = 12.sp)
                    }

                    // Action 3: Share
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_text_btn"),
                        onClick = {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, activeTextContent)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(intent, "Поделиться текстом"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Поделиться", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun writeStringUri(
    contentResolver: android.content.ContentResolver,
    uri: Uri,
    data: String,
    context: Context,
    toastMessage: String
) {
    try {
        contentResolver.openOutputStream(uri)?.use { out ->
            out.write(data.toByteArray())
        }
        Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Не удалось записать файл: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.0f at 0
                1.0f at 499
                1.0f at 500
                0.0f at 1000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "cursor_alpha"
    )
    Box(
        modifier = Modifier
            .width(8.dp)
            .height(13.dp)
            .background(Color(0xFF00FF66).copy(alpha = alpha))
    )
}

@Composable
fun WhisperModelStatusIndicator(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val localModelSize by viewModel.localModelSize.collectAsStateWithLifecycle()
    val isModelDownloaded by viewModel.isModelDownloaded.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloadingModel.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val activeEngine by viewModel.activeEngine.collectAsStateWithLifecycle()

    val isWhisperActive = activeEngine == "LOCAL_WHISPER"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("whisper_status_indicator"),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.5.dp,
            color = when {
                isDownloading -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                isModelDownloaded && isWhisperActive -> Color(0xFF2E7D32).copy(alpha = 0.5f)
                isModelDownloaded -> MaterialTheme.colorScheme.outlineVariant
                else -> Color(0xFFC62828).copy(alpha = 0.5f)
            }
        ),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDownloading -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                isModelDownloaded && isWhisperActive -> Color(0xFFE8F5E9).copy(alpha = 0.8f)
                isModelDownloaded -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f)
                else -> Color(0xFFFFEBEE).copy(alpha = 0.6f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isModelDownloaded) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = "Status Icon",
                            tint = if (isModelDownloaded) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Локальный Whisper: ${localModelSize.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val statusLabel = when {
                            isDownloading -> "Скачивается в кэш..."
                            isModelDownloaded -> "Готов к локальному распознаванию"
                            else -> "Требуется скачивание"
                        }
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // If Whisper not selected as current engine, show context label/switch option
                if (!isWhisperActive && !isDownloading && isModelDownloaded) {
                    TextButton(
                        onClick = { viewModel.setEngine("LOCAL_WHISPER") },
                        modifier = Modifier.testTag("activate_whisper_btn"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("Включить движок", fontSize = 11.sp)
                    }
                } else if (isWhisperActive) {
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "АКТИВЕН",
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            color = Color(0xFF1B5E20),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Content details / Action buttons / Selector row
            if (isDownloading) {
                Column(
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Text(
                        text = "Прогресс загрузки: ${(downloadProgress * 100).toInt()}% (размер ~${getModelRoughSize(localModelSize)})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = when {
                        isModelDownloaded -> {
                            val detailText = when (localModelSize) {
                                "tiny" -> "Размер ~75М. Низкие ресурсы, высокая скорость, оптимальна для простых аудио."
                                "base" -> "Размер ~145М. Сбалансированный выбор."
                                else -> "Размер ~460М. Наивысшая точность, настоятельно рекомендуется для русского языка."
                            }
                            "Модель кэширована во внутреннюю память телефона. Оффлайн-распознавание полностью готово к запуску.\n$detailText"
                        }
                        else -> {
                            val sizeSuggest = when (localModelSize) {
                                "tiny" -> "~75 МБ. Быстрая загрузка."
                                "base" -> "~145 МБ. Средняя точность."
                                else -> "~460 МБ. Рекомендуется для русского языка."
                            }
                            "Для использования локального Whisper нужно загрузить файлы весов ($sizeSuggest). После скачивания Интернет будет не нужен."
                        }
                    },
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Quick model selector row & instant Action button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick choose size buttons if not downloading
                    Row(
                        modifier = Modifier.weight(1.3f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val sizes = listOf("tiny" to "Tiny", "base" to "Base", "small" to "Small")
                        sizes.forEach { (sz, label) ->
                            val isChosen = localModelSize == sz
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setLocalModelSize(sz) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isChosen) {
                                    if (isModelDownloaded && isWhisperActive) Color(0xFF2E7D32).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = if (isChosen) {
                                    if (isModelDownloaded && isWhisperActive) Color(0xFF1B5E20) else MaterialTheme.colorScheme.primary
                                } else MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    
                    if (!isModelDownloaded) {
                        Button(
                            onClick = { viewModel.downloadModel() },
                            modifier = Modifier.weight(1f).testTag("download_model_main_btn"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Скачать", fontSize = 11.sp)
                        }
                    } else {
                        // Option to delete model size if ready
                        IconButton(
                            onClick = { viewModel.deleteLocalModel() },
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить модель",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getModelRoughSize(size: String): String {
    return when (size) {
        "tiny" -> "75 МБ"
        "base" -> "145 МБ"
        else -> "460 МБ"
    }
}
