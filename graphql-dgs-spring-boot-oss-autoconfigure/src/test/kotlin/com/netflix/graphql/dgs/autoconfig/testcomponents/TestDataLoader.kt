/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.autoconfig.testcomponents

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataLoader
import graphql.schema.DataFetchingEnvironment
import org.dataloader.BatchLoader
import org.dataloader.MappedBatchLoader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name = "testloader")
class TestDataLoader : BatchLoader<String, String> {
    override fun load(keys: MutableList<String>?): CompletionStage<MutableList<String>> {
        return CompletableFuture.supplyAsync { keys?.map { it.toUpperCase() }?.toMutableList() }
    }
}

@DgsComponent
class FetcherUsingDataLoader {
    @DgsData(parentType = "Query", field = "names")
    fun hello(dfe: DataFetchingEnvironment): CompletableFuture<MutableList<String>>? {
        val dataLoader = dfe.getDataLoader<String, String>("testloader")
        return dataLoader.loadMany(listOf("a", "b", "c"))
    }
}

@DgsDataLoader(name = "testMappedLoader")
class TestMappedDataLoader : MappedBatchLoader<String, String> {
    override fun load(keys: MutableSet<String>): CompletionStage<MutableMap<String, String>> {
        return CompletableFuture.supplyAsync { keys.associateWith { it.toUpperCase() }.toMutableMap() }
    }
}

@DgsComponent
class FetcherUsingMappedDataLoader {
    @DgsData(parentType = "Query", field = "namesFromMapped")
    fun hello(dfe: DataFetchingEnvironment): CompletableFuture<MutableList<String>>? {
        val dataLoader = dfe.getDataLoader<String, String>("testMappedLoader")
        return dataLoader.loadMany(listOf("a", "b", "c"))
    }
}

@Configuration
open class DataLoaderConfig {
    @Bean
    open fun createDataLoader(): BatchLoader<String, String> {
        return TestDataLoader()
    }

    @Bean
    open fun createFetcher(): FetcherUsingDataLoader {
        return FetcherUsingDataLoader()
    }

    @Bean
    open fun createMappedDataLoader(): MappedBatchLoader<String, String> {
        return TestMappedDataLoader()
    }

    @Bean
    open fun createFetcherUsingMappedLoader(): FetcherUsingMappedDataLoader {
        return FetcherUsingMappedDataLoader()
    }
}
