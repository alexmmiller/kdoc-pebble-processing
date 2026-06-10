Here is a comprehensive draft for your `README.md`. It covers all the essential aspects of the plugin's architecture, its configuration, and the specific hurdles we overcame to make it compatible with massive, heavily customized builds like the Kotlin standard library.

---

# Dokka JSON Output Plugin

A custom Dokka plugin that completely replaces Dokka's default HTML renderer to output a raw, structured JSON representation of your Kotlin documentation.

This plugin is designed for "headless" documentation pipelines where you want Dokka to handle the complex parsing, AST resolution, and multi-platform expect/actual merging, but you want to render the final visual output using a custom Static Site Generator (SSG) or templating engine (such as Pebble, Jinja, or React).

---

## 1. Introduction

By default, Dokka tightly couples its semantic analysis with its HTML generation. For teams building highly customized documentation websites, overriding Dokka's HTML output is notoriously difficult.

This plugin solves that problem by intercepting the pipeline just before rendering. It maps Dokka's internal Documentable Abstract Syntax Tree (AST) into clean, serializable Data Transfer Objects (DTOs) and writes them to disk as `.json` files. It preserves package hierarchies, generic bounds, platform source sets, and documentation tags while allowing frontend developers total freedom over the final HTML/CSS.

## 2. Getting Started

### Building the Plugin

Clone this repository and publish the plugin to your local Maven repository:

```bash
./gradlew publishToMavenLocal

```

### Applying the Plugin

In the target project where you want to generate documentation, add the plugin to your Dokka dependencies block. If you are using a multi-module setup, be sure to apply it to both the standard and multi-module configurations:

```kotlin
dependencies {
    dokkaPlugin("my.dokka.plugin:json-output-plugin:1.0.0-SNAPSHOT")
    dokkaHtmlMultiModulePlugin("my.dokka.plugin:json-output-plugin:1.0.0-SNAPSHOT")
}

```

## 3. Configuration Options

You can configure the JSON plugin by passing a JSON string to `pluginsMapConfiguration` inside your Dokka task setup:

```kotlin
tasks.withType<DokkaTaskPartial>().configureEach {
    pluginsMapConfiguration.put(
        "my.dokka.plugin.JsonOutputPlugin", 
        """{ "replaceHtmlExtension": true, "logLevel": "debug", "logFile": "build/dokka_json.log" }"""
    )
}

```

| Option | Type | Default | Description |
| --- | --- | --- | --- |
| `logLevel` | String | `"debug"` | Controls the verbosity of the plugin's internal logger (`"info"`, `"debug"`, `"warn"`, `"error"`). |
| `logFile` | String | *(Optional)* | Absolute or relative path to output the plugin's debug logs. Highly recommended as Dokka often swallows standard output. |
| `replaceHtmlExtension` | Boolean | `false` | If `true`, the plugin will rewrite all internal relative URLs to end in `.json` instead of `.html`. |
| `omitFields` | List | `[]` | A list of JSON keys to completely strip from the final output (e.g., `["breadcrumbs", "extras"]`). Useful for reducing disk footprint. |

## 4. Understanding Dokka Terminology

To successfully consume the JSON output, it helps to understand a few core Dokka concepts that dictate the structure of the data:

* **Documentable**: A node in Dokka's AST. Classes, functions, properties, packages, and modules are all `Documentable` objects.
* **DRI (Dokka Resource Identifier)**: A unique string identifier for every symbol in your codebase. (e.g., `kotlin.collections/List/size/#/PointingToDeclaration/`). DRIs are what Dokka uses to link disparate parts of the codebase together.
* **SourceSet**: Represents a target platform or compilation unit (e.g., `jvm`, `js`, `common`, `native`). Dokka merges declarations across SourceSets, which is why properties like `visibility` or `type` are mapped by SourceSet in the JSON.
* **PageNode**: Dokka's representation of a literal page that will be written to disk. The JSON plugin maps a `PageNode` back to its underlying `Documentable` to generate the JSON payload.

## 5. How It Works: Architecture & Lifecycle

The plugin operates in two distinct phases:

### Phase 1: The `JsonRenderer` (Synchronous AST Traversal)

The plugin implements the Dokka `Renderer` interface, completely overriding the default HTML generation. It walks the `RootPageNode` tree synchronously. For every page, it extracts the underlying `Documentable`, passes it to the `ModelMapper`, and translates the complex Dokka AST into clean Kotlin DTOs. These DTOs are serialized using `kotlinx.serialization` and written to disk.

