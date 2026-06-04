package my.dokka.plugin

import kotlinx.serialization.Serializable
import org.jetbrains.dokka.plugability.ConfigurableBlock

@Serializable
data class JsonPluginConfig(
    val logLevel: String = "debug", 
    val omitFields: List<String> = emptyList(),
    val logFile: String? = "/Users/alex/dokka_plugin_debug.log",
    val replaceHtmlExtension: Boolean = false // <--- ADD THIS (Defaults to true to preserve current behavior)
) : ConfigurableBlock