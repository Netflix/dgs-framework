/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.internal

import com.apollographql.federation.graphqljava.Federation
import com.netflix.graphql.dgs.*
import com.netflix.graphql.dgs.exceptions.DataFetcherInputArgumentSchemaMismatchException
import com.netflix.graphql.dgs.exceptions.DataFetcherSchemaMismatchException
import com.netflix.graphql.dgs.exceptions.DuplicateEntityFetcherException
import com.netflix.graphql.dgs.exceptions.InvalidDgsConfigurationException
import com.netflix.graphql.dgs.exceptions.InvalidDgsEntityFetcher
import com.netflix.graphql.dgs.exceptions.InvalidTypeResolverException
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.dgs.internal.utils.SelectionSetUtil
import graphql.TypeResolutionEnvironment
import graphql.execution.DataFetcherExceptionHandler
import graphql.language.FieldDefinition
import graphql.language.ImplementingTypeDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.ScalarTypeDefinition
import graphql.language.StringValue
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.parser.MultiSourceReader
import graphql.schema.Coercing
import graphql.schema.DataFetcherFactory
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeRuntimeWiring
import graphql.schema.visibility.DefaultGraphqlFieldVisibility
import graphql.schema.visibility.GraphqlFieldVisibility
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext
import org.springframework.core.BridgeMethodResolver
import org.springframework.core.MethodParameter
import org.springframework.core.annotation.MergedAnnotation
import org.springframework.core.annotation.MergedAnnotations
import org.springframework.core.annotation.SynthesizingMethodParameter
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourcePatternUtils
import org.springframework.util.ReflectionUtils
import java.io.IOException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull
import java.util.List.copyOf as immutableListOf
import java.util.Set.copyOf as immutableSetOf

/**
 * Main framework class that scans for components and configures a runtime executable schema.
 */
