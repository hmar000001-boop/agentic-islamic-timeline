import com.android.build.api.dsl.LibraryExtension
plugins { alias(libs.plugins.android.library) }
extensions.configure<LibraryExtension>("android") { namespace = "app.timeline.islamic.core.authorization"; compileSdk = 37; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }

dependencies { api(project(":core:agent-api")); implementation(project(":core:common")); implementation(libs.kotlinx.coroutines.core); testImplementation(libs.junit) }
