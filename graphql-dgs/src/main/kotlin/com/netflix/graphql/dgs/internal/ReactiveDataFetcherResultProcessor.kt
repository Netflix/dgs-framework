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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.IllegalArgumentException

class MonoDataFetcherResultProcessor : DataFetcherResultProcessor {
    override fun supportsType(originalResult: Any): Boolean {
        return originalResult is Mono<*>
    }

    override fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any {
        if (originalResult is Mono<*>) {
            return originalResult.toFuture()
        } else {
            throw IllegalArgumentException("Instance passed to ${this::class.qualifiedName} was not a Mono<*>. It was a ${originalResult::class.qualifiedName} instead")
        }
    }
}

class FluxDataFetcherResultProcessor : DataFetcherResultProcessor {
    override fun supportsType(originalResult: Any): Boolean {
        return originalResult is Flux<*>
    }

    override fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any {
        if (originalResult is Flux<*>) {
            return originalResult.collectList().toFuture()
        } else {
            throw IllegalArgumentException("Instance passed to ${this::class.qualifiedName} was not a Flux<*>. It was a ${originalResult::class.qualifiedName} instead")
        }
    }
}
