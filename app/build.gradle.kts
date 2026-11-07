plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.timeline.islamic"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.timeline.islamic"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0-phase3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("offlineSideload") { dimension = "distribution" }
        create("playDistribution") { dimension = "distribution" }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true; buildConfig = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:timeline-api"))
    implementation(project(":core:agent-api"))
    implementation(project(":core:agent-runtime"))
    implementation(project(":core:authorization"))
    implementation(project(":core:security"))
    implementation(project(":core:localization"))
    implementation(project(":core:persistence"))
    implementation(project(":feature:timeline"))
    implementation(project(":feature:canvas"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
