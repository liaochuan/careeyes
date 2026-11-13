import java.io.File
import java.util.Properties
import org.gradle.api.tasks.Copy

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.reader(Charsets.UTF_8).use { load(it) }
    }
}

fun localValue(key: String): String? =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(key)?.takeIf { it.isNotBlank() }

val signingStorePath = localValue("signing.storeFile")
val signingStorePasswordValue = localValue("signing.storePassword")
val signingKeyAliasValue = localValue("signing.keyAlias")
val signingKeyPasswordValue = localValue("signing.keyPassword")
val signingStoreTypeValue = localValue("signing.storeType") ?: "PKCS12"
val releaseSigningReady = signingStorePath != null &&
    signingStorePasswordValue != null &&
    signingKeyAliasValue != null &&
    signingKeyPasswordValue != null &&
    File(signingStorePath).exists()

if (!releaseSigningReady) {
    logger.warn("Release signing is not configured; release builds will be unsigned.")
}

// Release builds default to the most common phone ABI. Override with -Pabi=arm64-v8a,x86_64.
val requestedAbis = (providers.gradleProperty("abi").orNull ?: "arm64-v8a")
    .split(',', ' ')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .distinct()
require(requestedAbis.isNotEmpty()) { "No ABI specified. Use -Pabi=arm64-v8a or -Pabi=arm64-v8a,x86_64." }

val appVersionCode = 1
val appVersionNameValue = "1.0.0"

android {
    namespace = "org.liaocl.careeyes"
    compileSdk = 37
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "org.liaocl.careeyes"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionNameValue
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = File(requireNotNull(signingStorePath))
                storePassword = requireNotNull(signingStorePasswordValue)
                keyAlias = requireNotNull(signingKeyAliasValue)
                keyPassword = requireNotNull(signingKeyPasswordValue)
                storeType = signingStoreTypeValue
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            ndk {
                abiFilters += requestedAbis
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }
}

val distDir = rootProject.layout.projectDirectory.dir("dist")
val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
val distApkName = "careeyes-$appVersionNameValue-$appVersionCode-${requestedAbis.joinToString("-")}.apk"

tasks.register<Copy>("distRelease") {
    group = "build"
    description = "Build the signed release APK and copy it to dist/."
    dependsOn("assembleRelease")
    doFirst {
        require(releaseApk.get().asFile.exists()) {
            "Signed release APK was not produced. Check the release signing configuration in local.properties."
        }
    }
    from(releaseApk)
    into(distDir)
    rename { distApkName }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
