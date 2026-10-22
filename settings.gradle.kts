pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Локальный репозиторий для ffmpeg-kit AAR (загружается в CI)
        flatDir {
            dirs("app/libs")
        }
    }
}

rootProject.name = "VideoUniqueizer"
include(":app")
