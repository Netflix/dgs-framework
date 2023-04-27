/*
 * Copyright 2023 Netflix, Inc.
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

package com.netflix.graphql.dgs.internal

import com.netflix.graphql.dgs.DgsDataLoader
import com.netflix.graphql.dgs.DgsDataLoaderOptionsProvider
import org.dataloader.DataLoaderOptions

class DefaultDataLoaderOptionsProvider : DgsDataLoaderOptionsProvider {
    override fun getOptions(dataLoaderName: String, annotation: DgsDataLoader): DataLoaderOptions {
        val options = DataLoaderOptions()
            .setBatchingEnabled(annotation.batching)
            .setCachingEnabled(annotation.caching)
        if (annotation.maxBatchSize > 0) {
            options.setMaxBatchSize(annotation.maxBatchSize)
        }
        return options
    }
}
