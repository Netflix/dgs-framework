/*
 * Copyright 2021 Netflix, Inc.
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
import com.netflix.graphql.mocking.DgsSchemaTransformer
import com.netflix.graphql.mocking.MockProvider
import graphql.TypeResolutionEnvironment
import graphql.execution.DataFetcherExceptionHandler
import graphql.language.InterfaceTypeDefinition
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.schema.*
import graphql.schema.idl.*
import graphql.schema.visibility.DefaultGraphqlFieldVisibility
import graphql.schema.visibility.GraphqlFieldVisibility
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext
import org.springframework.core.DefaultParameterNameDiscoverer
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

/**
 * Main framework class that scans for components and configures a runtime executable schema.
 */
class DgsSchemaProvider(
    private val applicationContext: ApplicationContext,
    private val federationResolver: Optional<DgsFederationResolver>,
    private val existingTypeDefinitionRegistry: Optional<TypeDefinitionRegistry>,
    private val mockProviders: Optional<Set<MockProvider>>,
    private val schemaLocations: List<String> = listOf(DEFAULT_SCHEMA_LOCATION),
    private val dataFetcherResultProcessors: List<DataFetcherResultProcessor> = emptyList(),
    private val dataFetcherExceptionHandler: Optional<DataFetcherExceptionHandler> = Optional.empty(),
    private val cookieValueResolver: Optional<CookieValueResolver> = Optional.empty()
) {

    val dataFetcherInstrumentationEnabled = mutableMapOf<String, Boolean>()
    val entityFetchers = mutableMapOf<String, Pair<Any, Method>>()
    val dataFetchers = mutableListOf<DatafetcherReference>()

    private val defaultParameterNameDiscoverer = DefaultParameterNameDiscoverer()

    fun schema(schema: String? = null, fieldVisibility: GraphqlFieldVisibility = DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY): GraphQLSchema {
        val startTime = System.currentTimeMillis()
        val dgsComponents = applicationContext.getBeansWithAnnotation(DgsComponent::class.java).values
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

        val federationResolverInstance = federationResolver.orElseGet { DefaultDgsFederationResolver(this, dataFetcherExceptionHandler) }

        val entityFetcher = federationResolverInstance.entitiesFetcher()
        val typeResolver = federationResolverInstance.typeResolver()
        val codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(fieldVisibility)
        val runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring().codeRegistry(codeRegistryBuilder).fieldVisibility(fieldVisibility)

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

        return if (mockProviders.isPresent) {
            DgsSchemaTransformer().transformSchemaWithMockProviders(graphQLSchema, mockProviders.get())
        } else {
            graphQLSchema
        }
    }

    private fun invokeDgsTypeDefinitionRegistry(dgsComponent: Any, registry: TypeDefinitionRegistry): TypeDefinitionRegistry? {
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

            javaClass.methods.asSequence()
                .filter { method ->
                    MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                        .isPresent(DgsData::class.java)
                }
                .forEach { method ->
                    val mergedAnnotations =
                        MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                    mergedAnnotations.stream(DgsData::class.java).forEach { dgsDataAnnotation ->
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
        dataFetchers.add(DatafetcherReference(dgsComponent, method, mergedAnnotations, parentType, field))

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

                    entityFetchers[dgsEntityFetcherAnnotation.name] = dgsComponent to method
                }
        }
    }

    private fun createBasicDataFetcher(method: Method, dgsComponent: Any, isSubscription: Boolean): DataFetcher<Any?> {
        return DataFetcher<Any?> { environment ->
            val dfe = DgsDataFetchingEnvironment(environment)
            val result = DataFetcherInvoker(cookieValueResolver, defaultParameterNameDiscoverer, dfe, dgsComponent, method).invokeDataFetcher()
            when {
                isSubscription -> {
                    result
                }
                result != null -> {
                    dataFetcherResultProcessors.find { it.supportsType(result) }?.process(result, dfe) ?: result
                }
                else -> {
                    result
                }
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
                                    val typeName =
                                        ReflectionUtils.invokeMethod(method, dgsComponent, env.getObject()) as String
                                    env.schema.getObjectType(typeName)
                                }
                        )
                    }
                }
        }

        // Add a fallback type resolver for types that don't have a type resolver registered.
        // This works when the Java type has the same name as the GraphQL type.
        val unregisteredTypes = mergedRegistry.types()
            .asSequence()
            .filter { (_, typeDef) -> typeDef is InterfaceTypeDefinition || typeDef is UnionTypeDefinition }
            .map { (name, _) -> name }
            .filter { it !in registeredTypeResolvers }
        unregisteredTypes.forEach {
            runtimeWiringBuilder.type(
                TypeRuntimeWiring.newTypeWiring(it)
                    .typeResolver { env: TypeResolutionEnvironment ->
                        val instance = env.getObject<Any>()
                        val resolvedType = env.schema.getObjectType(instance::class.java.simpleName)
                        resolvedType
                            ?: throw InvalidTypeResolverException("The default type resolver could not find a suitable Java type for GraphQL type `${instance::class.java.simpleName}. Provide a @DgsTypeResolver.`")
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

interface DataFetcherResultProcessor {
    fun supportsType(originalResult: Any): Boolean
    fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any = process(originalResult)
    @Deprecated(
        "Replaced with process(originalResult, dfe)",
        replaceWith = ReplaceWith("process(originalResult: Any, dfe: DgsDataFetchingEnvironment)")
    )
    fun process(originalResult: Any): Any = originalResult
}

data class DatafetcherReference(val instance: Any, val method: Method, val annotations: MergedAnnotations, val parentType: String, val field: String)
