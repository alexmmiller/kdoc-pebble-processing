package my.dokka.plugin

import my.dokka.plugin.dtos.*
import org.jetbrains.dokka.base.resolvers.local.LocationProvider
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.doc.*
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.PageNode

class ModelMapper(
    private val locationProvider: LocationProvider,
    private val contextNode: PageNode,
    private val logger: PluginLogger,
    private val replaceHtmlExtension: Boolean
) {
    private fun resolveUrl(dri: DRI?, sourceSets: Set<DisplaySourceSet>): String? {
        if (dri == null) return null
        
        var url = locationProvider.resolve(dri, sourceSets, contextNode)
        if (url == null) {
            url = locationProvider.resolve(dri, emptySet(), contextNode)
        }
        
        // --- UNIVERSAL CROSS-MODULE FALLBACK ---
        if (url == null) {
            url = "unresolved:${dri}"
        }

        if (replaceHtmlExtension && !url.startsWith("http") && !url.startsWith("unresolved:")) {
            url = url.replace(".html", ".json")
        }
        return url
    }

    fun mapToDto(doc: Documentable): DocumentableDto? {
        logger.debug("Mapping documentable ${doc.name} of type ${doc::class.java.simpleName}")
        
        val displaySourceSets = doc.sourceSets.map { it.toDisplaySourceSet() }.toSet()
        val url = resolveUrl(doc.dri, displaySourceSets)

        return when (doc) {
            is DModule -> ModuleDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation),
                sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(),
                extras = mapExtras(doc.extra),
                packages = doc.packages.mapNotNull { mapToDto(it) }
            )
            is DPackage -> PackageDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                functions = doc.functions.mapNotNull { mapToDto(it) },
                properties = doc.properties.mapNotNull { mapToDto(it) },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                typeAliases = doc.typealiases.mapNotNull { mapToDto(it) }
            )
            is DClass -> ClassDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                constructors = doc.constructors.mapNotNull { mapToDto(it) },
                functions = doc.functions.mapNotNull { mapToDto(it) },
                properties = doc.properties.mapNotNull { mapToDto(it) },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                companion = doc.companion?.let { mapToDto(it) as? ObjectDto },
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                supertypes = mapSourceSetDependent(doc.supertypes) { ss, list -> 
                    list.map { TypeConstructorWithKindDto(mapBound(it.typeConstructor, setOf(ss.toDisplaySourceSet())), it.kind.toString()) } 
                },
                modifier = mapSourceSetDependent(doc.modifier) { _, it -> it.name },
                isExpectActual = doc.isExpectActual,
                typealiases = emptyList() 
            )
            is DEnum -> EnumDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                entries = doc.entries.mapNotNull { mapToDto(it) },
                constructors = doc.constructors.mapNotNull { mapToDto(it) },
                functions = doc.functions.mapNotNull { mapToDto(it) },
                properties = doc.properties.mapNotNull { mapToDto(it) },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                companion = doc.companion?.let { mapToDto(it) as? ObjectDto },
                supertypes = mapSourceSetDependent(doc.supertypes) { ss, list -> 
                    list.map { TypeConstructorWithKindDto(mapBound(it.typeConstructor, setOf(ss.toDisplaySourceSet())), it.kind.toString()) } 
                },
                isExpectActual = doc.isExpectActual,
                typealiases = emptyList() 
            )
            is DEnumEntry -> EnumEntryDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                functions = doc.functions.mapNotNull { mapToDto(it) },
                properties = doc.properties.mapNotNull { mapToDto(it) },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) }
            )
            is DFunction -> FunctionDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                isConstructor = doc.isConstructor,
                parameters = doc.parameters.mapNotNull { mapToDto(it) as? ParameterDto },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                type = mapBound(doc.type, displaySourceSets),
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                receiver = doc.receiver?.let { mapToDto(it) as? ParameterDto },
                modifier = mapSourceSetDependent(doc.modifier) { _, it -> it.name },
                isExpectActual = doc.isExpectActual,
                contextParameters = emptyList() 
            )
            is DInterface -> InterfaceDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                functions = doc.functions.mapNotNull { mapToDto(it) },
                properties = doc.properties.mapNotNull { mapToDto(it) },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                companion = doc.companion?.let { mapToDto(it) as? ObjectDto },
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                supertypes = mapSourceSetDependent(doc.supertypes) { ss, list -> 
                    list.map { TypeConstructorWithKindDto(mapBound(it.typeConstructor, setOf(ss.toDisplaySourceSet())), it.kind.toString()) } 
                },
                modifier = mapSourceSetDependent(doc.modifier) { _, it -> it.name },
                isExpectActual = doc.isExpectActual,
                typealiases = emptyList() 
            )
            is DObject -> ObjectDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                functions = doc.functions.mapNotNull { mapToDto(it) },
                properties = doc.properties.mapNotNull { mapToDto(it) },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                supertypes = mapSourceSetDependent(doc.supertypes) { ss, list -> 
                    list.map { TypeConstructorWithKindDto(mapBound(it.typeConstructor, setOf(ss.toDisplaySourceSet())), it.kind.toString()) } 
                },
                isExpectActual = doc.isExpectActual,
                typealiases = emptyList() 
            )
            is DAnnotation -> AnnotationDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                functions = doc.functions.mapNotNull { mapToDto(it) },
                properties = doc.properties.mapNotNull { mapToDto(it) },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                companion = doc.companion?.let { mapToDto(it) as? ObjectDto },
                constructors = doc.constructors.mapNotNull { mapToDto(it) },
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                isExpectActual = doc.isExpectActual
            )
            is DProperty -> PropertyDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                type = mapBound(doc.type, displaySourceSets),
                receiver = doc.receiver?.let { mapToDto(it) as? ParameterDto },
                setter = doc.setter?.let { mapToDto(it) as? FunctionDto },
                getter = doc.getter?.let { mapToDto(it) as? FunctionDto },
                modifier = mapSourceSetDependent(doc.modifier) { _, it -> it.name },
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                isExpectActual = doc.isExpectActual,
                contextParameters = emptyList() 
            )
            is DParameter -> ParameterDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                type = mapBound(doc.type, displaySourceSets)
            )
            is DTypeParameter -> TypeParameterDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                bounds = doc.bounds.map { mapBound(it, displaySourceSets) },
                variantTypeParameter = mapProjection(doc.variantTypeParameter, displaySourceSets) as VarianceDto
            )
            is DTypeAlias -> TypeAliasDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = mapExtras(doc.extra),
                type = mapBound(doc.type, displaySourceSets),
                underlyingType = mapSourceSetDependent(doc.underlyingType) { ss, it -> mapBound(it, setOf(ss.toDisplaySourceSet())) },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path }
            )
            else -> null
        }
    }

    private fun mapExtras(extra: PropertyContainer<*>): ExtrasDto {
        val isObviousMember = extra.allOfType<Any>().any { it::class.java.simpleName == "ObviousMember" }
        val isException = extra.allOfType<Any>().any { it::class.java.simpleName == "ExceptionInSupertypes" }

        val annotationsMap = extra.allOfType<org.jetbrains.dokka.model.Annotations>().firstOrNull()?.directAnnotations?.entries?.associate { (ss, list) ->
            ss.sourceSetID.toString() to list.map { anno ->
                AnnotationWrapperDto(
                    dri = anno.dri.toString(),
                    params = anno.params.entries.associate { (k, v) -> k to v.toString() }
                )
            }
        } ?: emptyMap()

        val defaultValuesMap = mutableMapOf<String, String>()
        extra.allOfType<Any>().firstOrNull { it::class.java.simpleName == "DefaultValue" }?.let { defValue ->
            try {
                val valueMethod = defValue::class.java.methods.firstOrNull { it.name == "getValue" || it.name == "getExpression" }
                val valueObj = valueMethod?.invoke(defValue)
                if (valueObj is Map<*, *>) {
                    valueObj.forEach { (ss, expr) ->
                        val ssName = ss?.let { it::class.java.getMethod("getSourceSetID").invoke(it).toString() } ?: "unknown"
                        defaultValuesMap[ssName] = expr.toString()
                    }
                } else if (valueObj != null) {
                    defaultValuesMap["unknown"] = valueObj.toString()
                }
            } catch (e: Exception) {
                logger.debug("Failed to safely extract DefaultValue: ${e.message}")
            }
        }

        val additionalModifiersMap = extra.allOfType<org.jetbrains.dokka.model.AdditionalModifiers>().firstOrNull()?.content?.entries?.associate { (ss, set) ->
            ss.sourceSetID.toString() to set.map { modifier ->
                try {
                    modifier::class.java.getMethod("getName").invoke(modifier).toString().lowercase()
                } catch (e: Exception) {
                    modifier.toString().substringAfterLast("$").substringBefore("@").lowercase()
                }
            }
        } ?: emptyMap()

        return ExtrasDto(
            annotations = annotationsMap,
            defaultValues = defaultValuesMap,
            additionalModifiers = additionalModifiersMap,
            isObviousMember = isObviousMember,
            isException = isException
        )
    }

    private fun mapProjection(proj: Projection, sourceSets: Set<DisplaySourceSet>): ProjectionDto {
        return when (proj) {
            is Star -> StarDto
            is Variance<*> -> when (proj) {
                is Covariance<*> -> CovarianceDto(mapBound(proj.inner, sourceSets))
                is Contravariance<*> -> ContravarianceDto(mapBound(proj.inner, sourceSets))
                is Invariance<*> -> InvarianceDto(mapBound(proj.inner, sourceSets))
            }
            is Bound -> mapBound(proj, sourceSets)
        }
    }

    private fun mapBound(bound: Bound, sourceSets: Set<DisplaySourceSet>): BoundDto {
        return when (bound) {
            is TypeParameter -> TypeParameterBoundDto(bound.dri.toString(), bound.name, bound.presentableName, resolveUrl(bound.dri, sourceSets))
            is Nullable -> NullableDto(mapBound(bound.inner, sourceSets), resolveUrl(null, sourceSets))
            is DefinitelyNonNullable -> DefinitelyNonNullableDto(mapBound(bound.inner, sourceSets))
            is TypeAliased -> TypeAliasedDto(mapBound(bound.typeAlias, sourceSets), mapBound(bound.inner, sourceSets), resolveUrl(null, sourceSets))
            is PrimitiveJavaType -> PrimitiveJavaTypeDto(bound.name)
            is JavaObject -> JavaObjectDto()
            is Void -> VoidDto()
            is Dynamic -> DynamicDto()
            is UnresolvedBound -> UnresolvedBoundDto(bound.name)
            is GenericTypeConstructor -> GenericTypeConstructorDto(
                dri = bound.dri.toString(), 
                projections = bound.projections.map { mapProjection(it, sourceSets) }, 
                presentableName = bound.presentableName,
                url = resolveUrl(bound.dri, sourceSets)
            )
            is FunctionalTypeConstructor -> FunctionalTypeConstructorDto(
                dri = bound.dri.toString(), 
                projections = bound.projections.map { mapProjection(it, sourceSets) }, 
                isExtensionFunction = bound.isExtensionFunction, 
                isSuspendable = bound.isSuspendable, 
                presentableName = bound.presentableName,
                url = resolveUrl(bound.dri, sourceSets)
            )
        }
    }

    private fun mapDocNodes(docs: SourceSetDependent<DocumentationNode>): Map<String, List<TagWrapperDto>> {
        return docs.entries.associate { (sourceSet, node) ->
            val displaySourceSets = setOf(sourceSet.toDisplaySourceSet())
            val tags = node.children.map { tagWrapper ->
                TagWrapperDto(
                    type = tagWrapper::class.java.simpleName,
                    text = extractText(tagWrapper.root, displaySourceSets).trim(),
                    name = if (tagWrapper is NamedTagWrapper) tagWrapper.name else null
                )
            }
            sourceSet.sourceSetID.toString() to tags
        }
    }

