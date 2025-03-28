plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    kotlin("plugin.serialization") version "1.9.0"
    application
}

group = "org.erbeenjoyers.drinkwater"
version = "1.0.0"
application {
    mainClass.set("org.erbeenjoyers.drinkwater.ApplicationKt")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=${extra["io.ktor.development"] ?: "false"}")
}

dependencies {
    implementation(projects.shared)
    implementation(libs.logback)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    testImplementation(libs.ktor.ktor.server.tests.jvm.v237)
    testImplementation(libs.kotlin.test.junit)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.websockets)
    implementation(libs.sqlite.jdbc)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.json.v160)
}