/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal;

import com.apollographql.federation.graphqljava.Federation;
import com.netflix.graphql.dgs.DgsCodeRegistry;
import com.netflix.graphql.dgs.DgsCodeRegistryBuilder;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.netflix.graphql.dgs.DgsDefaultTypeResolver;
import com.netflix.graphql.dgs.DgsDirective;
import com.netflix.graphql.dgs.DgsEnableDataFetcherInstrumentation;
import com.netflix.graphql.dgs.DgsEntityFetcher;
import com.netflix.graphql.dgs.DgsFederationResolver;
import com.netflix.graphql.dgs.DgsRuntimeWiring;
import com.netflix.graphql.dgs.DgsScalar;
import com.netflix.graphql.dgs.DgsTypeDefinitionRegistry;
import com.netflix.graphql.dgs.DgsTypeResolver;
import com.netflix.graphql.dgs.exceptions.DataFetcherInputArgumentSchemaMismatchException;
import com.netflix.graphql.dgs.exceptions.DataFetcherSchemaMismatchException;
import com.netflix.graphql.dgs.exceptions.DuplicateEntityFetcherException;
import com.netflix.graphql.dgs.exceptions.InvalidDgsConfigurationException;
import com.netflix.graphql.dgs.exceptions.InvalidDgsEntityFetcher;
import com.netflix.graphql.dgs.exceptions.InvalidTypeResolverException;
import com.netflix.graphql.dgs.exceptions.NoSchemaFoundException;
import com.netflix.graphql.dgs.federation.DefaultDgsFederationResolver;
import com.netflix.graphql.dgs.internal.method.InputArgumentResolver;
import com.netflix.graphql.dgs.internal.method.MethodDataFetcherFactory;
import com.netflix.graphql.dgs.internal.utils.SelectionSetUtil;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.ImplementingTypeDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectTypeExtensionDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.UnionTypeDefinition;
import graphql.language.Value;
import graphql.parser.MultiSourceReader;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.visibility.DefaultGraphqlFieldVisibility;
import graphql.schema.visibility.GraphqlFieldVisibility;
import kotlin.Pair;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.SynthesizingMethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Main framework class that scans for components and configures a runtime executable schema. */
public class DgsSchemaProvider {
    public static final String DEFAULT_SCHEMA_LOCATION = "classpath*:schema/**/*.graphql*";

    private static final Logger logger = LoggerFactory.getLogger(DgsSchemaProvider.class);

    private final ApplicationContext applicationContext;
    private final Optional<DgsFederationResolver> federationResolver;
    private final Optional<TypeDefinitionRegistry> existingTypeDefinitionRegistry;
    private final List<String> schemaLocations;
    private final List<DataFetcherResultProcessor> dataFetcherResultProcessors;
    private final Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler;
    private final EntityFetcherRegistry entityFetcherRegistry;
    private final Optional<DataFetcherFactory<?>> defaultDataFetcherFactory;
    private final MethodDataFetcherFactory methodDataFetcherFactory;
    private final Predicate<Object> componentFilter;
    private final boolean schemaWiringValidationEnabled;
    private final boolean enableEntityFetcherCustomScalarParsing;
    private final TypeResolver fallbackTypeResolver;
    private final boolean enableStrictMode;
    private final boolean federationEnabled;

    private final AtomicReference<DataFetcherInfo> dataFetcherInfo =
            new AtomicReference<>(new DataFetcherInfo(List.of(), Set.of(), Set.of()));

    public DgsSchemaProvider(
            ApplicationContext applicationContext,
            Optional<DgsFederationResolver> federationResolver,
            Optional<TypeDefinitionRegistry> existingTypeDefinitionRegistry,
            List<String> schemaLocations,
            List<DataFetcherResultProcessor> dataFetcherResultProcessors,
            Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler,
            EntityFetcherRegistry entityFetcherRegistry,
            Optional<DataFetcherFactory<?>> defaultDataFetcherFactory,
            MethodDataFetcherFactory methodDataFetcherFactory,
            Predicate<Object> componentFilter,
            boolean schemaWiringValidationEnabled,
            boolean enableEntityFetcherCustomScalarParsing,
            TypeResolver fallbackTypeResolver,
            boolean enableStrictMode,
            boolean federationEnabled) {
        this.applicationContext = applicationContext;
        this.federationResolver = federationResolver;
        this.existingTypeDefinitionRegistry = existingTypeDefinitionRegistry;
        this.schemaLocations = schemaLocations;
        this.dataFetcherResultProcessors = dataFetcherResultProcessors;
        this.dataFetcherExceptionHandler = dataFetcherExceptionHandler;
        this.entityFetcherRegistry = entityFetcherRegistry;
        this.defaultDataFetcherFactory = defaultDataFetcherFactory;
        this.methodDataFetcherFactory = methodDataFetcherFactory;
        this.componentFilter = componentFilter;
        this.schemaWiringValidationEnabled = schemaWiringValidationEnabled;
        this.enableEntityFetcherCustomScalarParsing = enableEntityFetcherCustomScalarParsing;
        this.fallbackTypeResolver = fallbackTypeResolver;
        this.enableStrictMode = enableStrictMode;
        this.federationEnabled = federationEnabled;
    }

    /** Constructor used by Spring; optional collaborators fall back to their defaults when no bean is present. */
    @Autowired
    public DgsSchemaProvider(
            ApplicationContext applicationContext,
            Optional<DgsFederationResolver> federationResolver,
            Optional<TypeDefinitionRegistry> existingTypeDefinitionRegistry,
            ObjectProvider<DataFetcherResultProcessor> dataFetcherResultProcessors,
            Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler,
            ObjectProvider<EntityFetcherRegistry> entityFetcherRegistry,
            Optional<DataFetcherFactory<?>> defaultDataFetcherFactory,
            MethodDataFetcherFactory methodDataFetcherFactory,
            ObjectProvider<TypeResolver> fallbackTypeResolver) {
        this(
                applicationContext,
                federationResolver,
                existingTypeDefinitionRegistry,
                List.of(DEFAULT_SCHEMA_LOCATION),
                dataFetcherResultProcessors.orderedStream().toList(),
                dataFetcherExceptionHandler,
                entityFetcherRegistry.getIfAvailable(EntityFetcherRegistry::new),
                defaultDataFetcherFactory,
                methodDataFetcherFactory,
                null,
                true,
                false,
                fallbackTypeResolver.getIfAvailable(),
                true,
                true);
    }

