package dev.mr3n

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import java.io.Closeable

class TunnelingServer(selectorManager: SelectorManager) : Closeable {

    val port = (Data.PORT_START..Data.PORT_END).toMutableList().apply { removeAll(Data.USING_PORT) }.random()

    val tunnelingSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", port)
    suspend fun get(): Socket = tunnelingSocket.accept()

    override fun close() {
        try {
            tunnelingSocket.close()
        } catch (_: Exception) {
        }
    }
}