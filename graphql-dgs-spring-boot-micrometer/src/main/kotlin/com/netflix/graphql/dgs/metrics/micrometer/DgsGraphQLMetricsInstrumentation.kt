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
import graphql.execution.instrumentation.SimpleInstrumentationContext.noOp
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.execution.instrumentation.parameters.InstrumentationValidationParameters
import graphql.schema.DataFetcher
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLTypeUtil
import graphql.validation.ValidationError
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
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
        if (!properties.query.enabled) {
            return noOp()
        }
        require(state is MetricsInstrumentationState)
        state.startTimer()

        state.operationName = Optional.ofNullable(parameters.operation)
        state.isIntrospectionQuery = QueryUtils.isIntrospectionQuery(parameters.executionInput)

        return SimpleInstrumentationContext.whenCompleted { result, exc ->
            val tags = buildList {
                addAll(tagsProvider.getContextualTags())
                addAll(tagsProvider.getExecutionTags(state, parameters, result, exc))
                addAll(state.tags())
            }

            state.stopTimer(
                properties.autotime
                    .builder(GqlMetric.QUERY.key)
                    .tags(tags)
            )
        }
    }

    override fun instrumentExecutionResult(
        executionResult: ExecutionResult,
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState
    ): CompletableFuture<ExecutionResult> {
        require(state is MetricsInstrumentationState)

        val errorTagValues = ErrorUtils.sanitizeErrorPaths(executionResult)
        if (errorTagValues.isNotEmpty()) {
            val baseTags = buildList {
                addAll(tagsProvider.getContextualTags())
                addAll(tagsProvider.getExecutionTags(state, parameters, executionResult, null))
                addAll(state.tags())
            }

            val registry = registrySupplier.get()
            for (errorTagValue in errorTagValues) {
                val errorTags = buildList(baseTags.size + 3) {
                    addAll(baseTags)
                    add(Tag.of(GqlTag.PATH.key, errorTagValue.path))
                    add(Tag.of(GqlTag.ERROR_CODE.key, errorTagValue.type))
                    add(Tag.of(GqlTag.ERROR_DETAIL.key, errorTagValue.detail))
                }
                registry.counter(GqlMetric.ERROR.key, errorTags)
            }
        }

        return CompletableFuture.completedFuture(executionResult)
    }

    override fun instrumentDataFetcher(
        dataFetcher: DataFetcher<*>,
        parameters: InstrumentationFieldFetchParameters,
        state: InstrumentationState
    ): DataFetcher<*> {
        require(state is MetricsInstrumentationState)
        val gqlField = TagUtils.resolveDataFetcherTagValue(parameters)

        if (parameters.isTrivialDataFetcher ||
            state.isIntrospectionQuery ||
            TagUtils.shouldIgnoreTag(gqlField) ||
            !schemaProvider.isFieldMetricsInstrumentationEnabled(gqlField) ||
            !properties.resolver.enabled
        ) {
            return dataFetcher
        }

        return DataFetcher { environment ->
            val registry = registrySupplier.get()
            val baseTags = buildList {
                add(Tag.of(GqlTag.FIELD.key, gqlField))
                addAll(tagsProvider.getContextualTags())
                addAll(state.tags())
            }

            val sampler = Timer.start(registry)
            try {
                val result = dataFetcher.get(environment)
                if (result is CompletionStage<*>) {
                    result.whenComplete { _, error ->
                        recordDataFetcherMetrics(
                            registry,
                            sampler,
                            state,
                            parameters,
                            error,
                            baseTags
                        )
                    }
                } else {
                    recordDataFetcherMetrics(registry, sampler, state, parameters, null, baseTags)
                }
                result
            } catch (exc: Exception) {
                recordDataFetcherMetrics(registry, sampler, state, parameters, exc, baseTags)
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
        require(state is MetricsInstrumentationState)
        val document = parameters.document
            ?: return noOp()
        val querySignatureRepository = optQuerySignatureRepository.orElse(null)
            ?: return noOp()

        return SimpleInstrumentationContext.whenCompleted { errors, throwable ->
            if (errors.isNullOrEmpty() && throwable == null) {
                state.querySignature = querySignatureRepository.get(document, parameters)
            }
        }
    }

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState
    ): InstrumentationContext<ExecutionResult>? {
        require(state is MetricsInstrumentationState)
        if (parameters.executionContext.getRoot<Any>() == null) {
            state.operation = Optional.of(parameters.executionContext.operationDefinition.operation.name.uppercase())
            if (state.operationName.isEmpty) {
                state.operationName = Optional.ofNullable(parameters.executionContext.operationDefinition?.name)
            }
        }
        if (properties.tags.complexity.enabled) {
            state.queryComplexity = ComplexityUtils.resolveComplexity(parameters)
        }
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
        val recordedTags = baseTags + tagsProvider.getFieldFetchTags(state, parameters, error)

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
        fun tags(): Iterable<Tag> {
            val tags = mutableListOf<Tag>()
            tags += Tag.of(
                GqlTag.QUERY_COMPLEXITY.key,
                queryComplexity.map { it.toString() }.orElse(TagUtils.TAG_VALUE_NONE)
            )
            tags += Tag.of(
                GqlTag.OPERATION.key,
                operation.map { it.uppercase() }.orElse(TagUtils.TAG_VALUE_NONE)
            )

            tags += limitedTagMetricResolver.tags(
                GqlTag.OPERATION_NAME.key,
                operationName.orElse(TagUtils.TAG_VALUE_ANONYMOUS)
            )

            tags += limitedTagMetricResolver.tags(
                GqlTag.QUERY_SIG_HASH.key,
                querySignature.map { it.hash }.orElse(TagUtils.TAG_VALUE_NONE)
            )

            return tags
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
        val TAG_VALUE_UNKNOWN = ErrorType.UNKNOWN.name

        fun resolveDataFetcherTagValue(parameters: InstrumentationFieldFetchParameters): String {
            val type = parameters.executionStepInfo.parent.type
            val parentType = GraphQLTypeUtil.unwrapNonNullAs<GraphQLNamedType>(type)
            return "${parentType.name}.${parameters.executionStepInfo.field.singleField.name}"
        }

        fun shouldIgnoreTag(tag: String): Boolean {
            return instrumentationIgnores.any { tag.contains(it) }
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
