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

package com.netflix.graphql.dgs.mvc

import com.netflix.graphql.dgs.mvc.DgsGraphQLRequestHeaderValidator.GraphQLRequestHeaderRuleException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders

/**
 * Represents a function that has the intent to validate the HTTP Headers before the GraphQL Query is even parsed.
 * For example, the [GraphQLCSRFRequestHeaderValidationRule] enforces a `content-type` policy that prevents a CSRF
 * exploit for GraphQL endpoints.
 */
@Order
fun interface GraphQLRequestHeaderValidationRule {
    /**
     * Validate the [HttpHeaders], and in case it is not valid, throw a [GraphQLRequestHeaderRuleException] exception or any of its derivatives.
     * @throws GraphQLRequestHeaderRuleException
     */
    fun assert(headers: HttpHeaders)
}
