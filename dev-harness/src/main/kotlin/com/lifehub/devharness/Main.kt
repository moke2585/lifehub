package com.lifehub.devharness

import com.lifehub.pluginapi.HostInfo
import com.lifehub.pluginapi.PluginApiVersion
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val options = parseOptions(args)
    if (options.help) {
        printUsage()
        return
    }
    if (options.error != null) {
        System.err.println("参数错误：${options.error}")
        printUsage()
        exitProcess(2)
    }
    if (options.paths.isEmpty()) {
        printUsage()
        exitProcess(2)
    }

    val validator = PluginPackageValidator(
        hostInfo = HostInfo(
            versionCode = options.hostVersion,
            versionName = "dev-harness",
            pluginApiMajor = PluginApiVersion.MAJOR,
        ),
    )

    var errors = 0
    var warnings = 0
    var packages = 0
    var okPackages = 0

    options.paths.forEach { path ->
        val report = validator.validate(File(path))
        println()
        println("== ${report.root.absolutePath} ==")
        printIssues(report.issues)

        report.packages.forEach { pkg ->
            packages++
            errors += pkg.errorCount
            warnings += pkg.warningCount
            if (pkg.errorCount == 0) okPackages++
            printPackage(pkg, options.denyCapabilities)
        }

        errors += report.issues.count { it.severity == IssueSeverity.ERROR }
        warnings += report.issues.count { it.severity == IssueSeverity.WARNING }
    }

    println()
    println("Summary: packages=$packages ok=$okPackages errors=$errors warnings=$warnings")
    if (errors > 0 || (options.strict && warnings > 0)) {
        exitProcess(1)
    }
}

private fun printPackage(pkg: PluginPackageReport, denyCapabilities: Boolean) {
    println()
    println("-- ${pkg.pluginId ?: pkg.folder.name} (${pkg.folder.name}) --")
    val manifest = pkg.manifest
    if (manifest == null) {
        printIssues(pkg.issues, prefix = "  ")
        return
    }

    println("  manifest: parsed")
    println("  pages: ${pkg.pageIds.ifEmpty { listOf("none") }.joinToString()}")
    pkg.signerFingerprint?.let { fingerprint ->
        println("  signer: $fingerprint")
    }
    if (pkg.hostActions.isNotEmpty()) {
        println("  host actions: ${pkg.hostActions.joinToString()}")
        println("    ^ 需要在宿主注册 PluginActionHandler")
    }
    if (pkg.dataSources.isNotEmpty()) {
        println("  data sources: ${pkg.dataSources.joinToString()}")
        println("    ^ 需要在宿主注册 PluginPageDataHandler")
    }
    if (pkg.homeWidgets.isNotEmpty()) {
        println("  home widgets: ${pkg.homeWidgets.joinToString()}")
    }
    LifecycleSimulator.summary(manifest, denyCapabilities).forEach { line ->
        println("  lifecycle: $line")
    }
    if (pkg.issues.isEmpty()) {
        println("  result: OK")
    } else {
        println("  result: ${pkg.errorCount} error(s), ${pkg.warningCount} warning(s)")
        printIssues(pkg.issues, prefix = "  ")
    }
}

private fun printIssues(issues: List<HarnessIssue>, prefix: String = "") {
    issues.forEach { issue ->
        val label = when (issue.severity) {
            IssueSeverity.ERROR -> "ERROR"
            IssueSeverity.WARNING -> "WARN"
        }
        println("$prefix[$label] ${issue.file}: ${issue.message}")
    }
}

private data class CliOptions(
    val paths: List<String> = emptyList(),
    val hostVersion: Int = 1,
    val denyCapabilities: Boolean = false,
    val strict: Boolean = false,
    val help: Boolean = false,
    val error: String? = null,
)

private fun parseOptions(args: Array<String>): CliOptions {
    val paths = mutableListOf<String>()
    var hostVersion = 1
    var denyCapabilities = false
    var strict = false
    var help = false
    var index = 0

    while (index < args.size) {
        val arg = args[index]
        when {
            arg == "--help" || arg == "-h" -> help = true
            arg == "--deny-capabilities" -> denyCapabilities = true
            arg == "--strict" -> strict = true
            arg == "--host-version" -> {
                val value = args.getOrNull(index + 1)
                    ?: return CliOptions(error = "--host-version 缺少数值")
                hostVersion = value.toIntOrNull()
                    ?: return CliOptions(error = "--host-version 必须是整数")
                index++
            }

            arg.startsWith("--host-version=") -> {
                hostVersion = arg.substringAfter('=').toIntOrNull()
                    ?: return CliOptions(error = "--host-version 必须是整数")
            }

            arg.startsWith("--") -> return CliOptions(error = "未知参数：$arg")
            else -> paths += arg
        }
        index++
    }

    if (hostVersion <= 0) {
        return CliOptions(error = "--host-version 必须大于 0")
    }
    return CliOptions(
        paths = paths,
        hostVersion = hostVersion,
        denyCapabilities = denyCapabilities,
        strict = strict,
        help = help,
    )
}

private fun printUsage() {
    println(
        """
        插件包校验与生命周期模拟工具。

        用法：
          dev-harness [选项] <插件根目录或 .lhplugin 文件>...

        选项：
          --host-version <n>      模拟的宿主 versionCode，默认 1
          --deny-capabilities     模拟用户拒绝能力授权
          --strict                出现 warning 时也返回非零退出码
          -h, --help              显示帮助

        示例：
          dev-harness app/src/main/assets/plugins
          dev-harness samples --strict
          dev-harness samples/com.lifehub.plugin.template --deny-capabilities
          dev-harness path/to/plugin.lhplugin
        """.trimIndent(),
    )
}
