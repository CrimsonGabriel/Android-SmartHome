plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.bazunia"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bazunia"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildToolsVersion = "35.0.0"
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- KOMUNIKACJA SIECIOWA (REST/HTTP) ---
    // OkHttp (do rejestracji IP na VPS i pobierania publicznego IP)
    implementation(libs.okhttp.v4120)

    // NanoHTTPD (do odbierania powiadomien na porcie 3000)
    implementation(libs.nanohttpd)

    // org.json (do obslugi JSON, jesli nie jest juz dostepny przez Android SDK/libs.json)
    implementation(libs.json.v20240303)

    // --- ZALEŻNOŚCI TESTOWE ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}