import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Module métier 100 % Kotlin/JVM (aucune dépendance Android) :
// modèles, calculs de facturation, filtres, statistiques, exports XLSX/PDF, sauvegarde.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    // Les tests écrivent des exemples d'exports (PDF/XLSX) dans build/exemples-exports.
    systemProperty("exports.dir", layout.buildDirectory.dir("exemples-exports").get().asFile.absolutePath)
}
