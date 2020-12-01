package com.netflix.graphql.dgs

import org.dataloader.BatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name="exampleLoader")
class ExampleBatchLoader: BatchLoader<String, String> {
    override fun load(keys: MutableList<String>?): CompletionStage<MutableList<String>> {
        return CompletableFuture.supplyAsync { mutableListOf("a", "b", "c") }
    }
}