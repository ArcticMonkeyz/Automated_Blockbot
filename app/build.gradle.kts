plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.rockblockbot"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.rockblockbot"
        minSdk = 29
        targetSdk = 34
        versionCode = 11
        versionName = "0.2.9"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
