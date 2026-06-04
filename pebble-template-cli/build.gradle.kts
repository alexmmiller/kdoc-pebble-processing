plugins {
    java
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

// Use Java 17
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Intercept the default "jar" task to bundle dependencies natively
tasks.named<Jar>("jar") {
    archiveFileName.set("PebbleTemplate.jar")
    
    manifest {
        attributes(mapOf("Main-Class" to "JsonMain"))
    }

    // Extract and bundle all dependencies into the final JAR
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    
    // Prevent duplicate file errors (e.g., overlapping META-INF/MANIFEST.MF files)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}