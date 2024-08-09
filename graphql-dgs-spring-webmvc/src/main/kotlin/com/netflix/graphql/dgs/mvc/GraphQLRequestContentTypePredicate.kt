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

import org.springframework.core.annotation.Order
import org.springframework.http.MediaType

/**
 * A [GraphQLRequestContentTypePredicate] is a predicate function that is meant to be be evaluated against the
 * content-type expressed by the HTTP headers.
 *
 * @see DgsGraphQLRequestHeaderValidator
 * @see DefaultDgsGraphQLRequestHeaderValidator
 */
@Order
fun interface GraphQLRequestContentTypePredicate {
    fun accept(contentType: MediaType?): Boolean

    companion object {
        /** The media-types that a GraphQL should strictly support.*/
        private val STRICT_GRAPHQL_CONTENT_TYPES =
            listOf(MediaType.APPLICATION_JSON, GraphQLMediaTypes.GRAPHQL_MEDIA_TYPE, MediaType.MULTIPART_FORM_DATA)

        /**
         * Implementation of a content type predicate that will accept none-null content-types that match any of the
         * media-types defined by [STRICT_GRAPHQL_CONTENT_TYPES]
         */
        val STRICT_GRAPHQL_CONTENT_TYPES_PREDICATE =
            GraphQLRequestContentTypePredicate { mediaType ->
                mediaType != null && STRICT_GRAPHQL_CONTENT_TYPES.find { it.isCompatibleWith(mediaType) } != null
            }

        val RECOMMENDED_GRAPHQL_CONTENT_TYPE_PREDICATES = listOf(STRICT_GRAPHQL_CONTENT_TYPES_PREDICATE)
    }
}
