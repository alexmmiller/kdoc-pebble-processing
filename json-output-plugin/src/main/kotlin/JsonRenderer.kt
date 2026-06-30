package my.dokka.plugin

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import my.dokka.plugin.dtos.*
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.pages.WithDocumentables
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.renderers.Renderer
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class JsonRenderer(private val context: DokkaContext) : Renderer {
    
    private val json = Json { 
        prettyPrint = false 
        classDiscriminator = "kind" 
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true 
    }

    override fun render(root: RootPageNode) {
        val fqcn = "my.dokka.plugin.JsonOutputPlugin"
        var config = configuration<JsonOutputPlugin, JsonPluginConfig>(context)

        if (config == null) {
            val rawConfig = context.configuration.pluginsConfiguration.find { it.fqPluginName == fqcn }?.values
            if (rawConfig != null) {
                context.logger.info("Dokka native config parsing failed. Manually parsing: $rawConfig")
                try {
                    config = json.decodeFromString<JsonPluginConfig>(rawConfig)
                } catch (e: Exception) {
                    context.logger.error("Manual config parsing failed: ${e.message}")
                }
            } else {
                context.logger.warn("No JSON config found in pluginsConfiguration for $fqcn! Falling back to defaults.")
            }
        }

        val finalConfig = config ?: JsonPluginConfig()
        val logger = PluginLogger(context.logger, finalConfig.logLevel, finalConfig.logFile)

        logger.info("Initializing JSON Renderer with config: $finalConfig")

        val locationProvider = context.plugin<DokkaBase>()
            .querySingle { locationProviderFactory }
            .getLocationProvider(root)

        val outputDir = context.configuration.outputDir
        logger.debug("Output directory set to: ${outputDir.absolutePath}")

        // --- NEW: Collect and write actual packages to package-list ---
        val packages = mutableSetOf<String>()
        fun collectPackages(node: PageNode) {
            if (node is WithDocumentables) {
                node.documentables.forEach { doc ->
                    if (doc is org.jetbrains.dokka.model.DPackage) {
                        doc.dri.packageName?.takeIf { it.isNotBlank() }?.let { packages.add(it) }
                    }
                }
            }
            node.children.forEach { collectPackages(it) }
        }
        collectPackages(root)

        val packageListFile = File(outputDir, "package-list")
        packageListFile.parentFile.mkdirs()
        
        val packageListContent = buildString {
            appendLine("\$dokka.format:json-v1\$")
            appendLine("\$dokka.linkExtension:json\$")
            packages.sorted().forEach {
                appendLine(it)
            }
        }
        
        packageListFile.writeText(packageListContent)
        logger.debug("Generated package-list with ${packages.size} packages.")

        if (context.configuration.modules.isNotEmpty()) {
            logger.info("Multimodule project detected. Generating root index.json...")
            
            val ext = if (finalConfig.replaceHtmlExtension) "json" else "html"
            val rootDto = MultimoduleRootDto(
                name = root.name,
                modules = context.configuration.modules.map { module ->
                    ModuleReferenceDto(
                        name = module.name,
                        url = "${module.relativePathToOutputDirectory.invariantSeparatorsPath}/index.$ext"
                    )
                }
            )
            
            val outputFile = File(outputDir, "index.json")
            outputFile.parentFile.mkdirs()
            
            val rawJsonElement = json.encodeToJsonElement(DocumentableDto.serializer(), rootDto)
            val filteredJsonElement = filterJson(rawJsonElement, finalConfig.omitFields)
            outputFile.writeText(json.encodeToString(JsonElement.serializer(), filteredJsonElement))
            
            logger.debug("Wrote Multimodule Root JSON to: ${outputFile.name}")
        }

        // Thread-safe lists to aggregate entries during traversal
        val allTypesList = ConcurrentLinkedQueue<TypeIndexEntryDto>()
        val packageNamesList = ConcurrentLinkedQueue<String>()

        // --- STANDARD SYNCHRONOUS TRAVERSAL ---
        fun traverse(node: PageNode, ancestors: List<PageNode>) {
            val currentPath = ancestors + node
            
            if (node is WithDocumentables && node.documentables.isNotEmpty()) {
                val documentable = node.documentables.first()
                logger.debug("Processing documentable: ${documentable.name} (${documentable.dri})")
                
                // Collect Package Names
                if (documentable is DPackage && !documentable.name.isNullOrBlank()) {
                    packageNamesList.add(documentable.name)
                }
                
                // Collect Types for All Types Index
                if (documentable is DClasslike || documentable is DTypeAlias) {
                    var typeUrl = locationProvider.resolve(node, context = null, skipExtension = false)
                    if (typeUrl != null && finalConfig.replaceHtmlExtension && !typeUrl.startsWith("http")) {
                        typeUrl = typeUrl.replace(".html", ".json")
                    }
                    
                    val kindStr = when (documentable) {
                        is DClass -> "class"
                        is DInterface -> "interface"
                        is DEnum -> "enum"
                        is DObject -> "object"
                        is DAnnotation -> "annotation"
                        is DTypeAlias -> "typeAlias"
                        else -> "type"
                    }
                    
                    allTypesList.add(
                        TypeIndexEntryDto(
                            name = documentable.name ?: "Unknown",
                            kind = kindStr,
                            dri = documentable.dri.toString(),
                            url = typeUrl,
                            sourceSets = documentable.sourceSets.map { it.sourceSetID.toString().substringAfterLast("/") }
                        )
                    )
                }

                val breadcrumbs = currentPath.map { ancestor ->
                    var url = locationProvider.resolve(ancestor, context = node, skipExtension = false)
                    if (url != null && finalConfig.replaceHtmlExtension && !url.startsWith("http")) {
                        url = url.replace(".html", ".json")
                    }
                    BreadcrumbNode(name = ancestor.name, url = url)
                }
                
                val mapper = ModelMapper(
                    locationProvider = locationProvider, 
                    contextNode = node, 
                    logger = logger,
                    replaceHtmlExtension = finalConfig.replaceHtmlExtension
                )
                
                val dto = mapper.mapToDto(documentable, breadcrumbs)
                
                if (dto != null) {
                    val pagePath = locationProvider.resolve(node, context = null, skipExtension = true)
                    val outputFile = File(outputDir, "$pagePath.json")
                    outputFile.parentFile.mkdirs()
                    
                    // 1. Encode your DTO to a JsonElement
                    val rawElement = json.encodeToJsonElement(DocumentableDto.serializer(), dto)

                    // 2. Apply your existing omitFields filter, then strip all empty arrays/strings/objects
                    val filteredElement = filterJson(rawElement, finalConfig.omitFields)
                    val cleanedElement = filteredElement.removeEmptyAndNulls()

                    // 3. Convert the cleaned AST to a string using the explicit JsonElement serializer
                    val finalJsonString = json.encodeToString(JsonElement.serializer(), cleanedElement)
                    
                    outputFile.writeText(finalJsonString)
                    logger.debug("Wrote JSON to: ${outputFile.name}")
                }
            }
            
            node.children.forEach { traverse(it, currentPath) }
        }

        traverse(root, emptyList())

        if (allTypesList.isNotEmpty()) {
            logger.info("Generating all-types.json index...")
            val allTypesDto = AllTypesDto(
                types = allTypesList.sortedBy { it.name }
            )
            val allTypesFile = File(outputDir, "all-types.json")
            allTypesFile.parentFile.mkdirs()
            
            val rawJsonElement = json.encodeToJsonElement(DocumentableDto.serializer(), allTypesDto)
            val filteredJsonElement = filterJson(rawJsonElement, finalConfig.omitFields)
            allTypesFile.writeText(json.encodeToString(JsonElement.serializer(), filteredJsonElement))
            
            logger.debug("Wrote All-Types JSON to: ${allTypesFile.name}")
        }
        
        // --- Write Package List ---
        logger.info("Generating package-list...")
        val packageListContent = generatePackageListContent(packageNamesList.toList())
        val packageListFile = File(outputDir, "package-list")
        packageListFile.parentFile.mkdirs()
        packageListFile.writeText(packageListContent)
        logger.debug("Wrote package-list to: ${packageListFile.name}")
        
        LinkPostProcessor.postProcess(context)
        logger.info("JSON rendering completed.")
    }
    
    /**
     * Generates the content for a Dokka-compatible package-list file.
     */
    private fun generatePackageListContent(packageNames: List<String>): String {
        return buildString {
            appendLine("\$dokka.format:html-v1")
            appendLine("\$dokka.linkExtension:html")
            // Note: \u001F is the required invisible Unit Separator character
            appendLine("\$dokka.location:.alltypes////PointingToDeclaration/\u001Fall-types.html")
            
            packageNames.sorted().forEach { packageName ->
                // Skip the root/empty package if it exists
                if (packageName.isNotBlank() && packageName != "[root]") {
                    appendLine(packageName)
                }
            }
        }
    }

    private fun filterJson(element: JsonElement, omitFields: List<String>): JsonElement {
        if (omitFields.isEmpty()) return element
        
        return when (element) {
            is JsonObject -> {
                val filteredMap = element.entries
                    .filterNot { omitFields.contains(it.key) }
                    .associate { it.key to filterJson(it.value, omitFields) }
                JsonObject(filteredMap)
            }
            is JsonArray -> {
                JsonArray(element.map { filterJson(it, omitFields) })
            }
            else -> element
        }
    }

    /**
    * Recursively removes nulls, empty strings, empty arrays, and empty objects from a JsonElement.
    */
    fun JsonElement.removeEmptyAndNulls(): JsonElement = when (this) {
        is JsonObject -> {
            val filtered = this.mapValues { it.value.removeEmptyAndNulls() }
                .filter { (_, value) ->
                    value !is JsonNull &&
                    !(value is JsonArray && value.isEmpty()) &&
                    !(value is JsonObject && value.isEmpty()) &&
                    !(value is JsonPrimitive && value.isString && value.content.isEmpty())
                }
            JsonObject(filtered)
        }
        is JsonArray -> {
            val filtered = this.map { it.removeEmptyAndNulls() }
                .filter {
                    it !is JsonNull &&
                    !(it is JsonArray && it.isEmpty()) &&
                    !(it is JsonObject && it.isEmpty()) &&
                    !(it is JsonPrimitive && it.isString && it.content.isEmpty())
                }
            JsonArray(filtered)
        }
        else -> this
    }
}