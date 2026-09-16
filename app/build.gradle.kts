import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.paparazzi")
}

// Clave de Gemini leída de local.properties (fuera del código fuente; vacía si no se ha puesto).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "org.ivansola.minutricion"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.ivansola.minutricion"
        minSdk = 24
        targetSdk = 34
        versionCode = 91
        versionName = "10.11"
        buildConfigField("String", "GEMINI_API_KEY",
            "\"${localProps.getProperty("gemini.api.key", "")}\"")
        // Clave de respaldo (otro proyecto de Google = otra cuota diaria); opcional.
        buildConfigField("String", "GEMINI_API_KEY_2",
            "\"${localProps.getProperty("gemini.api.key.2", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Firmado con la clave de debug para poder INSTALAR y probar el build rápido (R8)
            // en el móvil sin generar un keystore. Cambiar por una clave propia para publicar.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
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
        }
    }
    testOptions {
        // Paparazzi (layoutlib) necesita bastante memoria; sin esto el worker de test se cae.
        unitTests.all {
            it.maxHeapSize = "3g"
            it.maxParallelForks = 1
            it.jvmArgs("-XX:+UseParallelGC", "-Djava.awt.headless=true")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Cámara (CameraX) + escaneo de códigos de barras (ML Kit, modelo integrado, offline)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    testImplementation("junit:junit:4.13.2")
}
