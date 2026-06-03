plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    `maven-publish`
}

group = "my.dokka.plugin"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // These MUST be 1.9.20 (the latest stable Dokka release for the 1.9 branch)
    compileOnly("org.jetbrains.dokka:dokka-core:1.9.20")
    compileOnly("org.jetbrains.dokka:dokka-base:1.9.20") 
    
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}