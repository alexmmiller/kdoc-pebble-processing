#!/bin/bash

# ==========================================
# Configuration Paths (Adjust as needed)
# ==========================================
INPUT_DIR="/Users/alex/ADFA/Misc Software/Pebble Templating/kmath/build/dokka/html"
OUTPUT_DIR="./build/real-extra"
TEMPLATE_FILE="./templates/layout.pebble"
PEBBLE_JAR="pebble-template-cli/build/libs/PebbleTemplate.jar"
LOG_FILE="./build/pebble-debug.log"

PEBBLE_JAR="pebble-template-cli/build/libs/PebbleTemplate.jar"
# Ensure the base output directory and log directory exist
mkdir -p "$OUTPUT_DIR"
mkdir -p "$(dirname "$LOG_FILE")"

echo "🚀 Booting JVM for parallel Pebble HTML compilation..."

# Execute the Java program once, passing the entire directory and log file
java -jar "$PEBBLE_JAR" \
    --inputDir "$INPUT_DIR" \
    --template "$TEMPLATE_FILE" \
    --outputDir "$OUTPUT_DIR" \
    --logFile "$LOG_FILE"