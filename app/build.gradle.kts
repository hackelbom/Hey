plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}
android {
    namespace = "com.sms.sender"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.sms.sender"
        minSdk = 23
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}
dependencies {
    implementation("com.google.firebase:firebase-database-ktx:21.0.0")
}
