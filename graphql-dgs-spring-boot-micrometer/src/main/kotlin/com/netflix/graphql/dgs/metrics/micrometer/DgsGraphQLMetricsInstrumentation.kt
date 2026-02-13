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
import graphql.GraphQLException
import graphql.InvalidSyntaxError
import graphql.analysis.FieldComplexityCalculator
import graphql.analysis.QueryComplexityCalculator
import graphql.execution.DataFetcherResult
import graphql.execution.ExecutionContext
import graphql.execution.instrumentation.FieldFetchingInstrumentationContext
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
import graphql.execution.preparsed.persisted.PersistedQueryNotFound
import graphql.language.Field
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.OperationDefinition.Operation
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLTypeUtil
import graphql.validation.ValidationError
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.data.metrics.AutoTimer
import org.springframework.util.Assert.state
import java.util.Optional
import java.util.concurrent.CompletableFuture
import kotlin.jvm.optionals.getOrNull

class DgsGraphQLMetricsInstrumentation(
    private val schemaProvider: DgsSchemaProvider,
    private val registrySupplier: DgsMeterRegistrySupplier,
    private val tagsProvider: DgsGraphQLMetricsTagsProvider,
    private val properties: DgsGraphQLMetricsProperties,
    private val limitedTagMetricResolver: LimitedTagMetricResolver,
    private val optQuerySignatureRepository: Optional<QuerySignatureRepository> = Optional.empty(),
    private val autoTimer: AutoTimer,
) : SimplePerformantInstrumentation() {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(DgsGraphQLMetricsInstrumentation::class.java)
    }

    @Deprecated("Deprecated in Java")
    override fun createState(parameters: InstrumentationCreateStateParameters): InstrumentationState =
        MetricsInstrumentationState(
            registrySupplier.get(),
            limitedTagMetricResolver,
        )

    override fun beginExecution(
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState,
    ): InstrumentationContext<ExecutionResult> {
        if (!properties.query.enabled) {
            return noOp()
        }
        require(state is MetricsInstrumentationState)
        state.startTimer()

        state.operationNameValue = parameters.operation
        state.isIntrospectionQuery = QueryUtils.isIntrospectionQuery(parameters.executionInput)
        state.queryTypeValue = getPersistedQueryType(parameters.executionInput).name
        return SimpleInstrumentationContext.whenCompleted { result, exc ->
            val tags =
                buildList {
                    addAll(tagsProvider.getContextualTags())
                    addAll(tagsProvider.getExecutionTags(state, parameters, result, exc))
                    addAll(state.tags())
                }

            state.stopTimer(autoTimer.builder(GqlMetric.QUERY.key).tags(tags))
        }
    }

    override fun instrumentExecutionResult(
        executionResult: ExecutionResult,
        parameters: InstrumentationExecutionParameters,
        state: InstrumentationState,
    ): CompletableFuture<ExecutionResult> {
        require(state is MetricsInstrumentationState)

        // if this is an error due to PersistedQueryNotFound, we exclude from the gql.error metric
        // this is captured in a separate counter instead
        val persistedQueryNotFoundErrors = executionResult.errors.filter { it.errorType is PersistedQueryNotFound }
        if (persistedQueryNotFoundErrors.isNotEmpty()) {
            val registry = registrySupplier.get()
            persistedQueryNotFoundErrors.forEach {
                val errorTags =
                    buildList {
                        add(Tag.of(GqlTag.PERSISTED_QUERY_ID.key, it.extensions["persistedQueryId"].toString()))
                    }
                registry
                    .counter(GqlMetric.PERSISTED_QUERY_NOT_FOUND.key, errorTags)
                    .increment()
            }
            return CompletableFuture.completedFuture(executionResult)
        }

        val errorTagValues = ErrorUtils.sanitizeErrorPaths(executionResult.errors)
        if (errorTagValues.isNotEmpty()) {
            val baseTags =
                buildList {
                    addAll(tagsProvider.getContextualTags())
                    addAll(tagsProvider.getExecutionTags(state, parameters, executionResult, null))
                    addAll(state.tags())
                }

            val registry = registrySupplier.get()
            for (errorTagValue in errorTagValues) {
                val errorTags =
                    buildList(baseTags.size + 3) {
                        addAll(baseTags)
                        add(Tag.of(GqlTag.PATH.key, errorTagValue.path))
                        add(Tag.of(GqlTag.ERROR_CODE.key, errorTagValue.type))
                        add(Tag.of(GqlTag.ERROR_DETAIL.key, errorTagValue.detail))
                    }

                registry
                    .counter(GqlMetric.ERROR.key, errorTags)
                    .increment()
            }
        }

        return CompletableFuture.completedFuture(executionResult)
    }

    private fun checkResponseForErrors(
        value: Any?,
        error: Throwable?,
    ): Throwable? =
        error
            ?: (value as? DataFetcherResult<*>)
                ?.takeIf { it.hasErrors() }
                ?.let { GraphQLException("GraphQL errors in response: ${it.errors}") }

    /**
     * Port the implementation from MaxQueryComplexityInstrumentation in graphql-java and store the computed complexity
     * in the MetricsInstrumentationState for access to add tags to metrics.
     */
    override fun beginValidation(
        parameters: InstrumentationValidationParameters,
        state: InstrumentationState,
    ): InstrumentationContext<List<ValidationError>> {
        require(state is MetricsInstrumentationState)
        val document =
            parameters.document
                ?: return noOp()
        val querySignatureRepository =
            optQuerySignatureRepository.getOrNull()
                ?: return noOp()

        return SimpleInstrumentationContext.whenCompleted { errors, throwable ->
            if (errors.isNullOrEmpty() && throwable == null) {
                state.querySignatureValue = querySignatureRepository.get(document, parameters).getOrNull()
            }
        }
    }

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState,
    ): InstrumentationContext<ExecutionResult>? {
        require(state is MetricsInstrumentationState)
        if (parameters.executionContext.getRoot<Any>() == null) {
            state.operationValue = parameters.executionContext.operationDefinition.operation
            if (state.operationNameValue == null) {
                state.operationNameValue = parameters.executionContext.operationDefinition.nameOrFallback
            }
        }
        if (properties.tags.complexity.enabled) {
            state.queryComplexityValue = ComplexityUtils.resolveComplexity(parameters)
        }
        return super.beginExecuteOperation(parameters, state)
    }

    override fun beginFieldFetching(
        parameters: InstrumentationFieldFetchParameters,
        state: InstrumentationState,
    ): FieldFetchingInstrumentationContext? {
        require(state is MetricsInstrumentationState)

        val gqlField = TagUtils.resolveDataFetcherTagValue(parameters)

        if (parameters.isTrivialDataFetcher ||
            state.isIntrospectionQuery ||
            TagUtils.shouldIgnoreTag(gqlField) ||
            !schemaProvider.isFieldMetricsInstrumentationEnabled(gqlField) ||
            !properties.resolver.enabled
        ) {
            return super.beginFieldFetching(parameters, state)
        }

        val registry = registrySupplier.get()
        val baseTags =
            buildList {
                add(Tag.of(GqlTag.FIELD.key, gqlField))
                addAll(tagsProvider.getContextualTags())
                addAll(state.tags())
            }

        return object : FieldFetchingInstrumentationContext {
            var sampler: Timer.Sample? = null
            var dataFetcherResultTags: Iterable<Tag>? = null

            override fun onDispatched() {
                sampler = Timer.start(registry)
            }

            override fun onExceptionHandled(dataFetcherResult: DataFetcherResult<Any?>) {
                dataFetcherResultTags = tagsProvider.getFieldFetchDataFetcherResultTags(state, parameters, dataFetcherResult)
            }

            override fun onCompleted(
                result: Any?,
                t: Throwable?,
            ) {
                // if no throwable was raised during data fetching, the data fetcher might have returned a DataFetcherResult explicitly
                if (t == null) {
                    // offer the opportunity to add tags based on an explicitly returned data fetcher result by the data fetcher
                    // in case a raw object or null was returned by the data fetcher, use null, this
                    //  means no graphql errors were present
                    val dfResult: DataFetcherResult<*>? = result as? DataFetcherResult<*>
                    dataFetcherResultTags = tagsProvider.getFieldFetchDataFetcherResultTags(state, parameters, dfResult)
                }

                val allTags = baseTags.toMutableList()

                // to preserve backwards compatibility, we add the field fetch tag that are based on the data fetcher exception
                allTags += tagsProvider.getFieldFetchTags(state, parameters, checkResponseForErrors(result, t))

                // add any tags based on the data fetcher result
                dataFetcherResultTags?.let { allTags.addAll(it) }

                sampler?.stop(
                    autoTimer
                        .builder(GqlMetric.RESOLVER.key)
                        .tags(allTags)
                        .register(registry),
                )
            }
        }
    }

    /**
     * Returns a fallback name if the operation is unnamed.
     *
     * If the operation is named, the name is returned.
     *
     * Otherwise, a name is created from the first selection in the selection set,
     * prefixed with a `-` to indicate that it is a fallback name.
     *
     */
    private val OperationDefinition.nameOrFallback
        get() =
            name ?: when (val selection = selectionSet?.selections?.first()) {
                is Field -> "-${selection.name}"
                is InlineFragment -> "-${selection.typeCondition.name}"
                is FragmentSpread -> "-${selection.name}"
                null -> "-noSelections" // This should never happen, but it's possible
                else -> throw RuntimeException("Unknown Selection type: $selection")
            }

    enum class PersistedQueryType {
        NOT_APQ,
        FULL_APQ,
        APQ,
    }

    private fun getPersistedQueryType(executionInput: ExecutionInput): PersistedQueryType =
        if (executionInput.query == "PersistedQueryMarker" && "persistedQuery" in executionInput.extensions) {
            PersistedQueryType.APQ
        } else if (executionInput.query != "PersistedQueryMarker" && "persistedQuery" in executionInput.extensions) {
            PersistedQueryType.FULL_APQ
        } else {
            PersistedQueryType.NOT_APQ
        }

    class MetricsInstrumentationState(
        private val registry: MeterRegistry,
        private val limitedTagMetricResolver: LimitedTagMetricResolver,
    ) : InstrumentationState {
        private var timerSample: Timer.Sample? = null

        var isIntrospectionQuery = false
        internal var queryComplexityValue: Int? = null
        internal var operationValue: Operation? = null
        internal var operationNameValue: String? = null
        internal var querySignatureValue: QuerySignatureRepository.QuerySignature? = null
        internal var queryTypeValue: String? = PersistedQueryType.NOT_APQ.name
        val queryComplexity: Optional<Int> get() = Optional.ofNullable(queryComplexityValue)
        val operation: Optional<String> get() = Optional.ofNullable(operationValue?.name)
        val operationName: Optional<String> get() = Optional.ofNullable(operationNameValue)
        val querySignature: Optional<QuerySignatureRepository.QuerySignature> get() = Optional.ofNullable(querySignatureValue)

        fun startTimer() {
            this.timerSample = Timer.start(this.registry)
        }

        fun stopTimer(timer: Timer.Builder) {
            this.timerSample?.stop(timer.register(this.registry))
        }

        @Internal
        fun tags(): Iterable<Tag> {
            val tags = mutableListOf<Tag>()
            tags +=
                Tag.of(
                    GqlTag.QUERY_COMPLEXITY.key,
                    queryComplexityValue?.toString() ?: TagUtils.TAG_VALUE_NONE,
                )
            tags +=
                Tag.of(
                    GqlTag.OPERATION.key,
                    operationValue?.name ?: TagUtils.TAG_VALUE_NONE,
                )

            tags +=
                limitedTagMetricResolver.tags(
                    GqlTag.OPERATION_NAME.key,
                    operationNameValue ?: TagUtils.TAG_VALUE_ANONYMOUS,
                )

            tags +=
                limitedTagMetricResolver.tags(
                    GqlTag.QUERY_SIG_HASH.key,
                    querySignatureValue?.hash ?: TagUtils.TAG_VALUE_NONE,
                )
            tags +=
                Tag.of(
                    GqlTag.PERSISTED_QUERY_TYPE.key,
                    queryTypeValue ?: PersistedQueryType.NOT_APQ.name,
                )

            return tags
        }
    }

    internal object QueryUtils {
        fun isIntrospectionQuery(input: ExecutionInput): Boolean =
            input.query.contains("query IntrospectionQuery") || input.operationName == "IntrospectionQuery"
    }

    internal object ComplexityUtils {
        private val complexityCalculator: FieldComplexityCalculator =
            FieldComplexityCalculator {
                _,
                childComplexity,
                ->
                childComplexity + 1
            }

        private val queryComplexityBuckets = listOf(5, 10, 25, 50, 100, 200, 500, 1000, 2000, 5000, 10000)

        fun resolveComplexity(parameters: InstrumentationExecuteOperationParameters): Int? {
            val executionContext: ExecutionContext = parameters.executionContext
            val complexityCalculator: QueryComplexityCalculator =
                QueryComplexityCalculator
                    .newCalculator()
                    .fieldComplexityCalculator(complexityCalculator)
                    .schema(executionContext.graphQLSchema)
                    .document(executionContext.document)
                    .operationName(executionContext.executionInput.operationName)
                    .variables(executionContext.coercedVariables)
                    .build()
            val complexity: Int =
                try {
                    complexityCalculator.calculate()
                } catch (exc: Exception) {
                    log.error("Unable to compute the query complexity!", exc)
                    return null
                }
            for (bucket in queryComplexityBuckets) {
                if (complexity < bucket) {
                    return bucket
                }
            }
            return Int.MAX_VALUE
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

        fun shouldIgnoreTag(tag: String): Boolean = instrumentationIgnores.any { tag.contains(it) }
    }

    internal object ErrorUtils {
        fun sanitizeErrorPaths(errors: List<GraphQLError>): Collection<ErrorTagValues> {
            val dedupeErrorPaths = mutableMapOf<String, ErrorTagValues>()
            errors.forEach { error ->
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

                val path =
                    errorPath.joinToString(prefix = "[", postfix = "]") { segment ->
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

        private fun <T : GraphQLError> errorTypeExtension(error: T): String = extension(error, "errorType", TagUtils.TAG_VALUE_UNKNOWN)

        private fun <T : GraphQLError> errorDetailExtension(error: T): String = extension(error, "errorDetail", TagUtils.TAG_VALUE_NONE)

        private fun <T : GraphQLError> extension(
            error: T,
            key: String,
            default: String,
        ): String = error.extensions?.get(key)?.toString() ?: default

        internal data class ErrorTagValues(
            val path: String,
            val type: String,
            val detail: String,
        )
    }
}
