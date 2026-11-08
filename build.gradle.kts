// AGP 9.3.1 built-in Kotlin. The compiler and KSP implementation versions are explicitly
// selected because this build uses a newer KGP/KSP line than AGP's built-in default.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.13")
    }
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.cyclonedx)
}

allprojects {
    // AGP runtime classpaths and CycloneDX's BOM configurations already enforce rejection of
    // dynamic selectors. Gradle does not allow that strategy and dependency locking on the SAME
    // Configuration. Exclude those plugin-guarded configurations from locking; lock every other
    // resolvable project configuration. Static checks reject dynamic direct/catalog selectors,
    // and normal compile/processor configurations retain full transitive lock state.
    configurations.configureEach {
        val guardedByPluginDynamicPolicy = name.endsWith("runtimeClasspath", ignoreCase = true) ||
            name == "cyclonedxBom" || name == "cyclonedxDirectBom"
        if (guardedByPluginDynamicPolicy) {
            // AGP and CycloneDX apply fail-on-dynamic-version resolution rules to these
            // configurations. Explicitly keep locking off here, including any inherited state,
            // because Gradle rejects both policies on one Configuration.
            resolutionStrategy.deactivateDependencyLocking()
        } else if (isCanBeResolved) {
            resolutionStrategy.activateDependencyLocking()
        }
    }
}
