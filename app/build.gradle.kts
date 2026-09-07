plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.zvonilka.prototype"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.zvonilka.prototype"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1-dialer"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