private fun extractText(tag: DocTag, sourceSets: Set<DisplaySourceSet>): String {
        return when (tag) {
            // Base text: Escape HTML brackets so things like List<String> render correctly
            is Text -> tag.body.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            
            // Layout
            is P -> "<p>${tag.children.joinToString("") { extractText(it, sourceSets) }}</p>"
            is Br -> "<br/>"
            is BlockQuote -> "<blockquote>${tag.children.joinToString("") { extractText(it, sourceSets) }}</blockquote>"
            
            // Formatting
            is B -> "<b>${tag.children.joinToString("") { extractText(it, sourceSets) }}</b>"
            is Strong -> "<strong>${tag.children.joinToString("") { extractText(it, sourceSets) }}</strong>"
            is I -> "<i>${tag.children.joinToString("") { extractText(it, sourceSets) }}</i>"
            is Em -> "<em>${tag.children.joinToString("") { extractText(it, sourceSets) }}</em>"
            
            // Code
            is CodeInline -> "<code>${tag.children.joinToString("") { extractText(it, sourceSets) }}</code>"
            is CodeBlock -> "<pre><code>${tag.children.joinToString("") { extractText(it, sourceSets) }}</code></pre>"
            
            // Lists
            is Ul -> "<ul>${tag.children.joinToString("") { extractText(it, sourceSets) }}</ul>"
            is Ol -> "<ol>${tag.children.joinToString("") { extractText(it, sourceSets) }}</ol>"
            is Li -> "<li>${tag.children.joinToString("") { extractText(it, sourceSets) }}</li>"
            
            // Headers
            is H1 -> "<h1>${tag.children.joinToString("") { extractText(it, sourceSets) }}</h1>"
            is H2 -> "<h2>${tag.children.joinToString("") { extractText(it, sourceSets) }}</h2>"
            is H3 -> "<h3>${tag.children.joinToString("") { extractText(it, sourceSets) }}</h3>"
            is H4 -> "<h4>${tag.children.joinToString("") { extractText(it, sourceSets) }}</h4>"
            is H5 -> "<h5>${tag.children.joinToString("") { extractText(it, sourceSets) }}</h5>"
            is H6 -> "<h6>${tag.children.joinToString("") { extractText(it, sourceSets) }}</h6>"
            
            // Links
            is A -> {
                val href = tag.params["href"] ?: ""
                "<a href=\"$href\">${tag.children.joinToString("") { extractText(it, sourceSets) }}</a>"
            }
            is DocumentationLink -> {
                val href = resolveUrl(tag.dri, sourceSets)
                "<a href=\"$href\" data-dri=\"${tag.dri}\">${tag.children.joinToString("") { extractText(it, sourceSets) }}</a>"
            }
            
            // Fallback for unknown/custom tags
            is CustomDocTag -> tag.children.joinToString("") { extractText(it, sourceSets) }
            else -> tag.children.joinToString("") { extractText(it, sourceSets) }
        }
    }

    private fun <T, R> mapSourceSetDependent(
        dependent: SourceSetDependent<T>, 
        mapper: (org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet, T) -> R
    ): Map<String, R> {
        return dependent.entries.associate { it.key.sourceSetID.toString() to mapper(it.key, it.value) }
    }

    private fun mapSourceSets(sets: Set<org.jetbrains.dokka.DokkaConfiguration.DokkaSourceSet>): List<String> {
        return sets.map { it.sourceSetID.toString() }
    }
}