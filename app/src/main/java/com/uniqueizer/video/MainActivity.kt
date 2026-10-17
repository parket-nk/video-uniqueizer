package com.uniqueizer.video

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.PI
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var btnPickVideo: Button
    private lateinit var btnProcess: Button
    private lateinit var tvSelectedFile: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private var selectedVideoUri: Uri? = null

    private val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedVideoUri = uri
            tvSelectedFile.text = "Видео выбрано (нажмите «Сделать уникальным»)"
            btnProcess.isEnabled = true
            tvStatus.text = ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnPickVideo = findViewById(R.id.btnPickVideo)
        btnProcess = findViewById(R.id.btnProcess)
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        btnProcess.setOnClickListener {
            selectedVideoUri?.let { uri ->
                startVideoProcessing(uri)
            }
        }
    }

    private fun startVideoProcessing(uri: Uri) {
        btnPickVideo.isEnabled = false
        btnProcess.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvStatus.text = "Копирование видео во временный буфер..."

        Thread {
            try {
                // Копируем выбранное видео во временный файл в кэше приложения
                val inputFile = File(cacheDir, "input_${System.currentTimeMillis()}.mp4")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(inputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val outputFile = File(cacheDir, "unique_${System.currentTimeMillis()}.mp4")

                // 1. Параметры скорости (~1.1x с рандомизацией)
                val speed = 1.09 + Random.nextDouble(0.0, 0.03)

                // 2. Параметры зума (+7% = 1.07)
                val zoom = 1.07 + Random.nextDouble(0.0, 0.008)

                // 3. Параметры плавающего кадра (периоды и фазы синусоиды)
                val periodX = 7.5 + Random.nextDouble(0.0, 3.5)
                val periodY = 9.0 + Random.nextDouble(0.0, 4.0)
                val phaseX = Random.nextDouble(0.0, 2 * PI)
                val phaseY = Random.nextDouble(0.0, 2 * PI)
                val amp = 0.82

                // 4. Параметры света
                val brightness = Random.nextDouble(-0.02, 0.02)
                val contrast = 1.02 + Random.nextDouble(0.0, 0.03)
                val saturation = 1.03 + Random.nextDouble(0.0, 0.03)
                val gamma = 0.99 + Random.nextDouble(0.0, 0.03)

                // 5. Параметры звука
                val eqFreq = Random.nextInt(900, 3000)
                val eqGain = Random.nextDouble(-1.5, 1.5)
                val pitchDrift = 1.0 + Random.nextDouble(-0.003, 0.003)
                val shiftedRate = (44100 * pitchDrift).toInt()

                // Формирование фильтров FFmpeg
                val xExpr = "(in_w-out_w)/2+((in_w-out_w)/2)*$amp*sin(2*PI*t/$periodX+$phaseX)"
                val yExpr = "(in_h-out_h)/2+((in_h-out_h)/2)*$amp*cos(2*PI*t/$periodY+$phaseY)"

                val vf = "setpts=PTS/$speed," +
                        "crop=w='trunc(in_w/$zoom/2)*2':h='trunc(in_h/$zoom/2)*2':x='$xExpr':y='$yExpr'," +
                        "scale=in_w:in_h:flags=lanczos," +
                        "eq=brightness=$brightness:contrast=$contrast:saturation=$saturation:gamma=$gamma"

                val af = "asetrate=$shiftedRate,aresample=44100,equalizer=f=$eqFreq:t=q:w=1.2:g=$eqGain,atempo=$speed"

                val uniqueTag = UUID.randomUUID().toString().substring(0, 8)

                val ffmpegCommand = arrayOf(
                    "-y",
                    "-i", inputFile.absolutePath,
                    "-vf", vf,
                    "-af", af,
                    "-c:v", "libx264",
                    "-preset", "fast",
                    "-crf", "21",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac",
                    "-b:a", "192k",
                    "-map_metadata", "-1",
                    "-metadata", "title=video_$uniqueTag",
                    outputFile.absolutePath
                ).joinToString(" ") { "\"$it\"" }

                runOnUiThread {
                    tvStatus.text = "Идет уникализация через FFmpeg...\n(пожалуйста, подождите)"
                }

                FFmpegKit.executeAsync(ffmpegCommand) { session ->
                    val returnCode = session.returnCode
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        btnPickVideo.isEnabled = true
                        btnProcess.isEnabled = true

                        if (ReturnCode.isSuccess(returnCode)) {
                            // Сохранение видео в публичную галерею телефона
                            saveVideoToGallery(outputFile)
                            tvStatus.text = "Готово! Уникальное видео сохранено в вашу Галерею."
                            Toast.makeText(this, "Видео успешно сохранено в Галерею!", Toast.LENGTH_LONG).show()
                        } else {
                            tvStatus.text = "Ошибка при обработке видео: ${session.failStackTrace}"
                            Toast.makeText(this, "Ошибка обработки FFmpeg", Toast.LENGTH_SHORT).show()
                        }

                        // Очистка входного временного файла
                        inputFile.delete()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnPickVideo.isEnabled = true
                    btnProcess.isEnabled = true
                    tvStatus.text = "Ошибка: ${e.localizedMessage}"
                }
            }
        }.start()
    }

    private fun saveVideoToGallery(videoFile: File) {
        val fileName = "Unique_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/UniqueVideos")
            }
        }

        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let { destUri ->
            contentResolver.openOutputStream(destUri)?.use { out ->
                videoFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
        }
    }
}
