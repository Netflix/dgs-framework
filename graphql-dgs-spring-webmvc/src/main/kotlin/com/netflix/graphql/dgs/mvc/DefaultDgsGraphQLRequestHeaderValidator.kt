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

import org.springframework.http.HttpHeaders

class DefaultDgsGraphQLRequestHeaderValidator(
    private val contentTypePredicates: List<GraphQLRequestContentTypePredicate> = listOf(GraphQLRequestContentTypePredicate.STRICT_GRAPHQL_CONTENT_TYPES_PREDICATE),
    private val validationRules: List<GraphQLRequestHeaderValidationRule> = DgsGraphQLRequestHeaderValidator.RECOMMENDED_GRAPHQL_REQUEST_HEADERS_VALIDATOR,
) : DgsGraphQLRequestHeaderValidator {

    override fun assert(headers: HttpHeaders) {
        if (contentTypePredicates.isNotEmpty()) {
            contentTypePredicates.find { it.accept(headers.contentType) }
                ?: throw DgsGraphQLRequestHeaderValidator
                    .GraphqlRequestContentTypePredicateException("Unsupported Content-Type ${headers.contentType}")
        }
        validationRules.forEach { it.assert(headers) }
    }
}
