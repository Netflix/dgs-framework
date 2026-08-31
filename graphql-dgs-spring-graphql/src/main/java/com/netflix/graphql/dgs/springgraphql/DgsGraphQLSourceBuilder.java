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

package com.netflix.graphql.dgs.springgraphql;

import com.netflix.graphql.dgs.InputArgument;
import com.netflix.graphql.dgs.internal.DataFetcherReference;
import com.netflix.graphql.dgs.internal.DgsSchemaProvider;
import com.netflix.graphql.dgs.internal.SchemaProviderResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.graphql.execution.AbstractGraphQlSourceBuilder;
import org.springframework.graphql.execution.GraphQlSource.SchemaResourceBuilder;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;
import org.springframework.graphql.execution.SchemaMappingInspector;
import org.springframework.graphql.execution.SchemaReport;
import org.springframework.graphql.execution.SelfDescribingDataFetcher;
import org.springframework.graphql.execution.TypeDefinitionConfigurer;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class DgsGraphQLSourceBuilder extends AbstractGraphQlSourceBuilder<SchemaResourceBuilder>
        implements SchemaResourceBuilder {
    private final DgsSchemaProvider dgsSchemaProvider;
    private final boolean showSdlComments;

    private final List<TypeDefinitionConfigurer> typeDefinitionConfigurers = new ArrayList<>();
    private final List<RuntimeWiringConfigurer> runtimeWiringConfigurers = new ArrayList<>();

    private final Set<Resource> schemaResources = new LinkedHashSet<>();

    private TypeResolver typeResolver;

    private Consumer<SchemaReport> schemaReportConsumer;

    private Consumer<SchemaMappingInspector.Initializer> initializerConsumer;

    public DgsGraphQLSourceBuilder(DgsSchemaProvider dgsSchemaProvider, boolean showSdlComments) {
        this.dgsSchemaProvider = dgsSchemaProvider;
        this.showSdlComments = showSdlComments;
    }

    @Override
    protected GraphQLSchema initGraphQlSchema() {
        SchemaProviderResult schema = dgsSchemaProvider.schema(
                null,
                graphql.schema.visibility.DefaultGraphqlFieldVisibility.DEFAULT_FIELD_VISIBILITY,
                schemaResources,
                showSdlComments);
        setupSchemaReporter(schema);
        return schema.getGraphQLSchema();
    }

    @Override
    public SchemaResourceBuilder schemaResources(Resource... resources) {
        return this;
    }

    @Override
    public SchemaResourceBuilder configureTypeDefinitions(TypeDefinitionConfigurer configurer) {
        this.typeDefinitionConfigurers.add(configurer);
        return this;
    }

    @Override
    public SchemaResourceBuilder configureRuntimeWiring(RuntimeWiringConfigurer configurer) {
        this.runtimeWiringConfigurers.add(configurer);
        return this;
    }

    @Override
    public SchemaResourceBuilder defaultTypeResolver(TypeResolver typeResolver) {
        this.typeResolver = typeResolver;
        return this;
    }

    @Override
    public SchemaResourceBuilder inspectSchemaMappings(Consumer<SchemaReport> reportConsumer) {
        this.schemaReportConsumer = reportConsumer;
        return this;
    }

    @Override
    public SchemaResourceBuilder inspectSchemaMappings(
            Consumer<SchemaMappingInspector.Initializer> initializerConsumer, Consumer<SchemaReport> reportConsumer) {
        this.schemaReportConsumer = reportConsumer;
        this.initializerConsumer = initializerConsumer;
        return this;
    }

    @Override
    public SchemaResourceBuilder schemaFactory(
            BiFunction<TypeDefinitionRegistry, RuntimeWiring, GraphQLSchema> schemaFactory) {
        throw new IllegalStateException("Overriding the schema factory is not supported in this builder");
    }

    public static class DgsSelfDescribingDataFetcher implements SelfDescribingDataFetcher<Object> {
        private final DataFetcherReference dataFetcher;

        public DgsSelfDescribingDataFetcher(DataFetcherReference dataFetcher) {
            this.dataFetcher = dataFetcher;
        }

        public DataFetcherReference getDataFetcher() {
            return dataFetcher;
        }

        @Override
        public Object get(DataFetchingEnvironment environment) {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @Override
        public String getDescription() {
            return dataFetcher.getField();
        }

        @Override
        public ResolvableType getReturnType() {
            return ResolvableType.forMethodReturnType(dataFetcher.getMethod());
        }

        @Override
        public Map<String, ResolvableType> getArguments() {
            Map<String, ResolvableType> arguments = new LinkedHashMap<>();
            for (Parameter parameter : dataFetcher.getMethod().getParameters()) {
                if (!parameter.isAnnotationPresent(InputArgument.class)) {
                    continue;
                }
                String annotationName = parameter.getAnnotation(InputArgument.class).name();
                String name = !annotationName.isEmpty() ? annotationName : parameter.getName();
                arguments.put(name, ResolvableType.forClass(parameter.getType()));
            }
            return arguments;
        }
    }

    private Map<String, Map<String, SelfDescribingDataFetcher<Object>>> wrapDataFetchers(
            List<DataFetcherReference> dataFetchers) {
        Map<String, Map<String, SelfDescribingDataFetcher<Object>>> wrappedDataFetchers = new LinkedHashMap<>();
        for (DataFetcherReference dataFetcher : dataFetchers) {
            wrappedDataFetchers
                    .computeIfAbsent(dataFetcher.getParentType(), key -> new LinkedHashMap<>())
                    .put(dataFetcher.getField(), new DgsSelfDescribingDataFetcher(dataFetcher));
        }

        return wrappedDataFetchers;
    }

    @SuppressWarnings("unchecked")
    private void setupSchemaReporter(SchemaProviderResult schema) {
        // wrap DGS data fetchers in a SelfDescribingDataFetcher for schema reporting
        Map<String, Map<String, SelfDescribingDataFetcher<Object>>> selfDescribingDgsDataFetchers =
                wrapDataFetchers(dgsSchemaProvider.resolvedDataFetchers());

        Map<String, Map<String, DataFetcher<Object>>> mergedDataFetchers = new LinkedHashMap<>();
        selfDescribingDgsDataFetchers.forEach((type, fetchers) -> mergedDataFetchers.put(type, new HashMap<>(fetchers)));

        Map<String, Map<String, DataFetcher>> springGraphQLDataFetchers = schema.getRuntimeWiring().getDataFetchers();
        springGraphQLDataFetchers.forEach((type, springFetchers) -> {
            Map<String, DataFetcher<Object>> merged =
                    mergedDataFetchers.computeIfAbsent(type, key -> new LinkedHashMap<>());
            // Merge the spring data fetcher map with dgs data fetchers
            springFetchers.forEach((field, fetcher) -> merged.put(field, (DataFetcher<Object>) fetcher));
        });
        if (schemaReportConsumer != null) {
            configureGraphQl(builder -> {
                SchemaReport report =
                        SchemaMappingInspector.inspect(schema.getGraphQLSchema(), (Map) mergedDataFetchers);
                schemaReportConsumer.accept(report);
            });
        }
    }
}
