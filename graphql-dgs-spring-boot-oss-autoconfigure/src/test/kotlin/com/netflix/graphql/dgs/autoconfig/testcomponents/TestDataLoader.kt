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
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider
import graphql.schema.DataFetchingEnvironment
import org.dataloader.BatchLoader
import org.dataloader.DataLoaderOptions
import org.dataloader.MappedBatchLoader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name = "testloader")
class TestDataLoader : BatchLoader<String, String> {
    override fun load(keys: List<String>): CompletionStage<List<String>> {
        return CompletableFuture.supplyAsync { keys.map { it.uppercase() } }
    }
}

@DgsComponent
class FetcherUsingDataLoader {
    @DgsData(parentType = "Query", field = "names")
    fun hello(dfe: DataFetchingEnvironment): CompletableFuture<List<String>> {
        val dataLoader = dfe.getDataLoader<String, String>("testloader")
        return dataLoader.loadMany(listOf("a", "b", "c"))
    }
}

@DgsDataLoader(name = "testMappedLoader")
class TestMappedDataLoader : MappedBatchLoader<String, String> {
    override fun load(keys: Set<String>): CompletionStage<Map<String, String>> {
        return CompletableFuture.supplyAsync { keys.associateWith { it.uppercase() } }
    }
}

@DgsComponent
class FetcherUsingMappedDataLoader {
    @DgsData(parentType = "Query", field = "namesFromMapped")
    fun hello(dfe: DataFetchingEnvironment): CompletableFuture<List<String>> {
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

    @Bean
    open fun dgsDataLoaderOptionsProvider(): DgsDataLoaderOptionsProvider {
        return CustomDataLoaderOptionsProvider()
    }
}

class CustomDataLoaderOptionsProvider : DgsDataLoaderOptionsProvider {
    override fun getOptions(dataLoaderName: String, annotation: DgsDataLoader): DataLoaderOptions {
        val options = DataLoaderOptions()
            .setBatchingEnabled(false)
            .setCachingEnabled(false)

        options.setMaxBatchSize(50)
        return options
    }
}
