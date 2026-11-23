plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "192m"
        jvmArgs("-XX:MaxMetaspaceSize=192m", "-XX:ReservedCodeCacheSize=64m")
    }
}

tasks.register("verifyPluginModuleBoundaries") {
    group = "verification"
    description = "检查插件模块不得引用 app 宿主内部类（plugin-runtime 除外）"
    doLast {
        val allowedPrefix = "import com.lifehub.app.plugin.runtime."
        val pluginDirs = listOf(
            rootProject.file("../lifehub-plugin-ledger"),
            rootProject.file("../lifehub-plugin-memo"),
            rootProject.file("../lifehub-plugin-travel"),
        )
        val violations = pluginDirs.flatMap { dir ->
            fileTree(dir) {
                include("**/src/main/**/*.kt")
            }.files
        }.flatMap { file ->
            file.readLines()
                .filter { line ->
                    val trimmed = line.trim()
                    trimmed.startsWith("import com.lifehub.app.") &&
                        !trimmed.startsWith(allowedPrefix)
                }
                .map { "${file.absolutePath}: ${it.trim()}" }
        }
        require(violations.isEmpty()) {
            "插件模块引用了宿主内部类：\n${violations.joinToString("\n")}"
        }
        println("Plugin module boundaries OK")
    }
}
