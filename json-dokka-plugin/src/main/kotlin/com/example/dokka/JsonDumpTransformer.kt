package com.example.dokka

import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer
// 1. Swap your Jackson imports to these:
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File

fun DFunction.signature(): List<String> {
    val params = this.parameters.joinToString { "${it.name}: ${it.type}" }
    return listOf("${this.name}($params)")
}

class JsonDumpTransformer(private val context: DokkaContext) : DocumentableTransformer {
    
    // 2. Change this line to construct the mapper without using the 2.17.0 extension
    private val mapper = ObjectMapper().registerKotlinModule()

    override fun invoke(original: DModule, context: DokkaContext): DModule {
        // ... Keep the rest of your transformation logic exactly the same ...
        println("🚀 JSON DUMP TRANSFORMER IS RUNNING FOR MODULE: ${original.name} 🚀")
        
        val simplifiedModel = mapOf(
            "moduleName" to original.name,
            "packages" to original.packages.map { pkg ->
                mapOf(
                    "packageName" to pkg.name,
                    "classlines" to pkg.classlikes.map { clazz ->
                        mapOf(
                            "className" to clazz.name,
                            "doc" to clazz.documentation.values.flatMap { it.children }.map { it.toString() },
                            "functions" to clazz.functions.map { func ->
                                mapOf(
                                    "functionName" to func.name,
                                    "signatures" to func.signature(),
                                    "doc" to func.documentation.values.flatMap { it.children }.map { it.toString() }
                                )
                            }
                        )
                    }
                )
            }
        )
        
        val jsonString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(simplifiedModel)
        
        val outputDir = context.configuration.outputDir
        outputDir.mkdirs()
        File(outputDir, "api-documentation.json").writeText(jsonString)
        
        return original 
    }
}
