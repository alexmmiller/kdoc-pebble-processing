#!/bin/bash

IN_DIR="/Users/alex/ADFA/Misc Software/Pebble Templating/kmath/build/dokka/html"             
OUT_DIR="./build/rendered-docs"         
TEMPLATES_DIR="./templates"
JAVA_TOOL_CLASSPATH="/Users/alex/ADFA/Misc Software/Pebble Templating/kdoc-pebble-processing/pebble-template-cli/build/libs/PebbleTemplate.jar:."
LAYOUT_TEMPLATE="$TEMPLATES_DIR/layout.pebble"

mkdir -p "$OUT_DIR"

# 1. Look for the newly generated .json files
find "$IN_DIR" -type f -name "*.json" | while read -r filepath; do
    
    page_type=$(jq -r '.pageType' "$filepath")
    if [ "$page_type" == "null" ] || [ -z "$page_type" ]; then
        continue
    fi
    
    rel_path="${filepath#$IN_DIR/}"
    
    # 2. Swap the extension back to .html for the final templated output
    target_file="$OUT_DIR/${rel_path%.json}.html"
    target_dir=$(dirname "$target_file")
    mkdir -p "$target_dir"

    echo "Rendering [$page_type] -> $target_file"
    
    java -cp "$JAVA_TOOL_CLASSPATH" JsonMain --data "$filepath" --template "$LAYOUT_TEMPLATE" --output "$target_file"
done