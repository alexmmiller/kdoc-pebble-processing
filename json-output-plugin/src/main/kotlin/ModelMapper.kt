package my.dokka.plugin

import my.dokka.plugin.dtos.*
import org.jetbrains.dokka.base.resolvers.local.LocationProvider
import org.jetbrains.dokka.model.*
import org.jetbrains.dokka.model.doc.*
import org.jetbrains.dokka.pages.PageNode
import org.jetbrains.dokka.links.DRI

class ModelMapper(
    private val locationProvider: LocationProvider,
    private val contextNode: PageNode,
    private val logger: PluginLogger,
    private val replaceHtmlExtension: Boolean // <--- ADD THIS
) {

    fun mapToDto(doc: Documentable): DocumentableDto? {
        logger.debug("Mapping documentable ${doc.name} of type ${doc::class.java.simpleName}")
        
        val displaySourceSets = doc.sourceSets.map { it.toDisplaySourceSet() }.toSet()
        
        var url = locationProvider.resolve(doc.dri, displaySourceSets, contextNode)
        
        // Use the config flag here
        if (replaceHtmlExtension && url != null && !url.startsWith("http")) {
            url = url.replace(".html", ".json")
        }
        
        logger.debug("Resolved URL: $url")

        return when (doc) {
            is DModule -> ModuleDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation),
                sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(),
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                packages = doc.packages.mapNotNull { mapToDto(it) as? PackageDto }
            )
            is DPackage -> PackageDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                functions = doc.functions.mapNotNull { mapToDto(it) as? FunctionDto },
                properties = doc.properties.mapNotNull { mapToDto(it) as? PropertyDto },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                typeAliases = doc.typealiases.mapNotNull { mapToDto(it) as? TypeAliasDto }
            )
            is DClass -> ClassDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                constructors = doc.constructors.mapNotNull { mapToDto(it) as? FunctionDto },
                functions = doc.functions.mapNotNull { mapToDto(it) as? FunctionDto },
                properties = doc.properties.mapNotNull { mapToDto(it) as? PropertyDto },
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
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                entries = doc.entries.mapNotNull { mapToDto(it) as? EnumEntryDto },
                constructors = doc.constructors.mapNotNull { mapToDto(it) as? FunctionDto },
                functions = doc.functions.mapNotNull { mapToDto(it) as? FunctionDto },
                properties = doc.properties.mapNotNull { mapToDto(it) as? PropertyDto },
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
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                functions = doc.functions.mapNotNull { mapToDto(it) as? FunctionDto },
                properties = doc.properties.mapNotNull { mapToDto(it) as? PropertyDto },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) }
            )
            is DFunction -> FunctionDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
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
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                functions = doc.functions.mapNotNull { mapToDto(it) as? FunctionDto },
                properties = doc.properties.mapNotNull { mapToDto(it) as? PropertyDto },
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
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                functions = doc.functions.mapNotNull { mapToDto(it) as? FunctionDto },
                properties = doc.properties.mapNotNull { mapToDto(it) as? PropertyDto },
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
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                functions = doc.functions.mapNotNull { mapToDto(it) as? FunctionDto },
                properties = doc.properties.mapNotNull { mapToDto(it) as? PropertyDto },
                classlikes = doc.classlikes.mapNotNull { mapToDto(it) },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                companion = doc.companion?.let { mapToDto(it) as? ObjectDto },
                constructors = doc.constructors.mapNotNull { mapToDto(it) as? FunctionDto },
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                isExpectActual = doc.isExpectActual
            )
            is DProperty -> PropertyDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
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
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                type = mapBound(doc.type, displaySourceSets)
            )
            is DTypeParameter -> TypeParameterDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                bounds = doc.bounds.map { mapBound(it, displaySourceSets) },
                variantTypeParameter = mapProjection(doc.variantTypeParameter, displaySourceSets) as VarianceDto
            )
            is DTypeAlias -> TypeAliasDto(
                dri = doc.dri.toString(), name = doc.name, url = url,
                documentation = mapDocNodes(doc.documentation), sourceSets = mapSourceSets(doc.sourceSets),
                expectPresentInSet = doc.expectPresentInSet?.sourceSetID?.toString(), 
                extras = doc.extra.allOfType<Any>().map { it::class.java.simpleName },
                type = mapBound(doc.type, displaySourceSets),
                underlyingType = mapSourceSetDependent(doc.underlyingType) { ss, it -> mapBound(it, setOf(ss.toDisplaySourceSet())) },
                visibility = mapSourceSetDependent(doc.visibility) { _, it -> it.name },
                generics = doc.generics.mapNotNull { mapToDto(it) as? TypeParameterDto },
                sources = mapSourceSetDependent(doc.sources) { _, it -> it.path }
            )
            else -> null
        }
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
        
    fun resolveUrl(dri: DRI?): String? {
                if (dri == null) return null
                var url = locationProvider.resolve(dri, sourceSets, contextNode)
                
                // Use the config flag here
                if (replaceHtmlExtension && url != null && !url.startsWith("http")) {
                    url = url.replace(".html", ".json")
                }
                
                if (url != null) logger.debug("Resolved TYPE URL for $dri: $url")
                return url
            }
        return when (bound) {
            is TypeParameter -> TypeParameterBoundDto(bound.dri.toString(), bound.name, bound.presentableName, resolveUrl(bound.dri))
            is Nullable -> NullableDto(mapBound(bound.inner, sourceSets), resolveUrl(null)) // Delegate null url to prevent nesting URLs on wrappers
            is DefinitelyNonNullable -> DefinitelyNonNullableDto(mapBound(bound.inner, sourceSets))
            is TypeAliased -> TypeAliasedDto(mapBound(bound.typeAlias, sourceSets), mapBound(bound.inner, sourceSets), resolveUrl(null))
            is PrimitiveJavaType -> PrimitiveJavaTypeDto(bound.name)
            is JavaObject -> JavaObjectDto()
            is Void -> VoidDto()
            is Dynamic -> DynamicDto()
            is UnresolvedBound -> UnresolvedBoundDto(bound.name)
            is GenericTypeConstructor -> GenericTypeConstructorDto(
                dri = bound.dri.toString(), 
                projections = bound.projections.map { mapProjection(it, sourceSets) }, 
                presentableName = bound.presentableName,
                url = resolveUrl(bound.dri)
            )
            is FunctionalTypeConstructor -> FunctionalTypeConstructorDto(
                dri = bound.dri.toString(), 
                projections = bound.projections.map { mapProjection(it, sourceSets) }, 
                isExtensionFunction = bound.isExtensionFunction, 
                isSuspendable = bound.isSuspendable, 
                presentableName = bound.presentableName,
                url = resolveUrl(bound.dri)
            )
        }
    }

    private fun mapDocNodes(docs: SourceSetDependent<DocumentationNode>): Map<String, List<TagWrapperDto>> {
        return docs.entries.associate { (sourceSet, node) ->
            val tags = node.children.map { tagWrapper ->
                TagWrapperDto(
                    type = tagWrapper::class.java.simpleName, // E.g., "Description", "Param", "Return"
                    text = extractText(tagWrapper.root).trim(),
                    name = if (tagWrapper is NamedTagWrapper) tagWrapper.name else null
                )
            }
            sourceSet.sourceSetID.toString() to tags
        }
    }

    private fun extractText(tag: DocTag): String {
        return when (tag) {
            is Text -> tag.body
            is CodeInline -> "`" + tag.children.joinToString("") { extractText(it) } + "`"
            is CodeBlock -> "```\n" + tag.children.joinToString("") { extractText(it) } + "\n```"
            is P -> tag.children.joinToString("") { extractText(it) } + "\n\n"
            is Br -> "\n"
            else -> tag.children.joinToString("") { extractText(it) }
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