import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Signature : si un fichier keystore.properties (non versionné) existe à la racine, il est utilisé
// pour signer la version « release ». Sinon, on utilise la clé de partage du projet
// (keystore/remorquepro-partage.jks), afin que chaque APK compilé puisse être installé
// par-dessus le précédent sans perdre les données.
val proprietesSignature = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "ma.remorquepro.cac"
    compileSdk = 35

    defaultConfig {
        applicationId = "ma.remorquepro.cac"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("partage") {
            storeFile = rootProject.file("keystore/remorquepro-partage.jks")
            storePassword = "remorquepro"
            keyAlias = "remorquepro"
            keyPassword = "remorquepro"
        }
        if (proprietesSignature.isNotEmpty()) {
            create("privee") {
                storeFile = rootProject.file(proprietesSignature.getProperty("storeFile"))
                storePassword = proprietesSignature.getProperty("storePassword")
                keyAlias = proprietesSignature.getProperty("keyAlias")
                keyPassword = proprietesSignature.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("partage")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("privee") ?: signingConfigs.getByName("partage")
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Permet de pointer Robolectric vers un miroir Maven si besoin (-Probolectric.repo=…).
                (project.findProperty("robolectric.repo") as String?)?.let {
                    test.systemProperty("robolectric.dependency.repo.url", it)
                }
                test.systemProperty("exports.dir", layout.buildDirectory.dir("exemples-exports").get().asFile.absolutePath)
                test.maxHeapSize = "2g"
            }
        }
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