    public DgsSchemaProvider(
            ApplicationContext applicationContext,
            Optional<DgsFederationResolver> federationResolver,
            Optional<TypeDefinitionRegistry> existingTypeDefinitionRegistry,
            MethodDataFetcherFactory methodDataFetcherFactory) {
        this(
                applicationContext,
                federationResolver,
                existingTypeDefinitionRegistry,
                List.of(DEFAULT_SCHEMA_LOCATION),
                List.of(),
                Optional.empty(),
                new EntityFetcherRegistry(),
                Optional.empty(),
                methodDataFetcherFactory,
                null,
                true,
                false,
                null,
                true,
                true);
    }

    /** Creates a builder for {@link DgsSchemaProvider}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link DgsSchemaProvider}. */
    public static final class Builder {
        private ApplicationContext applicationContext;
        private Optional<DgsFederationResolver> federationResolver = Optional.empty();
        private Optional<TypeDefinitionRegistry> existingTypeDefinitionRegistry = Optional.empty();
        private List<String> schemaLocations = List.of(DEFAULT_SCHEMA_LOCATION);
        private List<DataFetcherResultProcessor> dataFetcherResultProcessors = List.of();
        private Optional<DataFetcherExceptionHandler> dataFetcherExceptionHandler = Optional.empty();
        private EntityFetcherRegistry entityFetcherRegistry = new EntityFetcherRegistry();
        private Optional<DataFetcherFactory<?>> defaultDataFetcherFactory = Optional.empty();
        private MethodDataFetcherFactory methodDataFetcherFactory;
        private Predicate<Object> componentFilter;
        private boolean schemaWiringValidationEnabled = true;
        private boolean enableEntityFetcherCustomScalarParsing = false;
        private TypeResolver fallbackTypeResolver;
        private boolean enableStrictMode = true;
        private boolean federationEnabled = true;

        private Builder() {
        }

        public Builder applicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
            return this;
        }

        public Builder federationResolver(Optional<DgsFederationResolver> federationResolver) {
            this.federationResolver = federationResolver;
            return this;
        }

        public Builder existingTypeDefinitionRegistry(Optional<TypeDefinitionRegistry> registry) {
            this.existingTypeDefinitionRegistry = registry;
            return this;
        }

        public Builder schemaLocations(List<String> schemaLocations) {
            this.schemaLocations = schemaLocations;
            return this;
        }

        public Builder dataFetcherResultProcessors(List<DataFetcherResultProcessor> dataFetcherResultProcessors) {
            this.dataFetcherResultProcessors = dataFetcherResultProcessors;
            return this;
        }

        public Builder dataFetcherExceptionHandler(Optional<DataFetcherExceptionHandler> handler) {
            this.dataFetcherExceptionHandler = handler;
            return this;
        }

        public Builder entityFetcherRegistry(EntityFetcherRegistry entityFetcherRegistry) {
            this.entityFetcherRegistry = entityFetcherRegistry;
            return this;
        }

        public Builder defaultDataFetcherFactory(Optional<DataFetcherFactory<?>> defaultDataFetcherFactory) {
            this.defaultDataFetcherFactory = defaultDataFetcherFactory;
            return this;
        }

        public Builder methodDataFetcherFactory(MethodDataFetcherFactory methodDataFetcherFactory) {
            this.methodDataFetcherFactory = methodDataFetcherFactory;
            return this;
        }

        public Builder componentFilter(Predicate<Object> componentFilter) {
            this.componentFilter = componentFilter;
            return this;
        }

        public Builder schemaWiringValidationEnabled(boolean schemaWiringValidationEnabled) {
            this.schemaWiringValidationEnabled = schemaWiringValidationEnabled;
            return this;
        }

        public Builder enableEntityFetcherCustomScalarParsing(boolean enableEntityFetcherCustomScalarParsing) {
            this.enableEntityFetcherCustomScalarParsing = enableEntityFetcherCustomScalarParsing;
            return this;
        }

        public Builder fallbackTypeResolver(TypeResolver fallbackTypeResolver) {
            this.fallbackTypeResolver = fallbackTypeResolver;
            return this;
        }

        public Builder enableStrictMode(boolean enableStrictMode) {
            this.enableStrictMode = enableStrictMode;
            return this;
        }

        public Builder federationEnabled(boolean federationEnabled) {
            this.federationEnabled = federationEnabled;
            return this;
        }

