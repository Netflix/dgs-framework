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

package com.netflix.graphql.dgs.transports.websockets.tests.webmvc

import com.example.server.DgsConstants
import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.DgsSubscription
import org.reactivestreams.Publisher
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactor.core.publisher.Flux

@SpringBootApplication(scanBasePackages = ["com.netflix.graphql.dgs.transports.websockets.tests.webmvc"])
open class WebmvcServer

fun main(args: Array<String>) {
    runApplication<WebmvcServer>(*args)
}

@DgsComponent
class HelloWorldDataFetcher {

    @DgsQuery(field = DgsConstants.QUERY.Hello)
    fun helloWorld(): String {
        return "Hello World!"
    }

    @DgsMutation(field = DgsConstants.MUTATION.Hello)
    fun helloMutation(): String {
        return "Hello Mutation!"
    }
}

@DgsComponent
class GreetingsSubscription {

    @DgsSubscription(field = DgsConstants.SUBSCRIPTION.Greetings)
    fun greetings(): Publisher<String> {
        return Flux.fromIterable(listOf("Hi", "Bonjour", "Hola", "Ciao", "Zdravo"))
    }
}
