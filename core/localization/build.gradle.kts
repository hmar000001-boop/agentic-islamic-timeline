import com.android.build.api.dsl.LibraryExtension
plugins { alias(libs.plugins.android.library) }
extensions.configure<LibraryExtension>("android") { namespace = "app.timeline.islamic.core.localization"; compileSdk { version = release(37) { minorApiLevel = 0 } }; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }

dependencies { implementation(libs.androidx.datastore.preferences); implementation(libs.kotlinx.coroutines.core); implementation(libs.kotlinx.coroutines.android) }
