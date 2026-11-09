import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
// Firebase (sign-in, cloud sync, push) switches on once app/google-services.json is there; without it the app
// still builds and runs, simply without accounts.
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.example.harikathamruthasara"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // PERMANENT on Google Play (changed to com.prathosh.hks for Prathosh's Play account, 2026-11-09) — never change after the first release.
        // (namespace above is only the code package and may stay com.example…)
        applicationId = "com.prathosh.hks"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Where the text + chant (source of truth) are published: the Hugging Face dataset the server mirrors to.
        // catalog.json lives here; file urls in it are relative to it. For local testing, set Settings → Source of
        // truth to the Mac's server: http://10.0.2.2:8765/files (10.0.2.2 = this Mac, from the emulator).
        buildConfigField("String", "SERVER_URL", "\"https://huggingface.co/datasets/ssurya1992/harikathamrutha-vagdhenu/resolve/main/hks\"")
    }

    // Play Store upload key: kept OUTSIDE the project, never in the code. ~/AndroidKeys/hks-upload.properties holds
    // storeFile, storePassword, keyAlias, keyPassword (made with the commands in RELEASE.md). Without that file the
    // release build is simply unsigned (debug builds don't need it).
    val uploadProps = Properties().apply {
        val f = File(System.getProperty("user.home"), "AndroidKeys/hks-upload.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (uploadProps.getProperty("storeFile") != null) create("upload") {
            storeFile = File(uploadProps.getProperty("storeFile"))
            storePassword = uploadProps.getProperty("storePassword")
            keyAlias = uploadProps.getProperty("keyAlias")
            keyPassword = uploadProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("upload")?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
                keepRules { files.add(file("keep-rules.pro")) }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    // the "ear": on-device Kannada letter model for Recite & check (speech/Ear.kt)
    implementation(libs.onnxruntime.android)
    // accounts, cloud sync, push
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play)
    // background nudges (streak, updates) and knowing when the app is in the background
    implementation(libs.androidx.work)
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
