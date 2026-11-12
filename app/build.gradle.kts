plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dk.galvo.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "dk.galvo.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 19
        versionName = "6.6"
    }
    // One fixed debug key for every build, so a new APK installs on top of the old one and keeps the user's progress.
    signingConfigs {
        getByName("debug") {
            storeFile = file("galvo-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
