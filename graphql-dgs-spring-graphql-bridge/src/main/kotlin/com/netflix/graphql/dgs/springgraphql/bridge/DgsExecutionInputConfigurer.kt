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

package com.netflix.graphql.dgs.springgraphql.bridge

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.context.DgsCustomContextBuilder
import com.netflix.graphql.dgs.context.DgsCustomContextBuilderWithRequest
import com.netflix.graphql.dgs.reactive.internal.DgsReactiveRequestData
import org.springframework.graphql.web.WebGraphQlHandler
import org.springframework.graphql.web.WebInput
import org.springframework.graphql.web.WebInterceptor
import org.springframework.graphql.web.WebOutput
import reactor.core.publisher.Mono
import java.util.*

class DgsExecutionInputConfigurer(private val dgsCustomContextBuilder: Optional<DgsCustomContextBuilder<*>>, private val dgsCustomContextBuilderWithRequest: Optional<DgsCustomContextBuilderWithRequest<*>>) : WebInterceptor {
    override fun intercept(webInput: WebInput, next: WebGraphQlHandler): Mono<WebOutput> {
        webInput.configureExecutionInput { input, builder ->

            val requestData = DgsReactiveRequestData(headers = webInput.headers)
            if(dgsCustomContextBuilder.isPresent) {
                builder.context(DgsContext(dgsCustomContextBuilder.get().build(), requestData))
            } else {
                builder.context(DgsContext(null, requestData))
            }

            input
        }

        return next.handle(webInput)
    }
}