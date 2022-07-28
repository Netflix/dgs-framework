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

package com.netflix.graphql.dgs.webmvc.autoconfigure

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.cacheControl.DgsCacheControlSupportProperties
import com.netflix.graphql.dgs.internal.DgsSchemaProvider
import com.netflix.graphql.dgs.internal.method.ArgumentResolver
import com.netflix.graphql.dgs.mvc.DefaultDgsGraphQLRequestHeaderValidator
import com.netflix.graphql.dgs.mvc.DgsGraphQLRequestHeaderValidator
import com.netflix.graphql.dgs.mvc.DgsRestController
import com.netflix.graphql.dgs.mvc.DgsRestSchemaJsonController
import com.netflix.graphql.dgs.mvc.GraphQLRequestContentTypePredicate
import com.netflix.graphql.dgs.mvc.GraphQLRequestHeaderValidationRule
import com.netflix.graphql.dgs.mvc.internal.method.HandlerMethodArgumentResolverAdapter
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver
import org.springframework.web.servlet.DispatcherServlet
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter
import org.springframework.web.servlet.mvc.method.annotation.ServletCookieValueMethodArgumentResolver
import org.springframework.web.servlet.mvc.method.annotation.ServletRequestDataBinderFactory
import kotlin.streams.toList

@Configuration
@ConditionalOnWebApplication
@EnableConfigurationProperties(value = [DgsWebMvcConfigurationProperties::class, DgsCacheControlSupportProperties::class])
open class DgsWebMvcAutoConfiguration {
    @Bean
    @Qualifier("dgsObjectMapper")
    @ConditionalOnMissingBean(name = ["dgsObjectMapper"])
    open fun dgsObjectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }

    @Bean
    open fun dgsRestController(
        dgsQueryExecutor: DgsQueryExecutor,
        @Qualifier("dgsObjectMapper") objectMapper: ObjectMapper,
        cacheControlProperties: DgsCacheControlSupportProperties
    ): DgsRestController {
        return DgsRestController(dgsQueryExecutor, objectMapper, DefaultDgsGraphQLRequestHeaderValidator(), cacheControlProperties.enabled)
    }

    @Configuration
    @ConditionalOnClass(DispatcherServlet::class)
    @ConditionalOnProperty(name = ["dgs.graphql.graphiql.enabled"], havingValue = "true", matchIfMissing = true)
    @Import(GraphiQLConfigurer::class)
    open class DgsGraphiQLConfiguration

    @Configuration
    @ConditionalOnProperty(name = ["dgs.graphql.schema-json.enabled"], havingValue = "true", matchIfMissing = true)
    open class DgsWebMvcSchemaJsonConfiguration {
        @Bean
        open fun dgsRestSchemaJsonController(dgsSchemaProvider: DgsSchemaProvider): DgsRestSchemaJsonController {
            return DgsRestSchemaJsonController(dgsSchemaProvider)
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    open class WebMvcArgumentHandlerConfiguration {

        @Qualifier
        private annotation class Dgs

        @Bean
        @Dgs
        open fun dgsWebDataBinderFactory(adapter: ObjectProvider<RequestMappingHandlerAdapter>): WebDataBinderFactory {
            return ServletRequestDataBinderFactory(listOf(), adapter.ifAvailable?.webBindingInitializer)
        }

        @Bean
        open fun requestHeaderMapResolver(@Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(RequestHeaderMapMethodArgumentResolver(), dataBinderFactory)
        }

        @Bean
        open fun requestHeaderResolver(beanFactory: ConfigurableBeanFactory, @Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(
                RequestHeaderMethodArgumentResolver(beanFactory), dataBinderFactory
            )
        }

        @Bean
        open fun requestParamResolver(@Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(RequestParamMethodArgumentResolver(false), dataBinderFactory)
        }

        @Bean
        open fun requestParamMapResolver(@Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(RequestParamMapMethodArgumentResolver(), dataBinderFactory)
        }

        @Bean
        open fun cookieValueResolver(beanFactory: ConfigurableBeanFactory, @Dgs dataBinderFactory: WebDataBinderFactory): ArgumentResolver {
            return HandlerMethodArgumentResolverAdapter(ServletCookieValueMethodArgumentResolver(beanFactory), dataBinderFactory)
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    open class WebMvcHeaderValidationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        open fun defaultOSSDgsGraphQLRequestHeadersValidator(
            validationRulesProvider: ObjectProvider<GraphQLRequestHeaderValidationRule>,
            contentTypePredicatesProviders: ObjectProvider<GraphQLRequestContentTypePredicate>,
        ): DgsGraphQLRequestHeaderValidator {
            return DefaultDgsGraphQLRequestHeaderValidator(
                validationRules = validationRulesProvider.orderedStream().toList(),
                contentTypePredicates = contentTypePredicatesProviders.orderedStream().toList()
            )
        }

        @Bean
        open fun graphQLRequestContentTypePredicates(): List<GraphQLRequestContentTypePredicate> {
            return GraphQLRequestContentTypePredicate.RECOMMENDED_GRAPHQL_CONTENT_TYPE_PREDICATES
        }

        @Bean
        @ConditionalOnProperty("dgs.graphql.header.validation.enabled", havingValue = "true", matchIfMissing = true)
        open fun graphqlRequestHeaderValidationRules(): List<GraphQLRequestHeaderValidationRule> {
            return DgsGraphQLRequestHeaderValidator.RECOMMENDED_GRAPHQL_REQUEST_HEADERS_VALIDATOR
        }
    }
}
