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
import org.springframework.context.ApplicationContext
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.web.multipart.MultipartFile
import java.io.InputStreamReader
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * Main framework class that scans for components and configures a runtime executable schema.
 */
class DgsSchemaProvider(private val applicationContext: ApplicationContext,
                        private val federationResolver: Optional<DgsFederationResolver>,
                        private val existingTypeDefinitionRegistry: Optional<TypeDefinitionRegistry>,
                        private val mockProviders: Optional<Set<MockProvider>>) {

    val dataFetcherInstrumentationEnabled = mutableMapOf<String, Boolean>()
    val entityFetchers = mutableMapOf<String, Pair<Any, Method>>()

    private val logger = LoggerFactory.getLogger(DgsSchemaProvider::class.java)

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    fun schema(schema: String? = null): GraphQLSchema {
        val startTime = System.currentTimeMillis()
        val dgsComponents = applicationContext.getBeansWithAnnotation(DgsComponent::class.java)
        val hasDynamicTypeRegistry = dgsComponents.values.any { it.javaClass.methods.any { m -> m.isAnnotationPresent(DgsTypeDefinitionRegistry::class.java) }}

        var mergedRegistry = if (schema == null) {
            findSchemaFiles(hasDynamicTypeRegistry=hasDynamicTypeRegistry).map {
                InputStreamReader(it.inputStream, StandardCharsets.UTF_8).use { reader -> SchemaParser().parse(reader) }
            }.fold(TypeDefinitionRegistry()) {a, b -> a.merge(b) }
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

        dgsComponents.values.mapNotNull { dgsComponent -> invokeDgsTypeDefinitionRegistry(dgsComponent) }.fold(mergedRegistry) { a, b -> a.merge(b)}
        findScalars(applicationContext, runtimeWiringBuilder)
        findDataFetchers(dgsComponents, codeRegistryBuilder, mergedRegistry)
        findTypeResolvers(dgsComponents, runtimeWiringBuilder, mergedRegistry)
        findEntityFetchers(dgsComponents)

        dgsComponents.values.forEach { dgsComponent -> invokeDgsCodeRegistry(dgsComponent, codeRegistryBuilder, mergedRegistry) }

        runtimeWiringBuilder.codeRegistry(codeRegistryBuilder.build())

        dgsComponents.values.forEach { dgsComponent -> invokeDgsRuntimeWiring(dgsComponent, runtimeWiringBuilder) }

        val graphQLSchema = Federation.transform(mergedRegistry, runtimeWiringBuilder.build()).fetchEntities(entityFetcher).resolveEntityType(typeResolver).build()

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
        return dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsTypeDefinitionRegistry::class.java) }.map { method ->
            if (method.returnType != TypeDefinitionRegistry::class.java) {
                throw InvalidDgsConfigurationException("Method annotated with @DgsTypeDefinitionRegistry must have return type TypeDefinitionRegistry")
            }

            method.invoke(dgsComponent) as TypeDefinitionRegistry
        }.reduceOrNull {a, b -> a.merge(b)}
    }


    private fun invokeDgsCodeRegistry(dgsComponent: Any, codeRegistryBuilder: GraphQLCodeRegistry.Builder, registry: TypeDefinitionRegistry) {
        dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsCodeRegistry::class.java) }.forEach { method ->
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
        dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsRuntimeWiring::class.java) }.forEach { method ->
            if (method.returnType != RuntimeWiring.Builder::class.java) {
                throw InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must have return type RuntimeWiring.Builder")
            }

            if (method.parameterCount != 1 || method.parameterTypes[0] != RuntimeWiring.Builder::class.java) {
                throw InvalidDgsConfigurationException("Method annotated with @DgsRuntimeWiring must accept an argument of type RuntimeWiring.Builder. ${dgsComponent.javaClass.name}.${method.name} has the following arguments: ${method.parameterTypes.joinToString()}")
            }

            method.invoke(dgsComponent, runtimeWiringBuilder)
        }
    }

    private fun findDataFetchers(dgsComponents: Map<String, Any>, codeRegistryBuilder: GraphQLCodeRegistry.Builder, typeDefinitionRegistry: TypeDefinitionRegistry) {
        dgsComponents.values.forEach { dgsComponent ->
            val javaClass = when {
                dgsComponent.javaClass.name.contains("EnhancerBySpringCGLIB") -> {
                    dgsComponent.javaClass.superclass
                }
                else -> {
                    dgsComponent.javaClass
                }
            }

            javaClass.methods.filter { it.isAnnotationPresent(DgsData::class.java) }.forEach { method ->
                val dgsDataAnnotation = method.getAnnotation(DgsData::class.java)

                val enableInstrumentation = if (method.isAnnotationPresent(DgsEnableDataFetcherInstrumentation::class.java)) {
                    val dgsEnableDataFetcherInstrumentation = method.getAnnotation(DgsEnableDataFetcherInstrumentation::class.java)
                    dgsEnableDataFetcherInstrumentation.value
                } else {
                    method.returnType != CompletionStage::class.java && method.returnType != CompletableFuture::class.java
                }

                dataFetcherInstrumentationEnabled["${dgsDataAnnotation.parentType}.${dgsDataAnnotation.field}"] = enableInstrumentation

                try {
                    if (!typeDefinitionRegistry.getType(dgsDataAnnotation.parentType).isPresent) {
                        logger.error("Parent type ${dgsDataAnnotation.parentType} not found, but it was referenced in ${javaClass.name} in @DgsData annotation for field ${dgsDataAnnotation.field}")
                        throw InvalidDgsConfigurationException("Parent type ${dgsDataAnnotation.parentType} not found, but it was referenced on ${javaClass.name} in @DgsData annotation for field ${dgsDataAnnotation.field}")
                    }
                    val type = typeDefinitionRegistry.getType(dgsDataAnnotation.parentType).get()
                    if (type is InterfaceTypeDefinition) {
                        val implementationsOf = typeDefinitionRegistry.getImplementationsOf(type)
                        implementationsOf.forEach { implType ->
                            val dataFetcher = createBasicDataFetcher(method, dgsComponent)
                            codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates(implType.name, dgsDataAnnotation.field), dataFetcher)
                            dataFetcherInstrumentationEnabled["${implType.name}.${dgsDataAnnotation.field}"] = enableInstrumentation
                        }
                    } else if (type is UnionTypeDefinition) {
                        type.memberTypes.filterIsInstance<TypeName>().forEach { memberType ->
                            val dataFetcher = createBasicDataFetcher(method, dgsComponent)
                            codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates(memberType.name, dgsDataAnnotation.field), dataFetcher)
                            dataFetcherInstrumentationEnabled["${memberType.name}.${dgsDataAnnotation.field}"] = enableInstrumentation
                        }
                    } else {
                        val dataFetcher = createBasicDataFetcher(method, dgsComponent)
                        codeRegistryBuilder.dataFetcher(FieldCoordinates.coordinates(dgsDataAnnotation.parentType, dgsDataAnnotation.field), dataFetcher)
                    }
                } catch (ex: Exception) {
                    logger.error("Invalid parent type " + dgsDataAnnotation.parentType)
                    throw ex
                }
            }
        }
    }

    private fun findEntityFetchers(dgsComponents: Map<String, Any>) {
        dgsComponents.values.forEach { dgsComponent ->
            val javaClass = when {
                dgsComponent.javaClass.name.contains("EnhancerBySpringCGLIB") -> {
                    dgsComponent.javaClass.superclass
                }
                else -> {
                    dgsComponent.javaClass
                }
            }

            javaClass.methods.filter { it.isAnnotationPresent(DgsEntityFetcher::class.java) }.forEach { method ->
                val dgsEntityFetcherAnnotation = method.getAnnotation(DgsEntityFetcher::class.java)

                val enableInstrumentation = method.getAnnotation(DgsEnableDataFetcherInstrumentation::class.java)?.value
                        ?: false
                dataFetcherInstrumentationEnabled["${"__entities"}.${dgsEntityFetcherAnnotation.name}"] = enableInstrumentation

                entityFetchers[dgsEntityFetcherAnnotation.name] = Pair(dgsComponent, method)
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
        return try {
            val args = mutableListOf<Any?>()

            method.parameters.forEach { parameter ->

                when {
                    parameter.isAnnotationPresent(InputArgument::class.java) -> {
                        val annotation = parameter.getAnnotation(InputArgument::class.java)
                        val parameterName = annotation.value
                        val collectionType = annotation.collectionType.java
                        val parameterValue: Any? = environment.getArgument(parameterName)

                        val convertValue: Any? = if (parameterValue is List<*> && collectionType != Object::class.java) {
                            try {
                                // Return a list of elements that are converted to their collection type, e.e.g. List<Person>, List<String> etc.
                                parameterValue.map { item -> objectMapper.convertValue(item, collectionType) }.toList()
                            } catch (ex: Exception) {
                                throw DgsInvalidInputArgumentException("Specified type '${collectionType}' is invalid for $parameterName.", ex)
                            }
                        } else if (parameterValue is List<*>) {
                            // Return as is for all other types of Lists, i.e. custom scalars e.g. List<UUID>, and other types like List<Integer> etc. that have Object collection type
                            parameterValue
                        } else if (parameterValue is MultipartFile) {
                            parameterValue
                        } else if (environment.fieldDefinition.arguments.find { it.name == parameterName }?.type is GraphQLScalarType) {
                            // Return the value with it's type for scalars
                            parameterValue
                        } else {
                            // Return the converted value mapped to the defined type
                            objectMapper.convertValue(parameterValue, parameter.type)
                        }

                        val paramType = parameter.type

                        if (convertValue != null && !paramType.isPrimitive && !paramType.isAssignableFrom(convertValue.javaClass)) {
                            throw DgsInvalidInputArgumentException("Specified type '${parameter.type}' is invalid. Found ${parameterValue?.javaClass?.name} instead.")
                        }

                        args.add(convertValue)
                    }
                    //This only works if parameter names are present in the class file
                    environment.containsArgument(parameter.name) -> {
                        val parameterValue: Any = environment.getArgument(parameter.name)
                        val convertValue = objectMapper.convertValue(parameterValue, parameter.type)
                        args.add(convertValue)
                    }
                    parameter.type == DataFetchingEnvironment::class.java || parameter.type == DgsDataFetchingEnvironment::class.java -> {
                        args.add(environment)
                    }
                    else -> {
                        logger.warn("Unknown argument '${parameter.name}' on data fetcher ${dgsComponent.javaClass.name}.${method.name}")
                        //This might cause an exception, but parameter's the best effort we can do
                        args.add(null)
                    }
                }
            }

            method.invoke(dgsComponent, *args.toTypedArray())
        } catch (ex: InvocationTargetException) {
            throw ex.targetException
        }
    }

    private fun findTypeResolvers(dgsComponents: Map<String, Any>, runtimeWiringBuilder: RuntimeWiring.Builder, mergedRegistry: TypeDefinitionRegistry) {
        val registeredTypeResolvers = mutableSetOf<String>()

        dgsComponents.values.forEach { dgsComponent ->
            dgsComponent.javaClass.methods.filter { it.isAnnotationPresent(DgsTypeResolver::class.java) }.forEach { method ->
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
                                        val typeName = method.invoke(dgsComponent, env.getObject()) as String
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
                is Coercing<*, *> -> runtimeWiringBuilder.scalar(GraphQLScalarType.newScalar().name(annotation.name).coercing(scalarComponent).build())
                else -> throw RuntimeException("Invalid @DgsScalar type: the class must implement graphql.schema.Coercing")
            }

        }
    }

    internal fun findSchemaFiles(basedir: String = "schema", hasDynamicTypeRegistry: Boolean = false): Array<Resource> {
        val cl = Thread.currentThread().contextClassLoader

        val resolver = PathMatchingResourcePatternResolver(cl)
        val schemas = try {
            val resources = resolver.getResources("classpath*:${basedir}/**/*.graphql*")
            if (resources.isEmpty()) {
                throw NoSchemaFoundException()
            }
            resources
        } catch (ex: Exception) {
            if (existingTypeDefinitionRegistry.isPresent || hasDynamicTypeRegistry) {
                logger.info("No schema files found, but a schema was provided as an TypeDefinitionRegistry")
                return arrayOf()
            } else {
                logger.error("No schema files found. Define schemas in src/main/resources/${basedir}/**/*.graphqls")
                throw NoSchemaFoundException()
            }
        }

        val testSchemas = try {
            resolver.getResources("classpath:${basedir}-test/**/*.graphql*")
        } catch (ex: Exception) {
            arrayOf<Resource>()
        }

        val metaInfSchemas = try {
            resolver.getResources("classpath*:META-INF/${basedir}/**/*.graphql*")
        } catch (ex: Exception) {
            arrayOf<Resource>()
        }

        return schemas + testSchemas + metaInfSchemas
    }

}
