import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
    id("com.google.devtools.ksp")
}

// Get git commit hash
fun getGitHash(): String {
    return try {
        val process = Runtime.getRuntime().exec("git rev-parse --short HEAD")
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

// Get build timestamp
fun getBuildTime(): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    return dateFormat.format(Date())
}

android {
    namespace = "com.viiibe.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.viiibe.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // OAuth redirect scheme for AppAuth
        manifestPlaceholders["appAuthRedirectScheme"] = "com.viiibe.app"

        // Build info fields available via BuildConfig
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime()}\"")
        buildConfigField("String", "GIT_HASH", "\"${getGitHash()}\"")
    }

    // Release signing configuration
    // For local builds: Create a signing.properties file (git-ignored) with:
    //   STORE_FILE=path/to/your/keystore.jks
    //   STORE_PASSWORD=your_store_password
    //   KEY_ALIAS=your_key_alias
    //   KEY_PASSWORD=your_key_password
    //
    // For CI/CD: Set environment variables or use Gradle properties
    // NEVER commit actual credentials to version control!
    signingConfigs {
        create("release") {
            val signingPropsFile = rootProject.file("signing.properties")
            if (signingPropsFile.exists()) {
                val signingProps = Properties()
                signingPropsFile.inputStream().use { signingProps.load(it) }
                storeFile = file(signingProps.getProperty("STORE_FILE"))
                storePassword = signingProps.getProperty("STORE_PASSWORD")
                keyAlias = signingProps.getProperty("KEY_ALIAS")
                keyPassword = signingProps.getProperty("KEY_PASSWORD")
            } else {
                // Fall back to environment variables (for CI/CD)
                storeFile = file(System.getenv("STORE_FILE") ?: "release.keystore")
                storePassword = System.getenv("STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://viiibe-backend-production.up.railway.app\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BACKEND_BASE_URL", "\"https://viiibe-backend-production.up.railway.app\"")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DISCLAIMER"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Media3 ExoPlayer for video playback
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Google Maps for Compose
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // P2P Multiplayer - Nearby Connections
    implementation("com.google.android.gms:play-services-nearby:19.1.0")

    // Biometric authentication for wallet security
    implementation("androidx.biometric:biometric:1.1.0")

    // OkHttp for API requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Charts
    implementation("com.patrykandpatrick.vico:compose-m3:2.0.0-alpha.19")

    // Web3/Blockchain (Android-compatible)
    implementation("org.web3j:core:4.8.8-android")

    // BouncyCastle for Android crypto
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Security for wallet key storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // QR Code generation
    implementation("com.google.zxing:core:3.5.2")

    // AppAuth for OAuth 2.0 PKCE flow (X/Twitter authentication)
    implementation("net.openid:appauth:0.11.1")

    // Browser for Chrome Custom Tabs (OAuth flow)
    implementation("androidx.browser:browser:1.8.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
