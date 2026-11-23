plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.lifehub.devharness.MainKt")
}

dependencies {
    implementation(project(":plugin-api"))
    implementation(project(":plugin-runtime"))
    testImplementation(project(":plugin-testing"))
    testImplementation(libs.junit)
}

tasks.register<JavaExec>("validatePlugins") {
    group = "verification"
    description = "校验 APK 内置插件与 samples 下的插件包"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.lifehub.devharness.MainKt")
    args(
        rootProject.file("app/src/main/assets/plugins").absolutePath,
        rootProject.file("../lifehub-plugin-ledger/src/main/assets/plugins").absolutePath,
        rootProject.file("../lifehub-plugin-memo/src/main/assets/plugins").absolutePath,
        rootProject.file("../lifehub-plugin-travel/src/main/assets/plugins").absolutePath,
        rootProject.file("samples").absolutePath,
    )
}
