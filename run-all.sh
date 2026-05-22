#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -e

# Visual colors for logging
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
CLEAR='\033[0m'

TEMPLATE_FILE="example-libs/templates/split-view.html"

echo -e "${BLUE}=======================================================${CLEAR}"
echo -e "${BLUE}   Starting One-Shot KDoc Pebble Pipeline Execution    ${CLEAR}"
echo -e "${BLUE}=======================================================${CLEAR}"

# Step 0: General Validation and Setup
echo -e "${YELLOW}[Step 0/4] Verifying existing template and creating output directory...${CLEAR}"
mkdir -p output

if [ ! -f "$TEMPLATE_FILE" ]; then
    echo -e "❌ Error: Split-view template not found at: $TEMPLATE_FILE"
    exit 1
fi

# Step 1: Build and Publish Custom Dokka Plugin
echo -e "${YELLOW}[Step 1/4] Compiling and publishing custom Dokka JSON Plugin...${CLEAR}"
if [ -d "json-dokka-plugin" ]; then
    cd json-dokka-plugin
    ./gradlew clean publishToMavenLocal
    cd ..
else
    echo -e "❌ Error: 'json-dokka-plugin' directory not found."
    exit 1
fi

# Step 2: Extract structured documentation data into raw JSON
echo -e "${YELLOW}[Step 2/4] Running Dokka pipeline on example library...${CLEAR}"
if [ -d "example-libs/example-data-processor" ]; then
    cd example-libs/example-data-processor
    ./gradlew clean dokkaHtml
    cd ../..
else
    echo -e "❌ Error: 'example-libs/example-data-processor' directory not found."
    exit 1
fi

# Step 3: Compile the Pebble Template CLI Engine executable
echo -e "${YELLOW}[Step 3/4] Packaging independent Pebble template CLI engine...${CLEAR}"
if [ -d "pebble-template-cli" ]; then
    cd pebble-template-cli
    ./gradlew clean shadowJar
    cd ..
else
    echo -e "❌ Error: 'pebble-template-cli' directory not found."
    exit 1
fi

# Step 4: Execute the Pebble generation pass using CLI variables
echo -e "${YELLOW}[Step 4/4] Executing dynamic template engine transformation...${CLEAR}"
JSON_DATA="example-libs/example-data-processor/build/dokka/html/api-documentation.json"
CLI_JAR="pebble-template-cli/build/libs/PebbleTemplate.jar"
TARGET_OUTPUT="output/split-docs.html"

if [ -f "$JSON_DATA" ] && [ -f "$CLI_JAR" ]; then
    java -jar "$CLI_JAR" \
        --data "$JSON_DATA" \
        --template "$TEMPLATE_FILE" \
        --output "$TARGET_OUTPUT"
    
    echo -e "${GREEN}=======================================================${CLEAR}"
    echo -e "${GREEN}🎉 SUCCESS! Pipeline complete.${CLEAR}"
    echo -e "${GREEN}📄 Rendered layout ready at: $(pwd)/$TARGET_OUTPUT${CLEAR}"
    echo -e "${GREEN}=======================================================${CLEAR}"
else
    echo -e "❌ Error: Pipeline output elements missing during execution check."
    exit 1
fi