import com.android.build.api.dsl.LibraryExtension
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}
extensions.configure<LibraryExtension>("android") {
    namespace = "app.timeline.islamic.feature.models"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:model-manager"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.core)
}
