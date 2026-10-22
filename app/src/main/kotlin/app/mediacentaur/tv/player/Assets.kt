package app.mediacentaur.tv.player

import android.content.Context
import java.io.File

/** Copies an assets directory into `filesDir` on every start, so libmpv gets real paths. */
object Assets {
    fun sync(context: Context, dir: String): File {
        val target = File(context.filesDir, dir)
        target.deleteRecursively()
        copy(context, dir, target)
        return target
    }

    private fun copy(context: Context, path: String, target: File) {
        val children = context.assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            context.assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            target.mkdirs()
            for (child in children) copy(context, "$path/$child", File(target, child))
        }
    }
}
