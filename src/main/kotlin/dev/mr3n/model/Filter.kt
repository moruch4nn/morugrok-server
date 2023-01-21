package dev.mr3n.model

import kotlinx.serialization.Serializable

/**
 * ブラックリスト/ホワイトリスト方式でIPアドレスをブロックできます。
 * type: ブラックリストかホワイトリストか
 * list: IPアドレス一覧。
 */
@Serializable
data class Filter(val type: Type,val list: List<String>) {
    enum class Type {
        BLACKLIST,
        WHITELIST
    }
}
