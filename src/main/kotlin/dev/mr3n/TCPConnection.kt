package dev.mr3n

import dev.mr3n.model.ConnectionInfo
import dev.mr3n.model.Filter
import dev.mr3n.model.Protocol
import dev.mr3n.model.ws.CreateTunnelRequest
import dev.mr3n.model.ws.PacketType
import dev.mr3n.model.ws.WebSocketPacket
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.internal.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.thread

class TCPConnection(
    var name: String?,
    val user: String,
    port: Int,
    val protocol: Protocol,
    filter: Filter,
    private val token: String,
    val webSocketSession: WebSocketSession
) : Closeable {
    private val selectorManager = SelectorManager(Dispatchers.IO)

    private var serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", port)
    // サーバー <-> morugrok <-> ユーザー 間のアドレスとコネクション一覧
    private val tcpCons = CopyOnWriteArraySet<Triple<String, ConnectionSocket, ConnectionSocket>>()
    // morugrok <-> サーバーホスト 間をつなぐコネクションを生成するマネージャー。TunnelingServer.get()でコネクションを待機する
    val tunnelingServer = TunnelingServer(selectorManager)

    private var thread = thread(isDaemon = true) { createTask().invoke() }

    private fun createTask(): ()->Unit {
        return { runBlocking {
            try {
                while (true) {
                    // ユーザーからの新規接続を待機
                    val socket = serverSocket.accept()
                    // 接続元のIPアドレス
                    val address = (socket.remoteAddress.toJavaAddress() as InetSocketAddress).hostString
                    // このIPアドレスがブロックされているかどうか
                    val isBlocked = when (filter.type) {
                        Filter.Type.BLACKLIST -> filter.list.contains(address)
                        Filter.Type.WHITELIST -> !filter.list.contains(address)
                    }
                    if (isBlocked) {
                        // if:IPアドレスがブロックされている場合
                        // 接続を閉じる
                        withContext(Dispatchers.IO) { socket.close() }
                    } else {
                        // if:アドレスがブロックされていない場合
                        // サーバーホスト側のmorugrokクライアントに新しいコネクションを作成するリクエストを送信
                        val json = DefaultJson.encodeToString(WebSocketPacket(PacketType.CREATE_TUNNEL, CreateTunnelRequest(tunnelingServer.port, Protocol.TCP, address, System.currentTimeMillis())))
                        webSocketSession.send(json)
                        // morugrokクライアントからの接続を待機
                        val tunnelingSocket = tunnelingServer.get()
                        // 接続が来たらmorugrokクライアントとのコネクションを確立
                        val tunnelingConnection = tunnelingSocket.connection()
                        // ユーザーからのコネクションを確立
                        val clientConnection = socket.connection()
                        // morugrokクライアント->ユーザー 方向のTCPプロキシを作成
                        val tunnelingConnectionSocket = ConnectionSocket(tunnelingConnection, clientConnection)
                        // ユーザー->morugrokクライアント 方向のTCPプロキシを作成
                        val clientConnectionSocket = ConnectionSocket(clientConnection, tunnelingConnection)
                        // アドレスと上で作成したコネクション２つをtripleにする
                        val con = Triple(address, tunnelingConnectionSocket, clientConnectionSocket)
                        // tripleをconnectionsに保存し、確立中のコネクション一覧を更新
                        tcpCons.add(con)
                        // morugrokクライアント->ユーザー の通信が終了した際にconnectionsからこのコネクションを削除。
                        tunnelingConnectionSocket.onEnd {
                            tunnelingSocket.close()
                            socket.close()
                            tcpCons.remove(con)
                        }
                        // ユーザー->morugrokクライアント の通信が終了した際にconnectionsからこのコネクションを削除。
                        clientConnectionSocket.onEnd {
                            tunnelingSocket.close()
                            socket.close()
                            tcpCons.remove(con)
                        }
                    }
                }
            } catch (_: ClosedChannelException) { }
        } }
    }

    // 公開ポート: 例 20051 -> morugrok_host:20051
    var port: Int = port
        set(value) {
            if(USING_PORT.contains(value)) { throw IllegalStateException("既にポート${value}は別のコネクションに使用されています。") }
            serverSocket.close()
            serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", value)
            thread = thread(isDaemon = true) { createTask() }
            field = value
        }

    // ブロックするIPのフィルター。詳細はFilterクラスを確認してください。
    var filter: Filter = filter
        set(value) {
            // filterが更新された場合
            // 既に確立されているコネクションを取得
            tcpCons.forEach { (address, socket1, socket2) ->
                // このアドレスをブロックするべきかどうか
                val isBlocked = when (value.type) {
                    Filter.Type.BLACKLIST -> value.list.contains(address)
                    Filter.Type.WHITELIST -> !value.list.contains(address)
                }
                // ブロックする場合はコネクションを閉じる
                if (isBlocked) {
                    socket1.close()
                    socket2.close()
                }
            }
            field = value
        }

    // このmorugrokのコネクションをすべて閉じる
    override fun close() {
        // ユーザー<->サーバーホスト間のコネクションをすべて閉じる
        try { tcpCons.toSet().forEach { triple -> triple.second.close();triple.third.close() } } catch(_: Exception) {}
        // morugrok<->ユーザー間のコネクションを閉じる
        try { serverSocket.close() } catch(_: Exception) {}
        // morugrok<->サーバーホスト間のコネクションを閉じる
        try { tunnelingServer.close() } catch(_: Exception) {}
        // こねくしょんのマネージャーを閉じる
        try { selectorManager.close() } catch(_: Exception) {}

        // morugrok<->サーバーホスト間のwebsocketセッションを閉じる
        try { runBlocking { webSocketSession.close() } } catch(_: Exception) {}

        // このコネクションのスレッドを閉じる
        try { thread.interrupt() } catch (_: Exception) { }

        // コネクション一覧からこのコネクションを削除
        CONNECTIONS[user]?.remove(token)
    }

    // TCPConnectionをConnectionInfo型に変換する
    fun toConnectionInfo() = ConnectionInfo(name, user, port, protocol, filter, token)

    init {
        // コネクション一覧にこのコネクションを追加
        CONNECTIONS[user] = (CONNECTIONS[user] ?: mutableMapOf()).also { it[token] = this }
    }

    /**
     * receive側で受信したパケットをsendTo側にそのまま送信します。
     */
    class ConnectionSocket(private val receive: Connection, private val sendTo: Connection) : Thread() {

        private val endRunnable = mutableListOf<()->Unit>()

        fun onEnd(runnable: ()->Unit) { endRunnable.add(runnable) }

        fun close() {
            endRunnable.forEach { it.invoke() }
            try { receive.socket.close() } catch (e: Exception) { e.printStackTrace() }
            try { sendTo.socket.close() } catch (e: Exception) { e.printStackTrace() }
            thread.interrupt()
        }

        private val thread = thread(isDaemon = true) {
            try {
                runBlocking {
                    val inputStream = receive.input
                    val outputStream = sendTo.output
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val bytesRead: Int = inputStream.readAvailable(buffer)
                        if (bytesRead == -1) throw SocketException() // end
                        outputStream.writeFully(buffer, 0, bytesRead)
                        outputStream.flush()
                    }
                }
            } catch(_: Exception) { } finally { this.close() }
        }
    }
}