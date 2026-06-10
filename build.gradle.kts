/*
 * AntiEnchants - Block unwanted enchantments server-wide.
 * Author: Scrulius (GitHub)
 * License: All Rights Reserved
 *
 * Standalone successor to AntiMending, generalised to a configurable enchantment
 * blacklist plus level caps, compensation, permission bypasses and villager
 * book-trade control.
 */

plugins {
    java
    id("com.gradleup.shadow") version "9.4.2"   // shades + relocates bStats
}

group = "dev.scrulius"
version = "1.2.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")     // Paper API
}

dependencies {
    // Paper API 26.1.2 (year-based versioning; .build.NN-stable pins a build).
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.69-stable")
    // bStats metrics (shaded + relocated below, as bStats requires).
    implementation("org.bstats:bstats-bukkit:3.2.1")
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

// The deliverable is the shadow jar (plain jar keeps a classifier out of the way).
tasks.jar {
    archiveClassifier.set("plain")
}
tasks.shadowJar {
    archiveClassifier.set("")
    relocate("org.bstats", "dev.scrulius.antienchants.bstats")
}
tasks.assemble {
    dependsOn(tasks.shadowJar)
}
