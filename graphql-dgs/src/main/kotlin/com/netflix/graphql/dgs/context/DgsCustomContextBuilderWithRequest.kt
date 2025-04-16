/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.graphql.dgs.context

import org.springframework.http.HttpHeaders
import org.springframework.web.context.request.WebRequest

/**
 * When a bean implementing this interface is found, the framework will call the [build] method for every request.
 * The result of the [build] method is placed on the [DgsContext] and can be retrieved with [DgsContext.customContext]
 * or with one of the static methods on [DgsContext] given a DataFetchingEnvironment or batchLoaderEnvironment.
 */
interface DgsCustomContextBuilderWithRequest<T> {
    fun build(
        extensions: Map<String, Any>?,
        headers: HttpHeaders?,
        webRequest: WebRequest?,
    ): T
}
