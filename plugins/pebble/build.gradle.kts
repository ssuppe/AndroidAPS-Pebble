plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "app.aaps.plugins.pebble"
    buildFeatures {
        dataBinding = true
    }
}

dependencies {
    implementation(project(":core:interfaces"))
    implementation(project(":core:data"))
    implementation(project(":shared:impl"))
    implementation(project(":core:ui"))
    implementation("com.getpebble:pebblekit:4.0.1")

    testImplementation(libs.org.junit.jupiter.api)
    testRuntimeOnly(libs.org.junit.jupiter.engine)
    testImplementation(libs.org.mockito.kotlin)
    testImplementation(project(":shared:tests"))
}
