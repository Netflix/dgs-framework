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
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.*
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.exceptions.*
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import com.netflix.graphql.mocking.DgsSchemaTransformer
import com.netflix.graphql.mocking.MockProvider
import graphql.TypeResolutionEnvironment
import graphql.execution.DataFetcherExceptionHandler
import graphql.language.InterfaceTypeDefinition
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeRuntimeWiring
import graphql.schema.visibility.DefaultGraphqlFieldVisibility
import graphql.schema.visibility.GraphqlFieldVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.annotation.MergedAnnotations
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.ReflectionUtils
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ValueConstants
import org.springframework.web.multipart.MultipartFile
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.coroutines.Continuation
import kotlin.reflect.full.callSuspend
import kotlin.reflect.jvm.kotlinFunction

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

    companion object {
        const val DEFAULT_SCHEMA_LOCATION = "classpath*:schema/**/*.graphql*"
    }

    val dataFetcherInstrumentationEnabled = mutableMapOf<String, Boolean>()
    val entityFetchers = mutableMapOf<String, Pair<Any, Method>>()
    val dataFetchers = mutableListOf<DatafetcherReference>()

    private val defaultParameterNameDiscoverer = DefaultParameterNameDiscoverer()
    private val logger = LoggerFactory.getLogger(DgsSchemaProvider::class.java)

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

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

                    val mergedAnnotations = MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                    val dgsDataAnnotation = mergedAnnotations.get(DgsData::class.java)
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
                        val type = typeDefinitionRegistry.getType(parentType).get()
                        if (type is InterfaceTypeDefinition) {
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
                        } else if (type is UnionTypeDefinition) {
                            type.memberTypes.asSequence().filterIsInstance<TypeName>().forEach { memberType ->
                                val dataFetcher =
                                    createBasicDataFetcher(method, dgsComponent, parentType == "Subscription")
                                codeRegistryBuilder.dataFetcher(
                                    FieldCoordinates.coordinates(memberType.name, field),
                                    dataFetcher
                                )
                                dataFetcherInstrumentationEnabled["${memberType.name}.$field"] = enableInstrumentation
                            }
                        } else {
                            val dataFetcher = createBasicDataFetcher(method, dgsComponent, parentType == "Subscription")
                            codeRegistryBuilder.dataFetcher(
                                FieldCoordinates.coordinates(parentType, field),
                                dataFetcher
                            )
                        }
                    } catch (ex: Exception) {
                        logger.error("Invalid parent type $parentType")
                        throw ex
                    }
                }
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
            val result = invokeDataFetcher(method, dgsComponent, dfe)
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

    private fun invokeDataFetcher(method: Method, dgsComponent: Any, environment: DataFetchingEnvironment): Any? {
        val args = mutableListOf<Any?>()
        val parameterNames = defaultParameterNameDiscoverer.getParameterNames(method) ?: emptyArray()
        method.parameters.asSequence().filter { it.type != Continuation::class.java }.forEachIndexed { idx, parameter ->

            when {
                parameter.isAnnotationPresent(InputArgument::class.java) -> {
                    val annotation = AnnotationUtils.getAnnotation(parameter, InputArgument::class.java)!!
                    val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String

                    val parameterName = name.ifBlank { parameterNames[idx] }
                    val collectionType = annotation.collectionType.java
                    val parameterValue: Any? = environment.getArgument(parameterName)

                    val convertValue: Any? = if (parameterValue is List<*> && collectionType != Object::class.java) {
                        try {
                            // Return a list of elements that are converted to their collection type, e.e.g. List<Person>, List<String> etc.
                            parameterValue.map { item -> objectMapper.convertValue(item, collectionType) }.toList()
                        } catch (ex: Exception) {
                            throw DgsInvalidInputArgumentException(
                                "Specified type '$collectionType' is invalid for $parameterName.",
                                ex
                            )
                        }
                    } else if (parameterValue is List<*>) {
                        // Return as is for all other types of Lists, i.e. custom scalars e.g. List<UUID>, and other scalar types like List<Integer> etc.
                        parameterValue
                    } else if (parameterValue is MultipartFile) {
                        parameterValue
                    } else if (environment.fieldDefinition.arguments.find { it.name == parameterName }?.type is GraphQLScalarType) {
                        // Return the value with it's type for scalars
                        parameterValue
                    } else {
                        // Return the converted value mapped to the defined type
                        if (parameter.type.isAssignableFrom(Optional::class.java)) {
                            if (collectionType != Object::class.java) {
                                objectMapper.convertValue(parameterValue, collectionType)
                            } else {
                                throw DgsInvalidInputArgumentException("When Optional<T> is used, the type must be specified using the collectionType argument of the @InputArgument annotation.")
                            }
                        } else {
                            objectMapper.convertValue(parameterValue, parameter.type)
                        }
                    }

                    val paramType = parameter.type
                    val optionalValue = getValueAsOptional(convertValue, parameter)

                    if (optionalValue != null && !paramType.isPrimitive && !paramType.isAssignableFrom(optionalValue.javaClass)) {
                        throw DgsInvalidInputArgumentException("Specified type '${parameter.type}' is invalid. Found ${parameterValue?.javaClass?.name} instead.")
                    }

                    if (convertValue == null && environment.fieldDefinition.arguments.none { it.name == parameterName }) {
                        logger.warn("Unknown argument '$parameterName' on data fetcher ${dgsComponent.javaClass.name}.${method.name}")
                    }

                    args.add(optionalValue)
                }

                parameter.isAnnotationPresent(RequestHeader::class.java) -> {
                    val requestData = DgsContext.getRequestData(environment)
                    val annotation = AnnotationUtils.getAnnotation(parameter, RequestHeader::class.java)!!
                    val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String
                    val parameterName = name.ifBlank { parameterNames[idx] }
                    val value = requestData?.headers?.get(parameterName)?.let {
                        if (parameter.type.isAssignableFrom(List::class.java)) {
                            it
                        } else {
                            it.joinToString()
                        }
                    } ?: if (annotation.defaultValue != ValueConstants.DEFAULT_NONE) annotation.defaultValue else null

                    if (value == null && annotation.required) {
                        throw DgsInvalidInputArgumentException("Required header '$parameterName' was not provided")
                    }

                    val optionalValue = getValueAsOptional(value, parameter)
                    args.add(optionalValue)
                }

                parameter.isAnnotationPresent(RequestParam::class.java) -> {
                    val requestData = DgsContext.getRequestData(environment)
                    val annotation = AnnotationUtils.getAnnotation(parameter, RequestParam::class.java)!!
                    val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String
                    val parameterName = name.ifBlank { parameterNames[idx] }
                    if (requestData is DgsWebMvcRequestData) {
                        val webRequest = requestData.webRequest
                        val value: Any? =
                            webRequest?.parameterMap?.get(parameterName)?.let {
                                if (parameter.type.isAssignableFrom(List::class.java)) {
                                    it
                                } else {
                                    it.joinToString()
                                }
                            }
                                ?: if (annotation.defaultValue != ValueConstants.DEFAULT_NONE) annotation.defaultValue else null

                        if (value == null && annotation.required) {
                            throw DgsInvalidInputArgumentException("Required request parameter '$parameterName' was not provided")
                        }

                        val optionalValue = getValueAsOptional(value, parameter)
                        args.add(optionalValue)
                    } else {
                        logger.warn("@RequestParam is not supported when using WebFlux")
                        args.add(null)
                    }
                }

                parameter.isAnnotationPresent(CookieValue::class.java) -> {
                    val requestData = DgsContext.getRequestData(environment)
                    val annotation = AnnotationUtils.getAnnotation(parameter, CookieValue::class.java)!!
                    val name: String = AnnotationUtils.getAnnotationAttributes(annotation)["name"] as String
                    val parameterName = name.ifBlank { parameterNames[idx] }
                    val value = if (cookieValueResolver.isPresent) { cookieValueResolver.get().getCookieValue(parameterName, requestData) } else { null }
                        ?: if (annotation.defaultValue != ValueConstants.DEFAULT_NONE) annotation.defaultValue else null

                    if (value == null && annotation.required) {
                        throw DgsMissingCookieException(parameterName)
                    }

                    val optionalValue = getValueAsOptional(value, parameter)
                    args.add(optionalValue)
                }

                environment.containsArgument(parameterNames[idx]) -> {
                    val parameterValue: Any = environment.getArgument(parameterNames[idx])
                    val convertValue = objectMapper.convertValue(parameterValue, parameter.type)
                    args.add(convertValue)
                }

                parameter.type == DataFetchingEnvironment::class.java || parameter.type == DgsDataFetchingEnvironment::class.java -> {
                    args.add(environment)
                }
                else -> {
                    logger.warn("Unknown argument '${parameterNames[idx]}' on data fetcher ${dgsComponent.javaClass.name}.${method.name}")
                    // This might cause an exception, but parameter's the best effort we can do
                    args.add(null)
                }
            }
        }

        return if (method.kotlinFunction?.isSuspend == true) {

            val launch = CoroutineScope(Dispatchers.Unconfined).async {
                return@async method.kotlinFunction!!.callSuspend(dgsComponent, *args.toTypedArray())
            }

            launch.asCompletableFuture()
        } else {
            ReflectionUtils.invokeMethod(method, dgsComponent, *args.toTypedArray())
        }
    }

    private fun getValueAsOptional(value: Any?, parameter: Parameter) =
        if (parameter.type.isAssignableFrom(Optional::class.java)) {
            Optional.ofNullable(value)
        } else {
            value
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

    internal fun findScalars(applicationContext: ApplicationContext, runtimeWiringBuilder: RuntimeWiring.Builder) {
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
}

interface DataFetcherResultProcessor {
    fun supportsType(originalResult: Any): Boolean
    fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any = process(originalResult)
    @Deprecated("Replaced with process(originalResult, dfe)")
    fun process(originalResult: Any): Any = originalResult
}

data class DatafetcherReference(val instance: Any, val method: Method, val annotations: MergedAnnotations, val parentType: String, val field: String)
