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

package com.netflix.graphql.dgs.webmvc.autoconfigure

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.HttpRequestHandler
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter
import org.springframework.web.servlet.view.RedirectView
import org.springframework.web.util.UriComponentsBuilder

@Configuration
open class GraphiQLConfigurer(
    val configProps: DgsWebMvcConfigurationProperties
) : WebMvcConfigurer {

    private val modifiedHtml: String

    init {
        val html = ClassPathResource("graphiql/graphiql.html").inputStream.use { it.reader().readText() }
        modifiedHtml = html.replace("<DGS_GRAPHIQL_TITLE>", configProps.graphiql.title)
    }

    @Bean
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    open fun graphiQlHandlerMapping(): SimpleUrlHandlerMapping {
        val mapping = SimpleUrlHandlerMapping()
        mapping.order = 0  // set higher than the GraphQL handler mapping
        mapping.urlMap = mapOf(configProps.graphiql.path to graphiQlHandler())
        return mapping
    }

    @Bean
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    open fun graphiQlHandlerAdapter(): HttpRequestHandlerAdapter {
        return HttpRequestHandlerAdapter()
    }

    @Bean
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    open fun graphiQlHandler(): HttpRequestHandler {
        return HttpRequestHandler { request, response ->
            val path = request.getParameter("path") ?: configProps.path
            val wsPath = request.getParameter("wsPath") ?: "/subscriptions"

            if (request.getParameter("path") == null || request.getParameter("wsPath") == null) {
                val redirectUri = UriComponentsBuilder.fromPath(configProps.graphiql.path).queryParam("path", path)
                    .queryParam("wsPath", wsPath).build().toUriString()
                val redirectView = RedirectView(redirectUri)
                redirectView.render(null, request, response)
                return@HttpRequestHandler
            }

            response.contentType = MediaType.TEXT_HTML_VALUE
            response.writer.write(modifiedHtml)
        }
    }
}
