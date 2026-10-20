# 🎬 Video Uniqueizer — Уникализатор видео

Android-приложение для уникализации видео (Reels / Shorts / TikTok).

## Алгоритмы обработки

| Алгоритм | Описание |
|---|---|
| ⚡ Ускорение | 1.05x–1.1x с сохранением тональности (atempo) |
| 🔍 Динамический зум | Кроп +5–7% с плавным pan-эффектом по синусоиде |
| 🎨 Цветокоррекция | Контраст +3–5%, микро-изменение яркости и насыщенности |
| 🧹 Метаданные | Полное удаление exif, смещение таймкодов |
| 🔒 Хеш | Генерация нового SHA-256 хеша |

## Технологии

- **Язык:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Обработка видео:** FFmpeg Kit 6.0 (full)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)

## Сборка

### Через GitHub Actions (рекомендуется)

1. Создайте репозиторий на GitHub
2. Запушьте все файлы проекта
3. Перейдите во вкладку **Actions**
4. Нажмите **Run workflow** (или дождитесь автоматического запуска)
5. После успешной сборки скачайте APK из **Artifacts**

### Локально (Android Studio)

1. Откройте проект в Android Studio
2. Sync Gradle
3. Run → Build APK

## Структура проекта

```
video-uniqueizer/
├── .github/workflows/build-apk.yml    — CI/CD для GitHub Actions
├── app/
│   ├── build.gradle.kts               — зависимости приложения
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/uniqueizer/app/
│       │   ├── MainActivity.kt         — UI (Jetpack Compose)
│       │   ├── VideoProcessor.kt       — логика обработки видео
│       │   └── ui/theme/
│       │       ├── Color.kt
│       │       ├── Theme.kt
│       │       └── Type.kt
│       └── res/
│           ├── mipmap-anydpi-v26/      — адаптивная иконка
│           └── values/
│               ├── strings.xml
│               ├── themes.xml
│               └── ic_launcher_background.xml
├── build.gradle.kts                    — корневой Gradle
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/gradle-wrapper.properties
```

## Лицензия

MIT
