package my.dokka.plugin

import kotlinx.serialization.Serializable
import org.jetbrains.dokka.plugability.ConfigurableBlock

@Serializable
data class JsonPluginConfig(
    val logLevel: String = "info", 
    val omitFields: List<String> = emptyList(),
    val logFile: String? = null,
    val replaceHtmlExtension: Boolean = true // <--- ADD THIS (Defaults to true to preserve current behavior)
) : ConfigurableBlock