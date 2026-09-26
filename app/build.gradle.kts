import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Configuration is read from environment variables first, then from the
 * git-ignored `local.properties` / `keystore.properties` files. Nothing secret
 * is committed to the repository.
 */
fun loadProperties(fileName: String): Properties = Properties().apply {
    val file = rootProject.file(fileName)
    if (file.exists()) file.inputStream().use { load(it) }
}

val localProperties = loadProperties("local.properties")
val keystoreProperties = loadProperties("keystore.properties")

fun config(name: String, source: Properties = localProperties): String =
    (System.getenv(name) ?: source.getProperty(name) ?: "").trim()

// Client-safe values only: the project URL and the anon/publishable key.
val supabaseUrl = config("SUPABASE_URL")
val supabaseAnonKey = config("SUPABASE_ANON_KEY")

// Firebase client configuration for push (FCM). All four are client-safe
// identifiers; without them the app builds and push stays off.
val firebaseProjectId = config("FIREBASE_PROJECT_ID")
val firebaseAppId = config("FIREBASE_APP_ID")
val firebaseApiKey = config("FIREBASE_API_KEY")
val firebaseSenderId = config("FIREBASE_SENDER_ID")

val releaseStoreFile = config("KAMPUSAGI_KEYSTORE_FILE", keystoreProperties)
val hasReleaseSigning = releaseStoreFile.isNotEmpty() && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.kampusagi.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kampusagi.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"$firebaseProjectId\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"$firebaseAppId\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"$firebaseApiKey\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"$firebaseSenderId\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = config("KAMPUSAGI_KEYSTORE_PASSWORD", keystoreProperties)
                keyAlias = config("KAMPUSAGI_KEY_ALIAS", keystoreProperties)
                keyPassword = config("KAMPUSAGI_KEY_PASSWORD", keystoreProperties)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/9/previous-compilation-data.bin"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.billing.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
