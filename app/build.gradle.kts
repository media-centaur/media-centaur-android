plugins {
    alias(libs.plugins.android.application)
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

dependencies {
    implementation(libs.libmpv)
    implementation(libs.okhttp)
}
