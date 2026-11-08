import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val configuredRootId = providers.gradleProperty("corpusPublisherRootKeyId").orElse("").get()
val configuredRootKey = providers.gradleProperty("corpusPublisherRootKeyB64").orElse("").get()
fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

extensions.configure<ApplicationExtension>("android") {
    namespace = "app.timeline.islamic"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.timeline.islamic"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.5.1-phase4c-buildconfig-fix"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    flavorDimensions += "distribution"
    productFlavors {
        create("offlineSideload") { dimension = "distribution" }
        create("playDistribution") { dimension = "distribution" }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// BuildConfig values use the lazy public Variant API; no deprecated eager buildConfigField calls.
extensions.configure<ApplicationAndroidComponentsExtension>("androidComponents") {
    onVariants(selector().all()) { variant ->
        // AGP's public Variant API exposes buildConfigFields as nullable because it may be
        // unavailable when BuildConfig generation is disabled. This app explicitly enables
        // android.buildFeatures.buildConfig above, so fail at configuration time if AGP does
        // not expose the map rather than silently omitting the publisher trust fields.
        val buildConfigFields = checkNotNull(variant.buildConfigFields) {
            "BuildConfig fields are unavailable for ${variant.name}; keep android.buildFeatures.buildConfig enabled."
        }
        buildConfigFields.put(
            "CORPUS_PUBLISHER_ROOT_KEY_ID",
            BuildConfigField("String", buildConfigString(configuredRootId), "Pinned publisher root key identifier")
        )
        buildConfigFields.put(
            "CORPUS_PUBLISHER_ROOT_KEY_B64",
            BuildConfigField("String", buildConfigString(configuredRootKey), "Pinned publisher root Ed25519 public key, base64")
        )
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:timeline-api"))
    implementation(project(":core:agent-api"))
    implementation(project(":core:agent-runtime"))
    implementation(project(":core:authorization"))
    implementation(project(":core:security"))
    implementation(project(":core:localization"))
    implementation(project(":core:persistence"))
    implementation(project(":core:corpus"))
    implementation(project(":core:retrieval"))
    implementation(project(":feature:timeline"))
    implementation(project(":feature:canvas"))
    implementation(project(":feature:corpus-management"))
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
