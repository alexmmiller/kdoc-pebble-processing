plugins {
    java
    application
    // The new, actively maintained GradleUp Shadow plugin
    id("com.gradleup.shadow") version "8.3.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Pebble engine
    implementation("io.pebbletemplates:pebble:3.2.2")
    
    // Jackson for JSON parsing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
}

// Tell Gradle where to find your main method
application {
    mainClass.set("JsonMain")
}

// Use Java 17
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Configure the Shadow plugin to output the exact filename you asked for.
// By casting it as a standard <Jar> task, we avoid legacy package-name imports.
tasks.named<Jar>("shadowJar") {
    archiveFileName.set("PebbleTemplate.jar")
}