### Phase 2: The `LinkPostProcessor` (Cross-Module Resolution)

Because Dokka resolves links across different modules *during* the HTML rendering phase, our JSON plugin must do the same. When the `JsonRenderer` encounters a DRI that belongs to an external module, it writes `"url": "unresolved:<DRI>"`.
Once all JSON files are written, the `LinkPostProcessor` spins up. It reads all JSON files on disk, builds a master index of every DRI, and performs a regex replacement to patch all `unresolved:` links into valid relative file paths.

## 6. The JSON Output Format

### Directory Structure

The plugin mirrors Dokka's standard hierarchical folder structure. However, instead of `index.html` files, you will find `.json` files.

Special aggregated files include:

* `index.json`: Created at the root of a multi-module build. Contains a list of all sub-modules and their URLs.
* `all-types.json`: Created at the root of a module. Contains a flat, searchable array of every class, interface, object, and type alias in that module.
* `package-list`: A dummy text file generated automatically by the plugin. It ensures that standard Dokka multi-module aggregator Gradle tasks don't crash when trying to copy assets.

### The Semantic Model (Polymorphism)

The JSON payloads are strictly typed. Every top-level object and nested member contains a `"kind"` discriminator (e.g., `"kind": "class"`, `"kind": "function"`, `"kind": "TypeAliased"`). This makes it incredibly easy to parse the JSON back into typed objects in your frontend layer.

*(To minimize disk footprint, the plugin's internal `Json` engine sets `encodeDefaults = false`, which means empty lists and maps are omitted entirely from the output).*

## 7. Resolving Cross-Module Links

If you are inspecting the JSON and notice a URL like `unresolved:kotlin.collections/List///PointingToDeclaration/`, this means the `LinkPostProcessor` failed to find that DRI in the current build environment.

This usually happens when:

1. The target module was not included in the Dokka multi-module task.
2. The target dependency is an external library, and external documentation links were not properly configured in the `build.gradle.kts` file.

If the DRI *is* present in the current build, the `LinkPostProcessor` will automatically patch it to a relative path like `../../kotlin-stdlib/kotlin.collections/-list/index.json`.

## 8. Consuming the JSON (Example: Pebble)

Because the JSON maintains Dokka's strict hierarchy, templating engines like Pebble or Jinja can iterate over it natively.

For example, to render a table of functions for a class:

```pebble
{% if functions is defined and functions is not empty %}
    <h2>Functions</h2>
    <table>
        {% for member in functions %}
            <tr>
                <td><a href="{{ member.url }}">{{ member.name }}</a></td>
                <td>
                    {# Render the parameters #}
                    fun {{ member.name }}(
                        {% for param in member.parameters %}
                            {{ param.name }}: {{ param.type.name }}
                        {% endfor %}
                    )
                </td>
            </tr>
        {% endfor %}
    </table>
{% endif %}

```

## 9. Advanced Usage: Kotlin Stdlib Integration

This plugin was rigorously tested against the official `kotlin-stdlib-docs` build pipeline. It is designed to cooperate gracefully with JetBrains' bespoke AST-mutating plugins:

* **VersionFilterPlugin:** The JSON plugin processes the tree *after* version filters have run, meaning internal or future APIs stripped by JetBrains' plugins are automatically excluded from the JSON.
* **SamplesTransformerPlugin:** JetBrains' sample plugin rewrites `@sample` tags and injects them directly into the visual `PageNode` block, leaving the abstract `Documentable` node empty. The `ModelMapper` is specifically programmed to recognize this edge case, extracting the executed code blocks directly from the visual page tree and mapping them back into the JSON `documentation` object.

## 10. Troubleshooting & Logs

If your Dokka task fails while using this plugin, always check your log file.

**"Execution failed for task: java.lang.reflect.InvocationTargetException"**
This is Gradle's generic wrapper for a reflection crash. It almost always means one of two things:

1. **Bad Log Path**: If `logFile` is set to an absolute path that doesn't exist on your OS (e.g., `/Users/alex/...` on a Linux machine), the plugin will crash before it can initialize. Keep log paths relative to the build directory (e.g., `build/dokka_json.log`).
2. **Missing Local Artifacts**: If running an isolated documentation project (like `kotlin-stdlib-docs`), ensure all target JARs and dependencies have been built and published to your local staging repository first.