plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.example.gjstore"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.gjstore"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
    }
}

// Automatically copy the APK to the root 'release' folder after build
tasks.register<Copy>("copyApkToRelease") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(layout.projectDirectory.dir("../release"))
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyApkToRelease")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.7.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Retrofit for Google Sheets API communication
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    debugImplementation(libs.okhttp.logging)

    // Lifecycle coroutines
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}