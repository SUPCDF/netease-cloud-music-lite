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
    }
}

rootProject.name = "NeteaseCloudMusicLite"

// 应用模块
include(":app")

// 核心基础模块
include(":core:common")
include(":core:network")
include(":core:data")
include(":core:player")

// UI 设计系统
include(":designsystem")

// 功能模块
include(":feature:login")
include(":feature:home")
include(":feature:playlist")
include(":feature:player")
include(":feature:comment")
include(":feature:mv")
include(":feature:mine")
include(":feature:people")
include(":feature:settings")
