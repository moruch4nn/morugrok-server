val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

plugins {
    application
    kotlin("jvm") version "1.7.21"
    id("io.ktor.plugin") version "2.1.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.7.21"
}

group = "dev.mr3n"
version = "0.0.1"
application {
    mainClass.set("dev.mr3n.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-core-jvm:2.2.1")
    implementation("io.ktor:ktor-server-auth-jvm:2.2.1")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:2.2.1")
    implementation("io.ktor:ktor-server-websockets-jvm:2.2.1")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.2.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.2.1")
    implementation("io.ktor:ktor-server-netty-jvm:2.2.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("io.ktor:ktor-server-tests-jvm:2.2.1")
}