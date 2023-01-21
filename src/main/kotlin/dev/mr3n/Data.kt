package dev.mr3n

import dev.mr3n.model.ConnectionRequest

// >>> 使用可能なポート範囲 >>>
const val PORT_START = 10000
const val PORT_END = 60000
// <<< 使用可能なポート範囲 <<<

/**
 * 現在使用中のポート一覧
 */
val USING_PORT: List<Int>
    get() = CONNECTIONS.values.map { it.values }.flatten().map { listOf(it.port, it.tunnelingServer.port) }.flatten()

/**
 * サーバーホストからのwebsocket接続を待機しているコネクション一覧
 */
val WAIT_CONNECTIONS = mutableMapOf<String, MutableMap<String, ConnectionRequest>>()

/**
 * コネクション一覧
 */
val CONNECTIONS = mutableMapOf<String, MutableMap<String, TCPConnection>>()

/**
 * パケットのバッファサイズ。
 */
const val DEFAULT_BUFFER_SIZE = 30000