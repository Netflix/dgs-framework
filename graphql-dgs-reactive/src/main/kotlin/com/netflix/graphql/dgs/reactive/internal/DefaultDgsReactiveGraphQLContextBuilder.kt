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

package com.netflix.graphql.dgs.reactive.internal

import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsRequestData
import com.netflix.graphql.dgs.reactive.DgsReactiveCustomContextBuilderWithRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.reactive.function.server.ServerRequest
import reactor.core.publisher.Mono
import java.util.*

open class DefaultDgsReactiveGraphQLContextBuilder(
    private val dgsReactiveCustomContextBuilderWithRequest: Optional<DgsReactiveCustomContextBuilderWithRequest<*>> = Optional.empty()
) {

    fun build(dgsRequestData: DgsReactiveRequestData?): Mono<DgsContext> {
        val customContext = if (dgsReactiveCustomContextBuilderWithRequest.isPresent) {
            dgsReactiveCustomContextBuilderWithRequest.get().build(
                dgsRequestData?.extensions ?: mapOf(),
                HttpHeaders.readOnlyHttpHeaders(
                    dgsRequestData?.headers
                        ?: HttpHeaders()
                ),
                dgsRequestData?.serverRequest
            )
        } else Mono.empty()

        return customContext.flatMap {
            Mono.just(
                DgsContext(
                    it,
                    dgsRequestData
                )
            )
        }.defaultIfEmpty(
            DgsContext(
                requestData = dgsRequestData
            )
        )
    }
}

/**
 * @param extensions Optional map of extensions - useful for customized GraphQL interactions between for example a gateway and dgs.
 * @param headers Http Headers
 * @param webRequest Spring [WebRequest]. This will only be available when deployed in a WebMVC (Servlet based) environment. See [serverRequest] for the WebFlux version.
 * @param serverRequest Spring reactive [ServerHttpRequest]. This will only be available when deployed in a WebFlux (non-Servlet) environment. See [webRequest] for the WebMVC version.
 */
data class DgsReactiveRequestData(
    override val extensions: Map<String, Any>? = emptyMap(),
    override val headers: HttpHeaders? = HttpHeaders.readOnlyHttpHeaders(HttpHeaders()),
    val serverRequest: ServerRequest? = null,
) : DgsRequestData
