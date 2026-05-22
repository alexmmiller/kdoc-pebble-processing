plugins {
    kotlin("jvm") version "1.9.23"
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    // compileOnly because Dokka provides this at runtime
    compileOnly("org.jetbrains.dokka:dokka-core:1.9.20")
    
    // implementation because we need Jackson bundled/accessible
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.3")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.example.dokka"
            artifactId = "json-dokka-plugin"
            version = "1.0.0"
        }
    }
}