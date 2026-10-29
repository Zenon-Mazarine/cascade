plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the run number so every build has a higher versionCode than the last.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
// Screenshot automation only: lets adb pick a style/theme and skips onboarding.
val screenshotMode = (findProperty("screenshotMode") as String?) == "true"

// The Play upload key comes from CI secrets and is never committed.
val uploadStore = System.getenv("CASCADE_UPLOAD_STORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
val uploadPassword = System.getenv("CASCADE_UPLOAD_PASSWORD")

android {
    namespace = "com.bidule.cascade"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bidule.cascade"
        minSdk = 26
        targetSdk = 36
        versionCode = 100 + buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("boolean", "SCREENSHOT_MODE", screenshotMode.toString())
        buildConfigField("boolean", "ALL_UNLOCKED", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Public key in this repo, only for the APK you install directly (not Play).
        create("sideload") {
            storeFile = file("sideload.keystore")
            storePassword = "cascade123"
            keyAlias = "cascade"
            keyPassword = "cascade123"
        }
        if (uploadStore != null && uploadPassword != null) {
            create("upload") {
                storeFile = uploadStore
                storePassword = uploadPassword
                keyAlias = "upload"
                keyPassword = uploadPassword
            }
        }
    }

    buildTypes {
        // What goes to Google Play: shrunk, signed with the upload key.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("sideload")
        }
        // The APK at the GitHub "latest" link: same code, sideload key, everything unlocked
        // (Play Billing can't run outside the Play Store, and this is the developer's own copy).
        create("sideload") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("sideload")
            buildConfigField("boolean", "ALL_UNLOCKED", "true")
            matchingFallbacks += listOf("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("sideload")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.android.billingclient:billing:8.3.0")

    // Frame-by-frame capture of the real UI for the ads (tools/footage.sh).
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
}
