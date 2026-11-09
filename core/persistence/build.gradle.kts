import com.android.build.api.dsl.LibraryExtension
plugins { alias(libs.plugins.android.library); alias(libs.plugins.ksp) }
extensions.configure<LibraryExtension>("android") { namespace = "app.timeline.islamic.core.persistence"; compileSdk { version = release(37) { minorApiLevel = 0 } }; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:timeline-api"))
    implementation(project(":core:agent-api"))
    implementation(project(":core:knowledge-api"))
    implementation(project(":core:security"))
    // AppDatabase publicly extends RoomDatabase; expose the supertype on consumer compile classpaths.
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // AgentAuthorizationRoomTest exercises the authorization engine against the Room stores.
    // Keep this dependency test-only; persistence main/runtime APIs do not depend on the engine.
    androidTestImplementation(project(":core:authorization"))
}
