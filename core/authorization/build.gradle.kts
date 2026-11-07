plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "app.timeline.islamic.core.authorization"; compileSdk = 36; defaultConfig { minSdk = 26 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 } }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies { api(project(":core:agent-api")); implementation(project(":core:common")); implementation(libs.kotlinx.coroutines.core); testImplementation(libs.junit) }
