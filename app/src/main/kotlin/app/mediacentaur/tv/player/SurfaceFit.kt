package app.mediacentaur.tv.player

import kotlin.math.roundToInt

/**
 * The video surface's size on the native path. `vo=mediacodec_embed` hands
 * the decoder's frames to the surface as they are and the compositor scales
 * them to the surface's bounds, so a surface the size of the panel stretches
 * any picture that is not the panel's shape: a 4:3 episode filled a 16:9
 * screen (the owner's walk, 2026-10-24). The surface is therefore sized to
 * the video's display aspect ratio, as large as fits, centred: bars at the
 * sides for 4:3, above and below for a film wider than the panel.
 */
object SurfaceFit {
    /**
     * Width and height for a picture of [aspect] (display width over height,
     * mpv's `video-params/aspect`) inside a [width]×[height] parent. An
     * unusable aspect or an unlaid-out parent gives the parent's own size.
     */
    fun size(aspect: Double, width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || aspect.isNaN() || aspect <= 0.0) return width to height
        val fitHeight = (width / aspect).roundToInt()
        return if (fitHeight <= height) width to fitHeight else (height * aspect).roundToInt() to height
    }
}
