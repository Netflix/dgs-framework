/*
 * Copyright 2021 Netflix, Inc.
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

package com.netflix.graphql.dgs

import org.dataloader.MappedBatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name = "exampleMappedLoader", optionsCustomizerName = "exampleDgsDataLoaderOptionsCustomizer")
class ExampleMappedBatchLoader : MappedBatchLoader<String, String> {
    override fun load(keys: MutableSet<String>?): CompletionStage<MutableMap<String, String>> {
        return CompletableFuture.supplyAsync {
            val result = mutableMapOf<String, String>()
            keys?.forEach { result[it] = it.toUpperCase() }
            result
        }
    }
}
