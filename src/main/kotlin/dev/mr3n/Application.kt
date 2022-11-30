package dev.mr3n

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import dev.mr3n.plugins.*
import java.security.Security

fun main() {
    System.setProperty("io.ktor.random.secure.random.provider", "DRBG")
    Security.setProperty("securerandom.drbg.config", "HMAC_DRBG,SHA-512,256,pr_and_reseed")
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureSockets()
    configureSerialization()
    configureSecurity()
}
