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

package com.netflix.graphql.dgs.subscriptions.graphql.sse

import com.netflix.graphql.dgs.DgsQueryExecutor
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.DispatcherServlet

@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(DispatcherServlet::class)
open class DgsGraphQLSSEAutoConfig(
    @Value("\${dgs.graphql.sse.pollPeriod:12000}") open val pollPeriod: Long,
) {
    @Bean
    open fun dgsGraphQlSseController(dgsQueryExecutor: DgsQueryExecutor): DgsGraphQLSSESubscriptionHandler =
        DgsGraphQLSSESubscriptionHandler(dgsQueryExecutor, pollPeriod)
}
