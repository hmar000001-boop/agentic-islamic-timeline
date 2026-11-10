import com.android.build.api.dsl.LibraryExtension
plugins { alias(libs.plugins.android.library) }
extensions.configure<LibraryExtension>("android") { namespace = "app.timeline.islamic.core.security"; compileSdk { version = release(37) { minorApiLevel = 0 } }; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }



dependencies {
    api(project(":core:agent-api"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
