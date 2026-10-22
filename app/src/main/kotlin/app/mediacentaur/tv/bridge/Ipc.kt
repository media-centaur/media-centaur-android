package app.mediacentaur.tv.bridge

import app.mediacentaur.tv.player.Mpv
import org.json.JSONArray
import org.json.JSONObject

/**
 * mpv's JSON IPC over the channel, against an [Mpv]. Inbound frames are
 * commands (`{"command": [...], "request_id": N}`), each answered with
 * `{"request_id": N, "error": "success" | reason, "data": value}`; outbound
 * frames are events in the shape the Unix socket produces. The host's
 * `quit` ends the viewing (`stop`), not the player. Property ids are the
 * host's, from its `observe_property`. `time-pos` is coalesced to one
 * event per second carrying the latest value; everything else passes as
 * it changes. [now] and [schedule] are injected for tests.
 */
class Ipc(
    private val mpv: Mpv,
    private val now: () -> Long = System::currentTimeMillis,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
) : Mpv.Listener {
    @Volatile var sink: ((String) -> Unit)? = null

    private val ids = HashMap<String, Int>()
    private var lastTimePosAt = 0L
    private var pendingTimePos: Any? = null
    private var timePosFlushScheduled = false

    init {
        mpv.listener = this
    }

    @Synchronized
    fun handle(text: String) {
        val request = try { JSONObject(text) } catch (_: Exception) { null }
        val command = request?.optJSONArray("command")
        if (command == null || command.length() == 0) {
            reply(request, "invalid parameter", null)
            return
        }
        val args = (0 until command.length()).map { command.get(it) }
        val name = args[0].toString()
        try {
            when (name) {
                "observe_property" -> {
                    val id = (args[1] as Number).toInt()
                    val property = args[2].toString()
                    ids[property] = id
                    mpv.observe(property, PropertyFormat.of(property))
                    reply(request, "success", null)
                }
                "get_property" -> {
                    val property = args[1].toString()
                    val value = PropertyFormat.read(mpv, property)
                    if (value == null) reply(request, "property unavailable", null) else reply(request, "success", value)
                }
                "set_property" -> {
                    mpv.setString(args[1].toString(), args[2].toString())
                    reply(request, "success", null)
                }
                "quit", "stop" -> {
                    mpv.stop()
                    reply(request, "success", null)
                }
                else -> {
                    mpv.command(args.map { it.toString() })
                    reply(request, "success", null)
                }
            }
        } catch (error: Exception) {
            reply(request, error.message ?: "error running command", null)
        }
    }

    private fun reply(request: JSONObject?, error: String, data: Any?) {
        val reply = JSONObject()
        reply.put("request_id", request?.opt("request_id") ?: 0)
        reply.put("error", error)
        if (data != null) reply.put("data", data)
        emit(reply)
    }

    // --- events from mpv ---

    @Synchronized
    override fun onProperty(name: String, value: Any?) {
        val data = PropertyFormat.render(name, value)
        if (name == "time-pos") {
            coalesceTimePos(data)
            return
        }
        emitPropertyChange(name, data)
    }

    @Synchronized
    override fun onEvent(name: String, reason: String?) {
        val event = JSONObject().put("event", name)
        if (reason != null) event.put("reason", reason)
        emit(event)
    }

    private fun coalesceTimePos(data: Any?) {
        val elapsed = now() - lastTimePosAt
        if (elapsed >= TIME_POS_INTERVAL_MS) {
            lastTimePosAt = now()
            emitPropertyChange("time-pos", data)
            return
        }
        pendingTimePos = data
        if (!timePosFlushScheduled) {
            timePosFlushScheduled = true
            schedule(TIME_POS_INTERVAL_MS - elapsed) { flushTimePos() }
        }
    }

    @Synchronized
    private fun flushTimePos() {
        timePosFlushScheduled = false
        val data = pendingTimePos ?: return
        pendingTimePos = null
        lastTimePosAt = now()
        emitPropertyChange("time-pos", data)
    }

    private fun emitPropertyChange(name: String, data: Any?) {
        val event = JSONObject().put("event", "property-change").put("name", name)
        ids[name]?.let { event.put("id", it) }
        if (data != null) event.put("data", data)
        emit(event)
    }

    private fun emit(frame: JSONObject) {
        sink?.invoke(frame.toString())
    }

    companion object {
        const val TIME_POS_INTERVAL_MS = 1000L

        /** For tests: a JSON array from values. */
        fun array(vararg values: Any?): JSONArray = JSONArray(values.toList())
    }
}