class DgsSchemaProvider
    @Autowired
    constructor(
        private val applicationContext: ApplicationContext,
        private val federationResolver: Optional<DgsFederationResolver>,
        private val existingTypeDefinitionRegistry: Optional<TypeDefinitionRegistry>,
        private val schemaLocations: List<String> = listOf(DEFAULT_SCHEMA_LOCATION),
        private val dataFetcherResultProcessors: List<DataFetcherResultProcessor> = emptyList(),
        private val dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler> = Optional.empty(),
        private val entityFetcherRegistry: EntityFetcherRegistry = EntityFetcherRegistry(),
        private val defaultDataFetcherFactory: Optional<DataFetcherFactory<*>> = Optional.empty(),
        private val methodDataFetcherFactory: MethodDataFetcherFactory,
        private val componentFilter: ((Any) -> Boolean)? = null,
        private val schemaWiringValidationEnabled: Boolean = true,
        private val enableEntityFetcherCustomScalarParsing: Boolean = false,
    ) {
        @Suppress("UNUSED_PARAMETER")
        @Deprecated("The mockProviders argument is no longer supported")
        constructor(
            applicationContext: ApplicationContext,
            federationResolver: Optional<DgsFederationResolver>,
            existingTypeDefinitionRegistry: Optional<TypeDefinitionRegistry>,
            mockProviders: Set<Any>,
            schemaLocations: List<String> = listOf(DEFAULT_SCHEMA_LOCATION),
            dataFetcherResultProcessors: List<DataFetcherResultProcessor> = emptyList(),
            dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler> = Optional.empty(),
            entityFetcherRegistry: EntityFetcherRegistry = EntityFetcherRegistry(),
            defaultDataFetcherFactory: Optional<DataFetcherFactory<*>> = Optional.empty(),
            methodDataFetcherFactory: MethodDataFetcherFactory,
            componentFilter: ((Any) -> Boolean)? = null,
            schemaWiringValidationEnabled: Boolean = true,
            enableEntityFetcherCustomScalarParsing: Boolean = false,
        ) : this(
            applicationContext,
            federationResolver,
            existingTypeDefinitionRegistry,
            schemaLocations,
            dataFetcherResultProcessors,
            dataFetcherExceptionHandler,
            entityFetcherRegistry,
            defaultDataFetcherFactory,
            methodDataFetcherFactory,
            componentFilter,
            schemaWiringValidationEnabled,
            enableEntityFetcherCustomScalarParsing,
        )

        private val dataFetcherInfo = AtomicReference(DataFetcherInfo(emptyList(), emptySet(), emptySet()))

        /**
         * Returns an immutable list of [DataFetcherReference]s that were identified after the schema was loaded.
         * The returned list will be unstable until the [schema] is fully loaded.
         */
        fun resolvedDataFetchers(): List<DataFetcherReference> = dataFetcherInfo.get().dataFetchers

        /**
         * Given a field, expressed as a GraphQL `<Type>.<field name>` tuple, return...
         * 1. `true` if the given field has _instrumentation_ enabled, or is missing an explicit setting.
         * 2. `false` if the given field has _instrumentation_ explicitly disabled.
         *
         * The method should be considered unstable until the [schema] is fully loaded.
         */
        fun isFieldTracingInstrumentationEnabled(field: String): Boolean = field in dataFetcherInfo.get().tracingEnabled

        /**
         * Given a field, expressed as a GraphQL `<Type>.<field name>` tuple, return...
         * 1. `true` if the given field has _instrumentation_ enabled, or is missing an explicit setting.
         * 2. `false` if the given field has _instrumentation_ explicitly disabled.
         *
         * The method should be considered unstable until the [schema] is fully loaded.
         */
        fun isFieldMetricsInstrumentationEnabled(field: String): Boolean = field in dataFetcherInfo.get().metricsEnabled

        fun schema(
            @Language("GraphQL") schema: String? = null,
            fieldVisibility: GraphqlFieldVisibility = DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY,
            schemaResources: Set<Resource> = emptySet(),
            showSdlComments: Boolean = true,
        ): SchemaProviderResult {
            val (result, dataFetcherInfo) = computeSchema(schema, fieldVisibility, schemaResources, showSdlComments)
            this.dataFetcherInfo.set(dataFetcherInfo)
            return result
        }

        private fun computeSchema(
            schema: String? = null,
            fieldVisibility: GraphqlFieldVisibility,
            schemaResources: Set<Resource> = emptySet(),
            showSdlComments: Boolean,
        ): Pair<SchemaProviderResult, DataFetcherInfo> {
            val startTime = System.currentTimeMillis()
            val dgsComponents =
                applicationContext
                    .getBeansWithAnnotation<DgsComponent>()
                    .values
                    .asSequence()
                    .let { beans -> if (componentFilter != null) beans.filter(componentFilter) else beans }
                    .map { bean -> DgsBean(bean) }
                    .toList()

            var mergedRegistry =
                if (schema == null) {
                    val hasDynamicTypeRegistry = dgsComponents.any { it.annotatedMethods<DgsTypeDefinitionRegistry>().any() }
                    val readerBuilder =
                        MultiSourceReader
                            .newMultiSourceReader()
                            .trackData(false)
                    for (schemaFile in findSchemaFiles(hasDynamicTypeRegistry)) {
                        readerBuilder.reader(schemaFile.inputStream.reader(), schemaFile.filename)
                        // Add a reader that inserts a newline between schema files to avoid issues when
                        // the source files aren't newline-terminated.
                        readerBuilder.reader("\n".reader(), "newline")
                    }

                    for (resource in schemaResources) {
                        readerBuilder.reader(resource.inputStream.reader(), resource.filename)
                    }
                    SchemaParser().parse(readerBuilder.build())
                } else {
                    SchemaParser().parse(schema)
                }

            if (existingTypeDefinitionRegistry.isPresent) {
                mergedRegistry = mergedRegistry.merge(existingTypeDefinitionRegistry.get())
            }

            val codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(fieldVisibility)
            if (defaultDataFetcherFactory.isPresent) {
                codeRegistryBuilder.defaultDataFetcher(defaultDataFetcherFactory.get())
            }

            val runtimeWiringBuilder =
                RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistryBuilder).fieldVisibility(fieldVisibility)

            val dgsCodeRegistryBuilder = DgsCodeRegistryBuilder(dataFetcherResultProcessors, codeRegistryBuilder, applicationContext)

            dgsComponents
                .asSequence()
                .mapNotNull { dgsComponent -> invokeDgsTypeDefinitionRegistry(dgsComponent, mergedRegistry) }
                .fold(mergedRegistry) { a, b -> a.merge(b) }
            findScalars(applicationContext, runtimeWiringBuilder)
            findDirectives(applicationContext, runtimeWiringBuilder)

            val dataFetcherInfo = MutableDataFetcherInfo()
            findDataFetchers(dgsComponents, dgsCodeRegistryBuilder, mergedRegistry, dataFetcherInfo)
            findTypeResolvers(dgsComponents, runtimeWiringBuilder, mergedRegistry)

            dgsComponents.forEach { dgsComponent ->
                invokeDgsCodeRegistry(
                    dgsComponent,
                    codeRegistryBuilder,
                    mergedRegistry,
                )
            }

            runtimeWiringBuilder.codeRegistry(codeRegistryBuilder.build())

            dgsComponents.forEach { dgsComponent -> invokeDgsRuntimeWiring(dgsComponent, runtimeWiringBuilder) }

            val runtimeWiring = runtimeWiringBuilder.build()

            findEntityFetchers(dgsComponents, mergedRegistry, runtimeWiring, dataFetcherInfo)

            val federationResolverInstance =
                federationResolver.orElseGet {
                    DefaultDgsFederationResolver(
                        entityFetcherRegistry,
                        dataFetcherExceptionHandler,
                        applicationContext,
                    )
                }

            val entityFetcher = federationResolverInstance.entitiesFetcher()
            val typeResolver = federationResolverInstance.typeResolver()
            val schemaOptions = SchemaGenerator.Options.defaultOptions().useCommentsAsDescriptions(showSdlComments)

            var graphQLSchema =
                Federation
                    .transform(mergedRegistry, runtimeWiring, schemaOptions)
                    .fetchEntities(entityFetcher)
                    .resolveEntityType(typeResolver)
                    .build()

            val endTime = System.currentTimeMillis()
            val totalTime = endTime - startTime
            logger.debug("DGS initialized schema in {}ms", totalTime)

            return SchemaProviderResult(graphQLSchema, runtimeWiring) to dataFetcherInfo.toImmutable()
        }

        private fun invokeDgsTypeDefinitionRegistry(
            dgsComponent: DgsBean,
            registry: TypeDefinitionRegistry,
        ): TypeDefinitionRegistry? =
            dgsComponent
                .annotatedMethods<DgsTypeDefinitionRegistry>()
                .map { method ->
                    if (method.returnType != TypeDefinitionRegistry::class.java) {
                        throw InvalidDgsConfigurationException(
                            "Method annotated with @DgsTypeDefinitionRegistry must have return type TypeDefinitionRegistry",
                        )
                    }
                    if (method.parameterCount == 1 && method.parameterTypes[0] == TypeDefinitionRegistry::class.java) {
                        ReflectionUtils.invokeMethod(method, dgsComponent.instance, registry) as TypeDefinitionRegistry
                    } else {
                        ReflectionUtils.invokeMethod(method, dgsComponent.instance) as TypeDefinitionRegistry
                    }
                }.reduceOrNull { a, b -> a.merge(b) }

        private fun invokeDgsCodeRegistry(
            dgsComponent: DgsBean,
            codeRegistryBuilder: GraphQLCodeRegistry.Builder,
            registry: TypeDefinitionRegistry,
        ) {
            val dgsCodeRegistryBuilder = DgsCodeRegistryBuilder(dataFetcherResultProcessors, codeRegistryBuilder, applicationContext)

            dgsComponent
                .annotatedMethods<DgsCodeRegistry>()
                .forEach { method ->
                    if (method.returnType != GraphQLCodeRegistry.Builder::class.java &&
                        method.returnType != DgsCodeRegistryBuilder::class.java
                    ) {
                        throw InvalidDgsConfigurationException(
                            "Method annotated with @DgsCodeRegistry must have return type GraphQLCodeRegistry.Builder or DgsCodeRegistryBuilder",
                        )
                    }

                    if (method.parameterCount != 2 ||
                        method.parameterTypes[0] != method.returnType ||
                        // Check that the first argument is of type DgsCodeRegistryBuilder or GraphQLCodeRegistry.Builder and the return type is the same
                        method.parameterTypes[1] != TypeDefinitionRegistry::class.java
                    ) {
                        throw InvalidDgsConfigurationException(
                            "Method annotated with @DgsCodeRegistry must accept the following arguments: GraphQLCodeRegistry.Builder or DgsCodeRegistryBuilder, and TypeDefinitionRegistry. ${dgsComponent.javaClass.name}.${method.name} has the following arguments: ${method.parameterTypes.joinToString()}",
                        )
                    }

                    if (method.returnType == DgsCodeRegistryBuilder::class.java) {
                        ReflectionUtils.invokeMethod(method, dgsComponent.instance, dgsCodeRegistryBuilder, registry)
                    } else if (method.returnType == GraphQLCodeRegistry.Builder::class.java) {
                        ReflectionUtils.invokeMethod(method, dgsComponent.instance, codeRegistryBuilder, registry)
                    }
                }
        }

        private fun invokeDgsRuntimeWiring(
            dgsComponent: DgsBean,
            runtimeWiringBuilder: RuntimeWiring.Builder,
        ) {
            dgsComponent
                .annotatedMethods<DgsRuntimeWiring>()
                .forEach { method ->
                    if (method.returnType != RuntimeWiring.Builder::class.java) {
                        throw InvalidDgsConfigurationException(
                            "Method annotated with @DgsRuntimeWiring must have return type RuntimeWiring.Builder",
                        )
                    }

                    if (method.parameterCount != 1 || method.parameterTypes[0] != RuntimeWiring.Builder::class.java) {
                        throw InvalidDgsConfigurationException(
                            "Method annotated with @DgsRuntimeWiring must accept an argument of type RuntimeWiring.Builder. ${dgsComponent.javaClass.name}.${method.name} has the following arguments: ${method.parameterTypes.joinToString()}",
                        )
                    }

                    ReflectionUtils.invokeMethod(method, dgsComponent.instance, runtimeWiringBuilder)
                }
        }

        private fun findDataFetchers(
            dgsComponents: Collection<DgsBean>,
            codeRegistryBuilder: DgsCodeRegistryBuilder,
            typeDefinitionRegistry: TypeDefinitionRegistry,
            dataFetcherInfo: MutableDataFetcherInfo,
        ) {
            dgsComponents.forEach { dgsComponent ->
                dgsComponent.methods
                    .map { method ->
                        val mergedAnnotations =
                            MergedAnnotations
                                .from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        method to mergedAnnotations
                    }.filter { (_, mergedAnnotations) -> mergedAnnotations.isPresent(DgsData::class.java) }
                    .forEach { (method, mergedAnnotations) ->
                        val filteredMergedAnnotations =
                            mergedAnnotations
                                .stream(DgsData::class.java)
                                .filter {
                                    AopUtils.getTargetClass((it.source as Method).declaringClass) ==
                                        AopUtils.getTargetClass(method.declaringClass)
                                }.toList()
                        filteredMergedAnnotations.forEach { dgsDataAnnotation ->
                            registerDataFetcher(
                                typeDefinitionRegistry,
                                codeRegistryBuilder,
                                dgsComponent,
                                method,
                                dgsDataAnnotation,
                                mergedAnnotations,
                                dataFetcherInfo,
                            )
                        }
                    }
            }
        }

        private fun registerDataFetcher(
            typeDefinitionRegistry: TypeDefinitionRegistry,
            codeRegistryBuilder: DgsCodeRegistryBuilder,
            dgsComponent: DgsBean,
            method: Method,
            dgsDataAnnotation: MergedAnnotation<DgsData>,
            mergedAnnotations: MergedAnnotations,
            dataFetcherInfo: MutableDataFetcherInfo,
        ) {
            val field = dgsDataAnnotation.getString("field").ifEmpty { method.name }
            val parentType = dgsDataAnnotation.getString("parentType")

            if (dataFetcherInfo.dataFetchers.any { it.parentType == parentType && it.field == field }) {
                logger.error("Duplicate data fetchers registered for {}.{}", parentType, field)
                throw InvalidDgsConfigurationException("Duplicate data fetchers registered for $parentType.$field")
            }

            dataFetcherInfo.dataFetchers += DataFetcherReference(dgsComponent.instance, method, mergedAnnotations, parentType, field)

            val enableTracingInstrumentation =
                if (method.isAnnotationPresent(DgsEnableDataFetcherInstrumentation::class.java)) {
                    val dgsEnableDataFetcherInstrumentation =
                        method.getAnnotation(DgsEnableDataFetcherInstrumentation::class.java)
                    dgsEnableDataFetcherInstrumentation.value
                } else {
                    method.returnType != CompletionStage::class.java && method.returnType != CompletableFuture::class.java
                }
            if (enableTracingInstrumentation) {
                dataFetcherInfo.tracingEnabled += "$parentType.$field"
            }

            val enableMetricsInstrumentation =
                if (method.isAnnotationPresent(DgsEnableDataFetcherInstrumentation::class.java)) {
                    val dgsEnableDataFetcherInstrumentation =
                        method.getAnnotation(DgsEnableDataFetcherInstrumentation::class.java)
                    dgsEnableDataFetcherInstrumentation.value
                } else {
                    true
                }
            if (enableMetricsInstrumentation) {
                dataFetcherInfo.metricsEnabled += "$parentType.$field"
            }

            val methodClassName = method.declaringClass.name
            try {
                val typeDefinition = typeDefinitionRegistry.getType(parentType).getOrNull()
                if (typeDefinition == null) {
                    logger.error(
                        "Parent type {} not found, but it was referenced in {} in @DgsData annotation for field {}",
                        parentType,
                        methodClassName,
                        field,
                    )
                    throw InvalidDgsConfigurationException(
                        "Parent type $parentType not found, but it was referenced on $methodClassName in @DgsData annotation for field $field",
                    )
                }
                when (typeDefinition) {
                    is InterfaceTypeDefinition -> {
                        if (schemaWiringValidationEnabled) {
                            val matchingField =
                                getMatchingFieldOnInterfaceOrExtensions(
                                    methodClassName,
                                    typeDefinition,
                                    field,
                                    typeDefinitionRegistry,
                                    parentType,
                                )
                            checkInputArgumentsAreValid(
                                method,
                                matchingField.inputValueDefinitions.mapTo(mutableSetOf()) { it.name },
                            )
                        }
                        val implementationsOf = typeDefinitionRegistry.getImplementationsOf(typeDefinition)
                        implementationsOf.forEach { implType ->
                            // if we have a datafetcher explicitly defined for a parentType/field, use that and do not
                            // register the base implementation for interfaces
                            val coordinates = FieldCoordinates.coordinates(implType.name, field)
                            if (!codeRegistryBuilder.hasDataFetcher(coordinates)) {
                                val dataFetcher = methodDataFetcherFactory.createDataFetcher(dgsComponent.instance, method, coordinates)
                                codeRegistryBuilder.dataFetcher(coordinates, dataFetcher)
                                if (enableTracingInstrumentation) {
                                    dataFetcherInfo.tracingEnabled += coordinates.toString()
                                }
                                if (enableMetricsInstrumentation) {
                                    dataFetcherInfo.metricsEnabled += coordinates.toString()
                                }
                            }
                        }
                    }
                    is UnionTypeDefinition -> {
                        typeDefinition.memberTypes.asSequence().filterIsInstance<TypeName>().forEach { memberType ->
                            val coordinates = FieldCoordinates.coordinates(memberType.name, field)
                            val dataFetcher = methodDataFetcherFactory.createDataFetcher(dgsComponent.instance, method, coordinates)
                            codeRegistryBuilder.dataFetcher(coordinates, dataFetcher)
                            if (enableTracingInstrumentation) {
                                dataFetcherInfo.tracingEnabled += coordinates.toString()
                            }
                            if (enableMetricsInstrumentation) {
                                dataFetcherInfo.metricsEnabled += coordinates.toString()
                            }
                        }
                    }
                    is ObjectTypeDefinition -> {
                        if (schemaWiringValidationEnabled) {
                            val matchingField =
                                getMatchingFieldOnObjectOrExtensions(
                                    methodClassName,
                                    typeDefinition,
                                    field,
                                    typeDefinitionRegistry,
                                    parentType,
                                )
                            checkInputArgumentsAreValid(
                                method,
                                matchingField.inputValueDefinitions
                                    .asSequence()
                                    .map { it.name }
                                    .toSet(),
                            )
                        }

                        val coordinates = FieldCoordinates.coordinates(parentType, field)
                        val dataFetcher = methodDataFetcherFactory.createDataFetcher(dgsComponent.instance, method, coordinates)
                        codeRegistryBuilder.dataFetcher(coordinates, dataFetcher)
                    }
                    else -> {
                        throw InvalidDgsConfigurationException(
                            "Parent type $parentType referenced on $methodClassName in " +
                                "@DgsData annotation for field $field must be either an interface, a union, or an object.",
                        )
                    }
                }
            } catch (ex: Exception) {
                logger.error("Invalid parent type {}", parentType)
                throw ex
            }
        }

        private fun getMatchingFieldOnObjectOrExtensions(
            methodClassName: String,
            type: ObjectTypeDefinition,
            field: String,
            typeDefinitionRegistry: TypeDefinitionRegistry,
            parentType: String,
        ): FieldDefinition =
            type.fieldDefinitions.firstOrNull { it.name == field }
                ?: typeDefinitionRegistry
                    .objectTypeExtensions()
                    .getOrDefault(parentType, emptyList())
                    .asSequence()
                    .flatMap { it.fieldDefinitions.filter { f -> f.name == field } }
                    .firstOrNull()
                ?: throw DataFetcherSchemaMismatchException(
                    "@DgsData in $methodClassName on field $field references " +
                        "object type `$parentType` it has no field named `$field`. All data fetchers registered with " +
                        "@DgsData|@DgsQuery|@DgsMutation|@DgsSubscription must match a field in the schema.",
                )

        private fun getMatchingFieldOnInterfaceOrExtensions(
            methodClassName: String,
            type: InterfaceTypeDefinition,
            field: String,
            typeDefinitionRegistry: TypeDefinitionRegistry,
            parentType: String,
        ): FieldDefinition =
            type.fieldDefinitions.firstOrNull { it.name == field }
                ?: typeDefinitionRegistry
                    .interfaceTypeExtensions()
                    .getOrDefault(parentType, emptyList())
                    .flatMap { it.fieldDefinitions.filter { f -> f.name == field } }
                    .firstOrNull()
                ?: throw DataFetcherSchemaMismatchException(
                    "@DgsData in $methodClassName on field `$field` references " +
                        "interface `$parentType` it has no field named `$field`. All data fetchers registered with @DgsData " +
                        "must match a field in the schema.",
                )

        private fun checkInputArgumentsAreValid(
            method: Method,
            argumentNames: Set<String>,
        ) {
            val bridgedMethod: Method = BridgeMethodResolver.findBridgedMethod(method)
            val methodParameters: List<MethodParameter> =
                bridgedMethod.parameters.map { parameter ->
                    val methodParameter = SynthesizingMethodParameter.forParameter(parameter)
                    methodParameter.initParameterNameDiscovery(methodDataFetcherFactory.parameterNameDiscoverer)
                    methodParameter
                }

            methodParameters.forEach { m ->
                val selectedArgResolver = methodDataFetcherFactory.getSelectedArgumentResolver(m) ?: return@forEach
                if (selectedArgResolver is InputArgumentResolver) {
                    val argName = selectedArgResolver.resolveArgumentName(m)
                    if (!argumentNames.contains(argName)) {
                        val paramName = m.parameterName ?: return@forEach
                        val arguments =
                            if (argumentNames.isNotEmpty()) {
                                "Found the following argument(s) in the schema: " + argumentNames.joinToString(prefix = "[", postfix = "]")
                            } else {
                                "No arguments on the field are defined in the schema."
                            }

                        throw DataFetcherInputArgumentSchemaMismatchException(
                            "@InputArgument(name = \"$argName\") defined in ${method.declaringClass} in method `${method.name}` " +
                                "on parameter named `$paramName` has no matching argument with name `$argName` in the GraphQL schema. " +
                                arguments,
                        )
                    }
                }
            }
        }

        private fun findEntityFetchers(
            dgsComponents: Collection<DgsBean>,
            registry: TypeDefinitionRegistry,
            runtimeWiring: RuntimeWiring,
            dataFetcherInfo: MutableDataFetcherInfo,
        ) {
            dgsComponents.forEach { dgsComponent ->
                dgsComponent
                    .annotatedMethods<DgsEntityFetcher>()
                    .forEach { method ->
                        val dgsEntityFetcherAnnotation = method.getAnnotation(DgsEntityFetcher::class.java)

                        if (method.parameterCount > 2) {
                            throw InvalidDgsEntityFetcher(
                                "@DgsEntityFetcher ${dgsComponent::class.java.name}.${method.name} is invalid. A DgsEntityFetcher can only accept up to 2 arguments",
                            )
                        }

                        if (!method.parameterTypes.any { it.isAssignableFrom(Map::class.java) }) {
                            throw InvalidDgsEntityFetcher(
                                "@DgsEntityFetcher ${dgsComponent::class.java.name}.${method.name} is invalid. A DgsEntityFetcher must accept an argument of type Map<String, Object>",
                            )
                        }

                        if (method.parameterTypes.any {
                                !it.isAssignableFrom(Map::class.java) &&
                                    !it.isAssignableFrom(DgsDataFetchingEnvironment::class.java)
                            }
                        ) {
                            throw InvalidDgsEntityFetcher(
                                "@DgsEntityFetcher ${dgsComponent::class.java.name}.${method.name} is invalid. A DgsEntityFetcher can only accept arguments of type Map<String, Object> or DgsDataFetchingEnvironment",
                            )
                        }

                        val entityFetcherTypeName = dgsEntityFetcherAnnotation.name
                        val coordinateName = "_entities.$entityFetcherTypeName"

                        val mergedAnnotations =
                            MergedAnnotations
                                .from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        dataFetcherInfo.dataFetchers +=
                            DataFetcherReference(dgsComponent.instance, method, mergedAnnotations, "Query", coordinateName)

                        val enableInstrumentation =
                            method.getAnnotation(DgsEnableDataFetcherInstrumentation::class.java)?.value
                                ?: false
                        if (enableInstrumentation) {
                            dataFetcherInfo.tracingEnabled += coordinateName
                            dataFetcherInfo.metricsEnabled += coordinateName
                        }

                        // Throw if an entity fetcher for the same type was already registered
                        if (entityFetcherRegistry.entityFetchers.contains(entityFetcherTypeName)) {
                            val firstEntityFetcher = entityFetcherRegistry.entityFetchers[entityFetcherTypeName]!!

                            // It's possible the schema() method is invoked multiple times, so check if the second entity fetcher is different from the existing one.
                            if (firstEntityFetcher != dgsComponent.instance to method) {
                                throw DuplicateEntityFetcherException(
                                    entityFetcherTypeName,
                                    firstEntityFetcher.first::class.java,
                                    firstEntityFetcher.second,
                                    dgsComponent.instance::class.java,
                                    method,
                                )
                            }
                        }

                        entityFetcherRegistry.entityFetchers[entityFetcherTypeName] = dgsComponent.instance to method

                        val type = registry.getType(entityFetcherTypeName)

                        if (enableEntityFetcherCustomScalarParsing) {
                            type.ifPresent {
                                val typeDefinition = it as? ImplementingTypeDefinition
                                val keyDirective = it.directives.find { directive -> directive.name == "key" }

                                keyDirective?.let { directive ->
                                    val fields = directive.argumentsByName["fields"]

                                    if (fields != null && fields.value is StringValue) {
                                        val fieldsSelection = (fields.value as StringValue).value
                                        val paths = SelectionSetUtil.toPaths(fieldsSelection)

                                        entityFetcherRegistry.entityFetcherInputMappings[entityFetcherTypeName] =
                                            paths
                                                .asSequence()
                                                .mapNotNull { path ->
                                                    val coercing = traverseType(path.iterator(), typeDefinition, registry, runtimeWiring)
                                                    if (coercing != null) {
                                                        path to coercing
                                                    } else {
                                                        null
                                                    }
                                                }.toMap()
                                    }
                                }
                            }
                        }
                    }
            }
        }

        private fun traverseType(
            path: Iterator<String>,
            type: ImplementingTypeDefinition<*>?,
            registry: TypeDefinitionRegistry,
            runtimeWiring: RuntimeWiring,
        ): Coercing<*, *>? {
            if (type == null || !path.hasNext()) {
                return null
            }

            val item = path.next()
            val fieldDefinition = type.fieldDefinitions.firstOrNull { it.name == item }
            val fieldDefinitionType = fieldDefinition?.type

            if (fieldDefinitionType is TypeName) {
                val fieldType = registry.getType(fieldDefinitionType.name).getOrNull()

                if (fieldType != null) {
                    return when (fieldType) {
                        is ObjectTypeDefinition -> traverseType(path, fieldType, registry, runtimeWiring)
                        is ScalarTypeDefinition -> runtimeWiring.scalars[fieldType.name]?.coercing
                        else -> null
                    }
                }
            }

            return null
        }

        private fun findTypeResolvers(
            dgsComponents: Collection<DgsBean>,
            runtimeWiringBuilder: RuntimeWiring.Builder,
            mergedRegistry: TypeDefinitionRegistry,
        ) {
            val registeredTypeResolvers = mutableSetOf<String>()

            dgsComponents.forEach { dgsComponent ->
                dgsComponent
                    .annotatedMethods<DgsTypeResolver>()
                    .forEach { method ->
                        val annotation = method.getAnnotation(DgsTypeResolver::class.java)

                        if (method.returnType != String::class.java) {
                            throw InvalidTypeResolverException("@DgsTypeResolvers must return String")
                        }

                        if (method.parameterCount != 1) {
                            throw InvalidTypeResolverException("@DgsTypeResolvers must take exactly one parameter")
                        }

                        if (!mergedRegistry.hasType(TypeName(annotation.name))) {
                            throw InvalidTypeResolverException("could not find type name '${annotation.name}' in schema")
                        }

                        var overrideTypeResolver = false
                        val defaultTypeResolver = method.getAnnotation(DgsDefaultTypeResolver::class.java)
                        if (defaultTypeResolver != null) {
                            overrideTypeResolver =
                                dgsComponents.any { component ->
                                    component != dgsComponent &&
                                        component.annotatedMethods<DgsTypeResolver>().any { method ->
                                            method.getAnnotation(DgsTypeResolver::class.java).name == annotation.name
                                        }
                                }
                        }
                        // do not add the default resolver if another resolver with the same name is present
                        if (defaultTypeResolver == null || !overrideTypeResolver) {
                            registeredTypeResolvers += annotation.name

                            val dgsComponentInstance = dgsComponent.instance
                            runtimeWiringBuilder.type(
                                TypeRuntimeWiring
                                    .newTypeWiring(annotation.name)
                                    .typeResolver { env: TypeResolutionEnvironment ->
                                        val typeName: String? =
                                            ReflectionUtils.invokeMethod(method, dgsComponentInstance, env.getObject()) as String?
                                        if (typeName != null) {
                                            env.schema.getObjectType(typeName)
                                        } else {
                                            null
                                        }
                                    },
                            )
                        }
                    }
            }

            // Add a fallback type resolver for types that don't have a type resolver registered.
            // This works when the Java type has the same name as the GraphQL type.
            // Check for unregistered interface types
            val unregisteredInterfaceTypes =
                mergedRegistry
                    .types()
                    .asSequence()
                    .filter { (_, typeDef) -> typeDef is InterfaceTypeDefinition }
                    .map { (name, _) -> name }
                    .filter { it !in registeredTypeResolvers }
            checkTypeResolverExists(unregisteredInterfaceTypes, runtimeWiringBuilder, "interface")

            // Check for unregistered union types
            val unregisteredUnionTypes =
                mergedRegistry
                    .types()
                    .asSequence()
                    .filter { (_, typeDef) -> typeDef is UnionTypeDefinition }
                    .map { (name, _) -> name }
                    .filter { it !in registeredTypeResolvers }
            checkTypeResolverExists(unregisteredUnionTypes, runtimeWiringBuilder, "union")
        }

        private fun checkTypeResolverExists(
            unregisteredTypes: Sequence<String>,
            runtimeWiringBuilder: RuntimeWiring.Builder,
            typeName: String,
        ) {
            unregisteredTypes.forEach {
                runtimeWiringBuilder.type(
                    TypeRuntimeWiring
                        .newTypeWiring(it)
                        .typeResolver { env: TypeResolutionEnvironment ->
                            val instance = env.getObject<Any>()
                            val resolvedType = env.schema.getObjectType(instance::class.java.simpleName)
                            resolvedType
                                ?: throw InvalidTypeResolverException(
                                    "The default type resolver could not find a suitable Java type for GraphQL $typeName type `$it`. Provide a @DgsTypeResolver for `${instance::class.java.simpleName}`.",
                                )
                        },
                )
            }
        }

        private fun findScalars(
            applicationContext: ApplicationContext,
            runtimeWiringBuilder: RuntimeWiring.Builder,
        ) {
            applicationContext.getBeansWithAnnotation<DgsScalar>().values.forEach { scalarComponent ->
                val annotation = scalarComponent::class.java.getAnnotation(DgsScalar::class.java)
                when (scalarComponent) {
                    is Coercing<*, *> ->
                        runtimeWiringBuilder.scalar(
                            GraphQLScalarType
                                .newScalar()
                                .name(annotation.name)
                                .coercing(scalarComponent)
                                .build(),
                        )
                    else -> throw RuntimeException("Invalid @DgsScalar type: the class must implement graphql.schema.Coercing")
                }
            }
        }

        private fun findDirectives(
            applicationContext: ApplicationContext,
            runtimeWiringBuilder: RuntimeWiring.Builder,
        ) {
            applicationContext.getBeansWithAnnotation<DgsDirective>().values.forEach { directiveComponent ->
                val annotation = AopUtils.getTargetClass(directiveComponent).getAnnotation(DgsDirective::class.java)
                when (directiveComponent) {
                    is SchemaDirectiveWiring ->
                        if (annotation.name.isNotBlank()) {
                            runtimeWiringBuilder.directive(annotation.name, directiveComponent)
                        } else {
                            runtimeWiringBuilder.directiveWiring(directiveComponent)
                        }
                    else -> throw RuntimeException(
                        "Invalid @DgsDirective type: the class must implement graphql.schema.idl.SchemaDirectiveWiring",
                    )
                }
            }
        }

        internal fun findSchemaFiles(hasDynamicTypeRegistry: Boolean = false): List<Resource> {
            val resolver = ResourcePatternUtils.getResourcePatternResolver(applicationContext)
            val schemas =
                schemaLocations
                    .asSequence()
                    .flatMap { resolver.getResources(it).asSequence() }
                    .toMutableSet()

            if (schemas.isEmpty()) {
                if (existingTypeDefinitionRegistry.isPresent || hasDynamicTypeRegistry) {
                    logger.info("No schema files found, but a schema was provided as an TypeDefinitionRegistry")
                } else {
                    logger.error(
                        "No schema files found in {}. Define schema locations with property dgs.graphql.schema-locations",
                        schemaLocations,
                    )
                    throw NoSchemaFoundException()
                }
            }

            val metaInfSchemas =
                try {
                    resolver.getResources("classpath*:META-INF/schema/**/*.graphql*")
                } catch (ex: IOException) {
                    arrayOf<Resource>()
                }

            schemas += metaInfSchemas

            return schemas.filter { resource ->
                val filename = resource.filename ?: return@filter false
                filename.endsWith(".graphql", ignoreCase = true) || filename.endsWith(".graphqls", ignoreCase = true)
            }
        }

        companion object {
            const val DEFAULT_SCHEMA_LOCATION = "classpath*:schema/**/*.graphql*"
            private val logger: Logger = LoggerFactory.getLogger(DgsSchemaProvider::class.java)

            private data class DgsBean(
                val instance: Any,
                val targetClass: Class<*> = AopUtils.getTargetClass(instance),
            ) {
                private val cachedMethods = ReflectionUtils.getUniqueDeclaredMethods(targetClass, ReflectionUtils.USER_DECLARED_METHODS)
                val methods: Sequence<Method> get() = cachedMethods.asSequence()

                inline fun <reified T : Annotation> annotatedMethods(): Sequence<Method> =
                    methods.filter { it.isAnnotationPresent(T::class.java) }
            }

            private class MutableDataFetcherInfo(
                val dataFetchers: MutableList<DataFetcherReference> = mutableListOf(),
                val tracingEnabled: MutableSet<String> = mutableSetOf(),
                val metricsEnabled: MutableSet<String> = mutableSetOf(),
            ) {
                fun toImmutable(): DataFetcherInfo =
                    DataFetcherInfo(
                        dataFetchers = immutableListOf(dataFetchers),
                        tracingEnabled = immutableSetOf(tracingEnabled),
                        metricsEnabled = immutableSetOf(metricsEnabled),
                    )
            }

            @JvmRecord
            private data class DataFetcherInfo(
                val dataFetchers: List<DataFetcherReference>,
                val tracingEnabled: Set<String>,
                val metricsEnabled: Set<String>,
            )
        }
    }

data class SchemaProviderResult(
    val graphQLSchema: GraphQLSchema,
    val runtimeWiring: RuntimeWiring,
)
