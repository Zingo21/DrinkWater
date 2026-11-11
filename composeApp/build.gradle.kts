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
    
    listOf(
        iosX64(),
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

val devServerUrl: String = providers.gradleProperty("drinkwater.serverUrl").orNull
    ?: rootProject.file("local.properties").takeIf { it.exists() }
        ?.let { file -> Properties().apply { file.inputStream().use(::load) }.getProperty("drinkwater.serverUrl") }
    ?: "http://10.0.2.2:8080"

android {
    namespace = "org.erbeenjoyers.drinkwater"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.erbeenjoyers.drinkwater"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // The server debug builds start on; release builds always use RELEASE_SERVER_URL in
        // ServerConfig.kt. 10.0.2.2 is the emulator's alias for the host machine. For a real device,
        // set drinkwater.serverUrl=http://<your-ip>:8080 in local.properties or pass it with -P.
        buildConfigField("String", "DEV_SERVER_URL", "\"$devServerUrl\"")
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
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

