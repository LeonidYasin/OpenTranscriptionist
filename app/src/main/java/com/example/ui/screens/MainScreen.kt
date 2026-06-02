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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
                    // Quick stats/API warning indicator if key is placeholder
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                        IconButton(onClick = {
                            Toast.makeText(context, "Внимание: Введите GEMINI_API_KEY в панели Secrets", Toast.LENGTH_LONG).show()
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
                                imageVector = if (isRecording) Icons.Default.Stop :+ Icons.Default.Mic,
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
            
            // 1. Process loading states
            if (processState is ProcessState.Loading) {
                val loadingMsg = (processState as ProcessState.Loading).message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(enabled = false) {}, // Scrim protection
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Text(
                                text = loadingMsg,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
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
                val errMsg = (processState as ProcessState.Error).message
                AlertDialog(
                    onDismissRequest = { viewModel.setIdle() },
                    icon = { Icon(Icons.Default.Error, contentDescription = "Error", tint = Color.Red) },
                    title = { Text("Ошибка") },
                    text = { Text(errMsg) },
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
        contract = ActivityResultContracts.CreateDocument("text/plain")
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
