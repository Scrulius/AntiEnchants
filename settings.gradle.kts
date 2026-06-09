rootProject.name = "AntiEnchants"

// Lets Gradle auto-provision the JDK required by the toolchain (Java 25 for Paper 26.1.x).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
