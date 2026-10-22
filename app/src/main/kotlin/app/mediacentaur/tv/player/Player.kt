package app.mediacentaur.tv.player

import android.content.Context
import android.util.Log as AndroidLog
import android.view.SurfaceHolder
import android.view.SurfaceView
import app.mediacentaur.tv.Log
import dev.jdtech.mpv.MPVLib
import java.io.File

/**
 * The one libmpv instance for the app's life, on a surface that stays
 * attached. Instance options are the TV's (decode and output paths, the
 * cache, the bundled scripts and `input.conf` copied from assets); session
 * options arrive from the host over the channel. [viewing] is true from a
 * file's start until mpv is idle again, which is when the shell shows the
 * page; a stop requested here (Back) or by the host (`quit`) ends with
 * reason `stop`, which the binding does not forward and [Player] supplies.
 */
class Player(
    context: Context,
    surfaceView: SurfaceView,
    private val onViewingChanged: (Boolean) -> Unit,
) : Mpv, SurfaceHolder.Callback, MPVLib.EventObserver, MPVLib.LogObserver {
    private val lib: MPVLib = MPVLib.create(context) ?: error("libmpv could not be loaded")
    override var listener: Mpv.Listener? = null

    @Volatile var viewing: Boolean = false
        private set

    @Volatile private var stopRequested = false

    init {
        val mpvDir = Assets.sync(context, "mpv")
        for ((name, value) in instanceOptions(mpvDir)) lib.setOptionString(name, value)
        lib.addObserver(this)
        lib.addLogObserver(this)
        lib.init()
        lib.observeProperty("idle-active", Mpv.Format.FLAG)
        surfaceView.holder.addCallback(this)
        Log.i("player: libmpv ${lib.getPropertyString("mpv-version")}")
    }

    private fun instanceOptions(mpvDir: File): List<Pair<String, String>> = listOf(
        "config" to "no",
        "idle" to "yes",
        "force-window" to "no",
        "vo" to "gpu",
        "gpu-context" to "android",
        "opengl-es" to "yes",
        "hwdec" to "mediacodec,mediacodec-copy",
        "hwdec-codecs" to "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1",
        "ao" to "audiotrack,opensles",
        "input-default-bindings" to "no",
        "input-conf" to File(mpvDir, "input.conf").path,
        "script" to File(mpvDir, "scripts/media-centaur").path,
        "cache" to "yes",
        "demuxer-max-bytes" to (150L * 1024 * 1024).toString(),
        "demuxer-readahead-secs" to "20",
    )

    // --- Mpv ---

    override fun command(args: List<String>) {
        if (args.firstOrNull() == "loadfile") stopRequested = false
        lib.command(args.toTypedArray())
    }

    override fun getString(name: String): String? = lib.getPropertyString(name)
    override fun getDouble(name: String): Double? = lib.getPropertyDouble(name)
    override fun getLong(name: String): Long? = lib.getPropertyInt(name)?.toLong()
    override fun getBoolean(name: String): Boolean? = lib.getPropertyBoolean(name)
    override fun setString(name: String, value: String) = lib.setPropertyString(name, value)
    override fun observe(name: String, format: Int) = lib.observeProperty(name, format)

    override fun stop() {
        stopRequested = true
        lib.command(arrayOf("stop"))
    }

    fun keypress(name: String) = lib.command(arrayOf("keypress", name))

    fun destroy() {
        lib.removeObserver(this)
        lib.removeLogObserver(this)
        lib.destroy()
    }

    // --- the surface ---

    override fun surfaceCreated(holder: SurfaceHolder) {
        lib.attachSurface(holder.surface)
        lib.setOptionString("force-window", "yes")
        lib.setPropertyString("vo", "gpu")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        lib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        lib.setPropertyString("vo", "null")
        lib.setOptionString("force-window", "no")
        lib.detachSurface()
    }

    // --- events from libmpv ---

    override fun eventProperty(property: String) = forward(property, null)
    override fun eventProperty(property: String, value: Long) = forward(property, value)
    override fun eventProperty(property: String, value: Double) = forward(property, value)
    override fun eventProperty(property: String, value: String) = forward(property, value)

    override fun eventProperty(property: String, value: Boolean) {
        if (property == "idle-active") {
            if (value && viewing) setViewing(false)
            return
        }
        forward(property, value)
    }

    private fun forward(name: String, value: Any?) {
        listener?.onProperty(name, value)
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> { setViewing(true); listener?.onEvent("start-file", null) }
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> listener?.onEvent("end-file", endReason())
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> listener?.onEvent("file-loaded", null)
            MPVLib.MpvEvent.MPV_EVENT_SEEK -> listener?.onEvent("seek", null)
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> listener?.onEvent("playback-restart", null)
            MPVLib.MpvEvent.MPV_EVENT_SHUTDOWN -> listener?.onEvent("shutdown", null)
        }
    }

    private fun endReason(): String = when {
        stopRequested -> "stop"
        lib.getPropertyBoolean("eof-reached") == true -> "eof"
        else -> "error"
    }

    private fun setViewing(value: Boolean) {
        if (viewing == value) return
        viewing = value
        Log.i(if (value) "viewing started" else "viewing ended")
        onViewingChanged(value)
    }

    // The binding already logs every mpv message under `mpv` at verbose; this
    // repeats only warnings and errors at their own priority so they stand out.
    override fun logMessage(prefix: String, level: Int, text: String) {
        val priority = when {
            level <= MPVLib.MpvLogLevel.MPV_LOG_LEVEL_ERROR -> AndroidLog.ERROR
            level <= MPVLib.MpvLogLevel.MPV_LOG_LEVEL_WARN -> AndroidLog.WARN
            else -> return
        }
        AndroidLog.println(priority, Log.MPV_TAG, "[$prefix] ${text.trimEnd()}")
    }
}
