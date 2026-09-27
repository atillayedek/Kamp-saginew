import java.util.Base64
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

/**
 * The key is compiled into the APK, so it must be the client key: a
 * publishable key or a legacy JWT whose role is "anon". A secret or
 * service_role key would bypass row level security for anyone holding the app.
 */
fun isClientKey(key: String): Boolean {
    if (key.startsWith("sb_publishable_")) return true
    if (key.startsWith("sb_secret_")) return false
    val payload = key.split(".").getOrNull(1) ?: return false
    val claims = try {
        String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
    } catch (e: IllegalArgumentException) {
        return false
    }
    return Regex("\"role\"\\s*:\\s*\"anon\"").containsMatchIn(claims)
}

if (supabaseAnonKey.isNotEmpty() && !isClientKey(supabaseAnonKey)) {
    throw GradleException(
        "SUPABASE_ANON_KEY is not a client key. Use the publishable key (sb_publishable_...) or the legacy " +
            "anon key from Supabase Dashboard -> Project Settings -> API Keys. Never build with the secret or " +
            "service_role key: it is compiled into the APK and bypasses row level security.",
    )
}

val privacyPolicyUrl = config("PRIVACY_POLICY_URL")

val releaseStoreFile = config("KAMPUSAGI_KEYSTORE_FILE", keystoreProperties)
val hasReleaseSigning = releaseStoreFile.isNotEmpty() && rootProject.file(releaseStoreFile).exists()

android {
    namespace = "com.kampusagi.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kampusagi.android"
        minSdk = 26
        targetSdk = 36
        // CI passes an increasing number (the workflow run number); local builds use 1.
        versionCode = config("KAMPUSAGI_VERSION_CODE").toIntOrNull() ?: 1
        versionName = "1.0.0"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")
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
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
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

    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
