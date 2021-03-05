package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsInstrumentationUtils.resolveDataFetcherTagValue
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsInstrumentationUtils.sanitizeErrorPaths
import com.netflix.graphql.dgs.metrics.micrometer.DgsGraphQLMetricsInstrumentationUtils.shouldIgnoreTag
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider
import graphql.ExecutionResult
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentation
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.metrics.AutoTimer
import java.util.*
import java.util.concurrent.CompletableFuture

class DgsGraphQLMetricsInstrumentation(
    private val registrySupplier: DgsMeterRegistrySupplier,
    private val tagsProvider: DgsGraphQLMetricsTagsProvider,
    private val autoTimer: AutoTimer,
    private val resultExecutionEmitter: List<DgsGraphQLMetricsExecutionEmitter>
) : SimpleInstrumentation() {

    private companion object {
        val logger: Logger = LoggerFactory.getLogger(DgsGraphQLMetricsInstrumentation::class.java)
    }

    override fun createState(): InstrumentationState {
        return MetricsInstrumentationState(registrySupplier.get())
    }

    override fun beginExecution(parameters: InstrumentationExecutionParameters): InstrumentationContext<ExecutionResult> {
        val state: MetricsInstrumentationState = parameters.getInstrumentationState()
        state.startTimer()

        return object : SimpleInstrumentationContext<ExecutionResult>() {
            override fun onCompleted(result: ExecutionResult, exc: Throwable?) {

                resultExecutionEmitter.forEach {
                    kotlin.runCatching { it.emit(result, exc) }
                        .onFailure { logger.error("Failed to emit execution result!", it) }
                }

                state.stopTimer(
                    autoTimer.builder(GqlMetric.QUERY.key)
                        .tags(tagsProvider.getContextualTags())
                        .tags(tagsProvider.getExecutionTags(parameters, result, exc))
                )
            }
        }
    }

    override fun instrumentExecutionResult(
        executionResult: ExecutionResult,
        parameters: InstrumentationExecutionParameters
    ): CompletableFuture<ExecutionResult> {

        val tags =
            Tags.empty()
                .and(tagsProvider.getContextualTags())
                .and(tagsProvider.getExecutionTags(parameters, executionResult, null))

        sanitizeErrorPaths(executionResult).forEach {
            registrySupplier
                .get()
                .counter(
                    GqlMetric.ERROR.key,
                    tags.and(GqlTag.ERROR_PATH.key, it.path)
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
                if (result is CompletableFuture<*>) {
                    result.whenComplete { _, error -> recordDataFetcherMetrics(registry, sampler, parameters, error, baseTags) }
                } else {
                    recordDataFetcherMetrics(registry, sampler, parameters, null, baseTags)
                }
            } catch (throwable: Throwable) {
                recordDataFetcherMetrics(registry, sampler, parameters, throwable, baseTags)
                throw throwable
            }
        }
    }

    private fun recordDataFetcherMetrics(
        registry: MeterRegistry,
        timerSampler: Timer.Sample,
        parameters: InstrumentationFieldFetchParameters,
        error: Throwable?,
        baseTags: Iterable<Tag>
    ) {

        val recordedTags = Tags.of(baseTags).and(tagsProvider.getFieldFetchTags(parameters, error))

        timerSampler.stop(
            registry,
            Timer.builder(GqlMetric.RESOLVER.key).tags(recordedTags)
        )
    }

    class MetricsInstrumentationState(private val registry: MeterRegistry) : InstrumentationState {
        private var timerSample: Optional<Timer.Sample> = Optional.empty()

        fun startTimer() {
            this.timerSample = Optional.of(Timer.start(this.registry))
        }

        fun stopTimer(timer: Timer.Builder) {
            this.timerSample.map { it.stop(timer.register(this.registry)) }
        }
    }
}
