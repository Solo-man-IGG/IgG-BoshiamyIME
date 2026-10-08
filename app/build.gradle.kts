plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tw.igg.boshiamyime"
    compileSdk = 35

    defaultConfig {
        applicationId = "tw.igg.boshiamyime"
        minSdk = 24
        targetSdk = 35
        versionCode = 29
        versionName = "1.1.1"
    }

    buildTypes {
        release {
            // R8 收縮（F-Droid 審查者 linsui 要求 2026-10-08）。
            // proguard-rules.pro 已有 -keep class tw.igg.boshiamyime.** { *; }，
            // 所以收縮的只有函式庫程式碼、自家程式碼不受混淆，風險可控。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
