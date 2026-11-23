import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 发布签名只从本机 Gradle 属性或环境变量读取，私钥与口令不得提交仓库。
val releaseStoreFile = providers.gradleProperty("lifehub.release.storeFile").orNull
    ?: System.getenv("LIFEHUB_RELEASE_STORE_FILE")
val releaseStorePassword = providers.gradleProperty("lifehub.release.storePassword").orNull
    ?: System.getenv("LIFEHUB_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("lifehub.release.keyAlias").orNull
    ?: System.getenv("LIFEHUB_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("lifehub.release.keyPassword").orNull
    ?: System.getenv("LIFEHUB_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val appVersionCode = 1
val appVersionName = "1.0.0"
val requireReleaseSigning = providers.gradleProperty("lifehubRequireReleaseSigning")
    .map { it.toBoolean() }
    .orElse(false)

android {
    namespace = "com.lifehub.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lifehub.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // 本地调试额外提供 x86_64，便于在模拟器上运行；发布包只保留 arm64-v8a。
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            isMinifyEnabled = false
            isDebuggable = false
            ndk {
                abiFilters += "arm64-v8a"
            }
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.register("checkReleasePrerequisites") {
    group = "verification"
    description = "检查发布版本号、签名配置与内置插件资产"
    doLast {
        require(appVersionCode > 0) { "versionCode 必须大于 0" }
        require(appVersionName.isNotBlank()) { "versionName 不能为空" }

        if (hasReleaseSigning) {
            println("Release signing: configured")
        } else if (requireReleaseSigning.get()) {
            error(
                "缺少 release 签名配置：请设置 lifehub.release.* Gradle 属性或 LIFEHUB_RELEASE_* 环境变量",
            )
        } else {
            logger.warn("WARN: 未配置 release 签名，生成的 APK 仅用于本地校验，不可发布")
        }

        val pluginRoots = listOf(
            file("src/main/assets/plugins"),
            rootProject.file("../lifehub-plugin-ledger/src/main/assets/plugins"),
            rootProject.file("../lifehub-plugin-memo/src/main/assets/plugins"),
            rootProject.file("../lifehub-plugin-travel/src/main/assets/plugins"),
        )
        require(pluginRoots.all { it.isDirectory }) {
            "缺少插件目录：${pluginRoots.filterNot { it.isDirectory }.joinToString { it.absolutePath }}"
        }
        val pluginCount = pluginRoots.sumOf { root ->
            root.listFiles()?.count { it.isDirectory } ?: 0
        }
        require(pluginCount > 0) { "插件目录为空：${pluginRoots.joinToString { it.absolutePath }}" }
        println("Release prerequisites OK: version $appVersionName ($appVersionCode), plugins=$pluginCount")
    }
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(project(":plugin-runtime"))
    implementation(project(":plugins:ledger"))
    implementation(project(":plugins:travel"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(project(":plugin-testing"))
}
