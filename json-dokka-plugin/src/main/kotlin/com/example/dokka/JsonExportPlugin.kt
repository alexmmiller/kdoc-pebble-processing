package com.example.dokka

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement

class JsonExportPlugin : DokkaPlugin() {
    
    // Registers the pass-through transformer execution step
    val jsonDumpTransformer by extending {
        CoreExtensions.documentableTransformer providing { context -> 
            JsonDumpTransformer(context) 
        }
    }

    // Explicitly acknowledge that you are using Dokka's preview API
    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement
}
