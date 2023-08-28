package com.netflix.graphql.dgs.metrics.micrometer.dataloader

import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import net.bytebuddy.implementation.bind.annotation.Pipe
import org.dataloader.BatchLoaderWithContext
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage

internal class BatchLoaderWithContextInterceptor(
    private val batchLoader: BatchLoaderWithContext<*, *>,
    private val name: String,
    private val registry: MeterRegistry
) {

    fun load(@Pipe pipe: Forwarder<CompletionStage<List<*>>, BatchLoaderWithContext<*, *>>): CompletionStage<List<*>> {
        logger.debug("Starting metered timer[{}] for {}.", ID, javaClass.simpleName)
        val timerSampler = Timer.start(registry)
        return try {
            pipe.to(batchLoader).whenComplete { result, _ ->
                logger.debug("Stopping timer[{}] for {}", ID, javaClass.simpleName)
                timerSampler.stop(
                    Timer.builder(ID)
                        .tags(
                            listOf(
                                Tag.of(GqlTag.LOADER_NAME.key, name),
                                Tag.of(GqlTag.LOADER_BATCH_SIZE.key, result.size.toString())
                            )
                        ).register(registry)
                )
            }
        } catch (exception: Exception) {
            logger.warn("Error creating timer interceptor '{}' for {} with exception {}", ID, javaClass.simpleName, exception.message)
            pipe.to(batchLoader)
        }
    }

    companion object {
        private val ID = GqlMetric.DATA_LOADER.key
        private val logger = LoggerFactory.getLogger(BatchLoaderWithContextInterceptor::class.java)
    }
}
