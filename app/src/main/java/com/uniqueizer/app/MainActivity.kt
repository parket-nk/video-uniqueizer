package com.uniqueizer.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.uniqueizer.app.ui.theme.VideoUniquezerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VideoUniquezerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UniquezerScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniquezerScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val videoProcessor = remember { VideoProcessor(context) }

    // Состояние экрана
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedVideoName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Выберите видео для уникализации") }
    var isCompleted by remember { mutableStateOf(false) }

    // Лаунчер выбора видео
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedVideoUri = it
            selectedVideoName = videoProcessor.getFileName(it)
            statusText = "Выбрано: $selectedVideoName"
            isCompleted = false
            progress = 0f
        }
    }

    // Лаунчер запроса разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            videoPickerLauncher.launch("video/*")
        } else {
            Toast.makeText(context, "Необходимо разрешение для доступа к видео", Toast.LENGTH_LONG).show()
        }
    }

    // Функция проверки и запроса разрешений
    fun checkAndPickVideo() {
        val permissionsNeeded = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — READ_MEDIA_VIDEO
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
        } else {
            // Android 12 и ниже — READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // WRITE_EXTERNAL_STORAGE нужен только для API < 29
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsNeeded.isEmpty()) {
            videoPickerLauncher.launch("video/*")
        } else {
            permissionLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🎬 Уникализатор видео",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Описание
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Алгоритмы обработки:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("⚡ Ускорение 1.05x–1.1x (без искажения звука)")
                    Text("🔍 Динамический зум/кроп (+5–7%)")
                    Text("🎨 Микро-цветокоррекция")
                    Text("🧹 Полная очистка метаданных")
                    Text("🔒 Генерация нового хеша файла")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Статус
            Text(
                text = statusText,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                color = if (isCompleted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )

            // Прогресс-бар
            if (isProcessing) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Кнопка выбора видео
            Button(
                onClick = { checkAndPickVideo() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isProcessing
            ) {
                Text(
                    text = if (selectedVideoUri == null) "📂 Выбрать видео" else "📂 Выбрать другое видео",
                    fontSize = 16.sp
                )
            }

            // Кнопка обработки
            Button(
                onClick = {
                    selectedVideoUri?.let { uri ->
                        isProcessing = true
                        isCompleted = false
                        progress = 0f
                        statusText = "Подготовка..."

                        coroutineScope.launch {
                            videoProcessor.processVideo(
                                inputUri = uri,
                                onProgress = { p, status ->
                                    progress = p
                                    statusText = status
                                },
                                onComplete = { success, message ->
                                    isProcessing = false
                                    isCompleted = success
                                    statusText = message
                                    progress = if (success) 1f else 0f
                                }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedVideoUri != null && !isProcessing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Text(
                    text = "🚀 Уникализировать",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Информация о завершении
            if (isCompleted) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "✅ Готово! Файл сохранён в галерею.",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}
