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

// The one version source. A release is the tag `v<versionName>`; CI checks the
// two agree. `versionCode` is derived so an upgrade install always climbs and
// nobody keeps two fields in step: 0.1.0 → 100, 1.2.3 → 10203.
val appVersionName = "0.1.0"

fun versionCodeFrom(name: String): Int {
    val (major, minor, patch) = name.split(".").map(String::toInt)
    return major * 10_000 + minor * 100 + patch
}

// Signing is a property of the environment, not a second build type: with the
// four MC_ANDROID_* variables set (CI reads them from repository secrets) the
// release build is signed with that keystore; without them `assembleRelease`
// yields an unsigned APK, which is what a contributor's machine should produce.
val releaseKeystore: String? = System.getenv("MC_ANDROID_KEYSTORE")

base {
    archivesName = "media-centaur-tv-$appVersionName"
}

android {
    namespace = "app.mediacentaur.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.mediacentaur.tv"
        minSdk = 26
        targetSdk = 36
        versionName = appVersionName
        versionCode = versionCodeFrom(appVersionName)
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("MC_ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MC_ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("MC_ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.libmpv)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
