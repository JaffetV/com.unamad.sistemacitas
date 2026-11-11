plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Acepta -PmarketplaceApiBaseUrl=... o MARKETPLACE_API_BASE_URL en el entorno.
val marketplaceApiBaseUrl = providers.gradleProperty("marketplaceApiBaseUrl")
    .orElse(providers.environmentVariable("MARKETPLACE_API_BASE_URL"))

fun String.asBuildConfigString(): String = "\"" +
    replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.comunamadsistemacitas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.comunamadsistemacitas"
        minSdk = 24
        targetSdk = 35
        versionCode = 8
        versionName = "1.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["cleartextAllowed"] = "false"
    }

    buildTypes {
        debug {
            // Android Emulator -> PC anfitrión. En teléfono físico configura la IP LAN.
            val debugApiUrl = marketplaceApiBaseUrl.orElse("http://10.0.2.2:8080").get()
            buildConfigField("String", "MARKETPLACE_API_BASE_URL", debugApiUrl.asBuildConfigString())
            manifestPlaceholders["cleartextAllowed"] = "true"
        }
        release {
            val releaseApiUrl = marketplaceApiBaseUrl.orNull?.trim()
                ?.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?: "https://configure-api-url-before-release.invalid"
            buildConfigField("String", "MARKETPLACE_API_BASE_URL", releaseApiUrl.asBuildConfigString())
            manifestPlaceholders["cleartextAllowed"] = "false"
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
}

// No permitas generar un APK release que apunte a localhost, HTTP o un host de ejemplo.
tasks.configureEach {
    if (name == "preReleaseBuild") {
        doFirst {
            val configuredUrl = marketplaceApiBaseUrl.orNull?.trim().orEmpty()
            check(configuredUrl.startsWith("https://", ignoreCase = true)) {
                "Para compilar release, define -PmarketplaceApiBaseUrl=https://tu-api-real o MARKETPLACE_API_BASE_URL con una URL HTTPS real."
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
}
