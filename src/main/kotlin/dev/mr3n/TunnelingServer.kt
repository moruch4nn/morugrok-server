package dev.mr3n

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import java.io.Closeable

/**
 * ユーザーごとに新しく作成されるTCPコネクションです。
 * このコネクションは morugrokサーバー<->サーバーホスト 間を繋いでいます。
 */
class TunnelingServer(selectorManager: SelectorManager) : Closeable {

    val port = (PORT_START..PORT_END).toMutableList().apply { removeAll(USING_PORT) }.random()

    val tunnelingSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", port)
    suspend fun get(): Socket = tunnelingSocket.accept()

    override fun close() {
        try { tunnelingSocket.close() } catch (_: Exception) { }
    }
}