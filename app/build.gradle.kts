import java.util.Properties

// Security Isolation: Safely grabs your personal NVD API key from a local untracked file.
// This prevents Git from accidentally pushing your private secrets up to public GitHub repositories.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}
val myNvdKey = localProperties.getProperty("nvd.api.key") ?: ""

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.owasp.dependencycheck)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.fluid.dropx"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fluid.dropx"
        minSdk = 29 // Crucial baseline: Dropping below API 29 breaks ThumbnailManager's loadThumbnail() calls
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // Optimization Engine: R8 minification strips dead code, renames long paths, and packs
            // resource assets tightly to reach that insanely optimized 4.89 MB compile footprint.
            isMinifyEnabled = true
            isShrinkResources = true

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

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Jetpack Compose Core Framework Dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Core Layout Utility Support
    implementation(libs.androidx.documentfile)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Embedded Ktor High-Performance Networking Stack
    implementation(libs.ktor.http)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.conditional.headers)

    // Hardware Engine QR Generator Utility
    implementation(libs.zxing.core)
}

// OWASP Security Analyzer Rule Matrix
dependencyCheck {
    // Fails the entire project compilation process immediately if any dependency contains
    // an open vulnerability flag carrying a CVSS severity score of 7.0 (High Severity) or higher.
    failBuildOnCVSS = 7.0f
    format = "HTML"
    nvd {
        apiKey = myNvdKey
    }
    suppressionFile = "project-suppressions.xml" // Ignores known non-applicable false-positives
}