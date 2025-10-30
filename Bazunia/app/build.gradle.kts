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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // --- NOWA, POPRAWNA LINIA DLA GOOGLE AUTH ---
    implementation(libs.play.services.auth)
    implementation(libs.zxing.embedded)
    // --- KOMUNIKACJA SIECIOWA (REST/HTTP) ---
    implementation(libs.okhttp.v4120)
    implementation(libs.nanohttpd)
    implementation(libs.json.v20240303)

    // --- ZALEŻNOŚCI TESTOWE ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

