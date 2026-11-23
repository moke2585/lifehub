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

rootProject.name = "lifehub"

include(":app")
include(":plugin-api")
include(":plugin-runtime")
include(":plugin-testing")
include(":plugins:ledger")
include(":plugins:memo")
include(":plugins:memo-apk")
include(":plugins:travel")
include(":dev-harness")

// 插件仓库与宿主仓库同级，各自独立；这里只把它们的源码目录纳入本次构建。
project(":plugins:ledger").projectDir = file("../lifehub-plugin-ledger")
project(":plugins:memo").projectDir = file("../lifehub-plugin-memo")
project(":plugins:memo-apk").projectDir = file("../lifehub-plugin-memo/apk")
project(":plugins:travel").projectDir = file("../lifehub-plugin-travel")
