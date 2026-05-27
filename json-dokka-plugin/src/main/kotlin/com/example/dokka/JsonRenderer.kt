package com.example.dokka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.resolvers.local.LocationProvider
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.renderers.Renderer
import java.io.File

class JsonRenderer(private val context: DokkaContext) : Renderer {
    private val mapper = ObjectMapper().registerKotlinModule()

    override fun render(root: RootPageNode) {
        // Output to the exact directory specified by the Gradle "outputDir"
        val outputDir = context.configuration.outputDir
        val locationProvider = context.plugin<DokkaBase>().querySingle { locationProviderFactory }.getLocationProvider(root)

        fun processPage(page: PageNode) {
            if (page is ContentPage) {
                val path = locationProvider.resolve(page)
                
                // FIX: Add null safety check for the resolved path
                if (path != null) {
                    // Swap the extension to .json so we write pure JSON files
                    val jsonPath = if (path.contains(".")) {
                        path.substringBeforeLast(".") + ".json"
                    } else {
                        "$path.json"
                    }
                    
                    val file = File(outputDir, jsonPath)
                    file.parentFile.mkdirs()
                    
                    val pageData = mapOf(
                        "pageType" to page::class.simpleName,
                        "name" to page.name,
                        "content" to contentNodeToMap(page.content, locationProvider, page)
                    )
                    
                    // Direct file write completely bypasses Dokka's HTML post-processing
                    file.writeText(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pageData))
                } else {
                    println("⚠️ Warning: Could not resolve path for page ${page.name}")
                }
            }
            page.children.forEach { processPage(it) }
        }

        processPage(root)
    }

    private fun contentNodeToMap(node: ContentNode, locationProvider: LocationProvider, currentPage: PageNode): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>("type" to node::class.simpleName)
        
        val styles = node.style.map { it.toString().substringAfterLast(".") }
        if (styles.isNotEmpty()) map["styles"] = styles

        when (node) {
            is ContentText -> map["text"] = node.text
            is ContentHeader -> map["level"] = node.level
            is ContentCodeBlock -> map["language"] = node.language
            is ContentCodeInline -> map["language"] = node.language
            // Maintain the .html references inside the JSON data so Pebble links work properly!
            is ContentDRILink -> map["address"] = locationProvider.resolve(node.address, node.sourceSets, currentPage) ?: "#"
            is ContentResolvedLink -> map["address"] = node.address
            is ContentEmbeddedResource -> {
                map["address"] = node.address
                map["altText"] = node.altText
            }
            is ContentTable -> {
                map["header"] = node.header.map { contentNodeToMap(it, locationProvider, currentPage) }
                map["caption"] = node.caption?.let { contentNodeToMap(it, locationProvider, currentPage) }
            }
            is ContentList -> map["ordered"] = node.ordered
            is ContentDivergentInstance -> {
                map["before"] = node.before?.let { contentNodeToMap(it, locationProvider, currentPage) }
                map["divergent"] = contentNodeToMap(node.divergent, locationProvider, currentPage)
                map["after"] = node.after?.let { contentNodeToMap(it, locationProvider, currentPage) }
            }
            is PlatformHintedContent -> map["inner"] = contentNodeToMap(node.inner, locationProvider, currentPage)
            else -> {} 
        }
        
        if (node.children.isNotEmpty()) {
            map["children"] = node.children.map { contentNodeToMap(it, locationProvider, currentPage) }
        }
        return map
    }
}