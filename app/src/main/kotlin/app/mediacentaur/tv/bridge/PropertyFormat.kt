package app.mediacentaur.tv.bridge

import app.mediacentaur.tv.player.Mpv
import org.json.JSONArray
import org.json.JSONObject

/**
 * How each mpv property crosses the bridge: the format it is observed and
 * read in, and how libmpv's value becomes the JSON the host sees over the
 * Unix socket. mpv renders node properties as JSON text and key-value lists
 * as `k=v,k=v` when read as strings; both come back as structures here.
 */
object PropertyFormat {
    private val doubles = setOf("time-pos", "duration", "percent-pos", "playback-time", "time-remaining", "volume", "speed")
    private val longs = setOf("playlist-count", "playlist-pos", "chapter", "chapters", "playlist-current-pos")
    private val flags = setOf("pause", "eof-reached", "idle-active", "core-idle", "seeking", "paused-for-cache", "mute")
    private val jsonText = setOf("track-list", "chapter-list", "playlist", "metadata", "video-params", "audio-params")
    private val keyValueLists = setOf("script-opts")

    fun of(name: String): Int = when (name) {
        in doubles -> Mpv.Format.DOUBLE
        in longs -> Mpv.Format.INT64
        in flags -> Mpv.Format.FLAG
        else -> Mpv.Format.STRING
    }

    /** Reads [name] from [mpv] in its format, rendered; null when unavailable. */
    fun read(mpv: Mpv, name: String): Any? = when (of(name)) {
        Mpv.Format.DOUBLE -> mpv.getDouble(name)
        Mpv.Format.INT64 -> mpv.getLong(name)
        Mpv.Format.FLAG -> mpv.getBoolean(name)
        else -> mpv.getString(name)?.let { render(name, it) }
    }

    /** The JSON value for a property's raw value from libmpv. */
    fun render(name: String, value: Any?): Any? = when (value) {
        null -> null
        is String -> when (name) {
            in jsonText -> parseJson(value)
            in keyValueLists -> keyValueMap(value)
            else -> scalar(value)
        }
        else -> value
    }

    private fun parseJson(text: String): Any? {
        val trimmed = text.trim()
        return try {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> scalar(trimmed)
            }
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun keyValueMap(text: String): JSONObject {
        val map = JSONObject()
        if (text.isBlank()) return map
        for (pair in text.split(',')) {
            val index = pair.indexOf('=')
            if (index > 0) map.put(pair.substring(0, index), pair.substring(index + 1))
        }
        return map
    }

    // mpv's string renderings of scalars (`aid`, `sid` are "no" or a number).
    private fun scalar(value: String): Any = when (value) {
        "yes" -> true
        "no" -> false
        else -> value.toLongOrNull() ?: value
    }
}
