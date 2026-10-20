plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.minitube.downloader"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.minitube.downloader"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "1.1-lite"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true } }
}
dependencies {
    val ytdlp = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdlp")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdlp")
}
