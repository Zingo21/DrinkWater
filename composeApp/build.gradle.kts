import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Push notifications need the Firebase project's google-services.json in this directory.
// Without it the app builds and runs as usual, just without push.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    // No iosX64 (simulators on Intel Macs): Compose Multiplatform 1.12 no longer publishes it.
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // The web app, for iPhones without an Apple developer account: see README.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("composeApp")
        browser {
            commonWebpackConfig {
                outputFileName = "composeApp.js"
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            // Firebase pulls in Fragment 1.1, which breaks registerForActivityResult; it needs 1.3 or later.
            implementation(libs.androidx.fragment)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.security.crypto)
            implementation(libs.firebase.messaging)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.material.icons.core)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.multiplatform.settings)
            implementation(projects.shared)
        }
        wasmJsMain.dependencies {
            // Time zone rules for kotlinx-datetime; without them only UTC and fixed offsets work in the browser.
            implementation(npm("@js-joda/timezone", libs.versions.jsJodaTimezone.get()))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.multiplatform.settings.test)
        }
    }
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

/** A `-P` Gradle property, or else the same key in the untracked local.properties. */
fun localSetting(name: String): String? = providers.gradleProperty(name).orNull ?: localProperties.getProperty(name)

val devServerUrl: String = localSetting("drinkwater.serverUrl") ?: "http://10.0.2.2:8080"

// Where release builds connect, e.g. drinkwater.releaseServerUrl=https://drinkwater.example.com in local.properties.
// Set at build time so the domain stays out of the repository; release builds refuse to build without it.
val releaseServerUrl: String = localSetting("drinkwater.releaseServerUrl")?.trim()?.trimEnd('/').orEmpty()

// The app's version, e.g. 1.2.0: from drinkwater.version, which the release workflow sets from the release's tag
// (.github/workflows/release.yml), or else from the newest v1.2.0-style tag in this checkout. Null when neither
// gives one, e.g. in a shallow clone without tags.
val appVersion: String? = localSetting("drinkwater.version")?.removePrefix("v")
    ?: try {
        providers.exec {
            commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
            isIgnoreExitValue = true
        }.standardOutput.asText.get().trim().removePrefix("v").ifEmpty { null }
    } catch (e: Exception) {
        null
    }

// Android only installs an update with a higher versionCode, so it grows with the version: 1.2.3 is 10203.
// Minor and patch therefore stay below 100.
val appVersionCode: Int? = appVersion?.let { version ->
    val parts = Regex("""^(\d+)\.(\d{1,2})\.(\d{1,2})$""").matchEntire(version)?.destructured?.toList()
        ?: throw GradleException("drinkwater.version must look like 1.2.3, with minor and patch below 100 (got \"$version\").")
    parts[0].toInt() * 10_000 + parts[1].toInt() * 100 + parts[2].toInt()
}

// The release signing key, see "Signing release builds" in README.md. Without it release builds come out unsigned.
val releaseKeystore: String? = localSetting("drinkwater.signing.storeFile")

android {
    namespace = "org.erbeenjoyers.drinkwater"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.erbeenjoyers.drinkwater"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode ?: 1
        versionName = appVersion ?: "0.0.0-dev"

        // The server debug builds start on. 10.0.2.2 is the emulator's alias for the host machine. For a real
        // device, set drinkwater.serverUrl=http://<your-ip>:8080 in local.properties or pass it with -P.
        buildConfigField("String", "DEV_SERVER_URL", "\"$devServerUrl\"")
        buildConfigField("String", "RELEASE_SERVER_URL", "\"$releaseServerUrl\"")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = localSetting("drinkwater.signing.storePassword")
                keyAlias = localSetting("drinkwater.signing.keyAlias")
                keyPassword = localSetting("drinkwater.signing.keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.compose.ui.tooling)
}

// A release build that starts on no server is no use to the friends it is given to, so stop before building one.
val checkReleaseServerUrl by tasks.registering {
    val url = releaseServerUrl
    doLast {
        if (!Regex("^https://[a-z0-9.\\-\\[\\]:]+$", RegexOption.IGNORE_CASE).matches(url)) {
            throw GradleException(
                "Set drinkwater.releaseServerUrl to the server's https:// address, e.g. " +
                    "drinkwater.releaseServerUrl=https://drinkwater.example.com in local.properties " +
                    "(currently \"$url\"). See \"Server address\" in README.md.",
            )
        }
    }
}

// A signed APK is one meant for phones, and without a version it would be versionCode 1, which phones refuse to
// install over anything newer. Unsigned release builds, as in CI's build job, don't need one.
val checkReleaseVersion by tasks.registering {
    val signed = releaseKeystore != null
    val version = appVersion
    doLast {
        if (signed && version == null) {
            throw GradleException(
                "A signed release needs a version: set drinkwater.version=1.2.3, or build in a checkout with a " +
                    "v1.2.3 tag. Releases are normally built by GitHub, see \"Releases\" in README.md.",
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkReleaseServerUrl, checkReleaseVersion) }
