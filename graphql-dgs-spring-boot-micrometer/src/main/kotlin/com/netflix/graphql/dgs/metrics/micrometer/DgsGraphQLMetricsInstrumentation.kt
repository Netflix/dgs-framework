package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsInstrumentationUtils.resolveDataFetcherTagValue
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsInstrumentationUtils.sanitizeErrorPaths
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsInstrumentationUtils.shouldIgnoreTag
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider
import graphql.ExecutionResult
import graphql.analysis.*
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.SimpleInstrumentationContext.whenCompleted
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.schema.DataFetcher
import graphql.validation.ValidationError
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.springframework.boot.actuate.metrics.AutoTimer
import java.util.*
import java.util.Optional.ofNullable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class DgsGraphQLMetricsInstrumentation(
    private val registrySupplier: DgsMeterRegistrySupplier,
    private val tagsProvider: DgsGraphQLMetricsTagsProvider,
    private val autoTimer: AutoTimer,
    private val fieldComplexityCalculator: (Int) -> Int = { childComplexity -> 1 + childComplexity }
) : SimpleInstrumentation() {

    private val queryComplexityBuckets = listOf(5, 10, 25, 50, 100, 200, 500, 1000, 2000, 5000, 10000)

    override fun createState(): InstrumentationState {
        return MetricsInstrumentationState(registrySupplier.get())
    }

    override fun beginExecution(parameters: InstrumentationExecutionParameters): InstrumentationContext<ExecutionResult> {
        val state: MetricsInstrumentationState = parameters.getInstrumentationState()
        state.startTimer()

        return object : SimpleInstrumentationContext<ExecutionResult>() {
            override fun onCompleted(result: ExecutionResult, exc: Throwable?) {

                state.stopTimer(
                    autoTimer.builder(GqlMetric.QUERY.key)
                        .tags(tagsProvider.getContextualTags())
                        .tags(tagsProvider.getExecutionTags(parameters, result, exc))
                        .tags(GqlTag.QUERY_COMPLEXITY.key, state.queryComplexity.toString())
                )
            }
        }
    }

    override fun instrumentExecutionResult(
        executionResult: ExecutionResult,
        parameters: InstrumentationExecutionParameters
    ): CompletableFuture<ExecutionResult> {

        val state = parameters.getInstrumentationState<MetricsInstrumentationState>()
        val tags =
            Tags.empty()
                .and(tagsProvider.getContextualTags())
                .and(tagsProvider.getExecutionTags(parameters, executionResult, null))

        sanitizeErrorPaths(executionResult).forEach {
            registrySupplier
                .get()
                .counter(
                    GqlMetric.ERROR.key,
                    tags.and(GqlTag.PATH.key, it.path)
                        .and(GqlTag.ERROR_CODE.key, it.type)
                        .and(GqlTag.ERROR_DETAIL.key, it.detail)
                ).increment()
        }

        return CompletableFuture.completedFuture(executionResult)
    }

    override fun instrumentDataFetcher(
        dataFetcher: DataFetcher<*>,
        parameters: InstrumentationFieldFetchParameters
    ): DataFetcher<*> {
        val gqlField = resolveDataFetcherTagValue(parameters)
        if (parameters.isTrivialDataFetcher || shouldIgnoreTag(gqlField)) {
            return dataFetcher
        }

        return DataFetcher { environment ->
            val registry = registrySupplier.get()
            val baseTags = Tags.of(GqlTag.FIELD.key, gqlField).and(tagsProvider.getContextualTags())

            val sampler = Timer.start(registry)
            try {
                val result = dataFetcher.get(environment)
                if (result is CompletionStage<*>) {
                    result.whenComplete { _, error -> recordDataFetcherMetrics(registry, sampler, parameters, error, baseTags) }
                } else {
                    recordDataFetcherMetrics(registry, sampler, parameters, null, baseTags)
                }
                result
            } catch (throwable: Throwable) {
                recordDataFetcherMetrics(registry, sampler, parameters, throwable, baseTags)
                throw throwable
            }
        }
    }

    /**
     * Port the implementation from MaxQueryComplexityInstrumentation in graphql-java and store the computed complexity
     * in the MetricsInstrumentationState for access to add tags to metrics.
     */
    override fun beginValidation(parameters: InstrumentationValidationParameters): InstrumentationContext<List<ValidationError>> {
        return whenCompleted { errors, throwable ->
            if (errors != null && errors.isNotEmpty() || throwable != null) {
                return@whenCompleted
            }
            val queryTraverser: QueryTraverser = newQueryTraverser(parameters)
            val valuesByParent: MutableMap<QueryVisitorFieldEnvironment?, Int?> =
                LinkedHashMap<QueryVisitorFieldEnvironment?, Int?>()
            queryTraverser.visitPostOrder(object : QueryVisitorStub() {
                override fun visitField(env: QueryVisitorFieldEnvironment?) {
                    val childsComplexity = valuesByParent.getOrDefault(env, 0)
                    val value = calculateComplexity(env!!, childsComplexity!!)
                    valuesByParent.compute(env.parentEnvironment) { key, oldValue -> ofNullable(oldValue).orElse(0) + value }
                }
            })
            val totalComplexity = valuesByParent.getOrDefault(null, 0)

            val state: MetricsInstrumentationState = parameters.getInstrumentationState()
            val complexity = queryComplexityBuckets.filter { totalComplexity!! < it }.minOrNull()
            state.queryComplexity = complexity
        }
    }

    /**
     * Port from MaxQueryComplexityInstrumentation in graphql-java
     */
    private fun calculateComplexity(queryVisitorFieldEnvironment: QueryVisitorFieldEnvironment, childsComplexity: Int): Int {
        if (queryVisitorFieldEnvironment.isTypeNameIntrospectionField) {
            return 0
        }
        return fieldComplexityCalculator(childsComplexity)
    }

    private fun newQueryTraverser(parameters: InstrumentationValidationParameters): QueryTraverser {
        return QueryTraverser.newQueryTraverser()
            .schema(parameters.schema)
            .document(parameters.document)
            .operationName(parameters.operation)
            .variables(parameters.variables)
            .build()
    }

    private fun recordDataFetcherMetrics(
        registry: MeterRegistry,
        timerSampler: Timer.Sample,
        parameters: InstrumentationFieldFetchParameters,
        error: Throwable?,
        baseTags: Iterable<Tag>
    ) {
        val recordedTags = Tags.of(baseTags).and(tagsProvider.getFieldFetchTags(parameters, error))
        timerSampler.stop(Timer.builder(GqlMetric.RESOLVER.key).tags(recordedTags).register(registry))
    }

    class MetricsInstrumentationState(private val registry: MeterRegistry) : InstrumentationState {
        private var timerSample: Optional<Timer.Sample> = Optional.empty()
        var queryComplexity: Int? = 0

        fun startTimer() {
            this.timerSample = Optional.of(Timer.start(this.registry))
        }

        fun stopTimer(timer: Timer.Builder) {
            this.timerSample.ifPresent { it.stop(timer.register(this.registry)) }
        }
    }
}
