import com.android.build.api.dsl.LibraryExtension
plugins { alias(libs.plugins.android.library) }
extensions.configure<LibraryExtension>("android") { namespace = "app.timeline.islamic.core.retrieval"; compileSdk { version = release(37) { minorApiLevel = 0 } }; defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }

dependencies {
    api(project(":core:knowledge-api"))
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
