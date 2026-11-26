// Tested with AGP 9.1.0 and Gradle 9.3.1 on JDK 21.
// AGP 9 compiles Kotlin itself, so org.jetbrains.kotlin.android is no longer applied.
// The Compose compiler plugin's version must match the Kotlin Gradle plugin version on the classpath.
plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
