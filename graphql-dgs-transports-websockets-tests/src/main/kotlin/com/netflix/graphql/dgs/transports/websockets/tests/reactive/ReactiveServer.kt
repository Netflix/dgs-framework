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

package com.netflix.graphql.dgs.transports.websockets.tests.reactive

import com.example.server.DgsConstants
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsSubscription
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.graphql.dgs.webflux.autoconfiguration.DgsWebFluxAutoConfiguration
import com.netflix.graphql.dgs.webmvc.autoconfigure.DgsWebMvcAutoConfiguration
import org.reactivestreams.Publisher
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.runApplication
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.reactive.server.ReactiveWebServerFactory
import org.springframework.context.annotation.Bean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@SpringBootApplication(
    exclude = [DgsWebMvcAutoConfiguration::class, WebMvcAutoConfiguration::class],
    scanBasePackages = ["com.netflix.graphql.dgs.transports.websockets.tests.reactive"],
)
@ImportAutoConfiguration(classes = [DgsWebFluxAutoConfiguration::class, DgsAutoConfiguration::class, WebFluxAutoConfiguration::class])
open class ReactiveServer {
    @DgsComponent
    class ReactiveHelloWorldDataFetcher {

        @DgsQuery(field = DgsConstants.QUERY.Hello)
        fun helloWorld(): Mono<String> {
            return Mono.just("Hello World!")
        }

        @DgsMutation(field = DgsConstants.MUTATION.Hello)
        fun helloMutation(): Mono<String> {
            return Mono.just("Hello Mutation!")
        }
    }

    @DgsComponent
    class ReactiveGreetingsSubscription {

        @DgsSubscription(field = DgsConstants.SUBSCRIPTION.Greetings)
        fun greetings(): Publisher<String> {
            return Flux.fromIterable(listOf("Hi", "Bonjour", "Hola", "Ciao", "Zdravo"))
        }
    }

    @Bean
    open fun reactiveWebServerFactory(): ReactiveWebServerFactory? {
        return NettyReactiveWebServerFactory()
    }
}

fun main(args: Array<String>) {

    runApplication<ReactiveServer>(*args)
}
