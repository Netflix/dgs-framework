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

package com.netflix.graphql.dgs.metrics.micrometer;

import com.netflix.graphql.dgs.Internal;
import com.netflix.graphql.dgs.internal.DgsSchemaProvider;
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric;
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag;
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider;
import com.netflix.graphql.dgs.metrics.micrometer.utils.QuerySignatureRepository;
import com.netflix.graphql.types.errors.ErrorType;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.InvalidSyntaxError;
import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.QueryComplexityCalculator;
import graphql.execution.DataFetcherResult;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.SimplePerformantInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters;
import graphql.execution.preparsed.persisted.PersistedQueryNotFound;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.FragmentSpread;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.OperationDefinition.Operation;
import graphql.language.Selection;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLTypeUtil;
import graphql.validation.ValidationError;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.data.metrics.AutoTimer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class DgsGraphQLMetricsInstrumentation extends SimplePerformantInstrumentation {
    private static final Logger log = LoggerFactory.getLogger(DgsGraphQLMetricsInstrumentation.class);

    private final DgsSchemaProvider schemaProvider;
    private final DgsMeterRegistrySupplier registrySupplier;
    private final DgsGraphQLMetricsTagsProvider tagsProvider;
    private final DgsGraphQLMetricsProperties properties;
    private final LimitedTagMetricResolver limitedTagMetricResolver;
    private final Optional<QuerySignatureRepository> optQuerySignatureRepository;
    private final AutoTimer autoTimer;

    public DgsGraphQLMetricsInstrumentation(
            DgsSchemaProvider schemaProvider,
            DgsMeterRegistrySupplier registrySupplier,
            DgsGraphQLMetricsTagsProvider tagsProvider,
            DgsGraphQLMetricsProperties properties,
            LimitedTagMetricResolver limitedTagMetricResolver,
            Optional<QuerySignatureRepository> optQuerySignatureRepository,
            AutoTimer autoTimer) {
        this.schemaProvider = schemaProvider;
        this.registrySupplier = registrySupplier;
        this.tagsProvider = tagsProvider;
        this.properties = properties;
        this.limitedTagMetricResolver = limitedTagMetricResolver;
        this.optQuerySignatureRepository = optQuerySignatureRepository;
        this.autoTimer = autoTimer;
    }

    public DgsGraphQLMetricsInstrumentation(
            DgsSchemaProvider schemaProvider,
            DgsMeterRegistrySupplier registrySupplier,
            DgsGraphQLMetricsTagsProvider tagsProvider,
            DgsGraphQLMetricsProperties properties,
            LimitedTagMetricResolver limitedTagMetricResolver,
            AutoTimer autoTimer) {
        this(
                schemaProvider,
                registrySupplier,
                tagsProvider,
                properties,
                limitedTagMetricResolver,
                Optional.empty(),
                autoTimer);
    }

    @Deprecated
    @Override
    public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
        return new MetricsInstrumentationState(registrySupplier.get(), limitedTagMetricResolver);
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecution(
            InstrumentationExecutionParameters parameters, InstrumentationState state) {
        if (!properties.getQuery().isEnabled()) {
            return SimpleInstrumentationContext.noOp();
        }
        MetricsInstrumentationState metricsState = requireMetricsState(state);
        metricsState.startTimer();

        metricsState.operationNameValue = parameters.getOperation();
        metricsState.isIntrospectionQuery = QueryUtils.isIntrospectionQuery(parameters.getExecutionInput());
        metricsState.queryTypeValue = getPersistedQueryType(parameters.getExecutionInput()).name();
        return SimpleInstrumentationContext.whenCompleted((result, exc) -> {
            List<Tag> tags = new ArrayList<>();
            tagsProvider.getContextualTags().forEach(tags::add);
            tagsProvider.getExecutionTags(metricsState, parameters, result, exc).forEach(tags::add);
            metricsState.tags().forEach(tags::add);

            metricsState.stopTimer(autoTimer.builder(GqlMetric.QUERY.getKey()).tags(tags));
        });
    }

    @Override
    public CompletableFuture<ExecutionResult> instrumentExecutionResult(
            ExecutionResult executionResult, InstrumentationExecutionParameters parameters, InstrumentationState state) {
        MetricsInstrumentationState metricsState = requireMetricsState(state);

        // if this is an error due to PersistedQueryNotFound, we exclude from the gql.error metric
        // this is captured in a separate counter instead
        List<GraphQLError> persistedQueryNotFoundErrors =
                executionResult.getErrors().stream()
                        .filter(error -> error.getErrorType() instanceof PersistedQueryNotFound)
                        .toList();
        if (!persistedQueryNotFoundErrors.isEmpty()) {
            MeterRegistry registry = registrySupplier.get();
            for (GraphQLError error : persistedQueryNotFoundErrors) {
                List<Tag> errorTags =
                        List.of(Tag.of(
                                GqlTag.PERSISTED_QUERY_ID.getKey(),
                                String.valueOf(error.getExtensions().get("persistedQueryId"))));
                registry.counter(GqlMetric.PERSISTED_QUERY_NOT_FOUND.getKey(), errorTags).increment();
            }
            return CompletableFuture.completedFuture(executionResult);
        }

        Collection<ErrorUtils.ErrorTagValues> errorTagValues =
                ErrorUtils.sanitizeErrorPaths(executionResult.getErrors());
        if (!errorTagValues.isEmpty()) {
            List<Tag> baseTags = new ArrayList<>();
            tagsProvider.getContextualTags().forEach(baseTags::add);
            tagsProvider.getExecutionTags(metricsState, parameters, executionResult, null).forEach(baseTags::add);
            metricsState.tags().forEach(baseTags::add);

            MeterRegistry registry = registrySupplier.get();
            for (ErrorUtils.ErrorTagValues errorTagValue : errorTagValues) {
                List<Tag> errorTags = new ArrayList<>(baseTags.size() + 3);
                errorTags.addAll(baseTags);
                errorTags.add(Tag.of(GqlTag.PATH.getKey(), errorTagValue.path()));
                errorTags.add(Tag.of(GqlTag.ERROR_CODE.getKey(), errorTagValue.type()));
                errorTags.add(Tag.of(GqlTag.ERROR_DETAIL.getKey(), errorTagValue.detail()));

                registry.counter(GqlMetric.ERROR.getKey(), errorTags).increment();
            }
        }

        return CompletableFuture.completedFuture(executionResult);
    }

    @Override
    public DataFetcher<?> instrumentDataFetcher(
            DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
        MetricsInstrumentationState metricsState = requireMetricsState(state);
        String gqlField = TagUtils.resolveDataFetcherTagValue(parameters);

        if (parameters.isTrivialDataFetcher()
                || metricsState.isIntrospectionQuery
                || TagUtils.shouldIgnoreTag(gqlField)
                || !schemaProvider.isFieldMetricsInstrumentationEnabled(gqlField)
                || !properties.getResolver().isEnabled()) {
            return dataFetcher;
        }

        return environment -> {
            MeterRegistry registry = registrySupplier.get();
            List<Tag> baseTags = new ArrayList<>();
            baseTags.add(Tag.of(GqlTag.FIELD.getKey(), gqlField));
            tagsProvider.getContextualTags().forEach(baseTags::add);
            metricsState.tags().forEach(baseTags::add);

            Timer.Sample sampler = Timer.start(registry);
            try {
                Object result = dataFetcher.get(environment);
                if (result instanceof CompletionStage<?> completionStage) {
                    completionStage.whenComplete((value, error) -> recordDataFetcherMetrics(
                            registry,
                            sampler,
                            metricsState,
                            parameters,
                            checkResponseForErrors(value, error),
                            baseTags));
                } else {
                    recordDataFetcherMetrics(
                            registry, sampler, metricsState, parameters, checkResponseForErrors(result, null), baseTags);
                }
                return result;
            } catch (Exception exc) {
                recordDataFetcherMetrics(registry, sampler, metricsState, parameters, exc, baseTags);
                throw exc;
            }
        };
    }

    private Throwable checkResponseForErrors(Object value, Throwable error) {
        if (error != null) {
            return error;
        }
        if (value instanceof DataFetcherResult<?> dataFetcherResult && dataFetcherResult.hasErrors()) {
            return new GraphQLException("GraphQL errors in response: " + dataFetcherResult.getErrors());
        }
        return null;
    }

    /**
     * Port the implementation from MaxQueryComplexityInstrumentation in graphql-java and store the computed complexity
     * in the MetricsInstrumentationState for access to add tags to metrics.
     */
    @Override
    public InstrumentationContext<List<ValidationError>> beginValidation(
            InstrumentationValidationParameters parameters, InstrumentationState state) {
        MetricsInstrumentationState metricsState = requireMetricsState(state);
        Document document = parameters.getDocument();
        if (document == null) {
            return SimpleInstrumentationContext.noOp();
        }
        QuerySignatureRepository querySignatureRepository = optQuerySignatureRepository.orElse(null);
        if (querySignatureRepository == null) {
            return SimpleInstrumentationContext.noOp();
        }

        return SimpleInstrumentationContext.whenCompleted((errors, throwable) -> {
            if ((errors == null || errors.isEmpty()) && throwable == null) {
                metricsState.querySignatureValue =
                        querySignatureRepository.get(document, parameters).orElse(null);
            }
        });
    }

    @Override
    public InstrumentationContext<ExecutionResult> beginExecuteOperation(
            InstrumentationExecuteOperationParameters parameters, InstrumentationState state) {
        MetricsInstrumentationState metricsState = requireMetricsState(state);
        if (parameters.getExecutionContext().getRoot() == null) {
            metricsState.operationValue =
                    parameters.getExecutionContext().getOperationDefinition().getOperation();
            if (metricsState.operationNameValue == null) {
                metricsState.operationNameValue =
                        nameOrFallback(parameters.getExecutionContext().getOperationDefinition());
            }
        }
        if (properties.getTags().getComplexity().isEnabled()) {
            metricsState.queryComplexityValue = ComplexityUtils.resolveComplexity(parameters);
        }
        return super.beginExecuteOperation(parameters, state);
    }

    /**
     * Returns a fallback name if the operation is unnamed.
     *
     * <p>If the operation is named, the name is returned.
     *
     * <p>Otherwise, a name is created from the first selection in the selection set,
     * prefixed with a {@code -} to indicate that it is a fallback name.
     */
    private static String nameOrFallback(OperationDefinition operationDefinition) {
        if (operationDefinition.getName() != null) {
            return operationDefinition.getName();
        }
        Selection<?> selection =
                operationDefinition.getSelectionSet() == null
                                || operationDefinition.getSelectionSet().getSelections().isEmpty()
                        ? null
                        : operationDefinition.getSelectionSet().getSelections().get(0);
        if (selection == null) {
            // This should never happen, but it's possible
            return "-noSelections";
        }
        if (selection instanceof Field field) {
            return "-" + field.getName();
        }
        if (selection instanceof InlineFragment inlineFragment) {
            return "-" + inlineFragment.getTypeCondition().getName();
        }
        if (selection instanceof FragmentSpread fragmentSpread) {
            return "-" + fragmentSpread.getName();
        }
        throw new RuntimeException("Unknown Selection type: " + selection);
    }

    private void recordDataFetcherMetrics(
            MeterRegistry registry,
            Timer.Sample timerSampler,
            MetricsInstrumentationState state,
            InstrumentationFieldFetchParameters parameters,
            Throwable error,
            Iterable<Tag> baseTags) {
        List<Tag> recordedTags = new ArrayList<>();
        baseTags.forEach(recordedTags::add);
        tagsProvider.getFieldFetchTags(state, parameters, error).forEach(recordedTags::add);

        timerSampler.stop(autoTimer.builder(GqlMetric.RESOLVER.getKey()).tags(recordedTags).register(registry));
    }

    private static MetricsInstrumentationState requireMetricsState(InstrumentationState state) {
        if (!(state instanceof MetricsInstrumentationState metricsState)) {
            throw new IllegalArgumentException("Failed requirement.");
        }
        return metricsState;
    }

    public enum PersistedQueryType {
        NOT_APQ,
        FULL_APQ,
        APQ
    }

    private PersistedQueryType getPersistedQueryType(ExecutionInput executionInput) {
        boolean hasPersistedQueryExtension = executionInput.getExtensions().containsKey("persistedQuery");
        boolean isMarker = "PersistedQueryMarker".equals(executionInput.getQuery());
        if (isMarker && hasPersistedQueryExtension) {
            return PersistedQueryType.APQ;
        }
        if (!isMarker && hasPersistedQueryExtension) {
            return PersistedQueryType.FULL_APQ;
        }
        return PersistedQueryType.NOT_APQ;
    }

    public static class MetricsInstrumentationState implements InstrumentationState {
        private final MeterRegistry registry;
        private final LimitedTagMetricResolver limitedTagMetricResolver;

        private Timer.Sample timerSample;

        boolean isIntrospectionQuery = false;
        Integer queryComplexityValue;
        Operation operationValue;
        String operationNameValue;
        QuerySignatureRepository.QuerySignature querySignatureValue;
        String queryTypeValue = PersistedQueryType.NOT_APQ.name();

        public MetricsInstrumentationState(
                MeterRegistry registry, LimitedTagMetricResolver limitedTagMetricResolver) {
            this.registry = registry;
            this.limitedTagMetricResolver = limitedTagMetricResolver;
        }

        public boolean isIntrospectionQuery() {
            return isIntrospectionQuery;
        }

        public void setIntrospectionQuery(boolean introspectionQuery) {
            this.isIntrospectionQuery = introspectionQuery;
        }

        public Optional<Integer> getQueryComplexity() {
            return Optional.ofNullable(queryComplexityValue);
        }

        public Optional<String> getOperation() {
            return Optional.ofNullable(operationValue).map(Enum::name);
        }

        public Optional<String> getOperationName() {
            return Optional.ofNullable(operationNameValue);
        }

        public Optional<QuerySignatureRepository.QuerySignature> getQuerySignature() {
            return Optional.ofNullable(querySignatureValue);
        }

        public void startTimer() {
            this.timerSample = Timer.start(this.registry);
        }

        public void stopTimer(Timer.Builder timer) {
            if (this.timerSample != null) {
                this.timerSample.stop(timer.register(this.registry));
            }
        }

        @Internal
        public Iterable<Tag> tags() {
            List<Tag> tags = new ArrayList<>();
            tags.add(Tag.of(
                    GqlTag.QUERY_COMPLEXITY.getKey(),
                    queryComplexityValue != null ? queryComplexityValue.toString() : TagUtils.TAG_VALUE_NONE));
            tags.add(Tag.of(
                    GqlTag.OPERATION.getKey(),
                    operationValue != null ? operationValue.name() : TagUtils.TAG_VALUE_NONE));

            limitedTagMetricResolver
                    .tags(
                            GqlTag.OPERATION_NAME.getKey(),
                            operationNameValue != null ? operationNameValue : TagUtils.TAG_VALUE_ANONYMOUS)
                    .forEach(tags::add);

            limitedTagMetricResolver
                    .tags(
                            GqlTag.QUERY_SIG_HASH.getKey(),
                            querySignatureValue != null ? querySignatureValue.getHash() : TagUtils.TAG_VALUE_NONE)
                    .forEach(tags::add);

            tags.add(Tag.of(
                    GqlTag.PERSISTED_QUERY_TYPE.getKey(),
                    queryTypeValue != null ? queryTypeValue : PersistedQueryType.NOT_APQ.name()));

            return tags;
        }
    }

    static final class QueryUtils {
        private QueryUtils() {
        }

        static boolean isIntrospectionQuery(ExecutionInput input) {
            return input.getQuery().contains("query IntrospectionQuery")
                    || "IntrospectionQuery".equals(input.getOperationName());
        }
    }

    static final class ComplexityUtils {
        private static final FieldComplexityCalculator COMPLEXITY_CALCULATOR =
                (environment, childComplexity) -> childComplexity + 1;

        private static final List<Integer> QUERY_COMPLEXITY_BUCKETS =
                List.of(5, 10, 25, 50, 100, 200, 500, 1000, 2000, 5000, 10000);

        private ComplexityUtils() {
        }

        static Integer resolveComplexity(InstrumentationExecuteOperationParameters parameters) {
            ExecutionContext executionContext = parameters.getExecutionContext();
            QueryComplexityCalculator complexityCalculator =
                    QueryComplexityCalculator
                            .newCalculator()
                            .fieldComplexityCalculator(COMPLEXITY_CALCULATOR)
                            .schema(executionContext.getGraphQLSchema())
                            .document(executionContext.getDocument())
                            .operationName(executionContext.getExecutionInput().getOperationName())
                            .variables(executionContext.getCoercedVariables())
                            .build();
            int complexity;
            try {
                complexity = complexityCalculator.calculate();
            } catch (Exception exc) {
                log.error("Unable to compute the query complexity!", exc);
                return null;
            }
            for (Integer bucket : QUERY_COMPLEXITY_BUCKETS) {
                if (complexity < bucket) {
                    return bucket;
                }
            }
            return Integer.MAX_VALUE;
        }
    }

    static final class TagUtils {
        private static final Set<String> INSTRUMENTATION_IGNORES = Set.of("__typename", "__Schema", "__Type");

        static final String TAG_VALUE_ANONYMOUS = "anonymous";
        static final String TAG_VALUE_NONE = "none";
        static final String TAG_VALUE_UNKNOWN = ErrorType.UNKNOWN.name();

        private TagUtils() {
        }

        static String resolveDataFetcherTagValue(InstrumentationFieldFetchParameters parameters) {
            var type = parameters.getExecutionStepInfo().getParent().getType();
            GraphQLNamedType parentType = GraphQLTypeUtil.unwrapNonNullAs(type);
            return parentType.getName() + "."
                    + parameters.getExecutionStepInfo().getField().getSingleField().getName();
        }

        static boolean shouldIgnoreTag(String tag) {
            return INSTRUMENTATION_IGNORES.stream().anyMatch(tag::contains);
        }
    }

    static final class ErrorUtils {
        private ErrorUtils() {
        }

        static Collection<ErrorTagValues> sanitizeErrorPaths(List<GraphQLError> errors) {
            Map<String, ErrorTagValues> dedupeErrorPaths = new LinkedHashMap<>();
            for (GraphQLError error : errors) {
                List<Object> errorPath;
                String errorType;
                String errorDetail = errorDetailExtension(error);
                if (error instanceof ValidationError validationError) {
                    errorPath =
                            validationError.getQueryPath() != null
                                    ? List.copyOf(validationError.getQueryPath())
                                    : List.of();
                    errorType = ErrorType.BAD_REQUEST.name();
                } else if (error instanceof InvalidSyntaxError) {
                    errorPath = List.of();
                    errorType = ErrorType.BAD_REQUEST.name();
                } else {
                    errorPath = error.getPath() != null ? List.copyOf(error.getPath()) : List.of();
                    errorType = errorTypeExtension(error);
                }

                StringJoiner joiner = new StringJoiner(", ", "[", "]");
                for (Object segment : errorPath) {
                    if (segment instanceof Number) {
                        joiner.add("number");
                    } else if (segment instanceof String stringSegment) {
                        joiner.add(stringSegment);
                    } else {
                        joiner.add(String.valueOf(segment));
                    }
                }
                String path = joiner.toString();

                String finalErrorType = errorType;
                dedupeErrorPaths.computeIfAbsent(path, key -> new ErrorTagValues(key, finalErrorType, errorDetail));
            }
            return dedupeErrorPaths.values();
        }

        private static String errorTypeExtension(GraphQLError error) {
            return extension(error, "errorType", TagUtils.TAG_VALUE_UNKNOWN);
        }

        private static String errorDetailExtension(GraphQLError error) {
            return extension(error, "errorDetail", TagUtils.TAG_VALUE_NONE);
        }

        private static String extension(GraphQLError error, String key, String defaultValue) {
            Map<String, Object> extensions = error.getExtensions();
            if (extensions == null) {
                return defaultValue;
            }
            Object value = extensions.get(key);
            return value != null ? value.toString() : defaultValue;
        }

        record ErrorTagValues(String path, String type, String detail) {
        }
    }
}
