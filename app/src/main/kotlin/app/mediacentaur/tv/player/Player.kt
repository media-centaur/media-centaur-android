package app.mediacentaur.tv.player

import android.content.Context
import android.util.Log as AndroidLog
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import app.mediacentaur.tv.Log
import dev.jdtech.mpv.MPVLib

/**
 * The one libmpv instance for the app's life, on a surface that stays
 * attached. Instance options are the TV's: the native output path
 * (`vo=mediacodec_embed` — the decoder writes to the surface, the panel
 * gets native resolution and HDR10, and mpv draws nothing; the host's page
 * over the surface is the OSD), the decoder, the cache. Every per-viewing
 * option arrives from the host on the `loadfile` itself, so nothing here
 * carries over between viewings. [viewing] is true from a file's start
 * until mpv is idle again; a stop asked for by the host (`quit`) ends with
 * reason `stop`, which the binding does not forward and [Player] supplies.
 * No key reaches mpv: the remote's keys go to the page.
 *
 * The surface takes the picture's shape: the player observes
 * `video-params/aspect` for itself and sizes the surface to it within its
 * parent ([SurfaceFit]), since on the native path the compositor scales the
 * frames to the surface's bounds. That property is the player's own, like
 * `idle-active`, and never reaches the host.
 */
class Player(
    context: Context,
    private val surfaceView: SurfaceView,
    private val onViewingChanged: (Boolean) -> Unit,
) : Mpv, SurfaceHolder.Callback, MPVLib.EventObserver, MPVLib.LogObserver {
    private val lib: MPVLib = MPVLib.create(context) ?: error("libmpv could not be loaded")
    override var listener: Mpv.Listener? = null

    @Volatile var viewing: Boolean = false
        private set

    @Volatile private var stopRequested = false

    /** The playing picture's display aspect ratio; 0.0 until a video reports one. */
    @Volatile private var aspect = 0.0

    init {
        for ((name, value) in instanceOptions()) lib.setOptionString(name, value)
        lib.addObserver(this)
        lib.addLogObserver(this)
        lib.init()
        lib.observeProperty("idle-active", Mpv.Format.FLAG)
        lib.observeProperty(ASPECT, Mpv.Format.DOUBLE)
        surfaceView.holder.addCallback(this)
        (surfaceView.parent as View).addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) fitSurface()
        }
        Log.i("player: libmpv ${lib.getPropertyString("mpv-version")}")
    }

    private fun instanceOptions(): List<Pair<String, String>> = listOf(
        "config" to "no",
        "idle" to "yes",
        "force-window" to "no",
        "vo" to "mediacodec_embed",
        "hwdec" to "mediacodec",
        "hwdec-codecs" to "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1",
        "ao" to "audiotrack,opensles",
        "input-default-bindings" to "no",
        "cache" to "yes",
        "demuxer-max-bytes" to (150L * 1024 * 1024).toString(),
        "demuxer-readahead-secs" to "20",
        // audiotrack underran once at every start with mpv's 0.2 s default
        // (the Sony, 2026-10-23); half a second starts clean.
        "audio-buffer" to "0.5",
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
    override fun setBoolean(name: String, value: Boolean) = lib.setPropertyBoolean(name, value)
    // The binding's integer setter takes an Int; mpv's int64 properties the host sets fit.
    override fun setLong(name: String, value: Long) = lib.setPropertyInt(name, value.toInt())
    override fun setDouble(name: String, value: Double) = lib.setPropertyDouble(name, value)
    override fun observe(name: String, format: Int) = lib.observeProperty(name, format)

    override fun stop() {
        stopRequested = true
        lib.command(arrayOf("stop"))
    }

    fun destroy() {
        lib.removeObserver(this)
        lib.removeLogObserver(this)
        lib.destroy()
    }

    // --- the surface ---

    override fun surfaceCreated(holder: SurfaceHolder) {
        lib.attachSurface(holder.surface)
        lib.setOptionString("force-window", "yes")
        lib.setPropertyString("vo", "mediacodec_embed")
    }

    // The native path reads no surface size: the decoder writes the video's
    // own size and the compositor scales it to the surface's bounds, which
    // fitSurface keeps in the picture's shape.
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    // Runs on the main thread (a property event arrives on libmpv's). The
    // parent is the full window; the surface is as large as the picture's
    // shape allows inside it, centred. Nothing changes when the size holds.
    private fun fitSurface() {
        surfaceView.post {
            val parent = surfaceView.parent as? View ?: return@post
            val (width, height) = SurfaceFit.size(aspect, parent.width, parent.height)
            val params = surfaceView.layoutParams as FrameLayout.LayoutParams
            if (params.width == width && params.height == height) return@post
            params.width = width
            params.height = height
            params.gravity = Gravity.CENTER
            surfaceView.layoutParams = params
            Log.i("player: surface ${width}x$height for aspect $aspect")
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        lib.setPropertyString("vo", "null")
        lib.setOptionString("force-window", "no")
        lib.detachSurface()
    }

    // --- events from libmpv ---

    override fun eventProperty(property: String) {
        // The aspect has no value between files; the surface keeps its shape
        // until the next picture reports one.
        if (property == ASPECT) return
        forward(property, null)
    }
    override fun eventProperty(property: String, value: Long) = forward(property, value)
    override fun eventProperty(property: String, value: Double) {
        if (property == ASPECT) {
            aspect = value
            fitSurface()
            return
        }
        forward(property, value)
    }
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

    // The binding drops mpv's reason. A stop asked for here or by the host is
    // `stop`; anything else is the file running out (`eof-reached` is not set
    // when mpv moves straight into the next playlist entry), and the host
    // treats every reason but stop/quit alike.
    private fun endReason(): String = if (stopRequested) "stop" else "eof"

    private companion object {
        const val ASPECT = "video-params/aspect"
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
