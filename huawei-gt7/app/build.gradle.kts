plugins { id("com.android.application") }
android {
    namespace = "com.pulselab.huaweigt7"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pulselab.huaweigt7"
        minSdk = 29
        targetSdk = 35
        versionCode = 20
        versionName = "2.0.0-fr"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation("com.garmin:fit:21.218.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.work:work-runtime:2.10.1")
}
