import java.util.Base64
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val epicAuthBackendUrl = providers.environmentVariable("EPIC_AUTH_BACKEND_URL")
    .orElse(providers.gradleProperty("EPIC_AUTH_BACKEND_URL"))
    .orElse("")
    .get()

val supabaseUrl = providers.environmentVariable("SUPABASE_URL")
    .orElse(providers.gradleProperty("SUPABASE_URL"))
    .orElse("")
    .get()

val supabasePublishableKey = providers.environmentVariable("SUPABASE_PUBLISHABLE_KEY")
    .orElse(providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY"))
    .orElse("")
    .get()

val sentryDsn = providers.environmentVariable("SENTRY_DSN")
    .orElse(providers.gradleProperty("SENTRY_DSN"))
    .orElse("")
    .get()

val releaseKeystorePath = providers.environmentVariable("GAMEHUB_RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("GAMEHUB_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("GAMEHUB_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("GAMEHUB_RELEASE_KEY_PASSWORD").orNull

fun quotedBuildConfig(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

val exactLauncherIconSource = layout.projectDirectory.file("src/main/icon/gamehub_ultra_exact.webp.b64")
val exactLauncherIconResDir = layout.buildDirectory.dir("generated/exactLauncherIcon/res")

val generateExactLauncherIcon by tasks.registering {
    inputs.file(exactLauncherIconSource)
    outputs.dir(exactLauncherIconResDir)

    doLast {
        val drawableDir = exactLauncherIconResDir.get().dir("drawable-nodpi").asFile
        drawableDir.mkdirs()
        val encoded = exactLauncherIconSource.asFile.readText().trim()
        drawableDir.resolve("gamehub_ultra_exact.webp").writeBytes(
            Base64.getDecoder().decode(encoded)
        )
    }
}

android {
    namespace = "com.cardenaspiero255.gamehubultra"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cardenaspiero255.gamehubultra"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
        buildConfigField(
            "String",
            "EPIC_AUTH_BACKEND_URL",
            quotedBuildConfig(epicAuthBackendUrl)
        )
        buildConfigField(
            "String",
            "SUPABASE_URL",
            quotedBuildConfig(supabaseUrl)
        )
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            quotedBuildConfig(supabasePublishableKey)
        )
        buildConfigField(
            "String",
            "SENTRY_DSN",
            quotedBuildConfig(sentryDsn)
        )
    }

    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            require(!releaseStorePassword.isNullOrBlank()) {
                "GAMEHUB_RELEASE_STORE_PASSWORD is required"
            }
            require(!releaseKeyAlias.isNullOrBlank()) {
                "GAMEHUB_RELEASE_KEY_ALIAS is required"
            }
            require(!releaseKeyPassword.isNullOrBlank()) {
                "GAMEHUB_RELEASE_KEY_PASSWORD is required"
            }
            create("secureRelease") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            signingConfig = signingConfigs.findByName("secureRelease")
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

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        getByName("main").res.srcDir(exactLauncherIconResDir)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateExactLauncherIcon)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    implementation("io.sentry:sentry-android:8.56.0")
    baselineProfile(project(":baseline-profile"))
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.21")
}
