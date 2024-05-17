package com.netflix.graphql.dgs.metrics.micrometer.dataloader

import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.util.concurrent.CompletionStage

internal class BatchLoaderWithContextInterceptor(
    private val batchLoaderWithContext: Any,
    private val name: String,
    private val registry: MeterRegistry
) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<out Any>): CompletionStage<*> {
        if (method.name == "load") {
            logger.debug("Starting metered timer[{}] for {}.", ID, javaClass.simpleName)
            val timerSampler = Timer.start(registry)
            return try {
                val future = method.invoke(batchLoaderWithContext, *(args)) as CompletionStage<*>
                future.whenComplete { result, _ ->
                    logger.debug("Stopping timer[{}] for {}", ID, javaClass.simpleName)

                    val resultSize = if (result is List<*>) {
                        result.size
                    } else if (result is Map<*, *>) {
                        result.size
                    } else {
                        throw IllegalStateException("BatchLoader or MappedBatchLoader should always return a List/Map. A ${result.javaClass.name} was found.")
                    }

                    timerSampler.stop(
                        Timer.builder(ID)
                            .tags(
                                listOf(
                                    Tag.of(GqlTag.LOADER_NAME.key, name),
                                    Tag.of(GqlTag.LOADER_BATCH_SIZE.key, resultSize.toString())
                                )
                            ).register(registry)
                    )
                }
            } catch (exception: Exception) {
                logger.warn("Error creating timer interceptor '{}' for {} with exception {}", ID, javaClass.simpleName, exception.message)
                @Suppress("UNCHECKED_CAST")
                method.invoke(batchLoaderWithContext, *(args)) as CompletionStage<List<*>>
            }
        }
        throw UnsupportedOperationException("Unsupported method: ${method.name}")
    }

    companion object {
        private val ID = GqlMetric.DATA_LOADER.key
        private val logger = LoggerFactory.getLogger(BatchLoaderWithContextInterceptor::class.java)
    }
}
