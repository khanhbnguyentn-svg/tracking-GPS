import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val releaseRequested = gradle.startParameter.taskNames.any { taskName ->
    val simpleTaskName = taskName.substringAfterLast(':').lowercase()
    simpleTaskName.contains("release") || simpleTaskName in setOf("assemble", "build", "bundle")
}

val signingPropertiesFile = providers.environmentVariable("TRACKER_SIGNING_PROPERTIES")
    .orNull
    ?.takeIf { it.isNotBlank() }
    ?.let(rootProject::file)
    ?: rootProject.file(".signing/signing.properties")
val releaseSigning = Properties()
val releaseSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
if (releaseRequested) {
    require(signingPropertiesFile.isFile) {
        "Release signing properties not found: ${signingPropertiesFile.absolutePath}"
    }
    signingPropertiesFile.inputStream().use(releaseSigning::load)
    releaseSigningKeys.forEach { key ->
        require(!releaseSigning.getProperty(key).isNullOrBlank()) {
            "Release signing property missing: $key"
        }
    }
    require(rootProject.file(releaseSigning.getProperty("storeFile")).isFile) {
        "Release signing keystore not found."
    }
}

android {
    namespace = "com.internal.tracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.internal.tracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "3.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += setOf(
        "META-INF/NOTICE.md",
        "META-INF/LICENSE.md",
    )

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions.jvmTarget = "17"

    val stableReleaseSigning = if (releaseRequested) {
        signingConfigs.create("stableRelease") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = stableReleaseSigning
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.security)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
}
