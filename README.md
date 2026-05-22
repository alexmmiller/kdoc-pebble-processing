# KDoc Pebble Processing Toolkit

This repository contains a setup for extracting structured documentation from Kotlin code using a custom Dokka plugin, exporting it to JSON, and compiling it into customized, beautiful HTML documentation via a standalone Pebble CLI tool.

## Project Structure

```text
kdoc-pebble-processing/
├── json-dokka-plugin/       # Custom Dokka plugin that intercepts AST and writes JSON
├── pebble-template-cli/     # Command-line utility tool running the Pebble engine
├── example-data-processor/  # Sample Kotlin utility library used as a demonstration target
├── templates/               # Pebble template files (e.g., split-screen viewer)
└── run-all.sh               # One-shot automation script for the entire pipeline
```

## Building the JSON-Dokka plugin
The JSON-Dokka plugin extends Dokka's documentation by introducing a custom transformer, producing a structured JSON representation of your codebase alongside thee standard output. This plugin can be published locally and integrated into any target project running Dokka.

```text
cd json-dokka-plugin
./gradlew publishToMavenLocal
cd ..
```

This compiles the plugin and registers the coordinates *com.example.dokka:json-dokka-plugin:1.0.0* locally on your machine.

## Generate JSON Docs from the Example Library


*example-data-processor* is a simple example project that includes the locally-published JSON-Dokka plugin to generate some exanple JSON docs.
```text
cd example-data-processor
./gradlew dokkaHtml
cd ..
```

__Note__: You can set the output file name in the *example-libs/example-data-processor/build.gradle.kts*

## Build the Pebble Template CLI Tool
You can build the standalone Pebble template CLI tool by calling the *shadowjar* task:
```text
cd pebble-template-cli
./gradlew shadowJar
cd ..
```

## Run the Template Compilation
To generate your documentation pages, execute the compiled JAR using your generated JSON dataset and your choice of Pebble HTML template layout:
```text
java -jar pebble-template-cli/build/libs/PebbleTemplate.jar \
  --data example-data-processor/build/dokka/html/api-documentation.json \
  --template templates/split-view.html \
  --output output/split-docs.html
```

## One-Shot Automation
*run-all.sh* is a one-shot end-to-end script to produce a templated documentation example file.
```text
chmod +x run-all.sh
./run-all.sh
```

It will place a side-by-side view of the JSON and template output page in *output/split-docs.html*. 
