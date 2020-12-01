package com.netflix.graphql.dgs

import org.dataloader.MappedBatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name="exampleMappedLoader")
class ExampleMappedBatchLoader : MappedBatchLoader<String, String> {
    override fun load(keys: MutableSet<String>?): CompletionStage<MutableMap<String, String>> {
        return CompletableFuture.supplyAsync  {
          val result = mutableMapOf<String,String>()
            keys?.forEach { result[it] = it.toUpperCase() }
            result
        }
    }
}