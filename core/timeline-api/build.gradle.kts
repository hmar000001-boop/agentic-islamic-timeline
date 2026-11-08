import com.android.build.api.dsl.LibraryExtension
plugins { alias(libs.plugins.android.library) }
extensions.configure<LibraryExtension>("android") { namespace = "app.timeline.islamic.core.timeline"; compileSdk = 36; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }


dependencies { api(libs.kotlinx.coroutines.core); testImplementation(libs.junit) }
