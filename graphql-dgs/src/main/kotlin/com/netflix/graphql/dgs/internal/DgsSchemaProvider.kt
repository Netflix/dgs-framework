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
import com.netflix.graphql.dgs.exceptions.DgsInvalidInputArgumentException
import com.netflix.graphql.dgs.exceptions.InvalidDgsConfigurationException
import com.netflix.graphql.dgs.exceptions.InvalidTypeResolverException
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver
import com.netflix.graphql.mocking.DgsSchemaTransformer
import com.netflix.graphql.mocking.MockProvider
import graphql.TypeResolutionEnvironment
import graphql.language.InterfaceTypeDefinition
import graphql.language.TypeName
import graphql.language.UnionTypeDefinition
import graphql.schema.*
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.TypeDefinitionRegistry
import graphql.schema.idl.TypeRuntimeWiring
import org.slf4j.LoggerFactory
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.core.annotation.MergedAnnotations
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.ReflectionUtils
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

/**
 * Main framework class that scans for components and configures a runtime executable schema.
 */
class DgsSchemaProvider(
    private val applicationContext: ApplicationContext,
    private val federationResolver: Optional<DgsFederationResolver>,
    private val existingTypeDefinitionRegistry: Optional<TypeDefinitionRegistry>,
    private val mockProviders: Optional<Set<MockProvider>>,
    private val schemaLocations: List<String> = listOf(DEFAULT_SCHEMA_LOCATION)
) {

    companion object {
        const val DEFAULT_SCHEMA_LOCATION = "classpath*:schema/**/*.graphql*"
    }

    val dataFetcherInstrumentationEnabled = mutableMapOf<String, Boolean>()
    val entityFetchers = mutableMapOf<String, Pair<Any, Method>>()

    private val defaultParameterNameDiscoverer = DefaultParameterNameDiscoverer()
    private val logger = LoggerFactory.getLogger(DgsSchemaProvider::class.java)

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    fun schema(schema: String? = null): GraphQLSchema {
        val startTime = System.currentTimeMillis()
        val dgsComponents = applicationContext.getBeansWithAnnotation(DgsComponent::class.java)
        val hasDynamicTypeRegistry =
            dgsComponents.values.any { it.javaClass.methods.any { m -> m.isAnnotationPresent(DgsTypeDefinitionRegistry::class.java) } }

        var mergedRegistry = if (schema == null) {
            findSchemaFiles(hasDynamicTypeRegistry = hasDynamicTypeRegistry).map {
                InputStreamReader(it.inputStream, StandardCharsets.UTF_8).use { reader -> SchemaParser().parse(reader) }
            }.fold(TypeDefinitionRegistry()) { a, b -> a.merge(b) }
        } else {
            SchemaParser().parse(schema)
        }

        if (existingTypeDefinitionRegistry.isPresent) {
            mergedRegistry = mergedRegistry.merge(existingTypeDefinitionRegistry.get())
        }

        DefaultDgsFederationResolver(this)

        val federationResolverInstance = federationResolver.orElseGet { DefaultDgsFederationResolver(this) }

        val entityFetcher = federationResolverInstance.entitiesFetcher()
        val typeResolver = federationResolverInstance.typeResolver()
        val codeRegistryBuilder = GraphQLCodeRegistry.newCodeRegistry()
        val runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring()

        dgsComponents.values.mapNotNull { dgsComponent -> invokeDgsTypeDefinitionRegistry(dgsComponent) }
            .fold(mergedRegistry) { a, b -> a.merge(b) }
        findScalars(applicationContext, runtimeWiringBuilder)
        findDataFetchers(dgsComponents, codeRegistryBuilder, mergedRegistry)
        findTypeResolvers(dgsComponents, runtimeWiringBuilder, mergedRegistry)
        findEntityFetchers(dgsComponents)

        dgsComponents.values.forEach { dgsComponent ->
            invokeDgsCodeRegistry(
                dgsComponent,
                codeRegistryBuilder,
                mergedRegistry
            )
        }

        runtimeWiringBuilder.codeRegistry(codeRegistryBuilder.build())

        dgsComponents.values.forEach { dgsComponent -> invokeDgsRuntimeWiring(dgsComponent, runtimeWiringBuilder) }

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

    private fun invokeDgsTypeDefinitionRegistry(dgsComponent: Any): TypeDefinitionRegistry? {
        return dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsTypeDefinitionRegistry::class.java) }
            .map { method ->
                if (method.returnType != TypeDefinitionRegistry::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsTypeDefinitionRegistry must have return type TypeDefinitionRegistry")
                }

                method.invoke(dgsComponent) as TypeDefinitionRegistry
            }.reduceOrNull { a, b -> a.merge(b) }
    }

    private fun invokeDgsCodeRegistry(
        dgsComponent: Any,
        codeRegistryBuilder: GraphQLCodeRegistry.Builder,
        registry: TypeDefinitionRegistry
    ) {
        dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsCodeRegistry::class.java) }
            .forEach { method ->
                if (method.returnType != GraphQLCodeRegistry.Builder::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsCodeRegistry must have return type GraphQLCodeRegistry.Builder")
                }

                if (method.parameterCount != 2 || method.parameterTypes[0] != GraphQLCodeRegistry.Builder::class.java || method.parameterTypes[1] != TypeDefinitionRegistry::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsCodeRegistry must accept the following arguments: GraphQLCodeRegistry.Builder, TypeDefinitionRegistry. ${dgsComponent.javaClass.name}.${method.name} has the following arguments: ${method.parameterTypes.joinToString()}")
                }

                method.invoke(dgsComponent, codeRegistryBuilder, registry)
            }
    }

    private fun invokeDgsRuntimeWiring(dgsComponent: Any, runtimeWiringBuilder: RuntimeWiring.Builder) {
        dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsRuntimeWiring::class.java) }
            .forEach { method ->
                if (method.returnType != RuntimeWiring.Builder::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must have return type RuntimeWiring.Builder")
                }

                if (method.parameterCount != 1 || method.parameterTypes[0] != RuntimeWiring.Builder::class.java) {
                    throw InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must accept an argument of type RuntimeWiring.Builder. ${dgsComponent.javaClass.name}.${method.name} has the following arguments: ${method.parameterTypes.joinToString()}")
                }

                method.invoke(dgsComponent, runtimeWiringBuilder)
            }
    }

    private fun findDataFetchers(
        dgsComponents: Map<String, Any>,
        codeRegistryBuilder: GraphQLCodeRegistry.Builder,
        typeDefinitionRegistry: TypeDefinitionRegistry
    ) {
        dgsComponents.values.forEach { dgsComponent ->
            val javaClass = AopUtils.getTargetClass(dgsComponent)

            javaClass.methods.asSequence()
                .filter { method -> MergedAnnotations.from(method).isPresent(DgsData::class.java) }
                .forEach { method ->

                    val dgsDataAnnotation = MergedAnnotations.from(method).get(DgsData::class.java)
                    val field = dgsDataAnnotation.getString("field").ifEmpty { method.name }
                    val parentType = dgsDataAnnotation.getString("parentType")

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
                                val dataFetcher = createBasicDataFetcher(method, dgsComponent)
                                codeRegistryBuilder.dataFetcher(
                                    FieldCoordinates.coordinates(implType.name, field),
                                    dataFetcher
                                )
                                dataFetcherInstrumentationEnabled["${implType.name}.$field"] = enableInstrumentation
                            }
                        } else if (type is UnionTypeDefinition) {
                            type.memberTypes.filterIsInstance<TypeName>().forEach { memberType ->
                                val dataFetcher = createBasicDataFetcher(method, dgsComponent)
                                codeRegistryBuilder.dataFetcher(
                                    FieldCoordinates.coordinates(memberType.name, field),
                                    dataFetcher
                                )
                                dataFetcherInstrumentationEnabled["${memberType.name}.$field"] = enableInstrumentation
                            }
                        } else {
                            val dataFetcher = createBasicDataFetcher(method, dgsComponent)
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

    private fun findEntityFetchers(dgsComponents: Map<String, Any>) {
        dgsComponents.values.forEach { dgsComponent ->
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

    private fun createBasicDataFetcher(method: Method, dgsComponent: Any): DataFetcher<Any?> {
        return DataFetcher<Any?> { environment ->
            val result = invokeDataFetcher(method, dgsComponent, DgsDataFetchingEnvironment(environment))
            result
        }
    }

    private fun invokeDataFetcher(method: Method, dgsComponent: Any, environment: DataFetchingEnvironment): Any? {
        val args = mutableListOf<Any?>()
        val parameterNames = defaultParameterNameDiscoverer.getParameterNames(method) ?: emptyArray()
        method.parameters.forEachIndexed { idx, parameter ->

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

        return ReflectionUtils.invokeMethod(method, dgsComponent, *args.toTypedArray())
    }

    private fun getValueAsOptional(value: Any?, parameter: Parameter) =
        if (value == null && parameter.type.isAssignableFrom(Optional::class.java)) {
            Optional.empty<Any>()
        } else if (parameter.type.isAssignableFrom(Optional::class.java)) {
            Optional.of(value!!)
        } else {
            value
        }

    private fun findTypeResolvers(
        dgsComponents: Map<String, Any>,
        runtimeWiringBuilder: RuntimeWiring.Builder,
        mergedRegistry: TypeDefinitionRegistry
    ) {
        val registeredTypeResolvers = mutableSetOf<String>()

        dgsComponents.values.forEach { dgsComponent ->
            dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsTypeResolver::class.java) }
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
                        overrideTypeResolver = dgsComponents.values.any { component ->
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
            .filterValues { it is InterfaceTypeDefinition || it is UnionTypeDefinition }
            .map { it.key }
            .filter { !registeredTypeResolvers.contains(it) }
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
        applicationContext.getBeansWithAnnotation(DgsScalar::class.java).forEach {
            val scalarComponent = it.value
            val annotation = scalarComponent::class.java.getAnnotation(DgsScalar::class.java)
            when (scalarComponent) {
                is Coercing<*, *> -> runtimeWiringBuilder.scalar(
                    GraphQLScalarType.newScalar().name(annotation.name).coercing(scalarComponent).build()
                )
                else -> throw RuntimeException("Invalid @DgsScalar type: the class must implement graphql.schema.Coercing")
            }
        }
    }

    internal fun findSchemaFiles(hasDynamicTypeRegistry: Boolean = false): Array<Resource> {
        val cl = Thread.currentThread().contextClassLoader

        val resolver = PathMatchingResourcePatternResolver(cl)
        val schemas = try {
            val resources = schemaLocations.flatMap { resolver.getResources(it).toList() }.distinct().toTypedArray()
            if (resources.isEmpty()) {
                throw NoSchemaFoundException()
            }
            resources
        } catch (ex: Exception) {
            if (existingTypeDefinitionRegistry.isPresent || hasDynamicTypeRegistry) {
                logger.info("No schema files found, but a schema was provided as an TypeDefinitionRegistry")
                arrayOf()
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

        return schemas + metaInfSchemas
    }
}
