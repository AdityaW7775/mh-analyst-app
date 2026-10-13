plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing: GitHub (or your PC) provides the MH Analyst key through these environment variables.
val keystorePath: String? = System.getenv("MH_KEYSTORE")
val keystorePassword: String? = System.getenv("MH_STORE_PASSWORD")

android {
    namespace = "com.thepagination.mhanalyst"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.thepagination.mhanalyst"   // same as the current app, so it installs over it
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "2.0"
        buildConfigField("String", "BASE_URL", "\"https://mahalaxmi.thepagination.com/\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "mhanalyst"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
