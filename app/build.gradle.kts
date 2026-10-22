import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
}

/**
 * The host's bundled mpv scripts (`priv/mpv/scripts/media-centaur`) run
 * unmodified inside the TV's libmpv. They have one source, the host
 * checkout named by `mediaCentaurHost` in gradle.properties, and are copied
 * into this build's assets rather than committed here.
 */
abstract class CopyBundledScripts : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDir.get().dir("mpv/scripts/media-centaur").asFile
        target.deleteRecursively()
        source.get().asFile.copyRecursively(target, overwrite = true)
    }
}

val copyBundledScripts by tasks.registering(CopyBundledScripts::class) {
    val host = providers.gradleProperty("mediaCentaurHost").get()
    source.set(rootProject.layout.projectDirectory.dir("$host/priv/mpv/scripts/media-centaur"))
    outputDir.set(layout.buildDirectory.dir("bundledScripts"))
}

android {
    namespace = "app.mediacentaur.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.mediacentaur.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyBundledScripts, CopyBundledScripts::outputDir)
    }
}

dependencies {
    implementation(libs.libmpv)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
