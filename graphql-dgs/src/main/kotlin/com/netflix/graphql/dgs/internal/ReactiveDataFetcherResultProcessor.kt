/*
 * Copyright 2022 Netflix, Inc.
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

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.context.ReactiveDgsContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.future
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.context.Context

class MonoDataFetcherResultProcessor : DataFetcherResultProcessor {
    override fun supportsType(originalResult: Any): Boolean {
        return originalResult is Mono<*>
    }

    override fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any {
        if (originalResult is Mono<*>) {
            return originalResult.contextWrite(reactorContextFrom(dfe)).toFuture()
        } else {
            throw IllegalArgumentException("Instance passed to ${this::class.qualifiedName} was not a Mono<*>. It was a ${originalResult::class.qualifiedName} instead")
        }
    }
}

class FlowDataFetcherResultProcessor : DataFetcherResultProcessor {
    override fun supportsType(originalResult: Any): Boolean {
        return originalResult is Flow<*>
    }

    override fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any {
        return if (originalResult is Flow<*>) {
            CoroutineScope(Dispatchers.Default).future {
                originalResult.toList()
            }
        } else {
            throw IllegalArgumentException("Instance passed to ${this::class.qualifiedName} was not a Flow<*>. It was a ${originalResult::class.qualifiedName} instead")
        }
    }
}

class FluxDataFetcherResultProcessor : DataFetcherResultProcessor {
    override fun supportsType(originalResult: Any): Boolean {
        return originalResult is Flux<*>
    }

    override fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any {
        if (originalResult is Flux<*>) {
            return originalResult.contextWrite(reactorContextFrom(dfe)).collectList().toFuture()
        } else {
            throw IllegalArgumentException("Instance passed to ${this::class.qualifiedName} was not a Flux<*>. It was a ${originalResult::class.qualifiedName} instead")
        }
    }
}

private fun reactorContextFrom(dfe: DgsDataFetchingEnvironment) =
    ReactiveDgsContext.from(dfe)?.reactorContext ?: Context.empty()
