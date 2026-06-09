/*
 * AntiEnchants - Block unwanted enchantments server-wide.
 * Author: Scrulius (GitHub)
 * License: All Rights Reserved
 *
 * Standalone successor to AntiMending, generalised to a configurable enchantment
 * blacklist plus villager book-trade control.
 */

plugins {
    java
}

group = "dev.scrulius"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")     // Paper API
}

dependencies {
    // Paper API 26.1.2 (year-based versioning; .build.NN-stable pins a build).
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.add("-Xlint:deprecation")
}
