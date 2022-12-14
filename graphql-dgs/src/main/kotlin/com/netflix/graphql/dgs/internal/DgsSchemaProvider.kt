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
import com.netflix.graphql.dgs.exceptions.InvalidDgsConfigurationException
import com.netflix.graphql.dgs.exceptions.InvalidTypeResolverException
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory
import com.netflix.graphql.mocking.DgsSchemaTransformer
import com.netflix.graphql.mocking.MockProvider
import graphql.TypeResolutionEnvironment
import graphql.execution.DataFetcherExceptionHandler
import graphql.language.InterfaceTypeDefinition
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.schema.Coercing
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactories
import graphql.schema.DataFetcherFactory
import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaDirectiveWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeRuntimeWiring
import graphql.schema.visibility.DefaultGraphqlFieldVisibility
import graphql.schema.visibility.GraphqlFieldVisibility
import org.intellij.lang.annotations.Language
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext
import org.springframework.core.annotation.MergedAnnotation
import org.springframework.core.annotation.MergedAnnotations
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.ReflectionUtils
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Main framework class that scans for components and configures a runtime executable schema.
 */
class DgsSchemaProvider(
    private val applicationContext: ApplicationContext,
    private val federationResolver: Optional<DgsFederationResolver>,
    private val existingTypeDefinitionRegistry: Optional<TypeDefinitionRegistry>,
    private val mockProviders: Set<MockProvider> = emptySet(),
    private val schemaLocations: List<String> = listOf(DEFAULT_SCHEMA_LOCATION),
    private val dataFetcherResultProcessors: List<DataFetcherResultProcessor> = emptyList(),
    private val dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler> = Optional.empty(),
    private val entityFetcherRegistry: EntityFetcherRegistry = EntityFetcherRegistry(),
    private val defaultDataFetcherFactory: Optional<DataFetcherFactory<*>> = Optional.empty(),
    private val methodDataFetcherFactory: MethodDataFetcherFactory,
    private val componentFilter: (Any) -> Boolean = { true }
) {

    private val schemaReadWriteLock = ReentrantReadWriteLock()

    private val dataFetcherInstrumentationEnabled = mutableMapOf<String, Boolean>()

    private val dataFetchers = mutableListOf<DataFetcherReference>()

    /**
     * Returns an immutable list of [DataFetcherReference]s that were identified after the schema was loaded.
     * The returned list will be unstable until the [schema] is fully loaded.
     */
    fun resolvedDataFetchers(): List<DataFetcherReference> {
        return schemaReadWriteLock.read {
            dataFetchers.toList()
        }
    }

    /**
     * Given a field, expressed as a GraphQL `<Type>.<field name>` tuple, return...
     * 1. `true` if the given field has _instrumentation_ enabled, or is missing an explicit setting.
     * 2. `false` if the given field has _instrumentation_ explicitly disabled.
     *
     * The method should be considered unstable until the [schema] is fully loaded.
     */
    fun isFieldInstrumentationEnabled(field: String): Boolean {
        return schemaReadWriteLock.read {
            dataFetcherInstrumentationEnabled.getOrDefault(field, true)
        }
    }

    fun schema(
        @Language("GraphQL") schema: String? = null,
        fieldVisibility: GraphqlFieldVisibility = DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY
    ): GraphQLSchema {
        schemaReadWriteLock.write {
            dataFetchers.clear()
            dataFetcherInstrumentationEnabled.clear()
            return computeSchema(schema, fieldVisibility)
        }
    }

    private fun computeSchema(schema: String? = null, fieldVisibility: GraphqlFieldVisibility): GraphQLSchema {
        val startTime = System.currentTimeMillis()
        val dgsComponents =
            applicationContext.getBeansWithAnnotation(DgsComponent::class.java).values.filter(componentFilter)
        val hasDynamicTypeRegistry =
            dgsComponents.any { it.javaClass.methods.any { m -> m.isAnnotationPresent(DgsTypeDefinitionRegistry::class.java) } }

        var mergedRegistry = if (schema == null) {
            findSchemaFiles(hasDynamicTypeRegistry = hasDynamicTypeRegistry).asSequence().map {
                InputStreamReader(it.inputStream, StandardCharsets.UTF_8).use { reader -> SchemaParser().parse(reader) }
            }.fold(TypeDefinitionRegistry()) { a, b -> a.merge(b) }
        } else {
            SchemaParser().parse(schema)
        }

        if (existingTypeDefinitionRegistry.isPresent) {
            mergedRegistry = mergedRegistry.merge(existingTypeDefinitionRegistry.get())
        }

        val federationResolverInstance =
            federationResolver.orElseGet {
                DefaultDgsFederationResolver(
                    entityFetcherRegistry,
                    dataFetcherExceptionHandler
                )
            }

        val entityFetcher = federationResolverInstance.entitiesFetcher()
        val typeResolver = federationResolverInstance.typeResolver()
        val codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(fieldVisibility)
        if (defaultDataFetcherFactory.isPresent) {
            codeRegistryBuilder.defaultDataFetcher(defaultDataFetcherFactory.get())
        }

        val runtimeWiringBuilder =
            RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistryBuilder).fieldVisibility(fieldVisibility)

        dgsComponents.asSequence()
            .mapNotNull { dgsComponent -> invokeDgsTypeDefinitionRegistry(dgsComponent, mergedRegistry) }
            .fold(mergedRegistry) { a, b -> a.merge(b) }
        findScalars(applicationContext, runtimeWiringBuilder)
        findDirectives(applicationContext, runtimeWiringBuilder)
        findDataFetchers(dgsComponents, codeRegistryBuilder, mergedRegistry)
        findTypeResolvers(dgsComponents, runtimeWiringBuilder, mergedRegistry)
        findEntityFetchers(dgsComponents)

        dgsComponents.forEach { dgsComponent ->
            invokeDgsCodeRegistry(
                dgsComponent,
                codeRegistryBuilder,
                mergedRegistry
            )
        }

        runtimeWiringBuilder.codeRegistry(codeRegistryBuilder.build())

        dgsComponents.forEach { dgsComponent -> invokeDgsRuntimeWiring(dgsComponent, runtimeWiringBuilder) }
        val graphQLSchema =
            Federation.transform(mergedRegistry, runtimeWiringBuilder.build()).fetchEntities(entityFetcher)
                .resolveEntityType(typeResolver).build()

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        logger.debug("DGS initialized schema in {}ms", totalTime)

        return if (mockProviders.isNotEmpty()) {
            DgsSchemaTransformer().transformSchemaWithMockProviders(graphQLSchema, mockProviders)
        } else {
            graphQLSchema
        }
    }

    private fun invokeDgsTypeDefinitionRegistry(
        dgsComponent: Any,
        registry: TypeDefinitionRegistry
    ): TypeDefinitionRegistry? {
        return dgsComponent.javaClass.methods.asSequence()
            .filter { it.isAnnotationPresent(DgsTypeDefinitionRegistry::class.java) }
            .map { method ->
                if (method.returnType != TypeDefinitionRegistry::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsTypeDefinitionRegistry must have return type TypeDefinitionRegistry")
                }
                if (method.parameterCount == 1 && method.parameterTypes[0] == TypeDefinitionRegistry::class.java) {
                    ReflectionUtils.invokeMethod(method, dgsComponent, registry) as TypeDefinitionRegistry
                } else {
                    ReflectionUtils.invokeMethod(method, dgsComponent) as TypeDefinitionRegistry
                }
            }.reduceOrNull { a, b -> a.merge(b) }
    }

    private fun invokeDgsCodeRegistry(
        dgsComponent: Any,
        codeRegistryBuilder: GraphQLCodeRegistry.Builder,
        registry: TypeDefinitionRegistry
    ) {
        dgsComponent.javaClass.methods.asSequence()
            .filter { it.isAnnotationPresent(DgsCodeRegistry::class.java) }
            .forEach { method ->
                if (method.returnType != GraphQLCodeRegistry.Builder::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsCodeRegistry must have return type GraphQLCodeRegistry.Builder")
                }

                if (method.parameterCount != 2 || method.parameterTypes[0] != GraphQLCodeRegistry.Builder::class.java || method.parameterTypes[1] != TypeDefinitionRegistry::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsCodeRegistry must accept the following arguments: GraphQLCodeRegistry.Builder, TypeDefinitionRegistry. ${dgsComponent.javaClass.name}.${method.name} has the following arguments: ${method.parameterTypes.joinToString()}")
                }

                ReflectionUtils.invokeMethod(method, dgsComponent, codeRegistryBuilder, registry)
            }
    }

    private fun invokeDgsRuntimeWiring(dgsComponent: Any, runtimeWiringBuilder: RuntimeWiring.Builder) {
        dgsComponent.javaClass.methods.asSequence()
            .filter { it.isAnnotationPresent(DgsRuntimeWiring::class.java) }
            .forEach { method ->
                if (method.returnType != RuntimeWiring.Builder::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must have return type RuntimeWiring.Builder")
                }

                if (method.parameterCount != 1 || method.parameterTypes[0] != RuntimeWiring.Builder::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must accept an argument of type RuntimeWiring.Builder. ${dgsComponent.javaClass.name}.${method.name} has the following arguments: ${method.parameterTypes.joinToString()}")
                }

                ReflectionUtils.invokeMethod(method, dgsComponent, runtimeWiringBuilder)
            }
    }

    private fun findDataFetchers(
        dgsComponents: Collection<Any>,
        codeRegistryBuilder: GraphQLCodeRegistry.Builder,
        typeDefinitionRegistry: TypeDefinitionRegistry
    ) {
        dgsComponents.forEach { dgsComponent ->
            val javaClass = AopUtils.getTargetClass(dgsComponent)
            ReflectionUtils.getUniqueDeclaredMethods(javaClass, ReflectionUtils.USER_DECLARED_METHODS).asSequence()
                .map { method ->
                    val mergedAnnotations = MergedAnnotations
                        .from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                    Pair(method, mergedAnnotations)
                }
                .filter { (_, mergedAnnotations) -> mergedAnnotations.isPresent(DgsData::class.java) }
                .forEach { (method, mergedAnnotations) ->
                    val filteredMergedAnnotations =
                        mergedAnnotations
                            .stream(DgsData::class.java)
                            .filter { AopUtils.getTargetClass((it.source as Method).declaringClass) == AopUtils.getTargetClass(method.declaringClass) }
                            .collect(Collectors.toList())
                    filteredMergedAnnotations.forEach { dgsDataAnnotation ->
                        registerDataFetcher(
                            typeDefinitionRegistry,
                            codeRegistryBuilder,
                            dgsComponent,
                            method,
                            dgsDataAnnotation,
                            mergedAnnotations
                        )
                    }
                }
        }
    }

    private fun registerDataFetcher(
        typeDefinitionRegistry: TypeDefinitionRegistry,
        codeRegistryBuilder: GraphQLCodeRegistry.Builder,
        dgsComponent: Any,
        method: Method,
        dgsDataAnnotation: MergedAnnotation<DgsData>,
        mergedAnnotations: MergedAnnotations
    ) {
        val field = dgsDataAnnotation.getString("field").ifEmpty { method.name }
        val parentType = dgsDataAnnotation.getString("parentType")

        if (dataFetchers.any { it.parentType == parentType && it.field == field }) {
            logger.error("Duplicate data fetchers registered for $parentType.$field")
            throw InvalidDgsConfigurationException("Duplicate data fetchers registered for $parentType.$field")
        }

        dataFetchers.add(DataFetcherReference(dgsComponent, method, mergedAnnotations, parentType, field))

        val enableInstrumentation =
            if (method.isAnnotationPresent(DgsEnableDataFetcherInstrumentation::class.java)) {
                val dgsEnableDataFetcherInstrumentation =
                    method.getAnnotation(DgsEnableDataFetcherInstrumentation::class.java)
                dgsEnableDataFetcherInstrumentation.value
            } else {
                method.returnType != CompletionStage::class.java && method.returnType != CompletableFuture::class.java
            }

        dataFetcherInstrumentationEnabled["$parentType.$field"] = enableInstrumentation

        try {
            if (!typeDefinitionRegistry.getType(parentType).isPresent) {
                logger.error("Parent type $parentType not found, but it was referenced in ${javaClass.name} in @DgsData annotation for field $field")
                throw InvalidDgsConfigurationException("Parent type $parentType not found, but it was referenced on ${javaClass.name} in @DgsData annotation for field $field")
            }
            when (val type = typeDefinitionRegistry.getType(parentType).get()) {
                is InterfaceTypeDefinition -> {
                    val implementationsOf = typeDefinitionRegistry.getImplementationsOf(type)
                    implementationsOf.forEach { implType ->
                        val dataFetcher =
                            createBasicDataFetcher(method, dgsComponent, parentType == "Subscription")
                        codeRegistryBuilder.dataFetcher(
                            FieldCoordinates.coordinates(implType.name, field),
                            dataFetcher
                        )
                        dataFetcherInstrumentationEnabled["${implType.name}.$field"] = enableInstrumentation
                    }
                }
                is UnionTypeDefinition -> {
                    type.memberTypes.asSequence().filterIsInstance<TypeName>().forEach { memberType ->
                        val dataFetcher =
                            createBasicDataFetcher(method, dgsComponent, parentType == "Subscription")
                        codeRegistryBuilder.dataFetcher(
                            FieldCoordinates.coordinates(memberType.name, field),
                            dataFetcher
                        )
                        dataFetcherInstrumentationEnabled["${memberType.name}.$field"] = enableInstrumentation
                    }
                }
                else -> {
                    val dataFetcher = createBasicDataFetcher(method, dgsComponent, parentType == "Subscription")
                    codeRegistryBuilder.dataFetcher(
                        FieldCoordinates.coordinates(parentType, field),
                        dataFetcher
                    )
                }
            }
        } catch (ex: Exception) {
            logger.error("Invalid parent type $parentType")
            throw ex
        }
    }

    private fun findEntityFetchers(dgsComponents: Collection<Any>) {
        dgsComponents.forEach { dgsComponent ->
            val javaClass = AopUtils.getTargetClass(dgsComponent)

            ReflectionUtils.getDeclaredMethods(javaClass).asSequence()
                .filter { it.isAnnotationPresent(DgsEntityFetcher::class.java) }
                .forEach { method ->
                    val dgsEntityFetcherAnnotation = method.getAnnotation(DgsEntityFetcher::class.java)

                    val enableInstrumentation =
                        method.getAnnotation(DgsEnableDataFetcherInstrumentation::class.java)?.value
                            ?: false
                    dataFetcherInstrumentationEnabled["${"__entities"}.${dgsEntityFetcherAnnotation.name}"] =
                        enableInstrumentation

                    entityFetcherRegistry.entityFetchers[dgsEntityFetcherAnnotation.name] = dgsComponent to method
                }
        }
    }

    private fun createBasicDataFetcher(method: Method, dgsComponent: Any, isSubscription: Boolean): DataFetcher<Any?> {
        val dataFetcher = methodDataFetcherFactory.createDataFetcher(dgsComponent, method)

        if (isSubscription) {
            return dataFetcher
        }

        return DataFetcherFactories.wrapDataFetcher(dataFetcher) { dfe, result ->
            result?.let {
                val env = DgsDataFetchingEnvironment(dfe)
                dataFetcherResultProcessors.find { it.supportsType(result) }?.process(result, env) ?: result
            }
        }
    }

    private fun findTypeResolvers(
        dgsComponents: Collection<Any>,
        runtimeWiringBuilder: RuntimeWiring.Builder,
        mergedRegistry: TypeDefinitionRegistry
    ) {
        val registeredTypeResolvers = mutableSetOf<String>()

        dgsComponents.forEach { dgsComponent ->
            val javaClass = AopUtils.getTargetClass(dgsComponent)
            javaClass.methods.asSequence()
                .filter { it.isAnnotationPresent(DgsTypeResolver::class.java) }
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
                        overrideTypeResolver = dgsComponents.any { component ->
                            component.javaClass.methods.any { method ->
                                method.isAnnotationPresent(DgsTypeResolver::class.java) &&
                                    method.getAnnotation(DgsTypeResolver::class.java).name == annotation.name &&
                                    component != dgsComponent
                            }
                        }
                    }
                    // do not add the default resolver if another resolver with the same name is present
                    if (defaultTypeResolver == null || !overrideTypeResolver) {
                        registeredTypeResolvers.add(annotation.name)

                        runtimeWiringBuilder.type(
                            TypeRuntimeWiring.newTypeWiring(annotation.name)
                                .typeResolver { env: TypeResolutionEnvironment ->
                                    val typeName: String? =
                                        ReflectionUtils.invokeMethod(method, dgsComponent, env.getObject()) as String?
                                    if (typeName != null) {
                                        env.schema.getObjectType(typeName)
                                    } else {
                                        null
                                    }
                                }
                        )
                    }
                }
        }

        // Add a fallback type resolver for types that don't have a type resolver registered.
        // This works when the Java type has the same name as the GraphQL type.
        // Check for unregistered interface types
        val unregisteredInterfaceTypes = mergedRegistry.types()
            .asSequence()
            .filter { (_, typeDef) -> typeDef is InterfaceTypeDefinition }
            .map { (name, _) -> name }
            .filter { it !in registeredTypeResolvers }
        checkTypeResolverExists(unregisteredInterfaceTypes, runtimeWiringBuilder, "interface")

        // Check for unregistered union types
        val unregisteredUnionTypes = mergedRegistry.types()
            .asSequence()
            .filter { (_, typeDef) -> typeDef is UnionTypeDefinition }
            .map { (name, _) -> name }
            .filter { it !in registeredTypeResolvers }
        checkTypeResolverExists(unregisteredUnionTypes, runtimeWiringBuilder, "union")
    }

    private fun checkTypeResolverExists(
        unregisteredTypes: Sequence<String>,
        runtimeWiringBuilder: RuntimeWiring.Builder,
        typeName: String
    ) {
        unregisteredTypes.forEach {
            runtimeWiringBuilder.type(
                TypeRuntimeWiring.newTypeWiring(it)
                    .typeResolver { env: TypeResolutionEnvironment ->
                        val instance = env.getObject<Any>()
                        val resolvedType = env.schema.getObjectType(instance::class.java.simpleName)
                        resolvedType
                            ?: throw InvalidTypeResolverException("The default type resolver could not find a suitable Java type for GraphQL $typeName type `$it`. Provide a @DgsTypeResolver for `${instance::class.java.simpleName}`.")
                    }
            )
        }
    }

    private fun findScalars(applicationContext: ApplicationContext, runtimeWiringBuilder: RuntimeWiring.Builder) {
        applicationContext.getBeansWithAnnotation(DgsScalar::class.java).forEach { (_, scalarComponent) ->
            val annotation = scalarComponent::class.java.getAnnotation(DgsScalar::class.java)
            when (scalarComponent) {
                is Coercing<*, *> -> runtimeWiringBuilder.scalar(
                    GraphQLScalarType.newScalar().name(annotation.name).coercing(scalarComponent).build()
                )
                else -> throw RuntimeException("Invalid @DgsScalar type: the class must implement graphql.schema.Coercing")
            }
        }
    }

    private fun findDirectives(applicationContext: ApplicationContext, runtimeWiringBuilder: RuntimeWiring.Builder) {
        applicationContext.getBeansWithAnnotation(DgsDirective::class.java).forEach { (_, directiveComponent) ->
            val annotation = directiveComponent::class.java.getAnnotation(DgsDirective::class.java)
            when (directiveComponent) {
                is SchemaDirectiveWiring ->
                    if (annotation.name.isNotBlank()) {
                        runtimeWiringBuilder.directive(annotation.name, directiveComponent)
                    } else {
                        runtimeWiringBuilder.directiveWiring(directiveComponent)
                    }
                else -> throw RuntimeException("Invalid @DgsDirective type: the class must implement graphql.schema.idl.SchemaDirectiveWiring")
            }
        }
    }

    internal fun findSchemaFiles(hasDynamicTypeRegistry: Boolean = false): List<Resource> {
        val cl = Thread.currentThread().contextClassLoader

        val resolver = PathMatchingResourcePatternResolver(cl)
        val schemas = try {
            val resources = schemaLocations.asSequence()
                .flatMap { resolver.getResources(it).asSequence() }
                .distinct()
                .toMutableList()
            if (resources.isEmpty()) {
                throw NoSchemaFoundException()
            }
            resources
        } catch (ex: Exception) {
            if (existingTypeDefinitionRegistry.isPresent || hasDynamicTypeRegistry) {
                logger.info("No schema files found, but a schema was provided as an TypeDefinitionRegistry")
                mutableListOf()
            } else {
                logger.error("No schema files found in $schemaLocations. Define schema locations with property dgs.graphql.schema-locations")
                throw NoSchemaFoundException()
            }
        }

        val metaInfSchemas = try {
            resolver.getResources("classpath*:META-INF/schema/**/*.graphql*")
        } catch (ex: Exception) {
            arrayOf<Resource>()
        }

        schemas += metaInfSchemas
        return schemas
    }

    companion object {
        const val DEFAULT_SCHEMA_LOCATION = "classpath*:schema/**/*.graphql*"
        private val logger: Logger = LoggerFactory.getLogger(DgsSchemaProvider::class.java)
    }
}
