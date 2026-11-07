package app.mediacentaur.tv.player

/**
 * How a file's picture reaches the surface, chosen per file by what the
 * device can decode.
 *
 *  * [DIRECT] — the hardware decoder writes its frames straight to the
 *    surface (`vo=mediacodec_embed`, `hwdec=mediacodec`): the panel gets the
 *    native resolution and HDR10, and nothing is copied. Every file starts
 *    here.
 *  * [GPU] — mpv decodes in software and draws through OpenGL onto the same
 *    surface (`vo=gpu`): for a file the device has no hardware decoder for
 *    (AV1 on a phone without one, an MPEG-4 ASP release its decoder
 *    refuses). HDR is tone-mapped, not passed through.
 *
 * The move is decided by what mpv did, not by a list of codecs: a device
 * may carry a decoder that still fails a file. On the direct path a file the
 * hardware cannot decode falls to mpv's software decoder, whose frames the
 * direct output cannot take, and mpv gives up the video: it logs `Could not
 * initialize video chain` (fatal, from `cplayer`) and drops the track while
 * `vid` still names it. That message is the one signal of exactly this
 * failure ([isVideoChainFailure]); no property tells it apart from a file
 * being unloaded.
 */
enum class OutputPath(val vo: String, val hwdec: String) {
    DIRECT("mediacodec_embed", "mediacodec"),
    GPU("gpu", "no");

    /** The path to move to when the video chain fails on this one, or null when there is none left. */
    fun afterVideoChainFailure(): OutputPath? = if (this == DIRECT) GPU else null

    companion object {
        /** Whether an mpv log line is its report of a video chain that failed to initialise. */
        fun isVideoChainFailure(prefix: String, fatal: Boolean, text: String): Boolean =
            fatal && prefix == "cplayer" && text.startsWith("Could not initialize video chain")
    }
}
