package com.netflix.graphql.dgs.metrics.micrometer

import com.netflix.graphql.dgs.Internal
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import com.netflix.graphql.dgs.metrics.micrometer.tagging.DgsGraphQLMetricsTagsProvider
import com.netflix.graphql.dgs.metrics.micrometer.utils.QuerySignatureRepository
import com.netflix.graphql.types.errors.ErrorType
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.InvalidSyntaxError
import graphql.analysis.QueryTraverser
import graphql.analysis.QueryVisitorFieldEnvironment
import graphql.analysis.QueryVisitorStub
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.schema.DataFetcher
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.validation.ValidationError
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class DgsGraphQLMetricsInstrumentation(
    private val schemaProvider: DgsSchemaProvider,
    private val registrySupplier: DgsMeterRegistrySupplier,
    private val tagsProvider: DgsGraphQLMetricsTagsProvider,
    private val properties: DgsGraphQLMetricsProperties,
    private val limitedTagMetricResolver: LimitedTagMetricResolver,
    private val optQuerySignatureRepository: Optional<QuerySignatureRepository> = Optional.empty()
) : SimplePerformantInstrumentation() {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DgsGraphQLMetricsInstrumentation::class.java)
    }

    override fun createState(parameters: InstrumentationCreateStateParameters): InstrumentationState {
        return MetricsInstrumentationState(
            registrySupplier.get(),
            limitedTagMetricResolver
        )
    }

    override fun beginExecution(
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState
    ): InstrumentationContext<ExecutionResult> {
        val miState: MetricsInstrumentationState = state as MetricsInstrumentationState
        miState.startTimer()

        miState.operationName = Optional.ofNullable(parameters.operation)
        miState.isIntrospectionQuery = QueryUtils.isIntrospectionQuery(parameters.executionInput)

        return SimpleInstrumentationContext.whenCompleted { result, exc ->
            miState.stopTimer(
                properties.autotime
                    .builder(GqlMetric.QUERY.key)
                    .tags(tagsProvider.getContextualTags())
                    .tags(tagsProvider.getExecutionTags(miState, parameters, result, exc))
                    .tags(miState.tags())
            )
        }
    }

    override fun instrumentExecutionResult(
        executionResult: ExecutionResult,
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState
    ): CompletableFuture<ExecutionResult> {
        val miState: MetricsInstrumentationState = state as MetricsInstrumentationState
        val tags =
            Tags.empty()
                .and(tagsProvider.getContextualTags())
                .and(tagsProvider.getExecutionTags(miState, parameters, executionResult, null))
                .and(miState.tags())

        ErrorUtils
            .sanitizeErrorPaths(executionResult)
            .forEach {
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
        parameters: InstrumentationFieldFetchParameters,
        state: InstrumentationState
    ): DataFetcher<*> {
        val miState: MetricsInstrumentationState = state as MetricsInstrumentationState
        val gqlField = TagUtils.resolveDataFetcherTagValue(parameters)

        if (parameters.isTrivialDataFetcher ||
            miState.isIntrospectionQuery ||
            TagUtils.shouldIgnoreTag(gqlField) ||
            !schemaProvider.isFieldInstrumentationEnabled(gqlField)
        ) {
            return dataFetcher
        }

        return DataFetcher { environment ->
            val registry = registrySupplier.get()
            val baseTags =
                Tags.of(GqlTag.FIELD.key, gqlField)
                    .and(tagsProvider.getContextualTags())
                    .and(miState.tags())

            val sampler = Timer.start(registry)
            try {
                val result = dataFetcher.get(environment)
                if (result is CompletionStage<*>) {
                    result.whenComplete { _, error ->
                        recordDataFetcherMetrics(
                            registry,
                            sampler,
                            miState,
                            parameters,
                            error,
                            baseTags
                        )
                    }
                } else {
                    recordDataFetcherMetrics(registry, sampler, miState, parameters, null, baseTags)
                }
                result
            } catch (exc: Exception) {
                recordDataFetcherMetrics(registry, sampler, miState, parameters, exc, baseTags)
                throw exc
            }
        }
    }

    /**
     * Port the implementation from MaxQueryComplexityInstrumentation in graphql-java and store the computed complexity
     * in the MetricsInstrumentationState for access to add tags to metrics.
     */
    override fun beginValidation(
        parameters: InstrumentationValidationParameters,
        state: InstrumentationState
    ): InstrumentationContext<List<ValidationError>> {
        return SimpleInstrumentationContext.whenCompleted { errors, throwable ->
            if (!errors.isNullOrEmpty() || throwable != null) {
                return@whenCompleted
            }
            val miState: MetricsInstrumentationState = state as MetricsInstrumentationState
            if (parameters.document != null) {
                miState.querySignature = optQuerySignatureRepository.flatMap { it.get(parameters.document, parameters) }
            }
        }
    }

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState
    ): InstrumentationContext<ExecutionResult>? {
        val miState: MetricsInstrumentationState = state as MetricsInstrumentationState
        if (parameters.executionContext.getRoot<Any>() == null) {
            miState.operation = Optional.of(parameters.executionContext.operationDefinition.operation.name.uppercase())
            if (!miState.operationName.isPresent) {
                miState.operationName = Optional.ofNullable(parameters.executionContext.operationDefinition?.name)
            }
        }

        miState.queryComplexity = ComplexityUtils.resolveComplexity(parameters)
        return super.beginExecuteOperation(parameters, state)
    }

    private fun recordDataFetcherMetrics(
        registry: MeterRegistry,
        timerSampler: Timer.Sample,
        state: MetricsInstrumentationState,
        parameters: InstrumentationFieldFetchParameters,
        error: Throwable?,
        baseTags: Iterable<Tag>
    ) {
        val recordedTags = Tags
            .of(baseTags)
            .and(tagsProvider.getFieldFetchTags(state, parameters, error))

        timerSampler.stop(
            properties
                .autotime
                .builder(GqlMetric.RESOLVER.key)
                .tags(recordedTags)
                .register(registry)
        )
    }

    class MetricsInstrumentationState(
        private val registry: MeterRegistry,
        private val limitedTagMetricResolver: LimitedTagMetricResolver
    ) : InstrumentationState {
        private var timerSample: Optional<Timer.Sample> = Optional.empty()

        var isIntrospectionQuery = false
        var queryComplexity: Optional<Int> = Optional.empty()
        var operation: Optional<String> = Optional.empty()
        var operationName: Optional<String> = Optional.empty()
        var querySignature: Optional<QuerySignatureRepository.QuerySignature> = Optional.empty()

        fun startTimer() {
            this.timerSample = Optional.of(Timer.start(this.registry))
        }

        fun stopTimer(timer: Timer.Builder) {
            this.timerSample.ifPresent { it.stop(timer.register(this.registry)) }
        }

        @Internal
        fun tags(): Tags {
            return Tags
                .of(
                    GqlTag.QUERY_COMPLEXITY.key,
                    queryComplexity.map { it.toString() }.orElse(TagUtils.TAG_VALUE_NONE)
                )
                .and(
                    GqlTag.OPERATION.key,
                    operation.map { it.uppercase() }.orElse(TagUtils.TAG_VALUE_NONE)
                )
                .and(
                    limitedTagMetricResolver.tags(
                        GqlTag.OPERATION_NAME.key,
                        operationName.orElse(TagUtils.TAG_VALUE_ANONYMOUS)
                    )
                )
                .and(
                    limitedTagMetricResolver.tags(
                        GqlTag.QUERY_SIG_HASH.key,
                        querySignature.map { it.hash }.orElse(TagUtils.TAG_VALUE_NONE)
                    )
                )
        }
    }

    internal object QueryUtils {
        fun isIntrospectionQuery(input: ExecutionInput): Boolean {
            return input.query.contains("query IntrospectionQuery") || input.operationName == "IntrospectionQuery"
        }
    }

    internal object ComplexityUtils {

        private val queryComplexityBuckets = listOf(5, 10, 25, 50, 100, 200, 500, 1000, 2000, 5000, 10000)

        fun resolveComplexity(parameters: InstrumentationExecuteOperationParameters): Optional<Int> {
            try {
                val queryTraverser: QueryTraverser = newQueryTraverser(parameters)
                val valuesByParent = mutableMapOf<QueryVisitorFieldEnvironment?, Int?>()
                queryTraverser.visitPostOrder(object : QueryVisitorStub() {
                    override fun visitField(env: QueryVisitorFieldEnvironment) {
                        val childComplexity = valuesByParent[env] ?: 0
                        val value = calculateComplexity(env, childComplexity)
                        valuesByParent.compute(env.parentEnvironment) { _, oldValue -> Optional.ofNullable(oldValue).orElse(0) + value }
                    }
                })
                val totalComplexity = valuesByParent[null] ?: 0
                return Optional.ofNullable(queryComplexityBuckets.asSequence().filter { totalComplexity < it }.minOrNull())
            } catch (exc: Exception) {
                log.error("Unable to compute the query complexity!", exc)
                return Optional.empty()
            }
        }

        /**
         * Port from MaxQueryComplexityInstrumentation in graphql-java
         */
        fun calculateComplexity(
            queryVisitorFieldEnvironment: QueryVisitorFieldEnvironment,
            childComplexity: Int,
            fieldComplexityCalculator: (Int) -> Int = { cx -> 1 + cx }
        ): Int {
            if (queryVisitorFieldEnvironment.isTypeNameIntrospectionField) {
                return 0
            }
            return fieldComplexityCalculator(childComplexity)
        }

        private fun newQueryTraverser(parameters: InstrumentationExecuteOperationParameters): QueryTraverser {
            return QueryTraverser
                .newQueryTraverser()
                .schema(parameters.executionContext.graphQLSchema)
                .document(parameters.executionContext.document)
                .operationName(parameters.executionContext.operationDefinition.name)
                .coercedVariables(parameters.executionContext.coercedVariables)
                .build()
        }
    }

    internal object TagUtils {
        private val instrumentationIgnores = setOf("__typename", "__Schema", "__Type")

        const val TAG_VALUE_ANONYMOUS = "anonymous"
        const val TAG_VALUE_NONE = "none"
        const val TAG_VALUE_UNKNOWN = "unknown"

        fun resolveDataFetcherTagValue(parameters: InstrumentationFieldFetchParameters): String {
            val type = parameters.executionStepInfo.parent.type
            val parentType = if (type is GraphQLNonNull) {
                type.wrappedType as GraphQLObjectType
            } else {
                type as GraphQLObjectType
            }

            return "${parentType.name}.${parameters.executionStepInfo.field.singleField.name}"
        }

        fun shouldIgnoreTag(tag: String): Boolean {
            return instrumentationIgnores.find { tag.contains(it) } != null
        }
    }

    internal object ErrorUtils {

        fun sanitizeErrorPaths(executionResult: ExecutionResult): Collection<ErrorTagValues> {
            val dedupeErrorPaths = mutableMapOf<String, ErrorTagValues>()
            executionResult.errors.forEach { error ->
                val errorPath: List<Any>
                val errorType: String
                val errorDetail = errorDetailExtension(error)
                when (error) {
                    is ValidationError -> {
                        errorPath = error.queryPath.orEmpty()
                        errorType = ErrorType.BAD_REQUEST.name
                    }
                    is InvalidSyntaxError -> {
                        errorPath = emptyList()
                        errorType = ErrorType.BAD_REQUEST.name
                    }
                    else -> {
                        errorPath = error.path.orEmpty()
                        errorType = errorTypeExtension(error)
                    }
                }

                val path = errorPath.joinToString(prefix = "[", postfix = "]") { segment ->
                    when (segment) {
                        is Number -> "number"
                        is String -> segment
                        else -> segment.toString()
                    }
                }

                dedupeErrorPaths.computeIfAbsent(path) {
                    ErrorTagValues(path, errorType, errorDetail)
                }
            }
            return dedupeErrorPaths.values
        }

        private fun <T : GraphQLError> errorTypeExtension(error: T): String {
            return extension(error, "errorType", TagUtils.TAG_VALUE_UNKNOWN)
        }

        private fun <T : GraphQLError> errorDetailExtension(error: T): String {
            return extension(error, "errorDetail", TagUtils.TAG_VALUE_NONE)
        }

        private fun <T : GraphQLError> extension(error: T, key: String, default: String): String {
            return error.extensions?.get(key)?.toString() ?: default
        }

        internal data class ErrorTagValues(val path: String, val type: String, val detail: String)
    }
}
