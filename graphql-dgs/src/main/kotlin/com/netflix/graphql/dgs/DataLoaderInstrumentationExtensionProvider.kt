package com.netflix.graphql.dgs

import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderWithContext
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext

interface DataLoaderInstrumentationExtensionProvider {
    fun provide(original: BatchLoader<*, *>, name: String) : BatchLoader<*, *>
    fun provide(original: BatchLoaderWithContext<*, *>, name: String) : BatchLoaderWithContext<*, *>
    fun provide(original: MappedBatchLoader<*, *>, name: String) : MappedBatchLoader<*, *>
    fun provide(original: MappedBatchLoaderWithContext<*, *>, name: String) : MappedBatchLoaderWithContext<*, *>
}