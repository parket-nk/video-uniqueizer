package com.uniqueizer.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random

/**
 * Класс-обработчик видео.
 * Реализует комплекс алгоритмов уникализации через FFmpeg Kit.
 */
class VideoProcessor(private val context: Context) {

    /**
     * Получает имя файла из URI.
     */
    fun getFileName(uri: Uri): String {
        var name = "video.mp4"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    /**
     * Получает длительность видео в миллисекундах через FFprobeKit.
     */
    private fun getVideoDurationMs(inputPath: String): Long {
        val session = com.arthenica.ffmpegkit.FFprobeKit.getMediaInformation(inputPath)
        val info = session.mediaInformation ?: return 0L
        val durationStr = info.duration ?: return 0L
        return (durationStr.toDoubleOrNull()?.times(1000))?.toLong() ?: 0L
    }

    /**
     * Основная функция обработки видео.
     *
     * @param inputUri URI выбранного видео
     * @param onProgress колбэк прогресса (0.0–1.0, текстовый статус)
     * @param onComplete колбэк завершения (успех, сообщение)
     */
    suspend fun processVideo(
        inputUri: Uri,
        onProgress: (Float, String) -> Unit,
        onComplete: (Boolean, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. Копируем исходное видео во временный файл кэша, чтобы FFmpeg имел полноценный seek-доступ
            withContext(Dispatchers.Main) {
                onProgress(0.05f, "Чтение файла из галереи...")
            }

            val tempDir = File(context.cacheDir, "uniqueizer")
            tempDir.mkdirs()
            val tempInput = File(tempDir, "input_${System.currentTimeMillis()}.mp4")
            val tempOutput = File(tempDir, "unique_${System.currentTimeMillis()}.mp4")

            // Копируем поток из ContentResolver в локальный файл
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                tempInput.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: throw IllegalStateException("Не удалось открыть исходное видео")

            // 2. Генерация случайных параметров уникализации
            val speed = 1.05 + Random.nextDouble() * 0.05        // 1.05x – 1.10x
            val cropPercent = 5 + Random.nextInt(3)               // 5–7%
            val contrastDelta = 0.03 + Random.nextDouble() * 0.02 // +3–5%
            val saturationDelta = 0.97 + Random.nextDouble() * 0.06 // 0.97–1.03
            val brightnessDelta = -0.02 + Random.nextDouble() * 0.04 // -0.02 – +0.02

            // Зум: кроп с динамическим плавным сдвигом (pan)
            val cropFactor = 1.0 - cropPercent / 100.0
            // Плавное перемещение кадра по синусоиде
            val panExprX = "((1-$cropFactor)*iw/2)*(1+sin(t*0.5))/2"
            val panExprY = "((1-$cropFactor)*ih/2)*(1+cos(t*0.7))/2"
            val cropW = "iw*$cropFactor"
            val cropH = "ih*$cropFactor"

            // Контраст / яркость / насыщенность через eq-фильтр
            val contrast = 1.0 + contrastDelta
            val brightness = brightnessDelta
            val saturation = saturationDelta

            // Атемпо для аудио (коррекция скорости без изменения тональности)
            val audioSpeed = speed

            withContext(Dispatchers.Main) {
                onProgress(0.10f, "Настройка фильтров...")
            }

            // 3. Формируем FFmpeg-команду
            // Видеофильтры: setpts (ускорение) → crop (зум/pan) → eq (цветокоррекция) → scale (обратно к исходному размеру)
            val videoFilters = buildString {
                // Ускорение видео
                append(String.format(Locale.US, "setpts=%.4f*PTS,", 1.0 / speed))
                // Динамический кроп с pan-эффектом
                append("crop=$cropW:$cropH:$panExprX:$panExprY,")
                // Масштабирование обратно к четному разрешению (требование H.264)
                append("scale=trunc(iw/2)*2:trunc(ih/2)*2,")
                // Цветокоррекция с принудительной локалью US (точка вместо запятой)
                append(String.format(Locale.US, "eq=contrast=%.3f:brightness=%.4f:saturation=%.3f", contrast, brightness, saturation))
            }

            // Аудиофильтр — atempo (сохраняет тональность при изменении скорости)
            val audioFilter = String.format(Locale.US, "atempo=%.4f", audioSpeed)

            // Полная команда FFmpeg
            // -map_metadata -1  — очистка всех метаданных
            // -fflags +bitexact  — убирает технические метки
            // -movflags +faststart — оптимизация для стриминга
            val ffmpegCommand = buildString {
                append("-i \"${tempInput.absolutePath}\" ")
                append("-vf \"$videoFilters\" ")
                append("-af \"$audioFilter\" ")
                append("-map_metadata -1 ")
                append("-fflags +bitexact ")
                append("-flags:v +bitexact ")
                append("-flags:a +bitexact ")
                append("-movflags +faststart ")
                append("-c:v libx264 -preset medium -crf 23 ")
                append("-c:a aac -b:a 128k ")
                append("-y \"${tempOutput.absolutePath}\"")
            }

            // 4. Получаем длительность для расчёта прогресса
            val durationMs = getVideoDurationMs(tempInput.absolutePath)
            val adjustedDurationMs = if (durationMs > 0) (durationMs / speed).toLong() else 0L

            withContext(Dispatchers.Main) {
                onProgress(0.15f, "Обработка видео...")
            }

            // 5. Запускаем FFmpeg
            val session = FFmpegKit.execute(ffmpegCommand)

            // Проверяем результат
            if (!ReturnCode.isSuccess(session.returnCode)) {
                val errorLog = session.failStackTrace ?: session.output ?: "Неизвестная ошибка"
                withContext(Dispatchers.Main) {
                    onComplete(false, "Ошибка обработки: $errorLog")
                }
                return@withContext
            }

            withContext(Dispatchers.Main) {
                onProgress(0.85f, "Сохранение в галерею...")
            }

            // 6. Сохраняем результат в MediaStore (галерея)
            val savedUri = saveToGallery(tempOutput)

            // 7. Генерируем хеш-отчёт
            val hash = computeFileHash(tempOutput)

            // 8. Очистка временных файлов
            tempInput.delete()
            tempOutput.delete()

            withContext(Dispatchers.Main) {
                if (savedUri != null) {
                    onProgress(1.0f, "Готово!")
                    onComplete(true,
                        "✅ Видео уникализировано и сохранено!\n" +
                        "Скорость: ${"%.2f".format(speed)}x\n" +
                        "Кроп: ${cropPercent}%\n" +
                        "SHA-256: ${hash.take(16)}..."
                    )
                } else {
                    onComplete(false, "Ошибка сохранения в галерею")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onComplete(false, "Ошибка: ${e.message}")
            }
        }
    }

    /**
     * Сохраняет обработанный файл в галерею через MediaStore.
     */
    private fun saveToGallery(file: File): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "unique_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/VideoUniqueizer")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collection, values) ?: return null

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream, bufferSize = 8192)
            }
        }

        // Снимаем флаг IS_PENDING на Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val updateValues = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, updateValues, null, null)
        }

        return uri
    }

    /**
     * Вычисляет SHA-256 хеш файла.
     */
    private fun computeFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
