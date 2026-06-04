package my.dokka.plugin

import kotlinx.serialization.decodeFromString // <--- ADD THIS
import kotlinx.serialization.json.*
import my.dokka.plugin.dtos.DocumentableDto
import my.dokka.plugin.dtos.ModuleReferenceDto
import my.dokka.plugin.dtos.MultimoduleRootDto
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.pages.RootPageNode
import org.jetbrains.dokka.pages.WithDocumentables
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.renderers.Renderer
import java.io.File

class JsonRenderer(private val context: DokkaContext) : Renderer {
    
    private val json = Json { 
        prettyPrint = true 
        classDiscriminator = "kind" 
        encodeDefaults = true
        ignoreUnknownKeys = true // <--- Prevents crashes on unknown JSON fields
    }

    override fun render(root: RootPageNode) {
        val fqcn = "my.dokka.plugin.JsonOutputPlugin"
        var config = configuration<JsonOutputPlugin, JsonPluginConfig>(context)

        // MANUAL FALLBACK PARSING
        // If Dokka's internal parser fails to map the config, intercept the raw JSON and parse it ourselves
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
                // If it reaches here, Gradle is failing to pass the pluginsMapConfiguration to the task entirely
                context.logger.warn("No JSON config found in pluginsConfiguration for $fqcn! Falling back to defaults.")
                context.logger.warn("Available configs were: ${context.configuration.pluginsConfiguration.map { it.fqPluginName }}")
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

        // --- MULTIMODULE ROOT GENERATION ---
        if (context.configuration.modules.isNotEmpty()) {
            logger.info("Multimodule project detected. Generating root index.json...")
            
            // Respects the deserialized configuration flag
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

        // --- STANDARD TRAVERSAL ---
        fun traverse(node: PageNode) {
            if (node is WithDocumentables && node.documentables.isNotEmpty()) {
                val documentable = node.documentables.first()
                logger.debug("Processing documentable: ${documentable.name} (${documentable.dri})")
                
                // Pass the extracted config down into ModelMapper
                val mapper = ModelMapper(
                    locationProvider = locationProvider, 
                    contextNode = node, 
                    logger = logger,
                    replaceHtmlExtension = finalConfig.replaceHtmlExtension
                )
                
                val dto = mapper.mapToDto(documentable)
                
                if (dto != null) {
                    val pagePath = locationProvider.resolve(node, context = null, skipExtension = true)
                    val outputFile = File(outputDir, "$pagePath.json")
                    outputFile.parentFile.mkdirs()
                    
                    val rawJsonElement = json.encodeToJsonElement(DocumentableDto.serializer(), dto)
                    val filteredJsonElement = filterJson(rawJsonElement, finalConfig.omitFields)
                    outputFile.writeText(json.encodeToString(JsonElement.serializer(), filteredJsonElement))
                    
                    logger.debug("Wrote JSON to: ${outputFile.name}")
                }
            }
            node.children.forEach { traverse(it) }
        }

        traverse(root)
        LinkPostProcessor.postProcess(context)
        logger.info("JSON rendering completed.")
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
}