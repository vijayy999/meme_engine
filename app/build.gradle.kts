plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.meme_engine"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.meme_engine"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)

    // Room
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.room.testing)

    // Glide
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)

    implementation(libs.documentfile)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}