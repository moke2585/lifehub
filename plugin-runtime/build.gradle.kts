plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":plugin-api"))
    testImplementation(project(":plugin-testing"))
    testImplementation(libs.junit)
}
