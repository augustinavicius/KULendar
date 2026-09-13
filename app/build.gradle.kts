import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: keystore.properties next to settings.gradle.kts locally, KULENDAR_* environment variables in CI.
// Without either, release builds are signed with the debug key.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

fun signingSetting(property: String, environmentVariable: String): String? =
    keystoreProperties?.getProperty(property) ?: providers.environmentVariable(environmentVariable).orNull

val releaseStoreFile = signingSetting("storeFile", "KULENDAR_KEYSTORE_FILE")

// Version, update channel and commit are set by the release workflow; local builds are version 1 of the "local" channel.
fun kulendarProperty(name: String): String? = providers.gradleProperty("kulendar.$name").orNull

if (kulendarProperty("requireReleaseSigning").toBoolean()) {
    check(releaseStoreFile != null) { "Release signing is required, but no keystore is configured" }
}

android {
    namespace = "io.github.augustinavicius.kulendar"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.augustinavicius.kulendar"
        minSdk = 26
        targetSdk = 37
        versionCode = kulendarProperty("versionCode")?.toInt() ?: 1
        versionName = kulendarProperty("versionName") ?: "${kulendarProperty("baseVersion")}.0-local"
        buildConfigField("String", "UPDATE_CHANNEL", "\"${kulendarProperty("channel") ?: "local"}\"")
        buildConfigField("String", "UPDATE_REPOSITORY", "\"${kulendarProperty("updateRepository")}\"")
        buildConfigField("String", "GIT_COMMIT", "\"${kulendarProperty("commit").orEmpty()}\"")
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingSetting("storePassword", "KULENDAR_KEYSTORE_PASSWORD")
                keyAlias = signingSetting("keyAlias", "KULENDAR_KEY_ALIAS")
                keyPassword = signingSetting("keyPassword", "KULENDAR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
