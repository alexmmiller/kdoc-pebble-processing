package com.example.dokka

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement

class JsonExportPlugin : DokkaPlugin() {
    private val dokkaBase by lazy { plugin<DokkaBase>() }

    // Replaces Dokka's default HtmlRenderer with our JSON exporter
    val jsonRenderer by extending {
        CoreExtensions.renderer providing { context -> 
            JsonRenderer(context) 
        } override dokkaBase.htmlRenderer
    }

    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement
}