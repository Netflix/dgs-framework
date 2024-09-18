package com.netflix.graphql.dgs.metrics.micrometer.dataloader

import com.netflix.graphql.dgs.DataLoaderInstrumentationExtensionProvider
import com.netflix.graphql.dgs.metrics.micrometer.DgsMeterRegistrySupplier
import com.netflix.graphql.dgs.metrics.micrometer.LimitedTagMetricResolver
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import java.lang.reflect.Proxy

class DgsDataLoaderInstrumentationProvider(
    private val meterRegistrySupplier: DgsMeterRegistrySupplier,
    private val limitedTagMetricResolver: LimitedTagMetricResolver
) : DataLoaderInstrumentationExtensionProvider {
    private val batchLoaderClasses = mutableMapOf<String, BatchLoader<*, *>>()
    private val batchLoaderWithContextClasses = mutableMapOf<String, BatchLoaderWithContext<*, *>>()
    private val mappedBatchLoaderClasses = mutableMapOf<String, MappedBatchLoader<*, *>>()
    private val mappedBatchLoaderWithContextClasses = mutableMapOf<String, MappedBatchLoaderWithContext<*, *>>()

    override fun provide(
        original: BatchLoader<*, *>,
        name: String,
    ): BatchLoader<*, *> =
        batchLoaderClasses.getOrPut(name) {
            val handler = BatchLoaderWithContextInterceptor(original, name, meterRegistrySupplier.get(), limitedTagMetricResolver)
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(BatchLoader::class.java),
                handler,
            ) as BatchLoader<*, *>
        }

    override fun provide(
        original: BatchLoaderWithContext<*, *>,
        name: String,
    ): BatchLoaderWithContext<*, *> =
        batchLoaderWithContextClasses.getOrPut(name) {
            val handler = BatchLoaderWithContextInterceptor(original, name, meterRegistrySupplier.get(), limitedTagMetricResolver)
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(BatchLoaderWithContext::class.java),
                handler,
            ) as BatchLoaderWithContext<*, *>
        }

    override fun provide(
        original: MappedBatchLoader<*, *>,
        name: String,
    ): MappedBatchLoader<*, *> =
        mappedBatchLoaderClasses.getOrPut(name) {
            val handler = BatchLoaderWithContextInterceptor(original, name, meterRegistrySupplier.get(), limitedTagMetricResolver)
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(MappedBatchLoader::class.java),
                handler,
            ) as MappedBatchLoader<*, *>
        }

    override fun provide(
        original: MappedBatchLoaderWithContext<*, *>,
        name: String,
    ): MappedBatchLoaderWithContext<*, *> =
        mappedBatchLoaderWithContextClasses.getOrPut(name) {
            val handler = BatchLoaderWithContextInterceptor(original, name, meterRegistrySupplier.get(), limitedTagMetricResolver)
            Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(MappedBatchLoaderWithContext::class.java),
                handler,
            ) as MappedBatchLoaderWithContext<*, *>
        }
}
