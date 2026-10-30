package dev.hush.client.discord

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Small helpers for reading loosely-shaped gateway payloads. */

internal fun JsonObject.str(key: String): String? {
    val p = this[key]
    return if (p is JsonPrimitive && p !is JsonNull) p.content else null
}

internal fun JsonObject.int(key: String): Int? = str(key)?.toIntOrNull()
internal fun JsonObject.long(key: String): Long? = str(key)?.toLongOrNull()
internal fun JsonObject.bool(key: String): Boolean? = str(key)?.toBooleanStrictOrNull()
internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonElement.asObj(): JsonObject? = this as? JsonObject

internal fun parsePermissions(raw: String?): Long =
    raw?.toULongOrNull()?.toLong() ?: raw?.toLongOrNull() ?: 0L

const val DISCORD_EPOCH = 1420070400000L

/** Milliseconds since the Unix epoch encoded in a Discord snowflake. */
fun snowflakeTime(id: String): Long = ((id.toLongOrNull() ?: 0L) ushr 22) + DISCORD_EPOCH

/** A snowflake-shaped nonce for the current moment. */
fun newNonce(): String = ((System.currentTimeMillis() - DISCORD_EPOCH) shl 22).toString()

fun idCompare(a: String, b: String): Int =
    (a.toLongOrNull() ?: 0L).compareTo(b.toLongOrNull() ?: 0L)
