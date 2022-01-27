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

interface DataFetcherResultProcessor {

    fun supportsType(originalResult: Any): Boolean

    fun process(originalResult: Any, dfe: DgsDataFetchingEnvironment): Any = process(originalResult)

    @Deprecated(
        "Replaced with process(originalResult, dfe)",
        replaceWith = ReplaceWith("process(originalResult: Any, dfe: DgsDataFetchingEnvironment)")
    )
    fun process(originalResult: Any): Any = originalResult
}
