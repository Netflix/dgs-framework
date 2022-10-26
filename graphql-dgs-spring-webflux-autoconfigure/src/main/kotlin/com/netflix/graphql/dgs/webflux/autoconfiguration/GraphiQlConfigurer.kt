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

package com.netflix.graphql.dgs.webflux.autoconfiguration

import org.springframework.core.io.Resource
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import org.springframework.web.reactive.resource.PathResourceResolver
import org.springframework.web.reactive.resource.ResourceTransformer
import org.springframework.web.reactive.resource.ResourceTransformerChain
import org.springframework.web.reactive.resource.TransformedResource
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.io.BufferedReader
import java.io.IOException
import java.nio.charset.StandardCharsets

class GraphiQlConfigurer(private val configProps: DgsWebfluxConfigurationProperties) : WebFluxConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val graphqlPath = configProps.path
        val graphiQLTitle = configProps.graphiql.title
        registry
            .addResourceHandler(configProps.graphiql.path + "/**")
            .addResourceLocations("classpath:/graphiql/")
            .resourceChain(true)
            .addResolver(PathResourceResolver())
            .addTransformer(TokenReplacingTransformer(mapOf("<DGS_GRAPHQL_PATH>" to graphqlPath, "<DGS_GRAPHIQL_TITLE>" to graphiQLTitle), configProps))
    }

    class TokenReplacingTransformer(
        private val replaceMap: Map<String, String>,
        private val configProps: DgsWebfluxConfigurationProperties
    ) :
        ResourceTransformer {
        @Throws(IOException::class)

        override fun transform(
            exchange: ServerWebExchange,
            resource: Resource,
            transformerChain: ResourceTransformerChain
        ): Mono<Resource> {
            if (exchange.request.uri.toASCIIString().endsWith(configProps.graphiql.path + "/index.html")) {
                var content = resource.inputStream.bufferedReader().use(BufferedReader::readText)
                replaceMap.forEach { content = content.replace(it.key, it.value) }
                return Mono.just(
                    TransformedResource(
                        resource,
                        content.toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )
                )
            }
            return Mono.just(resource)
        }
    }
}
