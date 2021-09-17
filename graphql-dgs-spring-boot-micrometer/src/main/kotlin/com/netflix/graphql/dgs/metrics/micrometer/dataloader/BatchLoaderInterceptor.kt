package com.netflix.graphql.dgs.metrics.micrometer.dataloader

import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlMetric
import com.netflix.graphql.dgs.metrics.DgsMetrics.GqlTag
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import net.bytebuddy.implementation.bind.annotation.Pipe
import org.dataloader.BatchLoader
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletionStage

internal class BatchLoaderInterceptor(
    private val batchLoader: BatchLoader<*, *>,
    private val name: String,
    private val registry: MeterRegistry
) {

    fun load(@Pipe pipe: Forwarder<CompletionStage<List<*>>, BatchLoader<*, *>>): CompletionStage<List<*>> {
        logger.debug("Starting metered timer[{}] for BatchLoader.", ID)
        val timerSampler = Timer.start(registry)
        return try {
            pipe.to(batchLoader).whenComplete { result, _ ->
                logger.debug("Finished timer[{}] for BatchLoader.", ID)
                timerSampler.stop(
                    Timer.builder(ID)
                        .tags(
                            Tags.of(
                                Tag.of(GqlTag.LOADER_NAME.key, name),
                                Tag.of(GqlTag.LOADER_BATCH_SIZE.key, result.size.toString())
                            )
                        ).register(registry)
                )
            }
        } catch (exception: Exception) {
            logger.warn("Error creating BatchLoader metric interceptor '{}'", ID)
            pipe.to(batchLoader)
        }
    }

    companion object {
        private val ID = GqlMetric.DATA_LOADER.key
        private val logger = LoggerFactory.getLogger(BatchLoaderInterceptor::class.java)
    }
}
