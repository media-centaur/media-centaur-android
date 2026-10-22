package app.mediacentaur.tv.player

/**
 * What the bridge needs from the player's mpv: typed reads and writes,
 * commands, property observation, and the events back. [Player] is the
 * libmpv implementation; tests use a fake.
 */
interface Mpv {
    fun command(args: List<String>)
    fun getString(name: String): String?
    fun getDouble(name: String): Double?
    fun getLong(name: String): Long?
    fun getBoolean(name: String): Boolean?
    fun setString(name: String, value: String)
    fun setBoolean(name: String, value: Boolean)
    fun setLong(name: String, value: Long)
    fun setDouble(name: String, value: Double)
    fun observe(name: String, format: Int)

    /** A deliberate stop of the current file; remembered so the end-file reason is `stop`. */
    fun stop()

    var listener: Listener?

    interface Listener {
        /** A property change: value null for no data, else Long, Double, Boolean or String. */
        fun onProperty(name: String, value: Any?)

        /** An mpv event by its IPC name; `end-file` carries the reason. */
        fun onEvent(name: String, reason: String?)
    }

    object Format {
        const val NONE = 0
        const val STRING = 1
        const val FLAG = 3
        const val INT64 = 4
        const val DOUBLE = 5
    }
}