        public DgsSchemaProvider build() {
            return new DgsSchemaProvider(
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
                    fallbackTypeResolver,
                    enableStrictMode,
                    federationEnabled);
        }
    }

    /**
     * Returns an immutable list of {@link DataFetcherReference}s that were identified after the schema was loaded.
     * The returned list will be unstable until the schema is fully loaded.
     */
    public List<DataFetcherReference> resolvedDataFetchers() {
        return dataFetcherInfo.get().dataFetchers();
    }

    /**
     * Given a field, expressed as a GraphQL {@code <Type>.<field name>} tuple, return {@code true} if the given field
     * has <em>instrumentation</em> enabled, or is missing an explicit setting, and {@code false} if the given field has
     * <em>instrumentation</em> explicitly disabled.
     *
     * <p>The method should be considered unstable until the schema is fully loaded.
     */
    public boolean isFieldTracingInstrumentationEnabled(String field) {
        return dataFetcherInfo.get().tracingEnabled().contains(field);
    }

    /**
     * Given a field, expressed as a GraphQL {@code <Type>.<field name>} tuple, return {@code true} if the given field
     * has <em>instrumentation</em> enabled, or is missing an explicit setting, and {@code false} if the given field has
     * <em>instrumentation</em> explicitly disabled.
     *
     * <p>The method should be considered unstable until the schema is fully loaded.
     */
    public boolean isFieldMetricsInstrumentationEnabled(String field) {
        return dataFetcherInfo.get().metricsEnabled().contains(field);
    }

    public SchemaProviderResult schema() {
        return schema(null);
    }

    public SchemaProviderResult schema(@Language("GraphQL") String schema) {
        return schema(schema, DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY);
    }

    public SchemaProviderResult schema(@Language("GraphQL") String schema, GraphqlFieldVisibility fieldVisibility) {
        return schema(schema, fieldVisibility, Set.of());
    }

    public SchemaProviderResult schema(
            @Language("GraphQL") String schema, GraphqlFieldVisibility fieldVisibility, Set<Resource> schemaResources) {
        return schema(schema, fieldVisibility, schemaResources, true);
    }

    public SchemaProviderResult schema(
            @Language("GraphQL") String schema,
            GraphqlFieldVisibility fieldVisibility,
            Set<Resource> schemaResources,
            boolean showSdlComments) {
        MutableDataFetcherInfo mutableDataFetcherInfo = new MutableDataFetcherInfo();
        SchemaProviderResult result = computeSchema(schema, fieldVisibility, schemaResources, showSdlComments,
                mutableDataFetcherInfo);
        this.dataFetcherInfo.set(mutableDataFetcherInfo.toImmutable());
        return result;
    }

    private SchemaProviderResult computeSchema(
            String schema,
            GraphqlFieldVisibility fieldVisibility,
            Set<Resource> schemaResources,
            boolean showSdlComments,
            MutableDataFetcherInfo dataFetcherInfo) {
        long startTime = System.currentTimeMillis();
        List<DgsBean> dgsComponents = applicationContext.getBeansWithAnnotation(DgsComponent.class).values().stream()
                .filter(bean -> componentFilter == null || componentFilter.test(bean))
                .map(DgsBean::new)
                .toList();

        TypeDefinitionRegistry mergedRegistry;
        if (schema == null) {
            boolean hasDynamicTypeRegistry = dgsComponents.stream()
                    .anyMatch(component -> !component.annotatedMethods(DgsTypeDefinitionRegistry.class).isEmpty());
            MultiSourceReader.Builder readerBuilder =
                    MultiSourceReader.newMultiSourceReader().trackData(false);
            for (Resource schemaFile : findSchemaFiles(hasDynamicTypeRegistry)) {
                try {
                    readerBuilder.reader(
                            new InputStreamReader(schemaFile.getInputStream(), StandardCharsets.UTF_8),
                            schemaFile.getFilename());
                } catch (IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
                // Add a reader that inserts a newline between schema files to avoid issues when
                // the source files aren't newline-terminated.
                readerBuilder.reader(new StringReader("\n"), "newline");
            }

            for (Resource resource : schemaResources) {
                try {
                    readerBuilder.reader(
                            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8),
                            resource.getFilename());
                } catch (IOException ex) {
                    throw new java.io.UncheckedIOException(ex);
                }
            }
            mergedRegistry = new SchemaParser().parse(readerBuilder.build());
        } else {
            mergedRegistry = new SchemaParser().parse(schema);
        }

        if (existingTypeDefinitionRegistry.isPresent()) {
            mergedRegistry = mergedRegistry.merge(existingTypeDefinitionRegistry.get());
        }

        GraphQLCodeRegistry.Builder codeRegistryBuilder =
                GraphQLCodeRegistry.newCodeRegistry().fieldVisibility(fieldVisibility);
        if (defaultDataFetcherFactory.isPresent()) {
            codeRegistryBuilder.defaultDataFetcher(defaultDataFetcherFactory.get());
        }

        RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring
                .newRuntimeWiring()
                .strictMode(enableStrictMode)
                .codeRegistry(codeRegistryBuilder)
                .fieldVisibility(fieldVisibility);

        DgsCodeRegistryBuilder dgsCodeRegistryBuilder =
                new DgsCodeRegistryBuilder(dataFetcherResultProcessors, codeRegistryBuilder, applicationContext);

        for (DgsBean dgsComponent : dgsComponents) {
            TypeDefinitionRegistry typeDefinitionRegistry =
                    invokeDgsTypeDefinitionRegistry(dgsComponent, mergedRegistry);
            if (typeDefinitionRegistry != null) {
                mergedRegistry.merge(typeDefinitionRegistry);
            }
        }
        findScalars(applicationContext, runtimeWiringBuilder);
        findDirectives(applicationContext, runtimeWiringBuilder);

        findDataFetchers(dgsComponents, dgsCodeRegistryBuilder, mergedRegistry, dataFetcherInfo);
        findTypeResolvers(dgsComponents, runtimeWiringBuilder, mergedRegistry);

        for (DgsBean dgsComponent : dgsComponents) {
            invokeDgsCodeRegistry(dgsComponent, codeRegistryBuilder, mergedRegistry);
        }

        runtimeWiringBuilder.codeRegistry(codeRegistryBuilder.build());

        for (DgsBean dgsComponent : dgsComponents) {
            invokeDgsRuntimeWiring(dgsComponent, runtimeWiringBuilder);
        }
        checkUnregisteredTypeResolvers(runtimeWiringBuilder, mergedRegistry);

        RuntimeWiring runtimeWiring = runtimeWiringBuilder.build();

        SchemaGenerator.Options schemaOptions =
                SchemaGenerator.Options.defaultOptions().useCommentsAsDescriptions(showSdlComments);

        GraphQLSchema graphQLSchema;
        if (federationEnabled) {
            findEntityFetchers(dgsComponents, mergedRegistry, runtimeWiring, dataFetcherInfo);

            DgsFederationResolver federationResolverInstance = federationResolver.orElseGet(() ->
                    new DefaultDgsFederationResolver(
                            entityFetcherRegistry, dataFetcherExceptionHandler, applicationContext));

            DataFetcher<Object> entityFetcher = federationResolverInstance.entitiesFetcher();
            TypeResolver typeResolver = federationResolverInstance.typeResolver();

            graphQLSchema = Federation
                    .transform(mergedRegistry, runtimeWiring, schemaOptions)
                    .fetchEntities(entityFetcher)
                    .resolveEntityType(typeResolver)
                    .build();
        } else {
            graphQLSchema = new SchemaGenerator().makeExecutableSchema(schemaOptions, mergedRegistry, runtimeWiring);
        }

        logger.debug("DGS initialized schema in {}ms", System.currentTimeMillis() - startTime);

        return new SchemaProviderResult(graphQLSchema, runtimeWiring);
    }

    private TypeDefinitionRegistry invokeDgsTypeDefinitionRegistry(
            DgsBean dgsComponent, TypeDefinitionRegistry registry) {
        TypeDefinitionRegistry result = null;
        for (Method method : dgsComponent.annotatedMethods(DgsTypeDefinitionRegistry.class)) {
            if (method.getReturnType() != TypeDefinitionRegistry.class) {
                throw new InvalidDgsConfigurationException(
                        "Method annotated with @DgsTypeDefinitionRegistry must have return type "
                                + "TypeDefinitionRegistry");
            }
            TypeDefinitionRegistry invoked;
            if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == TypeDefinitionRegistry.class) {
                invoked = (TypeDefinitionRegistry)
                        ReflectionUtils.invokeMethod(method, dgsComponent.instance(), registry);
            } else {
                invoked = (TypeDefinitionRegistry) ReflectionUtils.invokeMethod(method, dgsComponent.instance());
            }
            result = result == null ? invoked : result.merge(invoked);
        }
        return result;
    }

    private void invokeDgsCodeRegistry(
            DgsBean dgsComponent, GraphQLCodeRegistry.Builder codeRegistryBuilder, TypeDefinitionRegistry registry) {
        DgsCodeRegistryBuilder dgsCodeRegistryBuilder =
                new DgsCodeRegistryBuilder(dataFetcherResultProcessors, codeRegistryBuilder, applicationContext);

        for (Method method : dgsComponent.annotatedMethods(DgsCodeRegistry.class)) {
            if (method.getReturnType() != GraphQLCodeRegistry.Builder.class
                    && method.getReturnType() != DgsCodeRegistryBuilder.class) {
                throw new InvalidDgsConfigurationException(
                        "Method annotated with @DgsCodeRegistry must have return type GraphQLCodeRegistry.Builder or "
                                + "DgsCodeRegistryBuilder");
            }

            if (method.getParameterCount() != 2
                    || method.getParameterTypes()[0] != method.getReturnType()
                    // Check that the first argument is of type DgsCodeRegistryBuilder or GraphQLCodeRegistry.Builder
                    // and the return type is the same
                    || method.getParameterTypes()[1] != TypeDefinitionRegistry.class) {
                throw new InvalidDgsConfigurationException(
                        "Method annotated with @DgsCodeRegistry must accept the following arguments: "
                                + "GraphQLCodeRegistry.Builder or DgsCodeRegistryBuilder, and TypeDefinitionRegistry. "
                                + dgsComponent.instance().getClass().getName() + "." + method.getName()
                                + " has the following arguments: " + joinTypes(method.getParameterTypes()));
            }

            if (method.getReturnType() == DgsCodeRegistryBuilder.class) {
                ReflectionUtils.invokeMethod(method, dgsComponent.instance(), dgsCodeRegistryBuilder, registry);
            } else if (method.getReturnType() == GraphQLCodeRegistry.Builder.class) {
                ReflectionUtils.invokeMethod(method, dgsComponent.instance(), codeRegistryBuilder, registry);
            }
        }
    }

    private void invokeDgsRuntimeWiring(DgsBean dgsComponent, RuntimeWiring.Builder runtimeWiringBuilder) {
        for (Method method : dgsComponent.annotatedMethods(DgsRuntimeWiring.class)) {
            if (method.getReturnType() != RuntimeWiring.Builder.class) {
                throw new InvalidDgsConfigurationException(
                        "Method annotated with @DgsRuntimeWiring must have return type RuntimeWiring.Builder");
            }

            if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != RuntimeWiring.Builder.class) {
                throw new InvalidDgsConfigurationException(
                        "Method annotated with @DgsRuntimeWiring must accept an argument of type "
                                + "RuntimeWiring.Builder. " + dgsComponent.instance().getClass().getName() + "."
                                + method.getName() + " has the following arguments: "
                                + joinTypes(method.getParameterTypes()));
            }

            ReflectionUtils.invokeMethod(method, dgsComponent.instance(), runtimeWiringBuilder);
        }
    }

    private static String joinTypes(Class<?>[] types) {
        return Arrays.stream(types).map(Class::toString).collect(Collectors.joining(", "));
    }

    private void findDataFetchers(
            Collection<DgsBean> dgsComponents,
            DgsCodeRegistryBuilder codeRegistryBuilder,
            TypeDefinitionRegistry typeDefinitionRegistry,
            MutableDataFetcherInfo dataFetcherInfo) {
        for (DgsBean dgsComponent : dgsComponents) {
            for (Method method : dgsComponent.methods()) {
                MergedAnnotations mergedAnnotations =
                        MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
                if (!mergedAnnotations.isPresent(DgsData.class)) {
                    continue;
                }
                List<MergedAnnotation<DgsData>> filteredMergedAnnotations = mergedAnnotations
                        .stream(DgsData.class)
                        .filter(annotation -> AopUtils.getTargetClass(((Method) annotation.getSource())
                                                .getDeclaringClass())
                                        == AopUtils.getTargetClass(method.getDeclaringClass()))
                        .toList();
                for (MergedAnnotation<DgsData> dgsDataAnnotation : filteredMergedAnnotations) {
                    registerDataFetcher(
                            typeDefinitionRegistry,
                            codeRegistryBuilder,
                            dgsComponent,
                            method,
                            dgsDataAnnotation,
                            mergedAnnotations,
                            dataFetcherInfo);
                }
            }
        }
    }

    private void registerDataFetcher(
            TypeDefinitionRegistry typeDefinitionRegistry,
            DgsCodeRegistryBuilder codeRegistryBuilder,
            DgsBean dgsComponent,
            Method method,
            MergedAnnotation<DgsData> dgsDataAnnotation,
            MergedAnnotations mergedAnnotations,
            MutableDataFetcherInfo dataFetcherInfo) {
        String annotatedField = dgsDataAnnotation.getString("field");
        String field = !annotatedField.isEmpty() ? annotatedField : method.getName();
        String parentType = dgsDataAnnotation.getString("parentType");

        boolean duplicate = dataFetcherInfo.dataFetchers.stream()
                .anyMatch(reference ->
                        reference.getParentType().equals(parentType) && reference.getField().equals(field));
        if (duplicate) {
            logger.error("Duplicate data fetchers registered for {}.{}", parentType, field);
            throw new InvalidDgsConfigurationException(
                    "Duplicate data fetchers registered for " + parentType + "." + field);
        }

        dataFetcherInfo.dataFetchers.add(
                new DataFetcherReference(dgsComponent.instance(), method, mergedAnnotations, parentType, field));

        boolean enableTracingInstrumentation;
        if (method.isAnnotationPresent(DgsEnableDataFetcherInstrumentation.class)) {
            enableTracingInstrumentation =
                    method.getAnnotation(DgsEnableDataFetcherInstrumentation.class).value();
        } else {
            enableTracingInstrumentation = method.getReturnType() != CompletionStage.class
                    && method.getReturnType() != CompletableFuture.class;
        }
        if (enableTracingInstrumentation) {
            dataFetcherInfo.tracingEnabled.add(parentType + "." + field);
        }

        boolean enableMetricsInstrumentation;
        if (method.isAnnotationPresent(DgsEnableDataFetcherInstrumentation.class)) {
            enableMetricsInstrumentation =
                    method.getAnnotation(DgsEnableDataFetcherInstrumentation.class).value();
        } else {
            enableMetricsInstrumentation = true;
        }
        if (enableMetricsInstrumentation) {
            dataFetcherInfo.metricsEnabled.add(parentType + "." + field);
        }

        String methodClassName = method.getDeclaringClass().getName();
        try {
            TypeDefinition<?> typeDefinition =
                    typeDefinitionRegistry.getType(parentType).orElse(null);
            if (typeDefinition == null) {
                logger.error(
                        "Parent type {} not found, but it was referenced in {} in @DgsData annotation for field {}",
                        parentType,
                        methodClassName,
                        field);
                throw new InvalidDgsConfigurationException("Parent type " + parentType
                        + " not found, but it was referenced on " + methodClassName
                        + " in @DgsData annotation for field " + field);
            }
            if (typeDefinition instanceof InterfaceTypeDefinition interfaceTypeDefinition) {
                if (schemaWiringValidationEnabled) {
                    FieldDefinition matchingField = getMatchingFieldOnInterfaceOrExtensions(
                            methodClassName, interfaceTypeDefinition, field, typeDefinitionRegistry, parentType);
                    checkInputArgumentsAreValid(
                            method,
                            matchingField.getInputValueDefinitions().stream()
                                    .map(input -> input.getName())
                                    .collect(Collectors.toSet()));
                }
                List<ObjectTypeDefinition> implementationsOf =
                        typeDefinitionRegistry.getImplementationsOf(interfaceTypeDefinition);
                for (ObjectTypeDefinition implType : implementationsOf) {
                    // if we have a datafetcher explicitly defined for a parentType/field, use that and do not
                    // register the base implementation for interfaces
                    FieldCoordinates coordinates = FieldCoordinates.coordinates(implType.getName(), field);
                    if (!codeRegistryBuilder.hasDataFetcher(coordinates)) {
                        DataFetcher<?> dataFetcher = methodDataFetcherFactory.createDataFetcher(
                                dgsComponent.instance(), method, coordinates);
                        codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);
                        if (enableTracingInstrumentation) {
                            dataFetcherInfo.tracingEnabled.add(coordinates.toString());
                        }
                        if (enableMetricsInstrumentation) {
                            dataFetcherInfo.metricsEnabled.add(coordinates.toString());
                        }
                    }
                }
            } else if (typeDefinition instanceof UnionTypeDefinition unionTypeDefinition) {
                for (Type<?> memberType : unionTypeDefinition.getMemberTypes()) {
                    if (!(memberType instanceof TypeName typeName)) {
                        continue;
                    }
                    FieldCoordinates coordinates = FieldCoordinates.coordinates(typeName.getName(), field);
                    DataFetcher<?> dataFetcher =
                            methodDataFetcherFactory.createDataFetcher(dgsComponent.instance(), method, coordinates);
                    codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);
                    if (enableTracingInstrumentation) {
                        dataFetcherInfo.tracingEnabled.add(coordinates.toString());
                    }
                    if (enableMetricsInstrumentation) {
                        dataFetcherInfo.metricsEnabled.add(coordinates.toString());
                    }
                }
            } else if (typeDefinition instanceof ObjectTypeDefinition objectTypeDefinition) {
                if (schemaWiringValidationEnabled) {
                    FieldDefinition matchingField = getMatchingFieldOnObjectOrExtensions(
                            methodClassName, objectTypeDefinition, field, typeDefinitionRegistry, parentType);
                    checkInputArgumentsAreValid(
                            method,
                            matchingField.getInputValueDefinitions().stream()
                                    .map(input -> input.getName())
                                    .collect(Collectors.toSet()));
                }

                FieldCoordinates coordinates = FieldCoordinates.coordinates(parentType, field);
                DataFetcher<?> dataFetcher =
                        methodDataFetcherFactory.createDataFetcher(dgsComponent.instance(), method, coordinates);
                codeRegistryBuilder.dataFetcher(coordinates, dataFetcher);
            } else {
                throw new InvalidDgsConfigurationException("Parent type " + parentType + " referenced on "
                        + methodClassName + " in @DgsData annotation for field " + field
                        + " must be either an interface, a union, or an object.");
            }
        } catch (Exception ex) {
            logger.error("Invalid parent type {}", parentType);
            throw ex;
        }
    }

    private FieldDefinition getMatchingFieldOnObjectOrExtensions(
            String methodClassName,
            ObjectTypeDefinition type,
            String field,
            TypeDefinitionRegistry typeDefinitionRegistry,
            String parentType) {
        return type.getFieldDefinitions().stream()
                .filter(fieldDefinition -> fieldDefinition.getName().equals(field))
                .findFirst()
                .or(() -> typeDefinitionRegistry
                        .objectTypeExtensions()
                        .getOrDefault(parentType, List.<ObjectTypeExtensionDefinition>of())
                        .stream()
                        .flatMap(extension -> extension.getFieldDefinitions().stream())
                        .filter(fieldDefinition -> fieldDefinition.getName().equals(field))
                        .findFirst())
                .orElseThrow(() -> new DataFetcherSchemaMismatchException("@DgsData in " + methodClassName
                        + " on field " + field + " references object type `" + parentType
                        + "` it has no field named `" + field + "`. All data fetchers registered with "
                        + "@DgsData|@DgsQuery|@DgsMutation|@DgsSubscription must match a field in the schema."));
    }

    private FieldDefinition getMatchingFieldOnInterfaceOrExtensions(
            String methodClassName,
            InterfaceTypeDefinition type,
            String field,
            TypeDefinitionRegistry typeDefinitionRegistry,
            String parentType) {
        return type.getFieldDefinitions().stream()
                .filter(fieldDefinition -> fieldDefinition.getName().equals(field))
                .findFirst()
                .or(() -> typeDefinitionRegistry
                        .interfaceTypeExtensions()
                        .getOrDefault(parentType, List.of())
                        .stream()
                        .flatMap(extension -> extension.getFieldDefinitions().stream())
                        .filter(fieldDefinition -> fieldDefinition.getName().equals(field))
                        .findFirst())
                .orElseThrow(() -> new DataFetcherSchemaMismatchException("@DgsData in " + methodClassName
                        + " on field `" + field + "` references interface `" + parentType
                        + "` it has no field named `" + field + "`. All data fetchers registered with @DgsData "
                        + "must match a field in the schema."));
    }

    private void checkInputArgumentsAreValid(Method method, Set<String> argumentNames) {
        Method bridgedMethod = BridgeMethodResolver.findBridgedMethod(method);
        List<MethodParameter> methodParameters = new ArrayList<>();
        for (Parameter parameter : bridgedMethod.getParameters()) {
            MethodParameter methodParameter = SynthesizingMethodParameter.forParameter(parameter);
            methodParameter.initParameterNameDiscovery(methodDataFetcherFactory.getParameterNameDiscoverer());
            methodParameters.add(methodParameter);
        }

        for (MethodParameter m : methodParameters) {
            var selectedArgResolver = methodDataFetcherFactory.getSelectedArgumentResolver(m);
            if (!(selectedArgResolver instanceof InputArgumentResolver inputArgumentResolver)) {
                continue;
            }
            String argName = inputArgumentResolver.resolveArgumentName(m);
            if (!argumentNames.contains(argName)) {
                String paramName = m.getParameterName();
                if (paramName == null) {
                    continue;
                }
                String arguments = !argumentNames.isEmpty()
                        ? "Found the following argument(s) in the schema: "
                                + argumentNames.stream().collect(Collectors.joining(", ", "[", "]"))
                        : "No arguments on the field are defined in the schema.";

                throw new DataFetcherInputArgumentSchemaMismatchException("@InputArgument(name = \"" + argName
                        + "\") defined in " + method.getDeclaringClass() + " in method `" + method.getName()
                        + "` on parameter named `" + paramName + "` has no matching argument with name `" + argName
                        + "` in the GraphQL schema. " + arguments);
            }
        }
    }

    private void findEntityFetchers(
            Collection<DgsBean> dgsComponents,
            TypeDefinitionRegistry registry,
            RuntimeWiring runtimeWiring,
            MutableDataFetcherInfo dataFetcherInfo) {
        for (DgsBean dgsComponent : dgsComponents) {
            for (Method method : dgsComponent.annotatedMethods(DgsEntityFetcher.class)) {
                DgsEntityFetcher dgsEntityFetcherAnnotation = method.getAnnotation(DgsEntityFetcher.class);

                if (method.getParameterCount() > 2) {
                    throw new InvalidDgsEntityFetcher("@DgsEntityFetcher "
                            + dgsComponent.instance().getClass().getName() + "." + method.getName()
                            + " is invalid. A DgsEntityFetcher can only accept up to 2 arguments");
                }

                if (Arrays.stream(method.getParameterTypes()).noneMatch(type -> type.isAssignableFrom(Map.class))) {
                    throw new InvalidDgsEntityFetcher("@DgsEntityFetcher "
                            + dgsComponent.instance().getClass().getName() + "." + method.getName()
                            + " is invalid. A DgsEntityFetcher must accept an argument of type Map<String, Object>");
                }

                if (Arrays.stream(method.getParameterTypes())
                        .anyMatch(type -> !type.isAssignableFrom(Map.class)
                                && !type.isAssignableFrom(DgsDataFetchingEnvironment.class))) {
                    throw new InvalidDgsEntityFetcher("@DgsEntityFetcher "
                            + dgsComponent.instance().getClass().getName() + "." + method.getName()
                            + " is invalid. A DgsEntityFetcher can only accept arguments of type Map<String, Object> "
                            + "or DgsDataFetchingEnvironment");
                }

                String entityFetcherTypeName = dgsEntityFetcherAnnotation.name();
                String coordinateName = "_entities." + entityFetcherTypeName;

                MergedAnnotations mergedAnnotations =
                        MergedAnnotations.from(method, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
                dataFetcherInfo.dataFetchers.add(new DataFetcherReference(
                        dgsComponent.instance(), method, mergedAnnotations, "Query", coordinateName));

                DgsEnableDataFetcherInstrumentation instrumentationAnnotation =
                        method.getAnnotation(DgsEnableDataFetcherInstrumentation.class);
                boolean enableInstrumentation =
                        instrumentationAnnotation != null && instrumentationAnnotation.value();
                if (enableInstrumentation) {
                    dataFetcherInfo.tracingEnabled.add(coordinateName);
                    dataFetcherInfo.metricsEnabled.add(coordinateName);
                }

                // Throw if an entity fetcher for the same type was already registered
                Pair<Object, Method> firstEntityFetcher =
                        entityFetcherRegistry.getEntityFetchers().get(entityFetcherTypeName);
                if (firstEntityFetcher != null) {
                    // It's possible the schema() method is invoked multiple times, so check if the second entity
                    // fetcher is different from the existing one.
                    if (!firstEntityFetcher.equals(new Pair<>(dgsComponent.instance(), method))) {
                        throw new DuplicateEntityFetcherException(
                                entityFetcherTypeName,
                                firstEntityFetcher.getFirst().getClass(),
                                firstEntityFetcher.getSecond(),
                                dgsComponent.instance().getClass(),
                                method);
                    }
                }

                entityFetcherRegistry
                        .getEntityFetchers()
                        .put(entityFetcherTypeName, new Pair<>(dgsComponent.instance(), method));

                Optional<TypeDefinition> type = registry.getType(entityFetcherTypeName);

                if (enableEntityFetcherCustomScalarParsing) {
                    type.ifPresent(typeDef -> registerEntityFetcherInputMappings(
                            entityFetcherTypeName, typeDef, registry, runtimeWiring));
                }
            }
        }
    }

    private void registerEntityFetcherInputMappings(
            String entityFetcherTypeName,
            TypeDefinition<?> typeDef,
            TypeDefinitionRegistry registry,
            RuntimeWiring runtimeWiring) {
        ImplementingTypeDefinition<?> typeDefinition =
                typeDef instanceof ImplementingTypeDefinition<?> implementingTypeDefinition
                        ? implementingTypeDefinition
                        : null;
        Directive keyDirective = typeDef.getDirectives().stream()
                .filter(directive -> directive.getName().equals("key"))
                .findFirst()
                .orElse(null);
        if (keyDirective == null) {
            return;
        }

        var fields = keyDirective.getArgumentsByName().get("fields");
        if (fields == null) {
            return;
        }
        Value<?> value = fields.getValue();
        if (!(value instanceof StringValue stringValue)) {
            return;
        }
        String fieldsSelection = stringValue.getValue();
        List<List<String>> paths = fieldsSelection != null ? SelectionSetUtil.toPaths(fieldsSelection) : List.of();

        Map<List<String>, Coercing<?, ?>> mappings = new LinkedHashMap<>();
        for (List<String> path : paths) {
            Coercing<?, ?> coercing = traverseType(path.iterator(), typeDefinition, registry, runtimeWiring);
            if (coercing != null) {
                mappings.put(path, coercing);
            }
        }
        entityFetcherRegistry.getEntityFetcherInputMappings().put(entityFetcherTypeName, mappings);
    }

    private Coercing<?, ?> traverseType(
            java.util.Iterator<String> path,
            ImplementingTypeDefinition<?> type,
            TypeDefinitionRegistry registry,
            RuntimeWiring runtimeWiring) {
        if (type == null || !path.hasNext()) {
            return null;
        }

        String item = path.next();
        FieldDefinition fieldDefinition = type.getFieldDefinitions().stream()
                .filter(candidate -> candidate.getName().equals(item))
                .findFirst()
                .orElse(null);
        Type<?> fieldDefinitionType = fieldDefinition != null ? fieldDefinition.getType() : null;

        if (fieldDefinitionType instanceof TypeName typeName) {
            TypeDefinition<?> fieldType = registry.getType(typeName.getName()).orElse(null);

            if (fieldType instanceof ObjectTypeDefinition objectTypeDefinition) {
                return traverseType(path, objectTypeDefinition, registry, runtimeWiring);
            }
            if (fieldType instanceof ScalarTypeDefinition scalarTypeDefinition) {
                GraphQLScalarType scalarType = runtimeWiring.getScalars().get(scalarTypeDefinition.getName());
                return scalarType != null ? scalarType.getCoercing() : null;
            }
        }

        return null;
    }

    private void findTypeResolvers(
            Collection<DgsBean> dgsComponents,
            RuntimeWiring.Builder runtimeWiringBuilder,
            TypeDefinitionRegistry mergedRegistry) {
        for (DgsBean dgsComponent : dgsComponents) {
            for (Method method : dgsComponent.annotatedMethods(DgsTypeResolver.class)) {
                DgsTypeResolver annotation = method.getAnnotation(DgsTypeResolver.class);

                if (method.getReturnType() != String.class) {
                    throw new InvalidTypeResolverException("@DgsTypeResolvers must return String");
                }

                if (method.getParameterCount() != 1) {
                    throw new InvalidTypeResolverException("@DgsTypeResolvers must take exactly one parameter");
                }

                if (!mergedRegistry.hasType(new TypeName(annotation.name()))) {
                    throw new InvalidTypeResolverException(
                            "could not find type name '" + annotation.name() + "' in schema");
                }

                boolean overrideTypeResolver = false;
                DgsDefaultTypeResolver defaultTypeResolver = method.getAnnotation(DgsDefaultTypeResolver.class);
                if (defaultTypeResolver != null) {
                    overrideTypeResolver = dgsComponents.stream()
                            .anyMatch(component -> !component.equals(dgsComponent)
                                    && component.annotatedMethods(DgsTypeResolver.class).stream()
                                            .anyMatch(other -> other.getAnnotation(DgsTypeResolver.class)
                                                    .name()
                                                    .equals(annotation.name())));
                }
                // do not add the default resolver if another resolver with the same name is present
                if (defaultTypeResolver == null || !overrideTypeResolver) {
                    Object dgsComponentInstance = dgsComponent.instance();
                    runtimeWiringBuilder.type(TypeRuntimeWiring
                            .newTypeWiring(annotation.name())
                            .typeResolver(env -> {
                                String typeName = (String)
                                        ReflectionUtils.invokeMethod(method, dgsComponentInstance, (Object) env.getObject());
                                return typeName != null ? env.getSchema().getObjectType(typeName) : null;
                            }));
                }
            }
        }
    }

    private void checkUnregisteredTypeResolvers(
            RuntimeWiring.Builder runtimeWiringBuilder, TypeDefinitionRegistry mergedRegistry) {
        // Build the RuntimeWiring to get access to registered type resolvers
        RuntimeWiring runtimeWiring = runtimeWiringBuilder.build();
        Set<String> registeredTypeResolvers = runtimeWiring.getTypeResolvers().keySet();

        // Add a fallback type resolver for types that don't have a type resolver registered.
        // This works when the Java type has the same name as the GraphQL type.
        // Check for unregistered interface types
        List<String> unregisteredInterfaceTypes = mergedRegistry.types().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof InterfaceTypeDefinition)
                .map(Map.Entry::getKey)
                .filter(name -> !registeredTypeResolvers.contains(name))
                .toList();
        checkTypeResolverExists(unregisteredInterfaceTypes, runtimeWiringBuilder, "interface");

        // Check for unregistered union types
        List<String> unregisteredUnionTypes = mergedRegistry.types().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof UnionTypeDefinition)
                .map(Map.Entry::getKey)
                .filter(name -> !registeredTypeResolvers.contains(name))
                .toList();
        checkTypeResolverExists(unregisteredUnionTypes, runtimeWiringBuilder, "union");
    }

    private void checkTypeResolverExists(
            List<String> unregisteredTypes, RuntimeWiring.Builder runtimeWiringBuilder, String typeName) {
        for (String unregisteredType : unregisteredTypes) {
            runtimeWiringBuilder.type(TypeRuntimeWiring.newTypeWiring(unregisteredType).typeResolver(env -> {
                Object instance = env.getObject();
                GraphQLObjectType resolvedType =
                        env.getSchema().getObjectType(instance.getClass().getSimpleName());
                if (resolvedType != null) {
                    return resolvedType;
                }
                GraphQLObjectType fallbackType =
                        fallbackTypeResolver != null ? fallbackTypeResolver.getType(env) : null;
                if (fallbackType != null) {
                    return fallbackType;
                }
                throw new InvalidTypeResolverException("The default type resolver could not find a suitable Java "
                        + "type for GraphQL " + typeName + " type `" + unregisteredType + "`. Provide a "
                        + "@DgsTypeResolver for `" + instance.getClass().getSimpleName() + "`.");
            }));
        }
    }

    private void findScalars(ApplicationContext applicationContext, RuntimeWiring.Builder runtimeWiringBuilder) {
        for (Object scalarComponent : applicationContext.getBeansWithAnnotation(DgsScalar.class).values()) {
            DgsScalar annotation = AopUtils.getTargetClass(scalarComponent).getAnnotation(DgsScalar.class);
            if (scalarComponent instanceof Coercing<?, ?> coercing) {
                runtimeWiringBuilder.scalar(GraphQLScalarType
                        .newScalar()
                        .name(annotation.name())
                        .coercing(coercing)
                        .build());
            } else {
                throw new RuntimeException("Invalid @DgsScalar type: the class must implement graphql.schema.Coercing");
            }
        }
    }

    private void findDirectives(ApplicationContext applicationContext, RuntimeWiring.Builder runtimeWiringBuilder) {
        for (Object directiveComponent : applicationContext.getBeansWithAnnotation(DgsDirective.class).values()) {
            DgsDirective annotation = AopUtils.getTargetClass(directiveComponent).getAnnotation(DgsDirective.class);
            if (directiveComponent instanceof SchemaDirectiveWiring schemaDirectiveWiring) {
                if (!annotation.name().isBlank()) {
                    runtimeWiringBuilder.directive(annotation.name(), schemaDirectiveWiring);
                } else {
                    runtimeWiringBuilder.directiveWiring(schemaDirectiveWiring);
                }
            } else {
                throw new RuntimeException(
                        "Invalid @DgsDirective type: the class must implement graphql.schema.idl.SchemaDirectiveWiring");
            }
        }
    }

    public List<Resource> findSchemaFiles() {
        return findSchemaFiles(false);
    }

    public List<Resource> findSchemaFiles(boolean hasDynamicTypeRegistry) {
        ResourcePatternResolver resolver = ResourcePatternUtils.getResourcePatternResolver(applicationContext);
        Set<Resource> schemas = new LinkedHashSet<>();
        for (String schemaLocation : schemaLocations) {
            try {
                schemas.addAll(Arrays.asList(resolver.getResources(schemaLocation)));
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        }

        if (schemas.isEmpty()) {
            if (existingTypeDefinitionRegistry.isPresent() || hasDynamicTypeRegistry) {
                logger.info("No schema files found, but a schema was provided as an TypeDefinitionRegistry");
            } else {
                logger.error(
                        "No schema files found in {}. Define schema locations with property "
                                + "dgs.graphql.schema-locations",
                        schemaLocations);
                throw new NoSchemaFoundException();
            }
        }

        Resource[] metaInfSchemas;
        try {
            metaInfSchemas = resolver.getResources("classpath*:META-INF/schema/**/*.graphql*");
        } catch (IOException ex) {
            metaInfSchemas = new Resource[0];
        }

        schemas.addAll(Arrays.asList(metaInfSchemas));

        return schemas.stream()
                .filter(resource -> {
                    String filename = resource.getFilename();
                    if (filename == null) {
                        return false;
                    }
                    String lowerCase = filename.toLowerCase(java.util.Locale.ROOT);
                    return lowerCase.endsWith(".graphql") || lowerCase.endsWith(".graphqls");
                })
                .toList();
    }

    private record DgsBean(Object instance, Class<?> targetClass, List<Method> methods) {
        DgsBean(Object instance) {
            this(
                    instance,
                    AopUtils.getTargetClass(instance),
                    List.of(ReflectionUtils.getUniqueDeclaredMethods(
                            AopUtils.getTargetClass(instance), ReflectionUtils.USER_DECLARED_METHODS)));
        }

        List<Method> annotatedMethods(Class<? extends Annotation> annotation) {
            return methods.stream()
                    .filter(method -> method.isAnnotationPresent(annotation))
                    .toList();
        }
    }

    private static final class MutableDataFetcherInfo {
        private final List<DataFetcherReference> dataFetchers = new ArrayList<>();
        private final Set<String> tracingEnabled = new LinkedHashSet<>();
        private final Set<String> metricsEnabled = new LinkedHashSet<>();

        DataFetcherInfo toImmutable() {
            return new DataFetcherInfo(List.copyOf(dataFetchers), Set.copyOf(tracingEnabled),
                    Set.copyOf(metricsEnabled));
        }
    }

    private record DataFetcherInfo(
            List<DataFetcherReference> dataFetchers, Set<String> tracingEnabled, Set<String> metricsEnabled) {
    }
}
