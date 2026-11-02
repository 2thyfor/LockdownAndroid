plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lockdown.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.lockdown.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("LOCKDOWN_KEYSTORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("LOCKDOWN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LOCKDOWN_KEY_ALIAS")
                keyPassword = System.getenv("LOCKDOWN_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // Debug builds use the stable release key when the workflow provides it.
            val storeFilePath = System.getenv("LOCKDOWN_KEYSTORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("release") { signingConfig = signingConfigs.getByName("release") }
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
