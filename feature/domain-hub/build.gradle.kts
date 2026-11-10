import com.android.build.api.dsl.LibraryExtension
plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.compose) }
extensions.configure<LibraryExtension>("android") {
    namespace = "app.timeline.islamic.feature.domainhub"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    api(project(":core:domain-api"))
    implementation(project(":feature:prayer"))
    implementation(project(":feature:hifz"))
    implementation(project(":feature:travel"))
    implementation(project(":feature:halal"))
    implementation(project(":feature:zakat"))
    implementation(project(":feature:inheritance"))
    implementation(project(":feature:community-local"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
}
